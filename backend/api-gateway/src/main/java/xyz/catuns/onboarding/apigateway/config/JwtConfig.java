package xyz.catuns.onboarding.apigateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.catuns.onboarding.apigateway.config.properties.JwtProperties;
import xyz.catuns.onboarding.apigateway.provider.Payload;
import xyz.catuns.onboarding.apigateway.provider.PayloadTokenProvider;
import xyz.catuns.spring.jwt.core.provider.TokenProvider;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class JwtConfig {

    private final JwtProperties properties;

    @Bean
    TokenProvider<Payload> payloadTokenProvider() {
        return new PayloadTokenProvider(properties);
    }
}
