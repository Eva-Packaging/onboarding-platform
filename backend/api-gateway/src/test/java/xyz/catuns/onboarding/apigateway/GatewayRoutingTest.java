package xyz.catuns.onboarding.apigateway;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutingTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void userServiceRoute_pointsToLbUriWithPathRewrite() {
        Route route = fetchRoute("user-service");

        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://user-service");
        assertThat(route.getFilters()).isNotEmpty();
    }

    @Test
    void onboardingServiceRoute_pointsToOnboardingService() {
        Route route = fetchRoute("onboarding-service");

        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://onboarding-service");
    }

    @Test
    void adminRoute_routesToOnboardingService() {
        Route route = fetchRoute("admin-to-onboarding");

        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://onboarding-service");
    }

    @Test
    void provisioningServiceRoute_pointsToLbUriWithPathRewrite() {
        Route route = fetchRoute("provisioning-service");

        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://provisioning-service");
        assertThat(route.getFilters()).isNotEmpty();
    }

    private Route fetchRoute(String id) {
        return routeLocator.getRoutes()
                .filter(r -> id.equals(r.getId()))
                .blockFirst(Duration.ofSeconds(5));
    }
}