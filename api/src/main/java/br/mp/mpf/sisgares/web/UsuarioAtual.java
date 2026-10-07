package br.mp.mpf.sisgares.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import br.mp.mpf.sisgares.infra.Usuario;
import br.mp.mpf.sisgares.infra.seguranca.ResolvedorUsuario;

/**
 * Usuário da requisição corrente e checagem de perfil (sempre no backend, nunca só no front).
 * De onde vem a identidade depende de {@code app.auth.modo}: header X-Usuario-Id (simulado, só em
 * desenvolvimento local) ou JWT do Cognito (cognito). Os controllers não conhecem a diferença.
 */
@Component
public class UsuarioAtual {

    private final ResolvedorUsuario resolvedor;

    public UsuarioAtual(ResolvedorUsuario resolvedor) {
        this.resolvedor = resolvedor;
    }

    public Usuario de() {
        return resolvedor.resolver();
    }

    public Usuario gestor() {
        Usuario u = de();
        if (!u.gestor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restrito a administrador e atendente.");
        }
        return u;
    }

    public Usuario admin() {
        Usuario u = de();
        if (!u.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restrito ao administrador.");
        }
        return u;
    }
}
