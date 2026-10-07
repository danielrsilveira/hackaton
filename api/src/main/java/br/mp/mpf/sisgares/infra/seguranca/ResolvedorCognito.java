package br.mp.mpf.sisgares.infra.seguranca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.Usuario;

/**
 * Modo {@code cognito}: o usuário vem exclusivamente do JWT já validado pelo filtro do Spring Security.
 * O header X-Usuario-Id não é lido em lugar nenhum deste modo.
 *
 * <p>O token só prova a identidade (e-mail verificado). O perfil e a unidade vêm da tabela {@code usuario},
 * fonte única, para nunca divergirem de {@code cognito:groups}. E-mail sem cadastro não cria usuário:
 * responde 403 com mensagem genérica, igual para "sem e-mail", "e-mail não verificado" e "e-mail desconhecido",
 * para não revelar quais e-mails existem.
 */
@Component
@ConditionalOnProperty(name = "app.auth.modo", havingValue = "cognito")
public class ResolvedorCognito implements ResolvedorUsuario {

    private static final Logger log = LoggerFactory.getLogger(ResolvedorCognito.class);

    static final String MENSAGEM_NEGADO = "Acesso não autorizado.";

    private final CadastroRepository cadastros;

    public ResolvedorCognito(CadastroRepository cadastros) {
        this.cadastros = cadastros;
    }

    @Override
    public Usuario resolver() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth) || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
        }
        Jwt jwt = jwtAuth.getToken();
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank() || !emailVerificado(jwt)) {
            log.warn("Acesso negado: token sem e-mail verificado.");
            throw negado();
        }
        return cadastros.usuarioPorEmail(email).orElseThrow(() -> {
            log.warn("Acesso negado: e-mail {} sem cadastro no SISGARES.", Mascara.email(email));
            return negado();
        });
    }

    private static boolean emailVerificado(Jwt jwt) {
        Object v = jwt.getClaims().get("email_verified");
        return Boolean.TRUE.equals(v) || (v instanceof String s && "true".equalsIgnoreCase(s));
    }

    private static ResponseStatusException negado() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, MENSAGEM_NEGADO);
    }
}
