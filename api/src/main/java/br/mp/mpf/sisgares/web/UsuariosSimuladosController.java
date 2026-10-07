package br.mp.mpf.sisgares.web;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.Usuario;

/**
 * Lista de usuários fictícios para o seletor de perfil. Existe SOMENTE no modo simulado: no modo cognito
 * a rota não é registrada (404), para não expor nomes e e-mails de todos os usuários (LGPD).
 */
@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "app.auth.modo", havingValue = "simulado", matchIfMissing = true)
public class UsuariosSimuladosController {

    private final CadastroRepository cadastros;

    public UsuariosSimuladosController(CadastroRepository cadastros) {
        this.cadastros = cadastros;
    }

    @GetMapping("/usuarios")
    public List<Usuario> usuarios() {
        return cadastros.usuarios();
    }
}
