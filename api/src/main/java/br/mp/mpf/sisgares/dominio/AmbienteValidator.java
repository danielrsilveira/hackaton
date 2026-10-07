package br.mp.mpf.sisgares.dominio;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Regras do cadastro de ambientes (F9/RF02). Classe pura: sem Spring e sem banco.
 * <ul>
 * <li>Descrição obrigatória (até 200 caracteres) e única entre os ambientes ativos da unidade.</li>
 * <li>Pai da mesma unidade, ativo, sem ciclo (não pode ser o próprio ambiente nem um descendente).</li>
 * <li>Inativação bloqueada se houver filhos ativos ou reservas não transcorridas no ambiente.</li>
 * <li>Mudança de pai bloqueada se criar conflito RN6 entre reservas não transcorridas já gravadas.</li>
 * </ul>
 */
public class AmbienteValidator {

    public static final String REGRA = "RF02";
    public static final int TAMANHO_DESCRICAO = 200;
    /** Quantos conflitos/reservas listar na mensagem, para não gerar um aviso gigante. */
    static final int LIMITE_LISTA = 5;

    private final Clock clock;

    public AmbienteValidator(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param id        id do ambiente em edição ou null na inclusão (o chamador garante que existe na unidade)
     * @param unidadeId unidade do administrador
     */
    public List<Erro> validar(Long id, AmbienteInput in, long unidadeId, DadosAmbiente dados) {
        List<Erro> erros = new ArrayList<>();
        Map<Long, AmbienteInfo> todos = new HashMap<>();
        for (AmbienteInfo a : dados.ambientes(unidadeId)) {
            todos.put(a.id(), a);
        }
        AmbienteInfo atual = id == null ? null : todos.get(id);
        boolean ativo = in.ativo() == null || in.ativo();
        String descricao = in.descricao() == null ? "" : in.descricao().strip();

        // Descrição
        if (descricao.isEmpty()) {
            erros.add(new Erro(REGRA, "Informe a descrição do ambiente."));
        } else if (descricao.length() > TAMANHO_DESCRICAO) {
            erros.add(new Erro(REGRA, "A descrição deve ter até %d caracteres.".formatted(TAMANHO_DESCRICAO)));
        } else if (ativo && todos.values().stream().anyMatch(a -> a.ativo() && !Objects.equals(a.id(), id)
                && a.descricao().strip().equalsIgnoreCase(descricao))) {
            erros.add(new Erro(REGRA, "Já existe um ambiente ativo com a descrição \"%s\".".formatted(descricao)));
        }

        // Pai: mesma unidade, ativo e sem ciclo
        boolean paiValido = true;
        if (in.idPai() != null) {
            AmbienteInfo pai = todos.get(in.idPai());
            if (pai == null) {
                erros.add(new Erro(REGRA, "O ambiente pai informado não existe nesta unidade."));
                paiValido = false;
            } else if (id != null && (in.idPai().equals(id) || descendentes(id, todos).contains(in.idPai()))) {
                erros.add(new Erro(REGRA, "O ambiente pai não pode ser o próprio ambiente nem um de seus filhos."));
                paiValido = false;
            } else if (ativo && !pai.ativo()) {
                erros.add(new Erro(REGRA, "O ambiente pai \"%s\" está inativo.".formatted(pai.descricao())));
            }
        }

        if (atual == null) {
            return erros;
        }
        LocalDateTime agora = LocalDateTime.now(clock);

        // Inativação: sem filhos ativos e sem reservas não transcorridas
        if (atual.ativo() && !ativo) {
            List<String> filhosAtivos = todos.values().stream()
                    .filter(a -> a.ativo() && Objects.equals(a.idPai(), id))
                    .map(AmbienteInfo::descricao).sorted().toList();
            if (!filhosAtivos.isEmpty()) {
                erros.add(new Erro(REGRA, "Inative antes os ambientes filhos: %s.".formatted(String.join(", ", filhosAtivos))));
            }
            List<PeriodoOcupado> pendentes = dados.periodosNaoTranscorridos(Set.of(id), agora);
            if (!pendentes.isEmpty()) {
                erros.add(new Erro(REGRA, "O ambiente tem reservas previstas ou em andamento: %s. Altere ou cancele essas reservas antes de inativá-lo."
                        .formatted(listar(pendentes))));
            }
        }

        // Mudança de pai: os novos ancestrais passam a conflitar com o ambiente e seus descendentes (RN6)
        if (paiValido && !Objects.equals(atual.idPai(), in.idPai())) {
            Set<Long> novos = ancestrais(in.idPai(), todos);
            novos.removeAll(ancestrais(atual.idPai(), todos));
            if (!novos.isEmpty()) {
                Set<Long> subarvore = descendentes(id, todos);
                subarvore.add(id);
                List<String> conflitos = conflitos(dados.periodosNaoTranscorridos(subarvore, agora),
                        dados.periodosNaoTranscorridos(novos, agora));
                if (!conflitos.isEmpty()) {
                    erros.add(new Erro("RN6", "Com o novo pai, reservas já gravadas passariam a conflitar: %s."
                            .formatted(String.join("; ", conflitos))));
                }
            }
        }
        return erros;
    }

    /** Ids dos descendentes (filhos, netos...) de {@code id}. Tolerante a ciclos já existentes. */
    static Set<Long> descendentes(long id, Map<Long, AmbienteInfo> todos) {
        Set<Long> r = new LinkedHashSet<>();
        List<Long> fila = new ArrayList<>(List.of(id));
        while (!fila.isEmpty()) {
            long atual = fila.removeFirst();
            for (AmbienteInfo a : todos.values()) {
                if (Objects.equals(a.idPai(), atual) && a.id() != id && r.add(a.id())) {
                    fila.add(a.id());
                }
            }
        }
        return r;
    }

    /** {@code id} e seus ancestrais. Vazio quando {@code id} é null. */
    static Set<Long> ancestrais(Long id, Map<Long, AmbienteInfo> todos) {
        Set<Long> r = new LinkedHashSet<>();
        for (Long p = id; p != null && r.add(p); ) {
            AmbienteInfo a = todos.get(p);
            p = a == null ? null : a.idPai();
        }
        return r;
    }

    private static List<String> conflitos(List<PeriodoOcupado> deA, List<PeriodoOcupado> deB) {
        List<String> r = new ArrayList<>();
        for (PeriodoOcupado a : deA) {
            for (PeriodoOcupado b : deB) {
                if (a.reservaId() != b.reservaId() && a.periodo().conflitaComMargem(b.periodo())) {
                    r.add("#%d (%s, %s) × #%d (%s, %s)".formatted(a.reservaId(), a.ambienteDescricao(),
                            Formato.periodo(a.periodo()), b.reservaId(), b.ambienteDescricao(), Formato.periodo(b.periodo())));
                }
            }
        }
        return limitar(r);
    }

    private static String listar(List<PeriodoOcupado> periodos) {
        return String.join(", ", limitar(periodos.stream()
                .sorted(Comparator.comparing(PeriodoOcupado::inicio))
                .map(p -> "#%d (%s)".formatted(p.reservaId(), Formato.periodo(p.periodo())))
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
