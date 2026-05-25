package xyz.catuns.onboarding.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.catuns.onboarding.user.api.dto.RegistrationRequest;
import xyz.catuns.onboarding.user.api.dto.RegistrationResponse;
import xyz.catuns.onboarding.user.client.OnboardingInitResponse;
import xyz.catuns.onboarding.user.client.OnboardingServiceClient;
import xyz.catuns.onboarding.user.service.RegistrationResult;
import xyz.catuns.onboarding.user.service.UserRegistrationService;
import xyz.catuns.spring.base.exception.controller.ConflictException;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Registration")
public class RegistrationController {

    private final UserRegistrationService registrationService;
    private final OnboardingServiceClient onboardingClient;

    public RegistrationController(UserRegistrationService registrationService,
                                  OnboardingServiceClient onboardingClient) {
        this.registrationService = registrationService;
        this.onboardingClient = onboardingClient;
    }

    @PostMapping("/registrations")
    @ApiResponse(responseCode = "201",description = "HTTP Status CREATED")
    @Operation(summary = "Registration", description = "Post Submit Registration")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {

        final RegistrationResult result = registrationService.register(request);

        List<String> roleKeys = request.getRoleKeys() != null ? request.getRoleKeys() : List.of();
        OnboardingInitResponse onboarding;
        if (result.existingUser()) {
            onboarding = onboardingClient.getLatestOnboardingForUser(result.userId())
                    .map(latest -> new OnboardingInitResponse(latest.requestId(), latest.state(), Collections.emptyList()))
                    .orElseGet(() -> onboardingClient.createOnboardingRequest(result.userId(), result.correlationId(), roleKeys));
        } else {
            onboarding = onboardingClient.createOnboardingRequest(result.userId(), result.correlationId(), roleKeys);
        }

        RegistrationResponse response = RegistrationResponse.builder()
                .userId(result.userId())
                .onboardingRequestId(onboarding.requestId())
                .status(onboarding.state())
                .correlationId(result.correlationId())
                .steps(onboarding.steps())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}