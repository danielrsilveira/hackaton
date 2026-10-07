package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** RN13: status calculado pelo horário. */
public enum StatusReserva {
    PREVISTA, EM_ANDAMENTO, TRANSCORRIDA, CANCELADA;

    public static StatusReserva calcular(boolean cancelada, List<Periodo> periodos, LocalDateTime agora) {
        if (cancelada) {
            return CANCELADA;
        }
        if (periodos == null || periodos.isEmpty()) {
            return PREVISTA;
        }
        LocalDateTime inicio = periodos.stream().map(Periodo::inicio).min(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime termino = periodos.stream().map(Periodo::termino).max(Comparator.naturalOrder()).orElseThrow();
        if (agora.isBefore(inicio)) {
            return PREVISTA;
        }
        if (agora.isAfter(termino)) {
            return TRANSCORRIDA;
        }
        return EM_ANDAMENTO;
    }
}
