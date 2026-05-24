## EDP-243 · Observability, Telemetry and Logging Foundation

Establishes end-to-end observability across all four backend microservices and the Next.js frontend. Covers distributed tracing via Micrometer Tracing and Zipkin, Prometheus metrics scraping with custom business counters, structured JSON logging enriched with MDC fields (traceId, spanId, correlationId, userId), Kafka trace context propagation via B3 headers, and structured client/server logging in Next.js via Pino. Local infrastructure (Zipkin, Prometheus, Grafana) is added to the docker-compose stack.

---

## Commits

| Commit | Story | Summary |
|---|---|---|
| `e385d30` | EDP-244 | Add Micrometer Tracing and Zipkin reporter to all backend services |
| `4499715` | EDP-245 | Expose Prometheus metrics endpoint on all backend services |
| `bed6abf` | EDP-246 | Structured JSON logging with MDC enrichment across all backend services |
| `153fc5d` | EDP-247 | Kafka trace context propagation and consumer metrics |
| `5e42c45` | EDP-248 | Add Zipkin, Prometheus, and Grafana to local docker-compose stack |
| _(staged)_ | EDP-249 | Structured logging in Next.js frontend with Pino |

---

## What's in this PR

### EDP-244 · Micrometer Tracing and Zipkin reporter — all backend services

**Dependencies added to all four `pom.xml` files**

- `micrometer-tracing-bridge-brave` — Brave/Zipkin bridge for Micrometer's tracing abstraction
- `zipkin-reporter-brave` — Zipkin span exporter

**Application config (`application.yaml` on each service)**

```yaml
management:
  zipkin.tracing.endpoint: http://localhost:9411/api/v2/spans
  tracing.sampling.probability: 1.0
```

**Correlation ID bridged into Brave baggage**

Each service's `CorrelationIdFilter` (and the api-gateway's reactive equivalent) was updated to write the resolved `X-Correlation-ID` into a `BaggageField` so the correlation ID propagates on every outbound span and appears in the Zipkin UI. The api-gateway uses the WebFlux reactor context variant.

**api-gateway reactive context**

Micrometer's reactor context hooks are active via auto-configuration on the reactive stack; no additional wiring was needed beyond the bridge dependency.

**Tests**

- `CorrelationIdFilterTest` updated to assert baggage propagation.

---

### EDP-245 · Prometheus metrics endpoint — all backend services

**Dependency added to all four `pom.xml` files**

- `micrometer-registry-prometheus`

**Actuator config**

```yaml
management:
  endpoints.web.exposure.include: health,info,prometheus,metrics
  endpoint.prometheus.enabled: true
```

**Custom business counters**

| Service | Counter | Tag |
|---|---|---|
| `user-service` | `onboarding_registrations_total` | — |
| `onboarding-service` | `onboarding_step_failures_total` | `step_type` |
| `provisioning-service` | `provisioning_outcomes_total` | `provider`, `result` |
| `provisioning-service` | `ProvisioningMetrics` bean | wraps all three |

Counters are injected into service classes via `MeterRegistry` constructor injection.

**Tests**

- `ProvisioningMetricsTest` — uses `SimpleMeterRegistry` to assert each counter increments on the correct method call.

---

### EDP-246 · Structured JSON logging with MDC enrichment — all backend services

**Dependency added to all four `pom.xml` files**

- `net.logstash.logback:logstash-logback-encoder:8.0`

**`logback-spring.xml` on each service**

| Profile | Encoder | Notes |
|---|---|---|
| `dev` / default | `LogstashEncoder` with `prettyPrint` | Human-readable during local development |
| `prod` | `LogstashEncoder` raw JSON | Single-line JSON for log aggregators |

A static `service` field is set via `<springProperty>` reading `spring.application.name` so log lines are identifiable across aggregated streams.

**MDC enrichment**

Each service's `CorrelationIdFilter` was extended to populate:

