package br.mp.mpf.sisgares.infra.seguranca;

import java.util.Locale;

/** Modo de autenticação da API, definido por {@code app.auth.modo} (variável {@code AUTH_MODO}). */
public enum ModoAuth {

    /** Demonstração local: o usuário vem do header X-Usuario-Id. Sem autenticação real. */
    SIMULADO,

    /** Produção: exige JWT do Amazon Cognito. O header X-Usuario-Id é ignorado. */
    COGNITO;

    /** Aceita somente os valores exatos (sem cair em um default silencioso quando há erro de digitação). */
    public static ModoAuth de(String valor) {
        if (valor == null) {
            throw new IllegalStateException("AUTH_MODO não definido. Valores aceitos: simulado, cognito.");
        }
        return switch (valor.strip().toLowerCase(Locale.ROOT)) {
            case "simulado" -> SIMULADO;
            case "cognito" -> COGNITO;
            default -> throw new IllegalStateException("AUTH_MODO inválido. Valores aceitos: simulado, cognito.");
        };
    }
}
