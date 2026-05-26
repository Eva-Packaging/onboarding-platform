## Auth, API Client, and Logging — Development Branch PR

Delivers the authentication bridge between GitHub OAuth and backend JWTs, secure service-to-service calls, a shared frontend API client library, a central logging library, and a chain of correctness fixes that followed (Bearer token forwarding, `userId` type alignment, and JWT subject accuracy).

---

## Commits

| Commit    | Summary                                                            |
|-----------|--------------------------------------------------------------------|
| `0e9d945` | feature: created common module — `PayloadTokenProvider` & `Payload` |
| `cede07c` | feat: GitHub token exchange for backend JWT auth                   |
| `f1499c6` | feat: service-to-service JWT auth for internal onboarding API      |
| `af5e7c9` | feat: post-registration redirect flow                              |
| `9e636fb` | misc: outbox event properties added                                |
| `22f6dcc` | feature: created api-client                                        |
| `39a4bbf` | feature: implement central logging library                         |
| `c9983f9` | feat: migrate web app to central logging library                   |
| `b31a5a1` | fix: attach Bearer token to onboarding API requests                |
| `1470727` | fix: change userId from UUID to String in onboarding-service       |
| `22370b8` | db: migrate `onboarding_request.user_profile_id` from uuid to varchar |
| `48a049c` | fix: JWT carries internal profile UUID instead of GitHub numeric ID |

---

## What's in this PR

### Common module — `PayloadTokenProvider` & `Payload`

**New module: `backend/common`**

Shared library consumed by `user-service`, `onboarding-service`, and `api-gateway`.

| Class                   | Purpose                                                                                   |
|-------------------------|-------------------------------------------------------------------------------------------|
| `Payload`               | Record carrying `userId` and `correlationId` claims                                       |
| `PayloadTokenProvider`  | Generates and validates JWTs signed with a shared HMAC secret; wraps `xyz.catuns.spring.jwt` |
| `ServicePrincipal`      | Record carrying `sub` (caller identity) and `aud` (target service) for inter-service JWTs |
| `ServiceTokenProvider`  | Mints short-lived service JWTs (`type=SERVICE`) for machine-to-machine calls              |

All services reference `common` via Maven dependency; no code duplication.

---

### GitHub token exchange — `POST /api/v1/auth/token`

**New endpoint in `user-service`**

```
POST /api/v1/auth/token          (no auth required)
Body: { "githubAccessToken": "gho_..." }
Response: { "token": "<signed-jwt>", "expiresIn": 86400 }
```

**Backend flow (`TokenExchangeService`)**

1. Calls `GET https://api.github.com/user` with the supplied token as `Bearer`.
2. On `401`/`403` throws `GitHubAuthenticationException` → 401 response.
3. Looks up `ExternalIdentity` by `ProviderKey.GITHUB` + GitHub numeric ID.
4. Resolves the linked `UserProfile.id` (internal UUID).
5. Mints a JWT via `PayloadTokenProvider` with the internal UUID as `sub` and a fresh `correlationId`.

**api-gateway: public route exceptions**

`JwtAuthenticationFilter` whitelist extended to include `/api/v1/users/auth/token` and `/api/v1/users/registrations` so no bearer token is required for registration or token exchange.

**Frontend integration (`features/auth`)**

| File                            | Change                                                                                          |
|---------------------------------|-------------------------------------------------------------------------------------------------|
| `callbacks.ts` — `jwtCallback`  | Calls `exchangeToken(githubAccessToken)` immediately after GitHub OAuth; stores result in session |
| `callbacks.ts` — `sessionCallback` | Proactively refreshes backend JWT when within 5 minutes of expiry                             |
| `types/next-auth.d.ts`          | Extends `Session.user` with `backendToken`, `backendTokenExpiry`                               |
| `actions/token.ts`              | `exchangeToken(accessToken)` — thin wrapper calling `POST /api/v1/auth/token`                  |
| `config/client.ts`              | `onAuthenticated` interceptor attaches `Authorization: Bearer <backendToken>` on every request  |

**New exception handler**

`GlobalExceptionHandler` maps `GitHubAuthenticationException` → 401, `NoSuchElementException` → 404 with structured `{ code, message }` envelope.

**Tests**

- `AuthControllerTest` — 9 cases covering happy path, invalid/revoked tokens, unregistered users.
- `TokenExchangeServiceTest` — 9 cases covering JWT expiry, subject claim, correlationId, Bearer prefix, Feign error codes, unregistered user.

---

### Service-to-service JWT auth — internal onboarding API

**New filter in `onboarding-service`: `InternalApiAuthFilter`**

Guards all `POST /api/v1/internal/**` requests:

