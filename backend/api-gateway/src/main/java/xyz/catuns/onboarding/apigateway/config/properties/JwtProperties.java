package xyz.catuns.onboarding.apigateway.config.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import xyz.catuns.spring.jwt.core.properties.JwtMetadata;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties extends JwtMetadata {

    /**
     * Jwt public Paths
     */
    private List<String> publicPaths = new ArrayList<>();

}

