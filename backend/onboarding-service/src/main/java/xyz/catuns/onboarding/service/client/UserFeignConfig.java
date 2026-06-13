package xyz.catuns.onboarding.service.client;

import org.springframework.context.annotation.Bean;
import xyz.catuns.onboarding.common.security.provider.ServiceTokenProvider;

class UserFeignConfig {

    @Bean
    FeignServiceAuthInterceptor feignServiceAuthInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new FeignServiceAuthInterceptor(serviceTokenProvider);
    }
}