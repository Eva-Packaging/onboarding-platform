package xyz.catuns.onboarding.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.catuns.spring.jwt.auth.AuthTokenProvider;
import xyz.catuns.spring.jwt.autoconfigure.properties.JwtProperties;

@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    private final JwtProperties properties;

    @Bean
    AuthTokenProvider defaultAuthTokenProvider() {
        return new AuthTokenProvider(properties);
    }
}
