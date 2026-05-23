package xyz.catuns.onboarding.service.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class StepStateTransitions {

    public static final Map<OnboardingStepState, Set<OnboardingStepState>> ALLOWED;

    static {
        EnumMap<OnboardingStepState, Set<OnboardingStepState>> map = new EnumMap<>(OnboardingStepState.class);
        map.put(OnboardingStepState.PENDING,
            EnumSet.of(OnboardingStepState.PROCESSING));
        map.put(OnboardingStepState.PROCESSING,
            EnumSet.of(OnboardingStepState.SUCCEEDED,
                       OnboardingStepState.PENDING_EXTERNAL_ACCEPTANCE,
                       OnboardingStepState.FAILED,
                       OnboardingStepState.MANUAL_REVIEW));
        map.put(OnboardingStepState.PENDING_EXTERNAL_ACCEPTANCE,
            EnumSet.of(OnboardingStepState.SUCCEEDED, OnboardingStepState.FAILED));
        map.put(OnboardingStepState.FAILED,
            EnumSet.of(OnboardingStepState.PROCESSING));
        map.put(OnboardingStepState.MANUAL_REVIEW,
            EnumSet.of(OnboardingStepState.PROCESSING, OnboardingStepState.FAILED));
        map.put(OnboardingStepState.SUCCEEDED,
            EnumSet.noneOf(OnboardingStepState.class));
        ALLOWED = Collections.unmodifiableMap(map);
    }

    private StepStateTransitions() {}
}