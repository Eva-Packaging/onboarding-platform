package xyz.catuns.onboarding.service.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.catuns.onboarding.service.api.dto.OnboardingStatusResponse;
import xyz.catuns.onboarding.service.security.JwtPrincipalExtractor;
import xyz.catuns.onboarding.service.service.OnboardingStatusService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding")
public class OnboardingController {

    private final OnboardingStatusService statusService;
    private final JwtPrincipalExtractor principalExtractor;

    public OnboardingController(OnboardingStatusService statusService,
                                JwtPrincipalExtractor principalExtractor) {
        this.statusService = statusService;
        this.principalExtractor = principalExtractor;
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "Get onboarding status", description = "Returns full onboarding status for the authenticated owner")
    @ApiResponse(responseCode = "200", description = "HTTP Status OK")
    @ApiResponse(responseCode = "403", description = "Caller is not the owner of this request")
    @ApiResponse(responseCode = "404", description = "Onboarding request not found")
    public ResponseEntity<OnboardingStatusResponse> getStatus(
        @PathVariable UUID requestId,
        HttpServletRequest request
    ) {
        UUID callerId = principalExtractor.extractUserId(request);
        return ResponseEntity.ok(statusService.findById(requestId, callerId));
    }
}
