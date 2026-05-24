package xyz.catuns.onboarding.user.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class RegistrationResponse {
    private UUID userId;
    private UUID onboardingRequestId;
    private String status;
    private UUID correlationId;
    private List<StepSummaryDto> steps;
}