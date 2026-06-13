package xyz.catuns.onboarding.provisioning.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.catuns.onboarding.provisioning.atlassian.AtlassianHealthChecker;
import xyz.catuns.onboarding.provisioning.dto.ProviderHealthResponse;
import xyz.catuns.onboarding.provisioning.github.GithubHealthChecker;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class ProviderHealthController {

    private final GithubHealthChecker githubHealthChecker;
    private final AtlassianHealthChecker atlassianHealthChecker;

    public ProviderHealthController(GithubHealthChecker githubHealthChecker, AtlassianHealthChecker atlassianHealthChecker) {
        this.githubHealthChecker = githubHealthChecker;
        this.atlassianHealthChecker = atlassianHealthChecker;
    }

    @GetMapping("/provider-health")
    public ResponseEntity<List<ProviderHealthResponse>> getProviderHealth() {
        return ResponseEntity.ok(List.of(githubHealthChecker.check(), atlassianHealthChecker.check()));
    }
}
