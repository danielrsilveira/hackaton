package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;

/** Período de uma reserva não cancelada que pede o recurso. {@code quantidade} nula conta como 1 (RN8). */
public record UsoRecurso(long reservaId, long unidadeId, Long ambienteId, Integer quantidade, LocalDateTime inicio,
        LocalDateTime termino) {

    public Periodo periodo() {
        return new Periodo(inicio, termino);
    }

    public int qtd() {
        return quantidade == null ? 1 : quantidade;
    }
}