1. Extracts `Authorization: Bearer <service-jwt>` from the request.
2. Validates with `ServiceTokenProvider`; enforces `type=SERVICE`, `sub=user-service`, `aud=onboarding-service`.
3. Rejects with 401 on any validation failure.

**New Feign interceptor in `user-service`: `FeignServiceAuthInterceptor`**

Registered only on the `OnboardingFeignConfig` (scoped to the `onboarding-service` Feign client). Mints a fresh service JWT per request from `ServiceTokenProvider` and attaches it as the `Authorization` header.

**`OnboardingFeignConfig`**

Wires `FeignServiceAuthInterceptor` and `FeignCorrelationInterceptor` together; only applied to `OnboardingServiceFeignClient`.

---

### Post-registration redirect flow

**`features/auth/src/lib/auth/callbacks.ts`**

`signIn` sets `callbackUrl` to `/onboarding` so GitHub OAuth completion routes directly to the onboarding page, bypassing the default Next.js callback URL.

**`app/onboarding/page.tsx`**

| Guard condition                 | Action                                  |
|---------------------------------|-----------------------------------------|
| `registrationError` in session  | Redirect to `/register`                 |
| No `onboardingRequestId`        | Redirect to `/dashboard`                |

**`OnboardingPoller`**

| Poll result              | Action                    |
|--------------------------|---------------------------|
| `COMPLETED`              | Redirect to `/dashboard`  |
| `FAILED` or `CANCELLED`  | Redirect to `/support`    |

---

### Outbox event properties

`OutboxEventProperties` config class added to `user-service`. Externalises the aggregate type/event type values used when writing `OutboxEvent` rows so they can be driven from `application.yaml` rather than hardcoded strings.

---

### Frontend API client library — `frontend/clients/api-client`

New Nx library providing a typed, interceptor-based Axios client shared across all frontend features.

**Key exports**

| Export                  | Purpose                                                                                      |
|-------------------------|----------------------------------------------------------------------------------------------|
| `ApiClient`             | Axios wrapper with lifecycle hooks: `onRequest`, `onAuthenticated`, `onResponse`, `onError`  |
| `ApiError`              | Typed error class carrying `status`, `code`, `message`, backend error envelope               |
| `withApi`               | Server-action wrapper that catches `ApiError` and returns `ApiResponse<T>`                   |
| `useApiError`           | React hook normalising errors to display messages                                            |
| `ApiErrorBoundary`      | React error boundary for API errors                                                          |
| `ApiResponse<T>`        | Discriminated union `{ success: true, data } | { success: false, error }`                   |

**`features/base/src/lib/config/client.ts`** (updated)

Instantiates the shared `ApiClient` for the onboarding domain. `onAuthenticated` now reads `backendToken` from the NextAuth session via a dynamic `import('@feature/auth')` and attaches `Authorization: Bearer <token>` on every outbound request.

---

### Central logging library — `frontend/features/logging`

New Nx library; replaces the ad-hoc `web/lib/logger/*` files with a shared, importable package.

**Exports and path aliases**

| Alias                     | Entry point                    | Runtime         | Contents                                                      |
|---------------------------|--------------------------------|-----------------|---------------------------------------------------------------|
| `@feature/logging`        | `src/index.ts`                 | Browser          | pino browser logger, `getCorrelationId`, `setCorrelationId`   |
| `@feature/logging/server` | `src/server.ts`                | Node.js only    | pino server logger (pretty in dev, raw JSON in prod)          |
| `@feature/logging/config` | `src/config.ts`                | Edge-safe        | Plain-object pino options; no Node.js imports                 |

**Why `@feature/logging/config` is a separate entry point**

`instrumentation.ts` runs in the edge runtime for the `register()` function signature, but the actual logger setup must be deferred to the `nodejs` runtime. Importing `@feature/logging/server` dynamically inside `instrumentation.ts` caused Nx's `@nx/enforce-module-boundaries` rule to flag it as a lazy-loaded library (blocking static imports of `@feature/logging` elsewhere). The fix: export pino options as a plain object from `/config` (no Node.js APIs, edge-safe), import it statically in `instrumentation.ts`, then dynamically import only `pino` and `pino-http` directly.

**`next.config.js`**

```js
serverExternalPackages: ['pino', 'pino-pretty', 'thread-stream']
```

Prevents Next.js from bundling these packages. Without this, `thread-stream` workers receive a mangled `__filename` (`C:\ROOT\...`) and crash on startup in WSL/Windows environments.

**Child loggers**

All consuming files call `logger.child({ module: 'ComponentName' })` so every log line carries the originating file name in the `module` field.

---

