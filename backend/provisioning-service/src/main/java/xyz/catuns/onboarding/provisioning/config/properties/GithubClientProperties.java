package xyz.catuns.onboarding.provisioning.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "github")
public class GithubClientProperties {

    @NestedConfigurationProperty
    private GithubApiProperties api = new GithubApiProperties();

    @Data
    public static class GithubApiProperties {
        @NotBlank
        private String baseUrl;
        @NotBlank
        private String token;
        @NotBlank
        private String org;
    }

}
