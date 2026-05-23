package xyz.catuns.onboarding.provisioning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import xyz.catuns.spring.jwt.autoconfigure.annotation.EnableJwtSecurity;

@EnableJwtSecurity
@SpringBootApplication
public class ProvisioningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProvisioningServiceApplication.class, args);
    }

}
