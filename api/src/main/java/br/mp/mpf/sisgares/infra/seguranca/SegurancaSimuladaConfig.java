package br.mp.mpf.sisgares.infra.seguranca;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Modo {@code simulado} (somente profile {@code local}, ver {@link TravaModoAuth}): o Spring Security fica
 * aberto, preservando o comportamento da demonstração (header X-Usuario-Id, sem login).
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "app.auth.modo", havingValue = "simulado", matchIfMissing = true)
public class SegurancaSimuladaConfig {

    @Bean
    SecurityFilterChain simuladoFilterChain(HttpSecurity http) {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
