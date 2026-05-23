package xyz.catuns.onboarding.service;

import org.springframework.boot.SpringApplication;

public class TestOnboardingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(OnboardingServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