| MDC key | Source |
|---|---|
| `correlationId` | `X-Correlation-ID` request header (generated if absent) |
| `traceId` | `Tracer.currentSpan().context().traceId()` |
| `spanId` | `Tracer.currentSpan().context().spanId()` |

A new `MdcUserInterceptor` (`HandlerInterceptorAdapter` registered via `MdcWebMvcConfig`) populates:

| MDC key | Source |
|---|---|
| `userId` | `SecurityContextHolder` JWT subject claim |

**New files per service (example: `user-service`)**

- `MdcWebMvcConfig.java` — registers `MdcUserInterceptor`
- `MdcUserInterceptor.java` — sets `userId` MDC key from Spring Security context; clears on completion
- `logback-spring.xml` — replaces default pattern layout

**Tests**

- `MdcUserInterceptorTest` — asserts `userId` MDC key is set for authenticated requests and cleared on completion.
- `OutboxEventPublisherTest` — asserts MDC `correlationId` is forwarded as a Kafka record header.

---

### EDP-247 · Kafka trace context propagation and consumer metrics

**Application config**

```yaml
spring:
  kafka:
    template.observation-enabled: true
    listener.observation-enabled: true
```

Activates Micrometer tracing on `KafkaTemplate` producers and listener containers. Spring Kafka 3.x auto-configures `ObservationRegistry` hooks when `micrometer-tracing` is on the classpath.

**`KafkaConsumerMetrics` bean**

Registered in `KafkaConfig` on `onboarding-service` and `provisioning-service`. Binds consumer lag and offset metrics to the `MeterRegistry` so Prometheus scrapes `kafka_consumer_fetch_manager_records_lag` per topic partition.

**Correlation ID across Kafka boundaries**

`OutboxEventPublisher` (new in each service) reads `correlationId` from the active MDC and writes it as a Kafka record header (`X-Correlation-ID`). A consumer-side `KafkaListenerInterceptor` (or equivalent MDC restore logic) re-populates MDC when the message is processed, preserving correlation across async boundaries.

**Database migrations**

- `user-service/V3__add_kafka_fields_to_outbox.sql`
- `onboarding-service/V4__add_kafka_fields_to_outbox.sql`
- `provisioning-service/V3__add_kafka_fields_to_outbox.sql`

Each adds `b3_trace_context JSONB` and `headers JSONB` columns to `outbox_event`, storing the serialised B3 propagation context alongside the payload.

**Tests**

- `OnboardingDomainServiceTest` — asserts Kafka observation config is active and consumer lag metrics are bound.

---

### EDP-248 · Zipkin, Prometheus, and Grafana in docker-compose

**New services in `infra/docker/docker-compose.yml`**

| Service | Image | Port | Purpose |
|---|---|---|---|
| `zipkin` | `openzipkin/zipkin:3` | `9411` | Trace collector and UI |
| `prometheus` | `prom/prometheus:latest` | `9093:9090` | Metrics scraper |
| `grafana` | `grafana/grafana:latest` | `3001:3000` | Dashboards; default login `admin/admin` |

**Prometheus scrape config — `infra/docker/prometheus/prometheus.yml`**

Scrapes `/actuator/prometheus` on all four backend services at 15-second intervals. Targets use internal Docker network hostnames.

**Grafana provisioning — `infra/docker/grafana/provisioning/`**

| File | Purpose |
|---|---|
| `datasources/prometheus.yml` | Pre-wires Prometheus datasource at `http://prometheus:9093` — no manual configuration required on first start |
| `dashboards/dashboard.yml` | Dashboard provider pointing at the provisioned dashboard directory |
| `dashboards/spring-boot.json` | Starter dashboard with JVM memory, HTTP server request rate/duration, and Kafka consumer lag panels |

**`.env.example` additions**

```
ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans
PROMETHEUS_URL=http://prometheus:9093
GRAFANA_URL=http://grafana:3001
```

---

