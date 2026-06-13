package xyz.catuns.onboarding.service.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import xyz.catuns.onboarding.common.security.provider.ServicePrincipal;
import xyz.catuns.onboarding.common.security.provider.ServiceTokenProvider;

class FeignServiceAuthInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final ServicePrincipal PRINCIPAL = new ServicePrincipal("onboarding-service", "user-service");

    private final ServiceTokenProvider serviceTokenProvider;

    FeignServiceAuthInterceptor(ServiceTokenProvider serviceTokenProvider) {
        this.serviceTokenProvider = serviceTokenProvider;
    }

    @Override
    public void apply(RequestTemplate template) {
        String token = serviceTokenProvider.generate(PRINCIPAL).value();
        template.header(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
    }
}