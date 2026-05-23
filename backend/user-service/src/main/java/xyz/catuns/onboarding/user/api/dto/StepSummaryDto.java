package xyz.catuns.onboarding.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StepSummaryDto {
    private String type;
    private String state;
}