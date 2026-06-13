package xyz.catuns.onboarding.provisioning.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import xyz.catuns.onboarding.provisioning.config.properties.AppProperties;
import xyz.catuns.onboarding.provisioning.config.properties.AtlassianClientProperties;
import xyz.catuns.onboarding.provisioning.config.properties.GithubClientProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties({AppProperties.class, GithubClientProperties.class, AtlassianClientProperties.class})
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

    @Bean
    RestClient githubRestClient(GithubClientProperties properties) {
        String bearerToken = "Bearer " + properties.getApi().getToken();
        return RestClient.builder()
                .baseUrl(properties.getApi().getBaseUrl())
                .defaultHeader("Authorization", bearerToken)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean
    RestClient atlassianRestClient(AtlassianClientProperties properties) {
        String credentials = properties.getApi().getEmail() + ":" + properties.getApi().getToken();
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return RestClient.builder()
                .baseUrl(properties.getApi().getBaseUrl())
                .defaultHeader("Authorization", basicAuth)
                .defaultHeader("Accept", "application/json")
                .build();
    }

}
