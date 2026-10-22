# Observability Plan

Backend services deploy to GCP Cloud Run, where Cloud Logging, Cloud Monitoring
and Cloud Trace replace the self-hosted Zipkin / Prometheus / Loki / Promtail /
Grafana stack. That stack is only useful for local debugging, so it is **opt-in**:
off by default in every Spring profile and in `docker compose`, and switched on
with a single `observability` profile when needed.

## Goals

- The core platform (Postgres, Kafka, Schema Registry, Consul, the 4 services)
  runs with no observability containers and no wasted export work — no span
  reporting, no Prometheus registry, no reconnect warnings in the logs.
- One switch turns the full local stack back on, for both the containers and
  the Spring config.
- Cloud Run deployments (`dev` and `prod` profiles) never try to reach Zipkin
  or expose `/actuator/prometheus`.

## What stays on in every profile

| Feature                                            | Why                                                             |
|----------------------------------------------------|-----------------------------------------------------------------|
| Actuator `health` + liveness/readiness probes      | Cloud Run startup/liveness probes                               |
| Micrometer tracing (Brave bridge), sampling 1.0    | Populates `traceId`/`spanId` in log MDC; propagates over HTTP and Kafka |
| Kafka `template`/`listener` `observation-enabled`  | Trace context crosses the async event boundary                  |
| `X-Correlation-ID` filter + Feign interceptor      | Request correlation, independent of tracing                     |
| JSON logs (LogstashEncoder) outside `dev`/`local`  | Cloud Logging ingests structured stdout automatically           |

Dependencies (`zipkin-reporter-brave`, `micrometer-registry-prometheus`) stay on
the classpath; properties gate them. When export is disabled, Spring Boot does
not create the Zipkin sender/reporter or the `PrometheusMeterRegistry`
(Micrometer falls back to an in-memory `SimpleMeterRegistry`).

## What the `observability` profile adds

| Feature                    | Property (Spring Boot 3.5)                                       |
|----------------------------|------------------------------------------------------------------|
| Zipkin span export         | `management.zipkin.tracing.export.enabled: true` + `endpoint`    |
| Prometheus registry        | `management.prometheus.metrics.export.enabled: true`             |
| `/actuator/prometheus`     | `management.endpoints.web.exposure.include: health,prometheus`   |

Base `application.yaml` (all services):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      export:
        enabled: false
  prometheus:
    metrics:
      export:
        enabled: false
```

`application-observability.yaml` (all services):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  prometheus:
    metrics:
      export:
        enabled: true
  zipkin:
    tracing:
      export:
        enabled: true
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

The per-service `management.zipkin` overrides in `application-docker.yaml` are
removed; in Docker the endpoint comes from the `ZIPKIN_ENDPOINT` env var set in
`docker-compose.yml` (see below).

## Docker Compose

The five observability containers carry `profiles: [observability]`, so they
are skipped unless that profile is active. App services derive their Spring
profiles from the same variable:

```yaml
SPRING_PROFILES_ACTIVE: docker${COMPOSE_PROFILES:+,observability}
ZIPKIN_ENDPOINT: http://zipkin:9411/api/v2/spans
```

```bash
# Core only (default)
docker compose up -d

# Core + Zipkin/Prometheus/Loki/Promtail/Grafana, and services export to them
COMPOSE_PROFILES=observability docker compose up -d
# …or set COMPOSE_PROFILES=observability in .env
```

Use the `COMPOSE_PROFILES` variable rather than `--profile observability`: the
CLI flag starts the containers but does not set the variable, so the services
would run without the Spring `observability` profile.

Turning the stack off again: `docker compose --profile observability stop zipkin prometheus loki promtail grafana`
(or `down` and `up -d` without the variable), then restart the app services so
they drop the profile.

## Running services locally (outside Docker)

```bash
SPRING_PROFILES_ACTIVE=local,observability ./mvnw spring-boot:run
```

Start the stack with `COMPOSE_PROFILES=observability docker compose up -d zipkin prometheus loki grafana`.
Note that Prometheus scrapes the compose service names, so it only sees
services running in Docker.

## Why not profile groups on `dev`/`local`/`docker`

`scripts/gcloud/deploy-service.sh` deploys Cloud Run with
`SPRING_PROFILES_ACTIVE=dev` or `prod`, so grouping `observability` under `dev`
would re-enable Zipkin export on GCP. Grouping under `local`/`docker` would
make the services export to a stack that is now optional, producing repeated
connection warnings whenever only the core is running. An explicit switch keeps
containers and config in sync.

## Fixes bundled with this change

- `infra/prometheus/prometheus.yml` scraped `users:8080`, `onboarding:8080`,
  `provisioning:8080`; compose names are `user-service`, `onboarding-service`,
  `provisioning-service`.
- `provisioning-service` had no docker `ZIPKIN_ENDPOINT` override and would
  have exported to `localhost` inside its container; the endpoint now comes
  from the compose environment for all services.
- Removed the deprecated `management.endpoint.prometheus.enabled`.

## Verification

1. `docker compose config --services` lists no observability services;
   `COMPOSE_PROFILES=observability docker compose config --services` lists all.
2. Core only: `/actuator/prometheus` returns 404, `/actuator/health/liveness`
   and `/readiness` return 200, no Zipkin reporter warnings in service logs,
   log lines still carry `traceId`.
3. With the profile: `/actuator/prometheus` returns 200, Prometheus targets are
   UP, traces appear in Zipkin.

## Future (Phase 9 — GCP-native)

- **Logs**: add `logging.googleapis.com/trace` and `logging.googleapis.com/spanId`
  fields to the JSON encoder for Cloud Logging ↔ Cloud Trace correlation.
- **Metrics**: Cloud Run's built-in request/latency/instance metrics need no
  code. Custom app metrics would use `micrometer-registry-stackdriver` behind a
  `gcp` profile.
- **Traces**: export to Cloud Trace via OTLP (switch to the OTel bridge) with
  lower sampling in `prod`.

IAM already grants `roles/logging.logWriter`, `roles/monitoring.metricWriter`
and `roles/cloudtrace.agent` to the runtime service account.
