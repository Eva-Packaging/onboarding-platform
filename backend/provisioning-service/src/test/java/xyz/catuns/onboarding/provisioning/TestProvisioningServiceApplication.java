package xyz.catuns.onboarding.provisioning;

import org.springframework.boot.SpringApplication;

public class TestProvisioningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(ProvisioningServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
