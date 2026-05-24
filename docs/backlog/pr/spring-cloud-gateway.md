## EDP-224 · Spring Cloud Gateway — Unified API Entry Point and Centralized Documentation

Introduces `backend/api-gateway` as the single public entry point for all three backend services. The gateway routes client traffic to `user-service`, `onboarding-service`, and `provisioning-service` via Consul-based load balancing and path-based predicates. A global JWT authentication filter enforces bearer token validation at the edge and forwards identity headers downstream. Centralized API documentation is produced by routing per-service `/v3/api-docs` endpoints through the gateway and aggregating them into a single Swagger UI. A global CORS filter and correlation ID propagation filter complete the edge concerns.

---

## Commits

| Commit | Story | Summary |
|---|---|---|
| `8bccadd` | EDP-229 | Create api-gateway Maven module |
| `f139176` | EDP-225 | api-gateway module bootstrapping and Consul-based service routing |
| `7517cdd` | EDP-226 | Centralized OpenAPI documentation aggregation via SpringDoc |
| `7a09c66` | EDP-227 | Global JWT authentication filter at the gateway edge |
| `59acc94` | EDP-228 | Global CORS configuration and correlation ID propagation |

---

## What's in this PR

### EDP-229 / EDP-225 · api-gateway module bootstrapping and Consul-based service routing

**Module structure**

- `backend/api-gateway/pom.xml` — parent `xyz.catuns.spring:base-starter-parent`; dependencies: `spring-cloud-starter-gateway-server-webflux`, `spring-cloud-starter-consul-discovery`, `spring-boot-starter-actuator`, `springdoc-openapi-starter-webflux-ui`, `xyz.catuns.spring:jwt-core`, `reactor-test`
- `ApiGatewayApplication.java` — standard `@SpringBootApplication` entry point
- `Dockerfile` — multi-stage build (JDK 21 build stage + JRE 21 runtime stage)
- `backend/api-gateway/.env.example` — `JWT_SECRET`, `FRONTEND_ORIGIN`

**Route table**

| Incoming path | Downstream URI | Transform |
|---|---|---|
| `/api/v1/users/**` | `lb://user-service` | `RewritePath` strips `/users` segment |
| `/api/v1/onboarding/**` | `lb://onboarding-service` | Pass-through |
| `/api/v1/admin/**` | `lb://onboarding-service` | Pass-through |
| `/api/v1/provisioning/**` | `lb://provisioning-service` | `RewritePath` strips `/provisioning` segment |

All routes use Consul service discovery via `lb://` URIs. Consul discovery locator is enabled with `lower-case-service-id: true`.

**Docker Compose**

- `infra/docker/docker-compose.yml` — `api-gateway` service added, exposes port `8080` externally; downstream services moved to internal-only ports so all external traffic enters through the gateway

**Profile YAMLs**

- `application-dev.yaml`, `application-docker.yaml`, `application-local.yaml`, `application-prod.yaml` — environment-specific overrides for Consul host, JWT secret binding, and log levels

**Tests**

- `GatewayRoutingTest` — `@SpringBootTest` test verifying all four route definitions are wired to the correct `lb://` URIs; uses `RouteLocator` directly, no downstream services needed

---

### EDP-226 · Centralized OpenAPI documentation aggregation via SpringDoc

**Proxy routes**

Three gateway routes proxy the per-service OpenAPI JSON under a gateway-namespaced path using `RewritePath`:

| Gateway path | Rewrites to |
|---|---|
| `GET /v3/api-docs/user-service` | `lb://user-service → /v3/api-docs` |
| `GET /v3/api-docs/onboarding-service` | `lb://onboarding-service → /v3/api-docs` |
| `GET /v3/api-docs/provisioning-service` | `lb://provisioning-service → /v3/api-docs` |

**Docs UI shortcuts**

Redirect routes at `/api/v1/docs/{service}` issue HTTP 302 to `/swagger-ui/index.html?urls.primaryName={service}` so each service can be deep-linked directly.

**SpringDoc configuration**

```yaml
springdoc:
  swagger-ui:
    disable-swagger-default-url: true
    urls:
      - name: user-service
        url: /v3/api-docs/user-service
      - name: onboarding-service
        url: /v3/api-docs/onboarding-service
      - name: provisioning-service
        url: /v3/api-docs/provisioning-service
```

**Gateway OpenAPI bean**

- `AppConfig.java` — `OpenAPI` bean populated from `AppProperties.openApi` (title, version, description, contact) bound via `@ConfigurationProperties(prefix = "app")`

---

### EDP-227 · Global JWT authentication filter at the gateway edge

**`JwtAuthenticationFilter`** — `GlobalFilter` + `Ordered.HIGHEST_PRECEDENCE`

- Extracts the bearer token from the `Authorization` header; returns HTTP 401 JSON (`{"status":401,"error":"Unauthorized","message":"..."}`) for missing, non-`Bearer` prefixed, or whitespace-only values
- Delegates validation to `TokenProvider<Payload>` from `xyz.catuns.spring:jwt-core`
- On success: mutates the exchange to inject `X-User-Id` (from `payload.userId()`) and `X-Correlation-Id` (from `payload.correlationId()`, or a generated UUID if the claim is absent) before forwarding
- On `TokenProvider` exception: returns HTTP 401 without forwarding

