package br.mp.mpf.sisgares.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.Usuario;

/**
 * Resolve o usuário pelo header X-Usuario-Id, escolhido no seletor de perfil do front.
 * SEM AUTENTICAÇÃO: aceitável só na demonstração. Em produção, trocar por OIDC/Cognito.
 */
@Component
public class UsuarioAtual {

    public static final String HEADER = "X-Usuario-Id";

    private final CadastroRepository cadastros;

    public UsuarioAtual(CadastroRepository cadastros) {
        this.cadastros = cadastros;
    }

    public Usuario de(Long id) {
        return cadastros.usuario(id == null ? 1L : id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário desconhecido."));
    }

    public Usuario gestor(Long id) {
        Usuario u = de(id);
        if (!u.gestor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restrito a administrador e atendente.");
        }
        return u;
    }

    public Usuario admin(Long id) {
        Usuario u = de(id);
        if (!u.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restrito ao administrador.");
        }
        return u;
    }
}
