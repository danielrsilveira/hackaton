package br.mp.mpf.sisgares.dominio;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Regras do cadastro de recursos (F9/RF06) e do vínculo recurso × ambiente (RF08). Classe pura.
 * <ul>
 * <li>Descrição obrigatória (até 200 caracteres) e única entre os recursos ativos oferecidos na unidade.</li>
 * <li>Grupo existente e ativo; ícone dentre os disponíveis ({@link IconesRecurso}).</li>
 * <li>Unidade: a do administrador ou nenhuma (todas as unidades).</li>
 * <li>Limitado: disponibilidade de 1 a 9999. Não limitado: disponibilidade 0.</li>
 * <li>Com reservas previstas ou em andamento que pedem o recurso, bloqueia: inativar; restringir a uma unidade
 * onde elas não estão (RN9); e reduzir a disponibilidade abaixo do que já foi pedido (RN8).</li>
 * <li>RF08: ambientes da unidade, sem repetição; ambiente inativo só se já vinculado; restringir a ambientes
 * é bloqueado se reservas futuras pedem o recurso em outro ambiente ou sem ambiente (RN9).</li>
 * </ul>
 */
public class RecursoValidator {

    public static final String REGRA = "RF06";
    public static final String REGRA_AMBIENTES = "RF08";
    public static final int TAMANHO_DESCRICAO = 200;
    public static final int DISPONIBILIDADE_MAXIMA = 9999;
    static final int LIMITE_LISTA = 5;

    private final Clock clock;

    public RecursoValidator(Clock clock) {
        this.clock = clock;
    }

    /** Disponibilidade gravada: a informada para recurso limitado, 0 para os demais. */
    public static int disponibilidade(RecursoInput in) {
        return Boolean.TRUE.equals(in.limitado()) && in.disponibilidade() != null ? in.disponibilidade() : 0;
    }

    /**
     * @param id        id do recurso em edição ou null na inclusão (o chamador garante que é visível na unidade)
     * @param unidadeId unidade do administrador
     */
    public List<Erro> validar(Long id, RecursoInput in, long unidadeId, DadosRecurso dados) {
        List<Erro> erros = new ArrayList<>();
        List<RecursoResumo> existentes = dados.recursos(unidadeId);
        RecursoResumo atual = id == null ? null
                : existentes.stream().filter(r -> r.id() == id).findFirst().orElse(null);
        boolean ativo = in.ativo() == null || in.ativo();
        boolean limitado = Boolean.TRUE.equals(in.limitado());
        String descricao = in.descricao() == null ? "" : in.descricao().strip();

        if (descricao.isEmpty()) {
            erros.add(new Erro(REGRA, "Informe a descrição do recurso."));
        } else if (descricao.length() > TAMANHO_DESCRICAO) {
            erros.add(new Erro(REGRA, "A descrição deve ter até %d caracteres.".formatted(TAMANHO_DESCRICAO)));
        } else if (ativo && existentes.stream().anyMatch(r -> r.ativo() && !Objects.equals(r.id(), id)
                && r.descricao().strip().equalsIgnoreCase(descricao))) {
            erros.add(new Erro(REGRA, "Já existe um recurso ativo com a descrição \"%s\".".formatted(descricao)));
        }

        GrupoInfo grupo = in.grupoId() == null ? null : dados.grupos().get(in.grupoId());
        if (grupo == null) {
            erros.add(new Erro(REGRA, "Escolha o grupo do recurso."));
        } else if (!grupo.ativo() && (atual == null || atual.grupoId() != grupo.id())) {
            erros.add(new Erro(REGRA, "O grupo \"%s\" está inativo.".formatted(grupo.descricao())));
        }

        if (in.iconeArquivo() == null || !IconesRecurso.DISPONIVEIS.contains(in.iconeArquivo())) {
            erros.add(new Erro(REGRA, "Escolha um dos ícones disponíveis."));
        }

        if (in.unidadeId() != null && in.unidadeId() != unidadeId) {
            erros.add(new Erro(REGRA, "O recurso só pode ser restrito à sua unidade ou oferecido em todas."));
        }

        if (limitado && (in.disponibilidade() == null || in.disponibilidade() < 1
                || in.disponibilidade() > DISPONIBILIDADE_MAXIMA)) {
            erros.add(new Erro("RN8", "Recurso limitado precisa de disponibilidade entre 1 e %d."
                    .formatted(DISPONIBILIDADE_MAXIMA)));
            limitado = false; // evita checar picos com uma disponibilidade inválida
        }

        if (atual == null || !erros.isEmpty()) {
            return erros;
        }
        List<UsoRecurso> usos = dados.usosNaoTranscorridos(id, LocalDateTime.now(clock));
        if (usos.isEmpty()) {
            return erros;
        }

        if (atual.ativo() && !ativo) {
            erros.add(new Erro(REGRA, "O recurso é pedido em reservas previstas ou em andamento: %s. Altere ou cancele essas reservas antes de inativá-lo."
                    .formatted(listar(usos))));
        }
        if (in.unidadeId() != null) {
            List<UsoRecurso> fora = usos.stream().filter(u -> u.unidadeId() != in.unidadeId()).toList();
            if (!fora.isEmpty()) {
                erros.add(new Erro("RN9", "Há reservas de outras unidades que pedem o recurso: %s.".formatted(listar(fora))));
            }
        }
        if (limitado) {
            List<String> excessos = excessos(usos, in.disponibilidade());
            if (!excessos.isEmpty()) {
                erros.add(new Erro("RN8", "Com %d unidade(s), reservas já gravadas passariam do disponível: %s."
                        .formatted(in.disponibilidade(), String.join("; ", excessos))));
            }
        }
        return erros;
    }

