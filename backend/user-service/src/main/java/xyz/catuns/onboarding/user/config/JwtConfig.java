package xyz.catuns.onboarding.user.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.catuns.onboarding.common.security.provider.PayloadTokenProvider;
import xyz.catuns.spring.jwt.autoconfigure.properties.JwtProperties;

@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    private final JwtProperties properties;

    @Bean
    PayloadTokenProvider payloadTokenProvider() {
        return new PayloadTokenProvider(properties);
    }

}