### Fix: Bearer token missing from onboarding API requests

`features/base/src/lib/config/client.ts` — the `onAuthenticated` callback previously only logged the outgoing request without setting any header. Added `config.headers['Authorization'] = 'Bearer <backendToken>'` to mirror the pattern in the auth feature client. Symptom: `GET /api/v1/onboarding/{requestId}` returned 401 on every poll.

Poll interval changed from 3 000 ms to 30 000 ms.

---

### Fix: `userId` type alignment — onboarding-service (UUID → String)

The JWT `userId` claim carries a string value (initially the GitHub numeric ID, later the internal profile UUID). `onboarding-service` was parsing this claim as a `java.util.UUID`, causing an `IllegalArgumentException` at runtime whenever a non-UUID string arrived.

**Changes propagated through the full stack**

| Layer                              | Before             | After              |
|------------------------------------|--------------------|--------------------|
| `JwtPrincipalExtractor.extractUserId()` | Parsed claim as UUID | Returns raw String |
| `OnboardingRequest.userProfileId`  | `UUID`             | `String`           |
| `OnboardingStatusResponse.userId`  | `UUID`             | `String`           |
| `OnboardingInitRequest.userId`     | `@NotNull UUID`    | `@NotNull String`  |
| `OnboardingRequestRepository`      | `findBy...(UUID)`  | `findBy...(String)` |
| `OnboardingStatusService`          | `callerId: UUID`   | `callerId: String` |
| `OnboardingInitialisationService`  | `userId: UUID`     | `userId: String`   |
| `OnboardingInternalController`     | `@RequestParam UUID` | `@RequestParam String` |

**Flyway migration — `V5__alter_onboarding_request_user_profile_id_to_varchar.sql`**

```sql
alter table onboarding_request
    alter column user_profile_id type varchar(255)
        using user_profile_id::text;
```

Casts existing UUID values to their text representation; no data is lost.

---

### Fix: JWT subject is internal profile UUID, not GitHub ID

**Root cause**

`TokenExchangeService` was minting the JWT with `githubUserId` (e.g. `"123214008"`) as the `Payload.userId` claim. `onboarding-service` stored `user_profile_id` as the internal `UserProfile.id` (UUID). The ownership check `request.getUserProfileId().equals(callerId)` always failed because the two identifiers were from different spaces.

**Fix in `TokenExchangeService`**

```java
ExternalIdentity identity = identityRepository
    .findByProvider_ProviderKeyAndExternalUserId(ProviderKey.GITHUB, githubUserId)
    .orElseThrow(...);
String internalUserId = identity.getUserProfile().getId().toString();
JwtToken jwtToken = tokenProvider.generate(
    new Payload(internalUserId, UUID.randomUUID().toString()));
```

**Fix in `UserProfileService.getMe()`**

The `userId` parameter (now the internal UUID string) is parsed with `UUID.fromString()` and used to look up the GitHub identity via `findByProvider_ProviderKeyAndUserProfile_Id`. No new repository method required — this query already existed for the Atlassian identity lookup.

**`user-service` — `JwtPrincipalExtractor`**

Renamed `extractGithubUserId()` → `extractUserId()` to reflect that the claim is no longer GitHub-specific.

**Tests updated**

- `TokenExchangeServiceTest`: `stubIdentityFound()` now configures a `UserProfile` mock with a fixed `PROFILE_ID`; subject assertion verifies `PROFILE_ID.toString()` rather than the GitHub ID.
- `UserProfileControllerTest`: setup stub uses `extractUserId()` returning `USER_ID.toString()`; all `getMe(...)` stubs match on `USER_ID.toString()`.

---

## Test plan

- [ ] Register a new user via GitHub OAuth — verify `/onboarding` redirect with a valid `onboardingRequestId` in session
- [ ] Poll `GET /api/v1/onboarding/{requestId}` — request reaches onboarding-service with a valid `Authorization: Bearer` header (no 401)
- [ ] Confirm `onboarding_request.user_profile_id` matches `user_profile.id` (UUID), not the GitHub numeric ID
- [ ] Complete onboarding — verify redirect to `/dashboard`; trigger a failure state — verify redirect to `/support`
- [ ] Re-register an existing `PENDING_ONBOARDING` user — idempotent response, no duplicate records
- [ ] Call `GET /api/v1/me` — returns profile data; call with `?include=identities,onboarding` — returns GitHub identity and latest onboarding summary
- [ ] Call `POST /api/v1/internal/onboarding/init` without a service JWT — expect 401
- [ ] Run `./mvnw test` in `user-service` — 76 tests, all green
- [ ] Run `pnpm nx run-many -t test` — all frontend specs green