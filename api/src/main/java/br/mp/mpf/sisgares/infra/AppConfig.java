package br.mp.mpf.sisgares.infra;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.mp.mpf.sisgares.dominio.AmbienteValidator;
import br.mp.mpf.sisgares.dominio.RecursoValidator;
import br.mp.mpf.sisgares.dominio.ReservaValidator;

@Configuration
public class AppConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String[] origens;

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    ReservaValidator reservaValidator(Clock clock) {
        return new ReservaValidator(clock);
    }

    @Bean
    AmbienteValidator ambienteValidator(Clock clock) {
        return new AmbienteValidator(clock);
    }

    @Bean
    RecursoValidator recursoValidator(Clock clock) {
        return new RecursoValidator(clock);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origens).allowedMethods("GET", "POST", "PUT", "DELETE");
    }
}
