package br.mp.mpf.sisgares.infra.seguranca;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.mp.mpf.sisgares.PingController;
import br.mp.mpf.sisgares.dominio.DadosValidacao;
import br.mp.mpf.sisgares.infra.AppConfig;
import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.ReservaRepository;
import br.mp.mpf.sisgares.infra.Usuario;
import br.mp.mpf.sisgares.servico.AmbienteService;
import br.mp.mpf.sisgares.servico.InterpretacaoService;
import br.mp.mpf.sisgares.servico.SetorService;
import br.mp.mpf.sisgares.servico.ReservaService;
import br.mp.mpf.sisgares.web.CadastroController;
import br.mp.mpf.sisgares.web.ReservaController;
import br.mp.mpf.sisgares.web.UsuarioAtual;
import br.mp.mpf.sisgares.web.UsuarioController;
import br.mp.mpf.sisgares.web.UsuariosSimuladosController;

/** Modo {@code simulado} (padrão local): o comportamento da demonstração, com o header X-Usuario-Id, não mudou. */
@WebMvcTest(controllers = { ReservaController.class, CadastroController.class, UsuarioController.class,
        UsuariosSimuladosController.class, PingController.class })
@Import({ SegurancaSimuladaConfig.class, ResolvedorSimulado.class, UsuarioAtual.class, AppConfig.class })
@TestPropertySource(properties = { "app.auth.modo=simulado" })
class AuthSimuladoWebTest {

    private static final Usuario ANA = new Usuario(1, "Ana Souza (fictícia)", "ana.souza@exemplo.gov.br", "SOLICITANTE", 1, null);
    private static final Usuario CARLA = new Usuario(3, "Carla Mendes (fictícia)", "carla.mendes@exemplo.gov.br", "ADMIN", 1, null);

    @Autowired MockMvc mvc;

    @MockitoBean CadastroRepository cadastros;
    @MockitoBean ReservaRepository reservaRepo;
    @MockitoBean ReservaService reservaService;
    @MockitoBean AmbienteService ambienteService;
    @MockitoBean SetorService setorService;
    @MockitoBean InterpretacaoService interpretacaoService;
    @MockitoBean DadosValidacao dadosValidacao;

    @BeforeEach
    void usuarios() {
        given(cadastros.usuario(1L)).willReturn(Optional.of(ANA));
        given(cadastros.usuario(3L)).willReturn(Optional.of(CARLA));
        given(cadastros.usuario(99L)).willReturn(Optional.empty());
        given(cadastros.usuarios()).willReturn(List.of(ANA, CARLA));
    }

    @Test
    void semHeaderAssumeUsuario1() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void headerEscolheOUsuario() throws Exception {
        mvc.perform(get("/api/me").header("X-Usuario-Id", "3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ADMIN"));
    }

    @Test
    void usuarioDesconhecidoRetorna401() throws Exception {
        mvc.perform(get("/api/me").header("X-Usuario-Id", "99")).andExpect(status().isUnauthorized());
    }

    @Test
    void headerNaoNumericoRetorna400() throws Exception {
        mvc.perform(get("/api/me").header("X-Usuario-Id", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void perfilContinuaSendoChecadoNoBackend() throws Exception {
        mvc.perform(get("/api/ambientes?todos=true").header("X-Usuario-Id", "1")).andExpect(status().isForbidden());
        mvc.perform(get("/api/ambientes?todos=true").header("X-Usuario-Id", "3")).andExpect(status().isOk());
        mvc.perform(get("/api/painel-atendente?inicio=2026-10-07&dias=1").header("X-Usuario-Id", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listaDeUsuariosParaOSeletorExiste() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
}
