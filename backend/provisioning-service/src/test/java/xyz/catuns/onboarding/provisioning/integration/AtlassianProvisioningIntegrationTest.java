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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import xyz.catuns.onboarding.common.events.AtlassianProvisioningRequestedV1;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AtlassianProvisioningIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureWireMock(DynamicPropertyRegistry registry) {
        registry.add("atlassian.api.base-url", wireMock::baseUrl);
    }

    @Autowired private KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    @Autowired private ProvisioningAuditLogRepository auditLogRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;

    @MockBean private OutboxEventPublisher outboxEventPublisher;

    @Value("${app.kafka.topics.atlassian-provisioning}")
    private String atlassianTopic;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        outboxEventRepository.deleteAll();
        wireMock.stubFor(get(urlPathEqualTo("/rest/api/3/user/search"))
                .willReturn(okJson("[{\"accountId\":\"account-123\",\"emailAddress\":\"octocat@example.com\"}]")));
        wireMock.stubFor(post(urlPathEqualTo("/rest/api/3/group/user"))
                .willReturn(aResponse().withStatus(201)));
    }

    @Test
    void activeMembership_kafkaConsumerFlow_persistsAuditLogAndOutboxRows() throws Exception {
        AtlassianProvisioningRequestedV1 event = atlassianProvisioningEvent();

        kafkaTemplate.send(atlassianTopic, event.getUserId().toString(), event)
                .get(10, TimeUnit.SECONDS);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(auditLogRepository.count()).isEqualTo(1);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
        });

        ProvisioningAuditLog auditLog = auditLogRepository.findAll().getFirst();
        assertThat(auditLog.getActionName()).isEqualTo("ATLASSIAN_PROVISION");
        assertThat(auditLog.getResultState()).isEqualTo(ResultState.SUCCESS);
        assertThat(auditLog.getResponsePayload()).contains("\"membershipState\":\"ACTIVE\"");

        List<OutboxEvent> outboxRows = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        assertThat(outboxRows).hasSize(1);
        OutboxEvent outbox = outboxRows.getFirst();
        assertThat(outbox.getEventType()).isEqualTo("AtlassianProvisioningCompletedV1");
        assertThat(outbox.getTopic()).isEqualTo("edu.provisioning.atlassian.v1");
        assertThat(outbox.getPayload()).contains("\"success\":true");
    }

    private AtlassianProvisioningRequestedV1 atlassianProvisioningEvent() {
        return AtlassianProvisioningRequestedV1.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toString())
                .setCorrelationId(UUID.randomUUID().toString())
                .setProducer("onboarding-service")
                .setUserId(UUID.randomUUID().toString())
                .setOnboardingRequestId(UUID.randomUUID().toString())
                .setAtlassianIdentityId(null)
                .setAtlassianEmail("octocat@example.com")
                .setGroupName("developers")
                .setProviderTargetId(UUID.randomUUID().toString())
                .build();
    }
}