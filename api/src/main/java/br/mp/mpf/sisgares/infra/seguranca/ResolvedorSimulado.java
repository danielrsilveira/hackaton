package br.mp.mpf.sisgares.infra.seguranca;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.Usuario;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Modo {@code simulado}: usuário escolhido no seletor de perfil do front, enviado no header X-Usuario-Id.
 * SEM AUTENTICAÇÃO: só existe com {@code AUTH_MODO=simulado}, e a {@link TravaModoAuth} impede esse modo
 * fora do profile {@code local}. No modo cognito este bean nem é criado.
 */
@Component
@ConditionalOnProperty(name = "app.auth.modo", havingValue = "simulado", matchIfMissing = true)
public class ResolvedorSimulado implements ResolvedorUsuario {

    public static final String HEADER = "X-Usuario-Id";

    private final CadastroRepository cadastros;
    private final HttpServletRequest request; // proxy da requisição corrente

    public ResolvedorSimulado(CadastroRepository cadastros, HttpServletRequest request) {
        this.cadastros = cadastros;
        this.request = request;
    }

    @Override
    public Usuario resolver() {
        long id = idDoHeader();
        return cadastros.usuario(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário desconhecido."));
    }

    /** Sem o header, assume o usuário 1 (comportamento histórico do modo simulado). */
    private long idDoHeader() {
        String valor = request.getHeader(HEADER);
        if (valor == null || valor.isBlank()) {
            return 1L;
        }
        try {
            return Long.parseLong(valor.strip());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cabeçalho " + HEADER + " inválido.");
        }
    }
}