### EDP-249 · Structured logging in Next.js frontend with Pino

**Packages added to `frontend/package.json`**

- `pino ^10.3.1` — structured logger (browser build resolved automatically by webpack)
- `pino-http ^11.0.0` — HTTP middleware adapter (used in server instrumentation)
- `pino-pretty ^13.1.3` (devDependency) — human-readable dev transport

**New files**

| File | Purpose |
|---|---|
| `frontend/apps/web/lib/logger/server.ts` | pino instance; `pino-pretty` transport in development, raw JSON in production; static `service: 'web'` base field |
| `frontend/apps/web/lib/logger/correlation.ts` | Module-level correlation ID store backed by `sessionStorage`; `getCorrelationId` / `setCorrelationId` |
| `frontend/apps/web/lib/logger/client.ts` | pino browser logger; `transmit.send` POSTs each log event to `/api/log` via `navigator.sendBeacon` (fetch fallback); attaches current `correlationId` on every event |
| `frontend/apps/web/app/api/log/route.ts` | POST route that receives browser log events and re-emits them via the server logger with `method`, `url`, `statusCode`, `responseTime`, `correlationId` |
| `frontend/apps/web/middleware.ts` | Edge middleware; reads or generates `X-Correlation-ID`; forwards it on both the incoming request headers and response headers for all routes |
| `frontend/apps/web/components/providers/GlobalErrorHandler.tsx` | `'use client'` component; reads `session.user.correlationId` from NextAuth JWT via `useSession()` and calls `setCorrelationId()`; wires `window.onerror` and `unhandledrejection` into the browser logger |

**`instrumentation.ts` (updated)**

`register()` guards on `NEXT_RUNTIME === 'nodejs'`, dynamically imports `pino-http` and the server logger, and stores a configured `pinoHttp` instance on `globalThis.__httpLogger` for use by route handlers. Logs server start at `info` level.

**`app/layout.tsx` (updated)**

Mounts `<GlobalErrorHandler />` inside `<Providers>` so it has access to the NextAuth `SessionProvider` context.

**`features/base/src/lib/config/client.ts` (updated)**

Added a browser-only response interceptor (`typeof window !== 'undefined'` guard) that reads the `X-Correlation-ID` header from every Axios response and writes it to `sessionStorage` under the `correlation_id` key. The `correlation.ts` module reads the same key so per-request backend correlation IDs are reflected in subsequent browser log events without any cross-package import.

**Correlation ID sources (priority order)**

1. `session.user.correlationId` — set at login time from the registration response; seeded via `GlobalErrorHandler` on session load
2. `sessionStorage['correlation_id']` — updated by the Axios response interceptor on every backend API call; read directly by `getCorrelationId()`

---

## Test plan

- [ ] `docker compose -f infra/docker/docker-compose.yml up -d` — Zipkin UI reachable at `http://localhost:9411`, Prometheus targets at `http://localhost:9093/targets` all show **UP**, Grafana at `http://localhost:3001` with Prometheus datasource pre-configured
- [ ] Send `POST /api/v1/registrations` through the api-gateway — verify a multi-span trace (api-gateway → user-service) appears in Zipkin with `correlationId` baggage
- [ ] `GET /actuator/prometheus` on each service — verify JVM, HTTP, and custom business counter metrics are present
- [ ] Trigger a registration flow — verify `onboarding_registrations_total` increments in the user-service scrape
- [ ] Inspect server logs — every line is single-line JSON with `traceId`, `spanId`, `correlationId`, `service`, and `userId` fields
- [ ] Start the Next.js dev server (`pnpm nx run web:serve`) — server logs are colourised and human-readable via pino-pretty
- [ ] Throw a client-side JavaScript error — verify `window.onerror` fires, browser logger posts to `/api/log`, and the server log line includes `source: browser` and `correlationId`
- [ ] Check `X-Correlation-ID` response header on any Next.js route — value should be present and consistent within a session
