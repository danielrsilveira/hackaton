package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Formatação de datas para mensagens e e-mails (pt-BR). */
public final class Formato {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private Formato() {
    }

    public static String dataHora(LocalDateTime dt) {
        return dt == null ? "" : DATA_HORA.format(dt);
    }

    public static String periodo(LocalDateTime inicio, LocalDateTime termino) {
        boolean mesmoDia = inicio.toLocalDate().equals(termino.toLocalDate());
        return dataHora(inicio) + " – " + (mesmoDia ? HORA.format(termino) : dataHora(termino));
    }

    public static String periodo(Periodo p) {
        return periodo(p.inicio(), p.termino());
    }
}
