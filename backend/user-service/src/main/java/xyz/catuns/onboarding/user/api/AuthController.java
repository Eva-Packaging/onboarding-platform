package xyz.catuns.onboarding.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.catuns.onboarding.user.api.dto.TokenExchangeRequest;
import xyz.catuns.onboarding.user.api.dto.TokenResponse;
import xyz.catuns.onboarding.user.service.TokenExchangeService;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Auth")
@RequiredArgsConstructor
public class AuthController {

    private final TokenExchangeService tokenExchangeService;

    @PostMapping("/auth/token")
    @Operation(summary = "Exchange a GitHub OAuth token for a backend JWT")
    @ApiResponse(responseCode = "200", description = "HTTP Status OK")
    @ApiResponse(responseCode = "401", description = "Invalid or expired GitHub access token")
    @ApiResponse(responseCode = "404", description = "User not yet registered")
    public ResponseEntity<TokenResponse> exchangeToken(@Valid @RequestBody TokenExchangeRequest request) {
        return ResponseEntity.ok(tokenExchangeService.exchange(request));
    }
}
