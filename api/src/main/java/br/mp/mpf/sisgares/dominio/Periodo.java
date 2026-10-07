package br.mp.mpf.sisgares.dominio;

import java.time.Duration;
import java.time.LocalDateTime;

/** Intervalo [inicio, termino). Pode começar num dia e terminar em outro. */
public record Periodo(LocalDateTime inicio, LocalDateTime termino) {

    /** RN5/RN6: intervalo mínimo entre períodos de reservas do mesmo ambiente. */
    public static final Duration MARGEM = Duration.ofMinutes(30);

    /** Interseção simples (usada na contagem de recursos, RN8). */
    public boolean cruza(Periodo outro) {
        return inicio.isBefore(outro.termino) && outro.inicio.isBefore(termino);
    }

    /** Interseção considerando a margem de tolerância de 30 minutos (RN5/RN6). */
    public boolean conflitaComMargem(Periodo outro) {
        return inicio.isBefore(outro.termino.plus(MARGEM)) && outro.inicio.isBefore(termino.plus(MARGEM));
    }
}
