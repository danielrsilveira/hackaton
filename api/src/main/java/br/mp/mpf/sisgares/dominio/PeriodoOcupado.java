package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;

/** Período de outra reserva (não cancelada) para um ambiente. */
public record PeriodoOcupado(long reservaId, long ambienteId, String ambienteDescricao,
        LocalDateTime inicio, LocalDateTime termino) {

    public Periodo periodo() {
        return new Periodo(inicio, termino);
    }
}
