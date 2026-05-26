package xyz.catuns.onboarding.service.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.common.events.AtlassianProvisioningCompletedV1;
import xyz.catuns.onboarding.common.events.GithubProvisioningCompletedV1;
import xyz.catuns.onboarding.common.events.IdentityCorrelationCompletedV1;
import xyz.catuns.onboarding.common.events.IdentityCorrelationFailedV1;
import xyz.catuns.onboarding.common.events.UserRegisteredV1;
import xyz.catuns.onboarding.service.TestcontainersConfiguration;
import xyz.catuns.onboarding.service.api.dto.OnboardingInitRequest;
import xyz.catuns.onboarding.service.api.dto.OnboardingInitResponse;
import xyz.catuns.onboarding.service.domain.GroupMappingRule;
import xyz.catuns.onboarding.service.domain.OnboardingStep;
import xyz.catuns.onboarding.service.domain.OnboardingStepState;
import xyz.catuns.onboarding.service.domain.OutboxEvent;
import xyz.catuns.onboarding.service.domain.ProviderTarget;
import xyz.catuns.onboarding.service.domain.StepType;
import xyz.catuns.onboarding.service.outbox.OutboxEventPublisher;
import xyz.catuns.onboarding.service.repository.GroupMappingRuleRepository;
import xyz.catuns.onboarding.service.repository.OnboardingStepRepository;
import xyz.catuns.onboarding.service.repository.OutboxEventRepository;
import xyz.catuns.onboarding.service.repository.ProviderTargetRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OnboardingEventServiceTest {

    // Fixed UUIDs matching the V3 migration seed data
    private static final UUID STUDENT_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DUMMY_PROVIDER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Autowired private OnboardingEventService eventService;
    @Autowired private OnboardingInitialisationService initialisationService;
    @Autowired private OnboardingDomainService domainService;
    @Autowired private OnboardingStepRepository stepRepository;
    @Autowired private OutboxEventRepository outboxRepo;
    @Autowired private ProviderTargetRepository providerTargetRepository;
    @Autowired private GroupMappingRuleRepository groupMappingRuleRepository;

    // Prevent the @Scheduled publisher from firing and attempting KafkaAvroSerializer
    // (Schema Registry is not available in the test environment)
    @MockBean private OutboxEventPublisher outboxEventPublisher;

    // ── Task 1 + 2: UserRegisteredEventHandler ──────────────────────────────────

    @Test
    @Transactional
    void handleUserRegistered_existingRequest_writesThreeOutboxRowsInOneTransaction() {
        createProviderTargets();
        String userId = UUID.randomUUID().toString();
        UUID requestId = createRequest(userId).requestId();

        eventService.handleUserRegistered(userRegisteredEvent(userId, requestId.toString()));

        List<String> eventTypes = outboxRepo.findByPublishedFalseOrderByCreatedAtAsc()
                .stream().map(OutboxEvent::getEventType).toList();
        assertThat(eventTypes).containsExactlyInAnyOrder(
                "IdentityCorrelationRequestedV1",
                "GithubProvisioningRequestedV1",
                "AtlassianProvisioningRequestedV1");
    }

    @Test
    @Transactional
    void handleUserRegistered_existingRequest_transitionsAllStepsToProcessing() {
        createProviderTargets();
        String userId = UUID.randomUUID().toString();
        UUID requestId = createRequest(userId).requestId();

        eventService.handleUserRegistered(userRegisteredEvent(userId, requestId.toString()));

        assertThat(stepRepository.findByOnboardingRequest_Id(requestId))
                .allMatch(s -> s.getState() == OnboardingStepState.PROCESSING);
    }

    @Test
    @Transactional
    void handleUserRegistered_idempotent_secondDeliveryDoesNotDuplicateOutboxRows() {
        createProviderTargets();
        String userId = UUID.randomUUID().toString();
        UUID requestId = createRequest(userId).requestId();
        UserRegisteredV1 event = userRegisteredEvent(userId, requestId.toString());

        eventService.handleUserRegistered(event);
        long countAfterFirst = outboxRepo.count();

        eventService.handleUserRegistered(event);

        assertThat(outboxRepo.count()).isEqualTo(countAfterFirst);
    }

    // ── Task 2: Provisioning completion handlers ─────────────────────────────────

    @Test
    @Transactional
    void handleGithubProvisioningCompleted_success_transitionsGithubStepToSucceeded() {
        String userId = UUID.randomUUID().toString();
        UUID requestId = createRequest(userId).requestId();
        setStepProcessing(requestId, StepType.GITHUB_TEAM_PROVISIONING);

        eventService.handleGithubProvisioningCompleted(githubCompletedEvent(userId, requestId.toString(), true));

        assertStepState(requestId, StepType.GITHUB_TEAM_PROVISIONING, OnboardingStepState.SUCCEEDED);
    }

    @Test
    @Transactional
    void handleGithubProvisioningCompleted_failure_transitionsGithubStepToFailedWithErrorDetails() {
        String userId = UUID.randomUUID().toString();
        UUID requestId = createRequest(userId).requestId();
        setStepProcessing(requestId, StepType.GITHUB_TEAM_PROVISIONING);

        eventService.handleGithubProvisioningCompleted(githubCompletedEvent(userId, requestId.toString(), false));

        OnboardingStep step = getStep(requestId, StepType.GITHUB_TEAM_PROVISIONING);
        assertThat(step.getState()).isEqualTo(OnboardingStepState.FAILED);
        assertThat(step.getLastErrorCode()).isEqualTo("GITHUB_API_ERROR");
    }

    @Test
    @Transactional
    void handleAtlassianProvisioningCompleted_success_transitionsAtlassianStepToSucceeded() {
        String userId = UUID.randomUUID().toString();
        UUID requestId = createRequest(userId).requestId();
        setStepProcessing(requestId, StepType.JIRA_GROUP_PROVISIONING);

        eventService.handleAtlassianProvisioningCompleted(atlassianCompletedEvent(userId, requestId.toString(), true));

        assertStepState(requestId, StepType.JIRA_GROUP_PROVISIONING, OnboardingStepState.SUCCEEDED);
    }

    // ── Task 3: Identity correlation handlers ────────────────────────────────────

    @Test
    @Transactional
    void handleIdentityCorrelationCompleted_transitionsIdentityStepToSucceeded() {
        String userId = UUID.randomUUID().toString();
        UUID requestId = createRequest(userId).requestId();
        setStepProcessing(requestId, StepType.IDENTITY_CORRELATION);

        eventService.handleIdentityCorrelationCompleted(identityCompletedEvent(userId, requestId.toString()));

        assertStepState(requestId, StepType.IDENTITY_CORRELATION, OnboardingStepState.SUCCEEDED);
    }

    @Test
    @Transactional
    void handleIdentityCorrelationFailed_transitionsIdentityStepToFailedWithReasonCode() {
        String userId = UUID.randomUUID().toString();
        UUID requestId = createRequest(userId).requestId();
        setStepProcessing(requestId, StepType.IDENTITY_CORRELATION);

        eventService.handleIdentityCorrelationFailed(identityFailedEvent(userId, requestId.toString()));

        OnboardingStep step = getStep(requestId, StepType.IDENTITY_CORRELATION);
        assertThat(step.getState()).isEqualTo(OnboardingStepState.FAILED);
        assertThat(step.getLastErrorCode()).isEqualTo("IDENTITY_NOT_FOUND");
    }

    // ── Task 4: Terminal state check + lifecycle outbox write ────────────────────

    @Test
    @Transactional
    void allStepsSucceeded_writesOnboardingCompletedOutboxRowToLifecycleTopic() {
        String userId = UUID.randomUUID().toString();
        UUID requestId = createRequest(userId).requestId();
        setStepProcessing(requestId, StepType.IDENTITY_CORRELATION);
        setStepProcessing(requestId, StepType.GITHUB_TEAM_PROVISIONING);
        setStepProcessing(requestId, StepType.JIRA_GROUP_PROVISIONING);

        eventService.handleIdentityCorrelationCompleted(identityCompletedEvent(userId, requestId.toString()));
        eventService.handleGithubProvisioningCompleted(githubCompletedEvent(userId, requestId.toString(), true));
        eventService.handleAtlassianProvisioningCompleted(atlassianCompletedEvent(userId, requestId.toString(), true));

        assertThat(outboxRepo.findAll())
                .filteredOn(e -> "OnboardingCompletedV1".equals(e.getEventType()))
                .hasSize(1)
                .first()
                .extracting(OutboxEvent::getTopic)
                .isEqualTo("edu.onboarding.lifecycle.v1");
    }

    @Test
    @Transactional
    void anyStepFailed_writesOnboardingFailedOutboxRowToLifecycleTopic() {
        String userId = UUID.randomUUID().toString();
        UUID requestId = createRequest(userId).requestId();
        setStepProcessing(requestId, StepType.IDENTITY_CORRELATION);
        setStepProcessing(requestId, StepType.GITHUB_TEAM_PROVISIONING);
        setStepProcessing(requestId, StepType.JIRA_GROUP_PROVISIONING);

        eventService.handleIdentityCorrelationFailed(identityFailedEvent(userId, requestId.toString()));
        eventService.handleGithubProvisioningCompleted(githubCompletedEvent(userId, requestId.toString(), false));
        eventService.handleAtlassianProvisioningCompleted(atlassianCompletedEvent(userId, requestId.toString(), false));

        assertThat(outboxRepo.findAll())
                .filteredOn(e -> "OnboardingFailedV1".equals(e.getEventType()))
                .hasSize(1)
                .first()
                .extracting(OutboxEvent::getTopic)
                .isEqualTo("edu.onboarding.lifecycle.v1");
    }

    // ── Task 5: Outbox publisher — routing correctness ───────────────────────────

    @Test
    @Transactional
    void outboxRows_haveCorrectTopicForEachEventType() {
        createProviderTargets();
        String userId = UUID.randomUUID().toString();
        UUID requestId = createRequest(userId).requestId();

        eventService.handleUserRegistered(userRegisteredEvent(userId, requestId.toString()));

        List<OutboxEvent> rows = outboxRepo.findByPublishedFalseOrderByCreatedAtAsc();
        assertTopic(rows, "IdentityCorrelationRequestedV1", "edu.identity.correlation.v1");
        assertTopic(rows, "GithubProvisioningRequestedV1", "edu.provisioning.github.v1");
        assertTopic(rows, "AtlassianProvisioningRequestedV1", "edu.provisioning.atlassian.v1");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void createProviderTargets() {
        UUID githubTargetId = UUID.randomUUID();
        ProviderTarget githubTarget = new ProviderTarget();
        githubTarget.setId(githubTargetId);
        githubTarget.setProviderId(DUMMY_PROVIDER_ID);
        githubTarget.setTargetType("GITHUB_TEAM");
        githubTarget.setExternalKey("eva-org/students");
        githubTarget.setDisplayName("Students GitHub Team");
        githubTarget.setEnabled(true);
        providerTargetRepository.save(githubTarget);

        UUID atlassianTargetId = UUID.randomUUID();
        ProviderTarget atlassianTarget = new ProviderTarget();
        atlassianTarget.setId(atlassianTargetId);
        atlassianTarget.setProviderId(DUMMY_PROVIDER_ID);
        atlassianTarget.setTargetType("ATLASSIAN_GROUP");
        atlassianTarget.setExternalKey("student-group");
        atlassianTarget.setDisplayName("Students Atlassian Group");
        atlassianTarget.setEnabled(true);
        providerTargetRepository.save(atlassianTarget);

        GroupMappingRule githubRule = new GroupMappingRule();
        githubRule.setId(UUID.randomUUID());
        githubRule.setAppRoleId(STUDENT_ROLE_ID);
        githubRule.setProviderTargetId(githubTargetId);
        githubRule.setPriorityOrder(1);
        githubRule.setEnabled(true);
        groupMappingRuleRepository.save(githubRule);

        GroupMappingRule atlassianRule = new GroupMappingRule();
        atlassianRule.setId(UUID.randomUUID());
        atlassianRule.setAppRoleId(STUDENT_ROLE_ID);
        atlassianRule.setProviderTargetId(atlassianTargetId);
        atlassianRule.setPriorityOrder(1);
        atlassianRule.setEnabled(true);
        groupMappingRuleRepository.save(atlassianRule);
    }

    private OnboardingInitResponse createRequest(String userId) {
        return initialisationService.initialise(
                new OnboardingInitRequest(userId, UUID.randomUUID(), List.of("STUDENT")));
    }

    private void setStepProcessing(UUID requestId, StepType stepType) {
        OnboardingStep step = stepRepository
                .findByOnboardingRequest_IdAndStepType_StepKey(requestId, stepType)
                .orElseThrow(() -> new AssertionError("Step not found: " + stepType));
        domainService.transitionStep(step, OnboardingStepState.PROCESSING);
    }

    private void assertStepState(UUID requestId, StepType stepType, OnboardingStepState expected) {
        assertThat(getStep(requestId, stepType).getState()).isEqualTo(expected);
    }

    private OnboardingStep getStep(UUID requestId, StepType stepType) {
        return stepRepository
                .findByOnboardingRequest_IdAndStepType_StepKey(requestId, stepType)
                .orElseThrow(() -> new AssertionError("Step not found: " + stepType));
    }

    private void assertTopic(List<OutboxEvent> rows, String eventType, String expectedTopic) {
        assertThat(rows)
                .filteredOn(e -> eventType.equals(e.getEventType()))
                .as("outbox rows of type %s", eventType)
                .hasSize(1)
                .first()
                .extracting(OutboxEvent::getTopic)
                .isEqualTo(expectedTopic);
    }

    // ── Avro event builders ───────────────────────────────────────────────────────

    private UserRegisteredV1 userRegisteredEvent(String userId, String requestId) {
        return UserRegisteredV1.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toString())
                .setCorrelationId(UUID.randomUUID().toString())
                .setProducer("user-service")
                .setUserId(userId)
                .setOnboardingRequestId(requestId)
                .setDisplayName("Test User")
                .setPrimaryEmail("test@example.com")
                .setGithubUserId("gh-123")
                .setGithubLogin("testuser")
                .setRoleKeys(List.of("STUDENT"))
                .build();
    }

    private GithubProvisioningCompletedV1 githubCompletedEvent(String userId, String requestId, boolean success) {
        GithubProvisioningCompletedV1.Builder b = GithubProvisioningCompletedV1.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toString())
                .setCorrelationId(UUID.randomUUID().toString())
                .setProducer("provisioning-service")
                .setUserId(userId)
                .setOnboardingRequestId(requestId)
                .setProviderTargetId(UUID.randomUUID().toString())
                .setMembershipState(success ? "ACTIVE" : "FAILED")
                .setSuccess(success);
        if (!success) {
            b.setErrorCode("GITHUB_API_ERROR").setErrorMessage("Simulated failure");
        }
        return b.build();
    }

    private AtlassianProvisioningCompletedV1 atlassianCompletedEvent(String userId, String requestId, boolean success) {
        AtlassianProvisioningCompletedV1.Builder b = AtlassianProvisioningCompletedV1.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toString())
                .setCorrelationId(UUID.randomUUID().toString())
                .setProducer("provisioning-service")
                .setUserId(userId)
                .setOnboardingRequestId(requestId)
                .setProviderTargetId(UUID.randomUUID().toString())
                .setSuccess(success);
        if (!success) {
            b.setErrorCode("ATLASSIAN_API_ERROR").setErrorMessage("Simulated failure");
        }
        return b.build();
    }

    private IdentityCorrelationCompletedV1 identityCompletedEvent(String userId, String requestId) {
        return IdentityCorrelationCompletedV1.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toString())
                .setCorrelationId(UUID.randomUUID().toString())
                .setProducer("identity-service")
                .setUserId(userId)
                .setOnboardingRequestId(requestId)
                .setGithubIdentityId("gh-123")
                .setAtlassianIdentityId("atl-456")
                .setMatchStrategy("EMAIL_EXACT")
                .setMatched(true)
                .build();
    }

    private IdentityCorrelationFailedV1 identityFailedEvent(String userId, String requestId) {
        return IdentityCorrelationFailedV1.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toString())
                .setCorrelationId(UUID.randomUUID().toString())
                .setProducer("identity-service")
                .setUserId(userId)
                .setOnboardingRequestId(requestId)
                .setReasonCode("IDENTITY_NOT_FOUND")
                .setReasonMessage("No matching Atlassian identity")
                .build();
    }
}