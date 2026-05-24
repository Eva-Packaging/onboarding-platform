package xyz.catuns.onboarding.service.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.catuns.onboarding.service.api.dto.OnboardingInitRequest;
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
    @ApiResponse(responseCode = "200",description = "HTTP Status OK")
    ResponseEntity<OnboardingInitResponse> createOnboardingRequest(
        @Valid @RequestBody OnboardingInitRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(initialisationService.initialise(request));
    }
}