package xyz.catuns.onboarding.apigateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.catuns.onboarding.apigateway.config.properties.AppProperties;

@Configuration
@EnableConfigurationProperties({AppProperties.class})
class AppConfig {

    @Bean
    OpenAPI apiInfo(AppProperties appProperties) {
        var openApi = appProperties.getOpenApi();
        Info info = new Info();
        info.setTitle(openApi.getTitle());
        info.setVersion(openApi.getVersion());
        info.setDescription(openApi.getDescription());

        Contact contact = new Contact();
        contact.setUrl(openApi.getUrl());
        contact.setEmail(openApi.getEmail());
        info.setContact(contact);

        return new OpenAPI().info(info);
    }

}
