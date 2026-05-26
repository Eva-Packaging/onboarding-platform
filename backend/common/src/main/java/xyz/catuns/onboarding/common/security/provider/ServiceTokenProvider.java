package xyz.catuns.onboarding.common.security.provider;

import xyz.catuns.spring.jwt.core.provider.SimpleTokenProvider;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

public class ServiceTokenProvider extends SimpleTokenProvider<ServicePrincipal> {

    public static final String TOKEN_TYPE_KEY = "type";
    public static final String SERVICE_TOKEN_TYPE = "SERVICE";

    public ServiceTokenProvider(String secret, Duration expiration, String issuer) {
        super(secret, expiration, issuer);
        this.customizer = (jwt, principal) -> {
            jwt.subject(principal.serviceName());
            jwt.audience().add(principal.audience());
            jwt.claim(TOKEN_TYPE_KEY, SERVICE_TOKEN_TYPE);
        };
        this.validator = claims -> {
            Object typeObj = claims.get(TOKEN_TYPE_KEY);
            if (!SERVICE_TOKEN_TYPE.equals(String.valueOf(typeObj))) {
                throw new IllegalStateException("Token is not a service token");
            }
            String serviceName = Objects.requireNonNull(claims.getSubject(), "missing service name");
            Set<String> audiences = claims.getAudience();
            String audience = (audiences != null && !audiences.isEmpty())
                    ? audiences.iterator().next()
                    : null;
            return new ServicePrincipal(serviceName, audience);
        };
    }
}
