package br.mp.mpf.sisgares.infra.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Modo {@code cognito}: API stateless, resource server OAuth2 que exige Bearer JWT do user pool.
 * CSRF desligado porque não há sessão nem cookie de autenticação: só o header Authorization autentica.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "app.auth.modo", havingValue = "cognito")
public class SegurancaCognitoConfig {

    @Bean
    SecurityFilterChain cognitoFilterChain(HttpSecurity http) {
        http
                .cors(Customizer.withDefaults()) // usa o CORS do AppConfig (app.cors.allowed-origins)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/ping", "/actuator/health/**", "/mock-snp/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(naoAutenticado())
                        .accessDeniedHandler(acessoNegado()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(naoAutenticado())
                        .accessDeniedHandler(acessoNegado()));
        return http.build();
    }

    /**
     * Valida a assinatura pelo JWKS do user pool ({@code <issuer>/.well-known/jwks.json}, RS256) e os claims
     * de {@link ValidadoresCognito}. O JWKS é buscado sob demanda: a API sobe mesmo sem acesso ao Cognito.
     */
    @Bean
    JwtDecoder jwtDecoder(@Value("${app.auth.issuer-uri}") String issuerUri,
            @Value("${app.auth.client-id}") String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(ValidadoresCognito.normalizar(issuerUri) + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(ValidadoresCognito.para(issuerUri, clientId));
        return decoder;
    }

    /** 401 sem detalhes: não informa se o token está expirado, malformado, de outro pool etc. */
    static AuthenticationEntryPoint naoAutenticado() {
        return (request, response, ex) -> escrever(response, HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
    }

    static AccessDeniedHandler acessoNegado() {
        return (request, response, ex) -> escrever(response, HttpStatus.FORBIDDEN, "Acesso não autorizado.");
    }

    private static void escrever(HttpServletResponse response, HttpStatus status, String mensagem) throws IOException {
        response.setStatus(status.value());
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer"); // sem error/error_description
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status.value() + ",\"mensagem\":\"" + mensagem + "\"}");
    }
}
