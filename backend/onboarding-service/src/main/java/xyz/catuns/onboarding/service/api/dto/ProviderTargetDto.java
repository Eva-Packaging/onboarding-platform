package xyz.catuns.onboarding.service.api.dto;

public record ProviderTargetDto(
    String provider,
    String targetType,
    String externalKey
) {}