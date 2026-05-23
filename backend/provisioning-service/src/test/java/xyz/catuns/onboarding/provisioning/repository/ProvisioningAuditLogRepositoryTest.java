package xyz.catuns.onboarding.provisioning.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import xyz.catuns.onboarding.provisioning.JpaTestContainersConfiguration;
import xyz.catuns.onboarding.provisioning.domain.ProviderTarget;
import xyz.catuns.onboarding.provisioning.domain.ProvisioningAuditLog;
import xyz.catuns.onboarding.provisioning.domain.ResultState;
import xyz.catuns.onboarding.provisioning.domain.TargetType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaTestContainersConfiguration.class)
class ProvisioningAuditLogRepositoryTest {

    @Autowired
    ProvisioningAuditLogRepository auditRepository;

    @Autowired
    ProviderTargetRepository targetRepository;

    private UUID stepId;
    private UUID providerId;

    @BeforeEach
    void setUp() {
        stepId = UUID.randomUUID();
        providerId = UUID.randomUUID();
    }

    @Test
    void save_persistsAndAssignsGeneratedId() {
        ProvisioningAuditLog saved = auditRepository.save(buildLog(stepId, ResultState.SUCCESS, null, null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getResultState()).isEqualTo(ResultState.SUCCESS);
    }

    @Test
    void save_populatesCreatedAt() {
        ProvisioningAuditLog saved = auditRepository.save(buildLog(stepId, ResultState.PENDING, null, null));

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void save_roundTripsRequestPayloadJsonb() {
        String requestJson = "{\"action\":\"ADD_MEMBER\",\"userId\":\"u-123\"}";
        ProvisioningAuditLog saved = auditRepository.saveAndFlush(
            buildLog(stepId, ResultState.SUCCESS, requestJson, null)
        );

        Optional<ProvisioningAuditLog> found = auditRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getRequestPayload()).isEqualTo(requestJson);
    }

    @Test
    void save_roundTripsResponsePayloadJsonb() {
        String responseJson = "{\"status\":\"201\",\"message\":\"invited\"}";
        ProvisioningAuditLog saved = auditRepository.saveAndFlush(
            buildLog(stepId, ResultState.SUCCESS, null, responseJson)
        );

        Optional<ProvisioningAuditLog> found = auditRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getResponsePayload()).isEqualTo(responseJson);
    }

    @Test
    void save_allowsNullPayloads() {
        ProvisioningAuditLog saved = auditRepository.saveAndFlush(
            buildLog(stepId, ResultState.FAILURE, null, null)
        );

        Optional<ProvisioningAuditLog> found = auditRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getRequestPayload()).isNull();
        assertThat(found.get().getResponsePayload()).isNull();
    }

    @Test
    void findByOnboardingStepId_returnsAllLogsForStep() {
        UUID otherStepId = UUID.randomUUID();
        auditRepository.save(buildLog(stepId, ResultState.PENDING, null, null));
        auditRepository.save(buildLog(stepId, ResultState.SUCCESS, null, null));
        auditRepository.save(buildLog(otherStepId, ResultState.SUCCESS, null, null));

        List<ProvisioningAuditLog> found = auditRepository.findByOnboardingStepId(stepId);

        assertThat(found).hasSize(2);
        assertThat(found).allMatch(l -> l.getOnboardingStepId().equals(stepId));
    }

    @Test
    void save_storescorrelationId() {
        UUID correlationId = UUID.randomUUID();
        ProvisioningAuditLog log = buildLog(stepId, ResultState.SUCCESS, null, null);
        log.setCorrelationId(correlationId);
        ProvisioningAuditLog saved = auditRepository.saveAndFlush(log);

        assertThat(auditRepository.findById(saved.getId()))
            .isPresent()
            .get()
            .extracting(ProvisioningAuditLog::getCorrelationId)
            .isEqualTo(correlationId);
    }

    private ProvisioningAuditLog buildLog(UUID stepId, ResultState state,
                                          String requestPayload, String responsePayload) {
        ProvisioningAuditLog log = new ProvisioningAuditLog();
        log.setOnboardingStepId(stepId);
        log.setProviderId(providerId);
        log.setActionName("ADD_TEAM_MEMBER");
        log.setResultState(state);
        log.setCorrelationId(UUID.randomUUID());
        log.setRequestPayload(requestPayload);
        log.setResponsePayload(responsePayload);
        return log;
    }
}