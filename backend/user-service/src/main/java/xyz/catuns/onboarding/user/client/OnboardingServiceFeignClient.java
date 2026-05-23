package xyz.catuns.onboarding.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "${app.feign.clients.onboarding}", path = "/api/v1/internal")
interface OnboardingServiceFeignClient {

    @PostMapping("/onboarding-requests")
    OnboardingInitResponse createOnboardingRequest(@RequestBody OnboardingInitRequest request);
}