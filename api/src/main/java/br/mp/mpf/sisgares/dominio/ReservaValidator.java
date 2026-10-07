package br.mp.mpf.sisgares.dominio;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Regras de negócio da reserva (RN1–RN9, RN12 e ambiente disponível, RF01). Classe pura: sem Spring e sem banco,
 * recebe o relógio e uma porta de leitura de dados.
 */
public class ReservaValidator {

    private final Clock clock;

    public ReservaValidator(Clock clock) {
        this.clock = clock;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }

    /**
     * Validação completa de inclusão/alteração. Chamada na escolha de cada período (prévia) e
     * novamente ao salvar (RN7).
     *
     * @param reservaIdEmEdicao  id da reserva sendo alterada (excluída das checagens de conflito) ou null
     * @param periodosOriginais  períodos já gravados da reserva em edição: não são cobrados de novo pela RN4
     */
    public List<Erro> validar(ReservaInput in, ConfigRegras cfg, long unidadeId, DadosValidacao dados,
            Long reservaIdEmEdicao, List<Periodo> periodosOriginais) {
        List<Erro> erros = new ArrayList<>();
        List<Periodo> originais = periodosOriginais == null ? List.of() : periodosOriginais;

        // RN2: campos obrigatórios
        if (in.finalidade() == null || in.finalidade().isBlank()) {
            erros.add(new Erro("RN2", "Informe a finalidade da reserva."));
        }
        if (in.qtdParticipantes() == null || in.qtdParticipantes() < 1) {
            erros.add(new Erro("RN2", "Informe a quantidade estimada de participantes (mínimo 1)."));
        }
        if (in.ambienteId() == null && (in.complementoAmbiente() == null || in.complementoAmbiente().isBlank())) {
            erros.add(new Erro("RN2",
                    "Sem ambiente solicitado (\"Não solicitado / local próprio\"), o complemento do ambiente é obrigatório."));
        }

        // RF01: o ambiente precisa existir, estar ativo e pertencer à unidade
        boolean ambienteValido = true;
        if (in.ambienteId() != null) {
            Optional<AmbienteInfo> amb = dados.ambiente(in.ambienteId());
            if (amb.isEmpty() || !amb.get().ativo() || amb.get().unidadeId() != unidadeId) {
                erros.add(new Erro(AmbienteValidator.REGRA, "O ambiente escolhido não está disponível nesta unidade."));
                ambienteValido = false;
            }
        }

        // RN1: ao menos um período, término posterior ao início
        List<Periodo> periodos = in.periodos() == null ? List.of() : in.periodos();
        if (periodos.isEmpty()) {
            erros.add(new Erro("RN1", "Informe pelo menos um período."));
        }
        Map<Integer, Periodo> validos = new LinkedHashMap<>();
        for (int i = 0; i < periodos.size(); i++) {
            Periodo p = periodos.get(i);
            int n = i + 1;
            if (p == null || p.inicio() == null || p.termino() == null) {
                erros.add(new Erro("RN1", "Período " + n + ": informe início e término."));
            } else if (!p.termino().isAfter(p.inicio())) {
                erros.add(new Erro("RN1", "Período " + n + ": o término deve ser posterior ao início."));
            } else {
                validos.put(n, p);
            }
        }

        // RN3: faixa de horário
        validos.forEach((n, p) -> {
            if (foraDaFaixa(p.inicio().toLocalTime(), cfg) || foraDaFaixa(p.termino().toLocalTime(), cfg)) {
                erros.add(new Erro("RN3", "Período %d: início e término devem estar entre %s e %s."
                        .formatted(n, cfg.horaMin(), cfg.horaMax())));
            }
        });

        // RN4: antecedência mínima (períodos já gravados e inalterados não são cobrados de novo)
        LocalDateTime limite = agora().plusMinutes(cfg.antecedenciaMin());
        validos.forEach((n, p) -> {
            if (!originais.contains(p) && p.inicio().isBefore(limite)) {
                erros.add(new Erro("RN4", "Período %d: o início exige antecedência mínima de %d minutos (a partir de %s)."
                        .formatted(n, cfg.antecedenciaMin(), Formato.dataHora(limite))));
            }
        });

        // RN5/RN6: conflito com o mesmo ambiente, seus pais e filhos, com margem de 30 min
        if (in.ambienteId() != null && ambienteValido && !validos.isEmpty()) {
            long ambienteId = in.ambienteId();
            Set<Long> relacionados = dados.ambientesRelacionados(ambienteId);
            validos.forEach((n, p) -> {
                List<PeriodoOcupado> ocupados = dados.periodosOcupados(relacionados,
                        p.inicio().minus(Periodo.MARGEM), p.termino().plus(Periodo.MARGEM), reservaIdEmEdicao);
                for (PeriodoOcupado o : ocupados) {
                    if (o.periodo().conflitaComMargem(p)) {
                        boolean mesmo = o.ambienteId() == ambienteId;
                        erros.add(new Erro(mesmo ? "RN5" : "RN6",
                                "Período %d conflita com a reserva #%d (%s%s, %s), considerando a margem de 30 minutos."
                                        .formatted(n, o.reservaId(), o.ambienteDescricao(),
                                                mesmo ? "" : " – ambiente relacionado", Formato.periodo(o.periodo()))));
                    }
                }
            });
        }

        // RN9 (disponibilidade do recurso) e RN8 (quantidade de recursos limitados)
        List<ItemRecurso> itens = in.recursos() == null ? List.of() : in.recursos();
        for (ItemRecurso item : itens) {
            if (item == null || item.recursoId() == null) {
                continue;
            }
            Optional<RecursoInfo> opt = dados.recurso(item.recursoId());
            if (opt.isEmpty() || !opt.get().ativo()) {
                erros.add(new Erro("RN9", "O recurso %d não está disponível.".formatted(item.recursoId())));
                continue;
            }
            RecursoInfo r = opt.get();
            if (r.unidadeId() != null && r.unidadeId() != unidadeId) {
                erros.add(new Erro("RN9", "%s não é oferecido nesta unidade.".formatted(r.descricao())));
                continue;
            }
            if (!r.ambientesVinculados().isEmpty()
                    && (in.ambienteId() == null || !r.ambientesVinculados().contains(in.ambienteId()))) {
                erros.add(new Erro("RN9",
                        "%s só pode ser solicitado em reservas dos ambientes vinculados a ele.".formatted(r.descricao())));
                continue;
            }
            if (r.limitado()) {
                Integer qtd = item.quantidade();
                if (qtd == null || qtd < 1) {
                    erros.add(new Erro("RN8", "Informe a quantidade de %s.".formatted(r.descricao())));
                    continue;
                }
                validos.forEach((n, p) -> {
                    int reservados = dados.quantidadeReservada(r.id(), p.inicio(), p.termino(), reservaIdEmEdicao);
                    if (reservados + qtd > r.disponibilidade()) {
                        erros.add(new Erro("RN8", "%s: no período %d há %d de %d unidade(s) livre(s); foram pedidas %d."
                                .formatted(r.descricao(), n, Math.max(0, r.disponibilidade() - reservados),
                                        r.disponibilidade(), qtd)));
                    }
                });
            }
        }
        return erros;
    }

