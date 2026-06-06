package xyz.catuns.onboarding.common.security.provider;

import xyz.catuns.spring.jwt.core.properties.JwtMetadata;
import xyz.catuns.spring.jwt.core.provider.SimpleTokenProvider;

import java.util.Objects;

public class PayloadTokenProvider extends SimpleTokenProvider<Payload>  {

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String IS_ADMIN_KEY = "isAdmin";

    public PayloadTokenProvider(JwtMetadata jwtProperties) {
        super(jwtProperties);
        this.customizer = (jwt, payload) -> {
            jwt.subject(payload.userId());
            jwt.claim(CORRELATION_ID_KEY, payload.correlationId());
            if (payload.isAdmin()) {
                jwt.claim(IS_ADMIN_KEY, true);
            }
        };

        this.validator = ((claims -> {
            String userId = Objects.requireNonNull(claims.getSubject(), "missing user id");
            Object correlationClaim = claims.get(CORRELATION_ID_KEY);
            String correlationId = correlationClaim != null
                    ? String.valueOf(correlationClaim)
                    : null;
            boolean isAdmin = Boolean.TRUE.equals(claims.get(IS_ADMIN_KEY));
            return new Payload(userId, correlationId, isAdmin);
        }));
    }
}
