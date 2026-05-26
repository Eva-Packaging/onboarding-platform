package xyz.catuns.onboarding.user.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.catuns.onboarding.user.api.dto.RegistrationRequest;
import xyz.catuns.onboarding.user.domain.*;
import xyz.catuns.onboarding.user.exception.DuplicateRegistrationException;
import xyz.catuns.onboarding.user.repository.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserRegistrationService {

    private final UserProfileRepository profileRepo;
    private final ExternalIdentityRepository identityRepo;
    private final ExternalProviderRepository providerRepo;
    private final AppRoleRepository roleRepo;
    private final OutboxEventRepository outboxRepo;
    private final UserRegisteredV1PayloadBuilder payloadBuilder;
    private final Counter registrationCounter;

    public UserRegistrationService(
            UserProfileRepository profileRepo,
            ExternalIdentityRepository identityRepo,
            ExternalProviderRepository providerRepo,
            AppRoleRepository roleRepo,
            OutboxEventRepository outboxRepo,
            UserRegisteredV1PayloadBuilder payloadBuilder,
            MeterRegistry meterRegistry) {
        this.profileRepo = profileRepo;
        this.identityRepo = identityRepo;
        this.providerRepo = providerRepo;
        this.roleRepo = roleRepo;
        this.outboxRepo = outboxRepo;
        this.payloadBuilder = payloadBuilder;
        this.registrationCounter = Counter.builder("onboarding.registrations")
                .description("Total successful user registrations")
                .register(meterRegistry);
    }

    @Transactional
    public RegistrationResult register(RegistrationRequest request) {
        Optional<ExternalIdentity> existingIdentity = identityRepo
                .findByProvider_ProviderKeyAndExternalUserId(ProviderKey.GITHUB, request.getGithubUserId());
        if (existingIdentity.isPresent()) {
            UserProfile existing = existingIdentity.get().getUserProfile();
            if (existing.getStatus() == UserStatus.ACTIVE) {
                throw new DuplicateRegistrationException(request.getGithubUserId());
            }
            // PENDING_ONBOARDING: idempotent re-registration — return existing user
            return new RegistrationResult(existing.getId(), UUID.randomUUID(), true);
        }

        ExternalProvider githubProvider = providerRepo.findByProviderKey(ProviderKey.GITHUB)
                .orElseThrow(() -> new IllegalStateException("GITHUB ExternalProvider not found — check seed data"));

        UserProfile profile = new UserProfile();
        profile.setDisplayName(request.getDisplayName());
        profile.setPrimaryEmail(request.getPrimaryEmail() != null ? request.getPrimaryEmail() : "");
        profile.setStatus(UserStatus.PENDING_ONBOARDING);

        ExternalIdentity identity = new ExternalIdentity();
        identity.setUserProfile(profile);
        identity.setProvider(githubProvider);
        identity.setExternalUserId(request.getGithubUserId());
        identity.setUsername(request.getGithubLogin());
        identity.setEmail(request.getPrimaryEmail());
        identity.setDisplayName(request.getDisplayName());
        identity.setAvatarUrl(request.getAvatarUrl());
        identity.setPrimary(true);
        profile.getExternalIdentities().add(identity);

        List<String> roleKeys = request.getRoleKeys() != null ? request.getRoleKeys() : List.of();
        for (String key : roleKeys) {
            try {
                AppRoleKey appRoleKey = AppRoleKey.valueOf(key);
                roleRepo.findByRoleKey(appRoleKey).ifPresent(role -> {
                    UserRoleAssignment assignment = new UserRoleAssignment();
                    assignment.setUserProfile(profile);
                    assignment.setAppRole(role);
                    profile.getRoleAssignments().add(assignment);
                });
            } catch (IllegalArgumentException ignored) {
            }
        }

        UserProfile saved = profileRepo.save(profile);

        UUID correlationId = UUID.randomUUID();

        OutboxEvent outbox = new OutboxEvent();
        outbox.setAggregateType("UserProfile");
        outbox.setAggregateId(saved.getId());
        outbox.setEventType("UserRegisteredV1");
        outbox.setTopic("edu.user.registered.v1");
        outbox.setCorrelationId(MDC.get("correlationId"));
        outbox.setPayload(payloadBuilder.build(
                saved.getId(), correlationId,
                request.getGithubUserId(), request.getGithubLogin(),
                request.getPrimaryEmail(), roleKeys, saved.getCreatedAt()
        ));
        outboxRepo.save(outbox);
        registrationCounter.increment();
        return new RegistrationResult(saved.getId(), correlationId);
    }
}