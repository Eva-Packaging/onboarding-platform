package xyz.catuns.onboarding.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.catuns.onboarding.user.api.dto.MeResponse;
import xyz.catuns.onboarding.user.security.JwtPrincipalExtractor;
import xyz.catuns.onboarding.user.service.UserProfileService;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Profile")
public class UserProfileController {

    private final UserProfileService profileService;
    private final JwtPrincipalExtractor principalExtractor;

    public UserProfileController(UserProfileService profileService,
                                 JwtPrincipalExtractor principalExtractor) {
        this.profileService = profileService;
        this.principalExtractor = principalExtractor;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns the authenticated user's profile")
    @ApiResponse(responseCode = "200", description = "HTTP Status OK")
    public ResponseEntity<MeResponse> getMe(
        HttpServletRequest request,
        @RequestParam(name = "include", required = false, defaultValue = "") String includeParam
    ) {
        String githubUserId = principalExtractor.extractGithubUserId(request);
        Set<String> includes = parseIncludes(includeParam);
        return ResponseEntity.ok(profileService.getMe(githubUserId, includes));
    }

    private Set<String> parseIncludes(String includeParam) {
        if (includeParam.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(includeParam.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());
    }
}