package xyz.catuns.onboarding.service.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.catuns.onboarding.service.api.dto.OnboardingRetryRequest;
import xyz.catuns.onboarding.service.api.dto.OnboardingRetryResponse;
import xyz.catuns.onboarding.service.api.dto.OnboardingStatusResponse;
import xyz.catuns.onboarding.service.security.JwtPrincipalExtractor;
import xyz.catuns.onboarding.service.service.OnboardingRetryService;
import xyz.catuns.onboarding.service.service.OnboardingStatusService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding")
public class OnboardingController {

    private final OnboardingStatusService statusService;
    private final OnboardingRetryService retryService;
    private final JwtPrincipalExtractor principalExtractor;

    public OnboardingController(OnboardingStatusService statusService,
                                OnboardingRetryService retryService,
                                JwtPrincipalExtractor principalExtractor) {
        this.statusService = statusService;
        this.retryService = retryService;
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
        String callerId = principalExtractor.extractUserId(request);
        return ResponseEntity.ok(statusService.findById(requestId, callerId));
    }

    @PostMapping("/{requestId}/retry")
    @Operation(summary = "Retry failed onboarding steps", description = "Requeues FAILED or MANUAL_REVIEW steps back to PENDING")
    @ApiResponse(responseCode = "202", description = "Steps requeued")
    @ApiResponse(responseCode = "400", description = "Steps list is empty")
    @ApiResponse(responseCode = "404", description = "Onboarding request not found")
    @ApiResponse(responseCode = "409", description = "Requested step is not in a retryable state")
    public ResponseEntity<OnboardingRetryResponse> retry(
        @PathVariable UUID requestId,
        @Valid @RequestBody OnboardingRetryRequest retryRequest
    ) {
        return ResponseEntity.accepted().body(retryService.retry(requestId, retryRequest));
    }
}
