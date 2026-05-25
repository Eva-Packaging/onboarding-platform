package xyz.catuns.onboarding.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "github-api", url = "${app.feign.clients.github-url:https://api.github.com}")
public interface GitHubApiClient {

    @GetMapping("/user")
    GitHubUserResponse getUser(@RequestHeader("Authorization") String bearerToken);
}
