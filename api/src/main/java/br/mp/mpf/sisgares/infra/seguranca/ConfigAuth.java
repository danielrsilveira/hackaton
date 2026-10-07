package br.mp.mpf.sisgares.infra.seguranca;

/**
 * Regras de consistência da configuração de autenticação, sem Spring (testáveis).
 * As mensagens nunca incluem valores de configuração.
 */
public final class ConfigAuth {

    private ConfigAuth() {
    }

    /**
     * Valida a combinação modo × profile × parâmetros do Cognito e devolve o modo.
     *
     * @throws IllegalStateException se a combinação for insegura ou incompleta (a aplicação não sobe)
     */
    public static ModoAuth validar(String modo, boolean perfilLocal, String issuerUri, String clientId) {
        ModoAuth m = ModoAuth.de(modo);
        if (m == ModoAuth.SIMULADO && !perfilLocal) {
            throw new IllegalStateException("AUTH_MODO=simulado só é permitido com o profile 'local' ativo "
                    + "(o usuário simulado pelo header X-Usuario-Id pode ser forjado por qualquer cliente). "
                    + "Defina AUTH_MODO=cognito, AUTH_ISSUER_URI e AUTH_CLIENT_ID.");
        }
        if (m == ModoAuth.COGNITO) {
            if (vazio(issuerUri) || vazio(clientId)) {
                throw new IllegalStateException("AUTH_MODO=cognito exige AUTH_ISSUER_URI e AUTH_CLIENT_ID.");
            }
            if (!perfilLocal && !issuerUri.strip().startsWith("https://")) {
                throw new IllegalStateException("AUTH_ISSUER_URI deve usar https:// fora do profile 'local'.");
            }
        }
        return m;
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
