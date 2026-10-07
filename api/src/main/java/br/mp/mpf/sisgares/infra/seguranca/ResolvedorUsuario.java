package br.mp.mpf.sisgares.infra.seguranca;

import br.mp.mpf.sisgares.infra.Usuario;

/** Descobre quem está fazendo a requisição corrente. A implementação depende de {@code app.auth.modo}. */
public interface ResolvedorUsuario {

    /**
     * @throws org.springframework.web.server.ResponseStatusException 401 se não identificado, 403 se sem acesso ao sistema
     */
    Usuario resolver();
}
