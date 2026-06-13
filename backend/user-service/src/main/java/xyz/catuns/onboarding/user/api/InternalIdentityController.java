package xyz.catuns.onboarding.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.catuns.onboarding.user.api.dto.ExternalIdentityLookupResponse;
import xyz.catuns.onboarding.user.api.dto.IdentityLinkCreateRequest;
import xyz.catuns.onboarding.user.api.dto.IdentityLinkResponse;
import xyz.catuns.onboarding.user.domain.ExternalIdentity;
import xyz.catuns.onboarding.user.domain.ProviderKey;
import xyz.catuns.onboarding.user.repository.ExternalIdentityRepository;
import xyz.catuns.onboarding.user.service.IdentityLinkService;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal")
@Tag(name = "Internal", description = "Service-to-service endpoints (not exposed via gateway)")
public class InternalIdentityController {

    private final ExternalIdentityRepository externalIdentityRepository;
    private final IdentityLinkService identityLinkService;

    public InternalIdentityController(ExternalIdentityRepository externalIdentityRepository,
                                       IdentityLinkService identityLinkService) {
        this.externalIdentityRepository = externalIdentityRepository;
        this.identityLinkService = identityLinkService;
    }

    @GetMapping("/external-identities")
    @Operation(summary = "Find external identity by provider and email, or by provider and userProfileId", description = "Used by onboarding-service for identity correlation")
    @ApiResponse(responseCode = "200", description = "HTTP Status OK")
    @ApiResponse(responseCode = "404", description = "No matching external identity")
    ResponseEntity<ExternalIdentityLookupResponse> getExternalIdentity(
        @RequestParam ProviderKey provider,
        @RequestParam(required = false) String email,
        @RequestParam(required = false) UUID userProfileId
    ) {
        Optional<ExternalIdentity> identity = email != null
            ? externalIdentityRepository.findByProvider_ProviderKeyAndEmail(provider, email)
            : externalIdentityRepository.findByProvider_ProviderKeyAndUserProfile_Id(provider, userProfileId);

        return identity
            .map(i -> new ExternalIdentityLookupResponse(i.getId(), i.getExternalUserId(), i.getEmail(), "MATCHED"))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/identity-links")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create identity link", description = "Persists a GitHub-to-Atlassian identity correlation")
    @ApiResponse(responseCode = "201", description = "HTTP Status CREATED")
    IdentityLinkResponse createIdentityLink(@Valid @RequestBody IdentityLinkCreateRequest request) {
        return identityLinkService.createIdentityLink(request);
    }
}