package br.mp.mpf.sisgares.infra.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import br.mp.mpf.sisgares.PingController;
import br.mp.mpf.sisgares.dominio.DadosValidacao;
import br.mp.mpf.sisgares.infra.AppConfig;
import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.ReservaRepository;
import br.mp.mpf.sisgares.infra.Usuario;
import br.mp.mpf.sisgares.servico.AmbienteService;
import br.mp.mpf.sisgares.servico.ReservaService;
import br.mp.mpf.sisgares.web.CadastroController;
import br.mp.mpf.sisgares.web.ReservaController;
import br.mp.mpf.sisgares.web.UsuarioAtual;
import br.mp.mpf.sisgares.web.UsuarioController;
import br.mp.mpf.sisgares.web.UsuariosSimuladosController;

/**
 * Modo {@code cognito}: tokens reais (assinados com chave de teste) passam pelo mesmo decoder/validadores
 * de produção; só o JWKS do user pool é trocado por uma chave local.
 */
@WebMvcTest(controllers = { ReservaController.class, CadastroController.class, UsuarioController.class,
        UsuariosSimuladosController.class, PingController.class })
@Import({ SegurancaCognitoConfig.class, ResolvedorCognito.class, UsuarioAtual.class, AppConfig.class,
        AuthCognitoWebTest.ConfigDeTeste.class })
@TestPropertySource(properties = { "app.auth.modo=cognito", "app.auth.issuer-uri=" + TokenDeTeste.ISSUER,
        "app.auth.client-id=" + TokenDeTeste.CLIENT_ID, "app.cors.allowed-origins=http://localhost:4200" })
class AuthCognitoWebTest {

    private static final Usuario ANA = new Usuario(1, "Ana Souza (fictícia)", "ana.souza@exemplo.gov.br", "SOLICITANTE", 1, null);
    private static final Usuario CARLA = new Usuario(3, "Carla Mendes (fictícia)", "carla.mendes@exemplo.gov.br", "ADMIN", 1, null);
    private static final Usuario DIEGO = new Usuario(4, "Diego Rocha (fictício)", "diego.rocha@exemplo.gov.br", "ATENDENTE", 1, 2L);
    /** Corpo válido: a desserialização acontece antes da checagem de perfil no controller. */
    private static final String CONFIG_JSON = "{\"antecedenciaMin\":0,\"horaMin\":\"08:00\",\"horaMax\":\"18:00\",\"snpEndpoint\":\"http://localhost:8080/mock-snp/pedidos\",\"unidadeHoraMin\":null,\"unidadeHoraMax\":null}";
    private static final Map<String, Usuario> POR_EMAIL = Map.of(ANA.email(), ANA, CARLA.email(), CARLA, DIEGO.email(), DIEGO);

    @TestConfiguration
    static class ConfigDeTeste {
        @Bean
        TokenDeTeste tokenDeTeste() {
            return new TokenDeTeste();
        }