    /** RN12: só reservas não transcorridas (e não canceladas) podem ser alteradas. */
    public List<Erro> validarEdicao(boolean cancelada, List<Periodo> periodosAtuais) {
        StatusReserva status = StatusReserva.calcular(cancelada, periodosAtuais, agora());
        return switch (status) {
            case CANCELADA -> List.of(new Erro("RN12", "Reserva cancelada não pode ser alterada."));
            case TRANSCORRIDA -> List.of(new Erro("RN12", "Reserva já transcorrida não pode ser alterada."));
            default -> List.of();
        };
    }

    /** RN12: cancelamento exige antecedência mínima em relação ao início. */
    public List<Erro> validarCancelamento(boolean cancelada, List<Periodo> periodosAtuais, ConfigRegras cfg) {
        List<Erro> erros = new ArrayList<>(validarEdicao(cancelada, periodosAtuais));
        if (!erros.isEmpty() || periodosAtuais.isEmpty()) {
            return erros;
        }
        LocalDateTime inicio = periodosAtuais.stream().map(Periodo::inicio).min(Comparator.naturalOrder()).orElseThrow();
        if (agora().plusMinutes(cfg.antecedenciaMin()).isAfter(inicio)) {
            erros.add(new Erro("RN12", "O cancelamento exige antecedência mínima de %d minutos do início (%s)."
                    .formatted(cfg.antecedenciaMin(), Formato.dataHora(inicio))));
        }
        return erros;
    }

    private static boolean foraDaFaixa(LocalTime t, ConfigRegras cfg) {
        return t.isBefore(cfg.horaMin()) || t.isAfter(cfg.horaMax());
    }
}
