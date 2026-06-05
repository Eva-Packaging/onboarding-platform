package xyz.catuns.onboarding.provisioning;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = {
    "github.api.base-url=https://api.github.com",
    "github.api.token=test-token",
    "github.api.org=test-org",
    "jwt.secret=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
})
class ProvisioningServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
