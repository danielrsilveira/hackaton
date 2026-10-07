package br.mp.mpf.sisgares.infra.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TravaModoAuthTest {

    private static final String ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TESTE0001";

    /** Contexto mínimo com só a trava, sem banco: o que importa é se a inicialização falha. */
    private ApplicationContextRunner contexto(String... profiles) {
        return new ApplicationContextRunner()
                .withUserConfiguration(TravaModoAuth.class)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profiles));
    }

    // ---- Inicialização (contexto Spring) ----

    @Test
    void simuladoSemProfileLocalNaoSobe() {
        contexto("prod").withPropertyValues("app.auth.modo=simulado").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("profile 'local'");
        });
    }

    @Test
    void modoAusenteEquivaleASimuladoEtambemNaoSobeSemLocal() {
        contexto("prod").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void simuladoComProfileLocalSobe() {
        contexto("local", "seed").withPropertyValues("app.auth.modo=simulado").run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void simuladoSemNenhumProfileAtivoUsaODefaultLocalDoApplicationYml() {
        // Sem profile ativo, o application.yml define spring.profiles.default=local,seed.
        new ApplicationContextRunner().withUserConfiguration(TravaModoAuth.class)
                .withPropertyValues("app.auth.modo=simulado", "spring.profiles.default=local,seed")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void cognitoSemProfileLocalSobeComIssuerHttpsEClientId() {
        contexto("prod").withPropertyValues("app.auth.modo=cognito", "app.auth.issuer-uri=" + ISSUER,
                "app.auth.client-id=abc").run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void cognitoSemIssuerOuClientIdNaoSobe() {
        contexto("prod").withPropertyValues("app.auth.modo=cognito", "app.auth.client-id=abc")
                .run(ctx -> assertThat(ctx).hasFailed());
        contexto("prod").withPropertyValues("app.auth.modo=cognito", "app.auth.issuer-uri=" + ISSUER)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void modoInvalidoNaoSobe() {
        contexto("local").withPropertyValues("app.auth.modo=cogntio").run(ctx -> assertThat(ctx).hasFailed());
    }

    // ---- Regras puras ----

    @Test
    void issuerSemHttpsSoNoProfileLocal() {
        assertThatThrownBy(() -> ConfigAuth.validar("cognito", false, "http://localhost:9000/pool", "abc"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("https");
        assertThatCode(() -> ConfigAuth.validar("cognito", true, "http://localhost:9000/pool", "abc"))
                .doesNotThrowAnyException();
    }

    @Test
    void mensagensNaoVazamValoresDeConfiguracao() {
        assertThatThrownBy(() -> ConfigAuth.validar("cognito", false, "http://segredo.interno/pool", "client-secreto"))
                .hasMessageNotContaining("segredo.interno").hasMessageNotContaining("client-secreto");
    }

    @Test
    void valoresDoModoSaoExatos() {
        assertThat(ModoAuth.de("simulado")).isEqualTo(ModoAuth.SIMULADO);
        assertThat(ModoAuth.de(" Cognito ")).isEqualTo(ModoAuth.COGNITO);
        assertThatThrownBy(() -> ModoAuth.de("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ModoAuth.de(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mascaraDeEmailNaoExpoeOUsuario() {
        assertThat(Mascara.email("ana.souza@exemplo.gov.br")).isEqualTo("a***@exemplo.gov.br");
        assertThat(Mascara.email(null)).isEqualTo("(vazio)");
        assertThat(Mascara.email("sem-arroba")).isEqualTo("***");
    }
}
