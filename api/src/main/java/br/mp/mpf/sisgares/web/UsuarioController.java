package br.mp.mpf.sisgares.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.mp.mpf.sisgares.infra.Usuario;

@RestController
@RequestMapping("/api")
public class UsuarioController {

    private final UsuarioAtual usuarios;

    public UsuarioController(UsuarioAtual usuarios) {
        this.usuarios = usuarios;
    }

    /** O próprio usuário autenticado (o front usa o perfil para montar o menu). Vale nos dois modos. */
    @GetMapping("/me")
    public Usuario me() {
        return usuarios.de();
    }
}
