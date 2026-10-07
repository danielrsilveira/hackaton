package br.mp.mpf.sisgares.infra.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Trava de segurança: a API não sobe com {@code AUTH_MODO=simulado} sem o profile {@code local}.
 * Assim, um deploy esquecido no modo simulado falha na inicialização em vez de aceitar identidade forjada.
 * Também recusa o modo cognito sem issuer / client id.
 */
@Component
public class TravaModoAuth {

    public TravaModoAuth(Environment env,
            @Value("${app.auth.modo:simulado}") String modo,
            @Value("${app.auth.issuer-uri:}") String issuerUri,
            @Value("${app.auth.client-id:}") String clientId) {
        // acceptsProfiles considera o profile default (local,seed) quando nenhum está ativo.
        ConfigAuth.validar(modo, env.acceptsProfiles(Profiles.of("local")), issuerUri, clientId);
    }
}
