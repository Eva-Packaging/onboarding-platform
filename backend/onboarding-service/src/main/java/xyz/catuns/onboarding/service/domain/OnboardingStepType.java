package xyz.catuns.onboarding.service.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Getter
@Immutable
@Entity
@Table(name = "onboarding_step_type")
public class OnboardingStepType {

    @Id
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_key", nullable = false, length = 100)
    private StepType stepKey;

    @Column(nullable = false, length = 255)
    private String description;
}
