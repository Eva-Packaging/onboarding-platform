package xyz.catuns.onboarding.provisioning.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "atlassian")
public class AtlassianClientProperties {

    @NestedConfigurationProperty
    private AtlassianApiProperties api = new AtlassianApiProperties();

    @Data
    public static class AtlassianApiProperties {
        @NotBlank
        private String baseUrl;
        @NotBlank
        private String email;
        @NotBlank
        private String token;
    }

}