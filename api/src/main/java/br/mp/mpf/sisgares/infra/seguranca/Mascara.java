package br.mp.mpf.sisgares.infra.seguranca;

/** Mascara dados pessoais antes de irem para o log (LGPD): "ana.souza@x.gov.br" vira "a***@x.gov.br". */
public final class Mascara {

    private Mascara() {
    }

    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return "(vazio)";
        }
        int arroba = email.indexOf('@');
        if (arroba < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
