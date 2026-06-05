package xyz.catuns.onboarding.provisioning.integration;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import xyz.catuns.onboarding.common.events.GithubProvisioningRequestedV1;
import xyz.catuns.onboarding.provisioning.TestcontainersConfiguration;
import xyz.catuns.onboarding.provisioning.domain.OutboxEvent;
import xyz.catuns.onboarding.provisioning.domain.ProvisioningAuditLog;
import xyz.catuns.onboarding.provisioning.domain.ResultState;
import xyz.catuns.onboarding.provisioning.outbox.OutboxEventPublisher;
import xyz.catuns.onboarding.provisioning.repository.OutboxEventRepository;
import xyz.catuns.onboarding.provisioning.repository.ProvisioningAuditLogRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full consumer-to-outbox integration test.
 *
 * Schema Registry: Confluent's mock://test in-memory registry is used so that both the
 * KafkaAvroSerializer (producer side in this test) and KafkaAvroDeserializer (consumer side in
 * the Spring context) share the same JVM-local registry. This removes the need for a real Schema
 * Registry container while still exercising the full Avro serialization/deserialization path.
 *
 * GitHub API: WireMock stubs PUT /orgs/.../teams/.../memberships/... to return {"state":"active"}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "github.api.token=test-token",
    "github.api.org=test-org",
    "spring.kafka.properties.schema-registry-url=mock://test",
    "jwt.secret=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
})
class GithubProvisioningIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureWireMock(DynamicPropertyRegistry registry) {
        registry.add("github.api.base-url", wireMock::baseUrl);
    }

    @Autowired private KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    @Autowired private ProvisioningAuditLogRepository auditLogRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;

    // Prevent the outbox publisher from attempting KafkaAvroSerializer round-trip to real SR
    @MockBean private OutboxEventPublisher outboxEventPublisher;

    @Value("${app.kafka.topics.github-provisioning}")
    private String githubTopic;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        outboxEventRepository.deleteAll();
        wireMock.stubFor(put(urlPathMatching("/orgs/.*/teams/.*/memberships/.*"))
                .willReturn(okJson("{\"state\":\"active\"}")));
    }

    @Test
    void activeMembership_kafkaConsumerFlow_persistsAuditLogAndOutboxRows() throws Exception {
        GithubProvisioningRequestedV1 event = githubProvisioningEvent();

        kafkaTemplate.send(githubTopic, event.getUserId().toString(), event)
                .get(10, TimeUnit.SECONDS);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(auditLogRepository.count()).isEqualTo(1);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
        });

        ProvisioningAuditLog auditLog = auditLogRepository.findAll().getFirst();
        assertThat(auditLog.getActionName()).isEqualTo("GITHUB_PROVISION");
        assertThat(auditLog.getResultState()).isEqualTo(ResultState.SUCCESS);
        assertThat(auditLog.getResponsePayload()).contains("ACTIVE");

        List<OutboxEvent> outboxRows = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        assertThat(outboxRows).hasSize(1);
        OutboxEvent outbox = outboxRows.getFirst();
        assertThat(outbox.getEventType()).isEqualTo("GithubProvisioningCompletedV1");
        assertThat(outbox.getTopic()).isEqualTo("edu.provisioning.github.v1");
        assertThat(outbox.getPayload())
                .contains("\"membershipState\":\"ACTIVE\"")
                .contains("\"success\":true");
    }

    private GithubProvisioningRequestedV1 githubProvisioningEvent() {
        return GithubProvisioningRequestedV1.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toString())
                .setCorrelationId(UUID.randomUUID().toString())
                .setProducer("onboarding-service")
                .setUserId(UUID.randomUUID().toString())
                .setOnboardingRequestId(UUID.randomUUID().toString())
                .setGithubLogin("octocat")
                .setGithubOrg("evaitcs")
                .setGithubTeamSlug("students")
                .setProviderTargetId(UUID.randomUUID().toString())
                .build();
    }
}