**Public-path bypass**

Configured via `jwt.public-paths` bound to `JwtProperties` (`@ConfigurationProperties(prefix = "jwt")`, extends `JwtMetadata`). Default bypass list:

```
/swagger-ui/**, /v3/api-docs/**, /actuator/**, /error/**, /api/v1/docs/**
```

Path matching uses `AntPathMatcher`.

**Supporting classes**

- `Payload(String userId, String correlationId)` — record holding validated token claims
- `PayloadTokenProvider extends SimpleTokenProvider<Payload>` — maps JWT `sub` claim → `userId`, `correlationId` claim → `correlationId`
- `JwtConfig` — `@Configuration` that produces the `TokenProvider<Payload>` bean from `JwtProperties`

**Tests** (`JwtAuthenticationFilterTest` — 9 tests)

| Test | Covers |
|---|---|
| `getOrder_returnsHighestPrecedence` | Filter order |
| `filter_publicPath_swaggerUi_passesThrough` | `AntPathMatcher` bypass |
| `filter_publicPath_actuatorHealth_passesThrough` | Bypass on `/actuator/**` |
| `filter_missingAuthorizationHeader_returns401WithMessage` | Null header → 401 + JSON body |
| `filter_authHeaderWithoutBearerPrefix_returns401` | `Basic` scheme → 401 |
| `filter_validToken_withCorrelationId_forwardsUserIdAndCorrelationIdHeaders` | `X-User-Id` and `X-Correlation-Id` forwarded |
| `filter_validToken_withNullCorrelationId_generatesRandomUuidAsCorrelationId` | UUID generated when claim absent |
| `filter_invalidToken_tokenProviderThrows_returns401WithMessage` | Exception → 401 + JSON body |
| `filter_tokenWithSurroundingSpaces_trimsBeforeValidation` | `.trim()` on extracted token |

---

### EDP-228 · Global CORS configuration and correlation ID propagation

**CORS configuration**

Added to `application.yaml` under `spring.cloud.gateway.server.webflux.globalcors`:

```yaml
globalcors:
  cors-configurations:
    '[/**]':
      allowedOrigins: "${FRONTEND_ORIGIN:http://localhost:3000}"
      allowedMethods: [GET, POST, PUT, DELETE, OPTIONS]
      allowedHeaders: ["*"]
      allowCredentials: true
```

`FRONTEND_ORIGIN` defaults to `http://localhost:3000` and can be overridden per environment via the `.env` file.

**`CorrelationIdFilter`** — `GlobalFilter` at `Ordered.HIGHEST_PRECEDENCE + 1`

Runs just after `JwtAuthenticationFilter` so it reads the correlation ID already injected by JWT validation on authenticated paths, and generates a fresh UUID for public paths that bypass JWT.

- Reads `X-Correlation-Id` from the incoming request; generates a UUID if the header is absent or blank
- Mutates the forwarded request so all downstream services receive the header
- Registers a `beforeCommit` hook to write the same value onto the response — every response carries `X-Correlation-Id` regardless of path

**Tests** (`CorrelationIdFilterTest` — 5 tests)

| Test | Covers |
|---|---|
| `getOrder_isHighestPrecedencePlusOne` | Filter order |
| `filter_noCorrelationId_generatesUuidAndAddsToRequestAndResponse` | UUID generated, written to both request and response |
| `filter_withExistingCorrelationId_preservesItOnRequestAndResponse` | Existing header preserved end-to-end |
| `filter_withBlankCorrelationId_treatsAsAbsentAndGeneratesUuid` | Blank header treated as absent |
| `filter_generatedIdsAreUniqueAcrossRequests` | Each request gets a distinct UUID |

---

## Test plan

- [ ] Run `./mvnw test` in `backend/api-gateway` — all 14 filter tests + routing tests pass
- [ ] Start `docker compose up` and confirm the gateway registers in Consul at `http://localhost:8500`
- [ ] CORS preflight: `curl -X OPTIONS http://localhost:8080/api/v1/users/me -H "Origin: http://localhost:3000" -v` → `Access-Control-Allow-Origin: http://localhost:3000`, HTTP 200
- [ ] No-auth request: `curl http://localhost:8080/api/v1/users/me` → HTTP 401 JSON envelope
- [ ] Swagger UI: open `http://localhost:8080/swagger-ui/index.html` — service selector shows `user-service`, `onboarding-service`, `provisioning-service`
- [ ] End-to-end smoke: `POST /api/v1/users/registrations` through the gateway with a valid JWT → forwarded to `user-service` as `POST /api/v1/registrations`
- [ ] Correlation ID: any response includes `X-Correlation-Id` header; sending `X-Correlation-Id: my-id` on a public path returns `X-Correlation-Id: my-id` in the response