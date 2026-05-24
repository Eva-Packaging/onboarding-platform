package xyz.catuns.onboarding.service.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.catuns.onboarding.service.api.dto.OnboardingInitRequest;
import xyz.catuns.onboarding.service.api.dto.OnboardingLatestResponse;

import java.util.UUID;
import xyz.catuns.onboarding.service.api.dto.OnboardingInitResponse;
import xyz.catuns.onboarding.service.service.OnboardingInitialisationService;

@RestController
@RequestMapping("/api/v1/internal")
public class OnboardingInternalController {

    private final OnboardingInitialisationService initialisationService;

    public OnboardingInternalController(OnboardingInitialisationService initialisationService) {
        this.initialisationService = initialisationService;
    }


    @PostMapping("/onboarding-requests")
    @Operation(summary = "Create Onboarding", description = "Internally created onboarding request (user-service)")
    @ApiResponse(responseCode = "201", description = "HTTP Status CREATED")
    ResponseEntity<OnboardingInitResponse> createOnboardingRequest(
        @Valid @RequestBody OnboardingInitRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(initialisationService.initialise(request));
    }

    @GetMapping("/onboarding-requests/latest")
    @Operation(summary = "Get Latest Onboarding", description = "Returns the most recent onboarding request for a user")
    @ApiResponse(responseCode = "200", description = "HTTP Status OK")
    ResponseEntity<OnboardingLatestResponse> getLatestOnboarding(@RequestParam UUID userId) {
        return initialisationService.findLatestForUser(userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}