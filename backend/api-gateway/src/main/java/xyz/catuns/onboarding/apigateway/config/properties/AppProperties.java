package xyz.catuns.onboarding.apigateway.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import xyz.catuns.spring.base.properties.OpenApiProperties;
import xyz.catuns.spring.base.properties.OpenFeignProperties;

@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    @NestedConfigurationProperty
    private OpenFeignProperties feign = new OpenFeignProperties();

    @NestedConfigurationProperty
    private OpenApiProperties openApi = new OpenApiProperties();

}
