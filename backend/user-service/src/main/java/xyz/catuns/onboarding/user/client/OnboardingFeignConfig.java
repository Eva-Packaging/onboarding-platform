package xyz.catuns.onboarding.user.client;

import org.springframework.context.annotation.Bean;
import xyz.catuns.onboarding.common.security.provider.ServiceTokenProvider;

class OnboardingFeignConfig {

    @Bean
    FeignServiceAuthInterceptor feignServiceAuthInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new FeignServiceAuthInterceptor(serviceTokenProvider);
    }
}