        @Bean
        @Primary
        JwtDecoder jwtDecoderDeTeste(TokenDeTeste t) {
            return t.decoder();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired TokenDeTeste tokens;

    @MockitoBean CadastroRepository cadastros;
    @MockitoBean ReservaRepository reservaRepo;
    @MockitoBean ReservaService reservaService;
    @MockitoBean AmbienteService ambienteService;
    @MockitoBean DadosValidacao dadosValidacao;

    @BeforeEach
    void usuarios() {
        given(cadastros.usuarioPorEmail(anyString()))
                .willAnswer(i -> Optional.ofNullable(POR_EMAIL.get(((String) i.getArgument(0)).toLowerCase())));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ---------- 401: sem token / token inválido ----------

    @Test
    void semTokenRetorna401SemDetalhes() throws Exception {
        MvcResult r = mvc.perform(get("/api/ambientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("error_description", "Jwt", "exception");
    }

    @Test
    void tokenMalformadoRetorna401SemRevelarMotivo() throws Exception {
        MvcResult r = mvc.perform(get("/api/me").header("Authorization", bearer("isto.nao.e-um-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("error_description", "Jwt", "decode");
        assertThat(r.getResponse().getHeader("WWW-Authenticate")).doesNotContain("error_description");
    }

    @Test
    void tokenAssinadoPorOutraChaveRetorna401() throws Exception {
        String t = tokens.novo(ANA.email()).assinadoPorOutraChave().build();
        mvc.perform(get("/api/me").header("Authorization", bearer(t))).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoRetorna401() throws Exception {
        String t = tokens.novo(ANA.email()).expira(Instant.now().minusSeconds(3600)).build();
        mvc.perform(get("/api/me").header("Authorization", bearer(t))).andExpect(status().isUnauthorized());
    }

    @Test
    void issuerDeOutroPoolRetorna401() throws Exception {
        String t = tokens.novo(ANA.email())
                .issuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_OUTROPOOL").build();
        mvc.perform(get("/api/me").header("Authorization", bearer(t))).andExpect(status().isUnauthorized());
    }

    @Test
    void audienceDeOutroClientRetorna401() throws Exception {
        String t = tokens.novo(ANA.email()).audience("outro-client").build();
        mvc.perform(get("/api/me").header("Authorization", bearer(t))).andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenNaoEAceito() throws Exception {
        String t = tokens.novo(ANA.email()).tokenUse("access").build();
        mvc.perform(get("/api/me").header("Authorization", bearer(t))).andExpect(status().isUnauthorized());
    }

    // ---------- Rotas públicas ----------

    @Test
    void pingEPublico() throws Exception {
        mvc.perform(get("/api/ping")).andExpect(status().isOk());
    }

    @Test
    void healthEMockSnpPassamSemToken() throws Exception {
        // Neste slice não há actuator nem o mock-snp: 404 prova que o filtro de segurança deixou passar.
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isNotFound());
        mvc.perform(get("/mock-snp/pedidos/1")).andExpect(status().isNotFound());
    }

    @Test
    void outrosEndpointsDoActuatorExigemToken() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }

    // ---------- Identidade vem do token, perfil vem do banco ----------

    @Test
    void tokenValidoIdentificaUsuarioPeloEmail() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", bearer(tokens.idToken(ANA.email()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.perfil").value("SOLICITANTE"));
    }

    @Test
    void emailEmMaiusculasEncontraOMesmoUsuario() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", bearer(tokens.idToken("ANA.Souza@Exemplo.gov.br"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void emailSemCadastroRetorna403GenericoSemCriarUsuario() throws Exception {
        String email = "desconhecido@exemplo.gov.br";
        MvcResult r = mvc.perform(get("/api/me").header("Authorization", bearer(tokens.idToken(email))))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain(email);
        org.mockito.Mockito.verify(cadastros, org.mockito.Mockito.never()).usuario(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void emailNaoVerificadoRetorna403() throws Exception {
        String t = tokens.novo(ANA.email()).emailVerificado(false).build();
        mvc.perform(get("/api/me").header("Authorization", bearer(t))).andExpect(status().isForbidden());
    }

    @Test
    void tokenSemEmailRetorna403() throws Exception {
        String t = tokens.novo(null).build();
        mvc.perform(get("/api/me").header("Authorization", bearer(t))).andExpect(status().isForbidden());
    }

    // ---------- 403 por perfil (checagem no backend) ----------

    @Test
    void solicitanteNaoAcessaRotaDeAtendente() throws Exception {
        mvc.perform(get("/api/painel-atendente?inicio=2026-10-07&dias=1")
                .header("Authorization", bearer(tokens.idToken(ANA.email()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/notificacoes").header("Authorization", bearer(tokens.idToken(ANA.email()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void solicitanteNaoAcessaRotaDeAdmin() throws Exception {
        String auth = bearer(tokens.idToken(ANA.email()));
        mvc.perform(get("/api/ambientes?todos=true").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(put("/api/config").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(CONFIG_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void atendenteAcessaPainelMasNaoRotaDeAdmin() throws Exception {
        String auth = bearer(tokens.idToken(DIEGO.email()));
        mvc.perform(get("/api/painel-atendente?inicio=2026-10-07&dias=1").header("Authorization", auth))
                .andExpect(status().isOk());
        mvc.perform(get("/api/ambientes?todos=true").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(put("/api/config").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(CONFIG_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAcessaRotaDeAdmin() throws Exception {
        mvc.perform(get("/api/ambientes?todos=true").header("Authorization", bearer(tokens.idToken(CARLA.email()))))
                .andExpect(status().isOk());
    }

    // ---------- O header X-Usuario-Id é ignorado por completo ----------

    @Test
    void headerDeOutroUsuarioEIgnoradoComTokenValido() throws Exception {
        String auth = bearer(tokens.idToken(ANA.email()));
        // Ana (solicitante) tenta se passar por Carla (admin, id 3) e por Diego (atendente, id 4).
        mvc.perform(get("/api/me").header("Authorization", auth).header("X-Usuario-Id", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/ambientes?todos=true").header("Authorization", auth).header("X-Usuario-Id", "3"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/painel-atendente?inicio=2026-10-07&dias=1").header("Authorization", auth)
                .header("X-Usuario-Id", "4")).andExpect(status().isForbidden());
    }

    @Test
    void headerSozinhoNaoAutentica() throws Exception {
        mvc.perform(get("/api/me").header("X-Usuario-Id", "3")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ambientes?todos=true").header("X-Usuario-Id", "3")).andExpect(status().isUnauthorized());
    }

    // ---------- /api/usuarios não existe neste modo ----------

    @Test
    void listaDeUsuariosNaoEExpostaNoModoCognito() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", bearer(tokens.idToken(CARLA.email()))))
                .andExpect(status().isNotFound());
        org.mockito.Mockito.verify(cadastros, org.mockito.Mockito.never()).usuarios();
    }

    // ---------- CORS preservado ----------

    @Test
    void preflightDaOrigemPermitidaPassaSemToken() throws Exception {
        mvc.perform(options("/api/reservas").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void preflightDeOrigemNaoPermitidaEhRecusado() throws Exception {
        mvc.perform(options("/api/reservas").header("Origin", "http://malicioso.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
