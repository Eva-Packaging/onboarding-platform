package xyz.catuns.onboarding.user.client;

import feign.FeignException;
import org.springframework.stereotype.Component;
import xyz.catuns.onboarding.user.exception.OnboardingServiceUnavailableException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OnboardingServiceClient {

    private final OnboardingServiceFeignClient feignClient;

    public OnboardingServiceClient(OnboardingServiceFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    public OnboardingInitResponse createOnboardingRequest(UUID userId, UUID correlationId, List<String> roleKeys) {
        try {
            return feignClient.createOnboardingRequest(new OnboardingInitRequest(userId, correlationId, roleKeys));
        } catch (FeignException e) {
            throw new OnboardingServiceUnavailableException(
                    "Onboarding service unavailable — could not create onboarding request", e);
        }
    }

    public Optional<OnboardingLatestResponse> getLatestOnboardingForUser(UUID userId) {
        try {
            return Optional.of(feignClient.getLatestOnboarding(userId));
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        } catch (FeignException e) {
            throw new OnboardingServiceUnavailableException(
                    "Onboarding service unavailable — could not fetch latest onboarding request", e);
        }
    }
}
