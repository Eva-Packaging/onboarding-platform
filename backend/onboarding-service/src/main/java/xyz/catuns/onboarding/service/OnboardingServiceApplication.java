package xyz.catuns.onboarding.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import xyz.catuns.spring.jwt.autoconfigure.annotation.EnableJwtSecurity;

@EnableJwtSecurity
@SpringBootApplication
public class OnboardingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnboardingServiceApplication.class, args);
    }

}
