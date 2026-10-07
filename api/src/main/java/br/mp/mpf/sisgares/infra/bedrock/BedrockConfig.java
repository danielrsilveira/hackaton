package br.mp.mpf.sisgares.infra.bedrock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Cliente do Bedrock. Só é criado quando app.bedrock.enabled=true, para que a aplicação
 * suba sem credenciais AWS no ambiente padrão.
 *
 * Credenciais via cadeia padrão do SDK (DefaultCredentialsProvider): variáveis de ambiente
 * AWS_*, profile AWS_PROFILE (ex.: "workshop") ou role da instância/task na AWS.
 */
@Configuration
@ConditionalOnProperty(name = "app.bedrock.enabled", havingValue = "true")
public class BedrockConfig {

    @Bean
    BedrockRuntimeClient bedrockRuntimeClient(@Value("${app.bedrock.region}") String region) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        // Modo adaptativo trata throttling (RPM/TPM) e falhas transitórias.
                        .retryStrategy(RetryMode.ADAPTIVE)
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .build())
                .build();
    }
}
