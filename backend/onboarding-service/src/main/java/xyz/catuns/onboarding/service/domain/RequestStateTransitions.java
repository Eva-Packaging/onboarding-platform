package xyz.catuns.onboarding.service.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class RequestStateTransitions {

    public static final Map<OnboardingRequestState, Set<OnboardingRequestState>> ALLOWED;

    static {
        EnumMap<OnboardingRequestState, Set<OnboardingRequestState>> map = new EnumMap<>(OnboardingRequestState.class);
        map.put(OnboardingRequestState.REQUESTED,
            EnumSet.of(OnboardingRequestState.IN_PROGRESS));
        map.put(OnboardingRequestState.IN_PROGRESS,
            EnumSet.of(OnboardingRequestState.ACTION_REQUIRED,
                       OnboardingRequestState.COMPLETED,
                       OnboardingRequestState.PARTIAL_SUCCESS,
                       OnboardingRequestState.FAILED));
        map.put(OnboardingRequestState.ACTION_REQUIRED,
            EnumSet.of(OnboardingRequestState.IN_PROGRESS,
                       OnboardingRequestState.COMPLETED,
                       OnboardingRequestState.PARTIAL_SUCCESS,
                       OnboardingRequestState.FAILED));
        map.put(OnboardingRequestState.COMPLETED,    EnumSet.noneOf(OnboardingRequestState.class));
        map.put(OnboardingRequestState.PARTIAL_SUCCESS, EnumSet.noneOf(OnboardingRequestState.class));
        map.put(OnboardingRequestState.FAILED,       EnumSet.noneOf(OnboardingRequestState.class));
        ALLOWED = Collections.unmodifiableMap(map);
    }

    private RequestStateTransitions() {}
}