    /**
     * RF08: valida a nova lista de ambientes do recurso.
     *
     * @param ambienteIds       ambientes da unidade escolhidos (vazio = sem restrição)
     * @param jaVinculados      ambientes da unidade já vinculados (podem continuar mesmo se inativos)
     * @param vinculosMantidos  vínculos com ambientes de outras unidades, que não mudam
     */
    public List<Erro> validarAmbientes(long recursoId, List<Long> ambienteIds, long unidadeId, Set<Long> jaVinculados,
            Set<Long> vinculosMantidos, DadosRecurso dados) {
        if (ambienteIds == null) {
            return List.of(new Erro(REGRA_AMBIENTES, "Informe a lista de ambientes (pode ser vazia)."));
        }
        List<Erro> erros = new ArrayList<>();
        Map<Long, AmbienteInfo> daUnidade = dados.ambientes(unidadeId).stream()
                .collect(Collectors.toMap(AmbienteInfo::id, Function.identity()));
        Set<Long> vistos = new HashSet<>();
        for (Long a : ambienteIds) {
            AmbienteInfo amb = a == null ? null : daUnidade.get(a);
            if (amb == null) {
                erros.add(new Erro(REGRA_AMBIENTES, "O ambiente %s não existe nesta unidade.".formatted(a)));
            } else if (!vistos.add(a)) {
                erros.add(new Erro(REGRA_AMBIENTES, "%s aparece mais de uma vez.".formatted(amb.descricao())));
            } else if (!amb.ativo() && !jaVinculados.contains(a)) {
                erros.add(new Erro(REGRA_AMBIENTES, "%s está inativo e não pode ser vinculado.".formatted(amb.descricao())));
            }
        }
        if (!erros.isEmpty()) {
            return erros;
        }
        Set<Long> finais = new HashSet<>(vistos);
        finais.addAll(vinculosMantidos);
        if (finais.isEmpty()) {
            return erros; // sem restrição: o recurso pode ser pedido em qualquer ambiente
        }
        List<UsoRecurso> fora = dados.usosNaoTranscorridos(recursoId, LocalDateTime.now(clock)).stream()
                .filter(u -> u.ambienteId() == null || !finais.contains(u.ambienteId())).toList();
        if (!fora.isEmpty()) {
            erros.add(new Erro("RN9", "Reservas previstas ou em andamento pedem o recurso em outros ambientes: %s."
                    .formatted(listar(fora))));
        }
        return erros;
    }

    /**
     * RN8 aplicada às reservas já gravadas: para cada período, soma o pedido de todas as reservas que o cruzam
     * (mesmo critério da validação da reserva) e aponta os que passam da nova disponibilidade.
     */
    static List<String> excessos(List<UsoRecurso> usos, int disponibilidade) {
        List<String> r = new ArrayList<>();
        for (UsoRecurso u : usos.stream().sorted(Comparator.comparing(UsoRecurso::inicio)).toList()) {
            Map<Long, Integer> porReserva = new LinkedHashMap<>();
            for (UsoRecurso v : usos) {
                if (v.periodo().cruza(u.periodo())) {
                    porReserva.put(v.reservaId(), v.qtd());
                }
            }
            int total = porReserva.values().stream().mapToInt(Integer::intValue).sum();
            if (total > disponibilidade) {
                r.add("%s: %d pedido(s) nas reservas %s".formatted(Formato.periodo(u.periodo()), total,
                        porReserva.keySet().stream().map(id -> "#" + id).collect(Collectors.joining(", "))));
            }
        }
        return limitar(r.stream().distinct().toList());
    }

    private static String listar(Collection<UsoRecurso> usos) {
        return String.join(", ", limitar(usos.stream()
                .sorted(Comparator.comparing(UsoRecurso::inicio))
                .map(u -> "#%d (%s)".formatted(u.reservaId(), Formato.periodo(u.periodo())))
                .toList()));
    }

    private static List<String> limitar(List<String> itens) {
        if (itens.size() <= LIMITE_LISTA) {
            return itens;
        }
        List<String> r = new ArrayList<>(itens.subList(0, LIMITE_LISTA));
        r.add("e mais %d".formatted(itens.size() - LIMITE_LISTA));
        return r;
    }
}
