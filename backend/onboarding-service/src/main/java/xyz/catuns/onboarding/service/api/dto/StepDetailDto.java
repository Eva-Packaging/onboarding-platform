package xyz.catuns.onboarding.service.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepDetailDto(
    String type,
    String state,
    ProviderTargetDto target,
    int attemptCount,
    String lastErrorCode,
    Instant startedAt,
    Instant completedAt
) {}