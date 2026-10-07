package br.mp.mpf.sisgares.infra.seguranca;

import java.util.List;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

/**
 * Validações do JWT do Cognito, além da assinatura (RS256 pelo JWKS do user pool):
 * <ul>
 *   <li>{@code iss} igual ao issuer configurado;</li>
 *   <li>{@code exp} / {@code nbf} (tolerância padrão de 60 s);</li>
 *   <li>{@code token_use = id}: a API aceita somente o ID token, nunca o access token;</li>
 *   <li>{@code aud} contém o client id do app client do SISGARES (rejeita tokens de outros clients/pools).</li>
 * </ul>
 *
 * <p><b>Por que o ID token e não o access token?</b> O SISGARES identifica o usuário pelo claim {@code email}
 * (a tabela {@code usuario} é a fonte única de perfil). O access token do Cognito não traz {@code email}:
 * só {@code sub}, {@code username}, {@code cognito:groups}, {@code client_id} e {@code scope}. Personalizá-lo
 * exigiria o trigger Pre Token Generation V2/V3, que depende de plano pago do Cognito. A API é o backend do
 * próprio app client (a audiência do ID token é exatamente o client id dela), então aceitar o ID token é
 * seguro desde que {@code aud} e {@code token_use} sejam verificados, como aqui. Se a API passar a atender
 * outros clients, migrar para access token + mapeamento por {@code sub}.
 */
public final class ValidadoresCognito {

    private ValidadoresCognito() {
    }

    public static OAuth2TokenValidator<Jwt> para(String issuerUri, String clientId) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(normalizar(issuerUri)),
                new JwtClaimValidator<String>("token_use", "id"::equals),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(clientId)));
    }

    /** O claim iss do Cognito não termina em "/"; tolera a barra final na configuração. */
    public static String normalizar(String issuerUri) {
        String s = issuerUri.strip();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
