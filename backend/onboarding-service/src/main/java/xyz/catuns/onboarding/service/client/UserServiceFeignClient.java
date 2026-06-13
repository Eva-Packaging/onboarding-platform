package xyz.catuns.onboarding.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "${app.feign.clients.user}", path = "/api/v1/internal", configuration = UserFeignConfig.class)
public interface UserServiceFeignClient {

    @GetMapping("/external-identities?provider=ATLASSIAN")
    ExternalIdentityLookupResponse getAtlassianIdentityByEmail(@RequestParam("email") String email);

    @GetMapping("/external-identities?provider=GITHUB")
    ExternalIdentityLookupResponse getGithubIdentityByUserProfileId(@RequestParam("userProfileId") UUID userProfileId);

    @PostMapping("/identity-links")
    IdentityLinkResponse createIdentityLink(@RequestBody IdentityLinkCreateRequest request);
}