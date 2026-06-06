## EDP-74 · Scaffold GCP Terraform infrastructure modules

Scaffolds the GCP infrastructure-as-code for the platform's Cloud Run / Cloud SQL / Secret Manager / IAM topology under `infra/terraform/`, and builds the local tooling needed to actually use it: a root `Makefile` that drives Maven builds, Buildpacks-based Docker image builds, and Cloud Run deploys from a single `.env`; cross-platform install scripts for `terraform` and `gcloud` (Linux/macOS/Windows); a `scripts/gcloud/deploy-service.sh` rolling-update / bootstrap deploy script; and `docker-compose.yml` changes that pull backend images from the same Artifact Registry path the build pipeline pushes to. Documentation (`docs/build-and-deploy.md`, `README.md`, `CLAUDE.md`) is updated to describe the new build/deploy flow.

---

## Commits

| Commit    | Summary                                                                       |
|-----------|-------------------------------------------------------------------------------|
| `0a4f3bd` | Scaffold GCP Terraform infrastructure modules                                 |
| `644621c` | Add root Makefile and update build-images.sh for this project                 |
| `aadcb94` | Add Cloud Run deploy script and gcloud install scripts                        |
| `94aa712` | Wire docker-compose images to GCP Artifact Registry and document build/deploy |

---

## What's in this PR

### `infra/terraform/` — GCP infrastructure modules

Root configuration plus four child modules, validated with `terraform validate` (`Success! The configuration is valid.`):

- **`versions.tf` / `backend.tf`** — `google` / `google-beta` providers (`~> 5.0`); GCS backend with a placeholder state prefix, bucket supplied via `-backend-config`.
- **`variables.tf`** — `project_id`, `region`, `environment` (validated to `dev`/`prod`), `vpc_network`, Cloud SQL tier/credentials (`db_password` marked `sensitive`), scaling bounds, and a `service_images` map covering all five deployables (`user-service`, `onboarding-service`, `provisioning-service`, `api-gateway`, `web`).
- **`main.tf`** — wires the modules together:
  ```hcl
  locals {
    backend_services = ["user-service", "onboarding-service", "provisioning-service"]
    all_services     = concat(local.backend_services, ["api-gateway", "web"])
  }
  ```
  Calls `module.iam` (per-service service accounts + least-privilege bindings), `module.secrets` (7 Secret Manager entries: `github-token`, `atlassian-api-key`, `atlassian-account-email`, `postgres-password`, `nextauth-secret`, `github-oauth-id`, `github-oauth-secret`), `module.cloud_sql`, and one `module.cloud_run_*` block per service — including `api-gateway`, whose env vars reference the upstream services' resolved URLs.
- **`outputs.tf`** — exposes every service URL (including `api_gateway_url`), `cloud_sql_connection_name`, service-account emails, and secret IDs.
- **`environments/{dev,prod}.tfvars`** — environment-specific variable sets.
- **`.gitignore`** — excludes `*.tfstate`, `*.tfplan`, `.terraform/`, but explicitly keeps `.terraform.lock.hcl` checked in for reproducible provider versions.

**`modules/cloud-run/`** — `google_cloud_run_v2_service` with `dynamic "env"` blocks for both plain and Secret-Manager-backed env vars, `lifecycle { ignore_changes = [template[0].containers[0].image] }` (so `gcloud run deploy` rolling updates aren't fought by Terraform), and an optional public-access IAM binding.

**`modules/cloud-sql/`** — Postgres 15 instance, private IP, automated backups, plus `google_sql_database` / `google_sql_user` resources for the per-service databases.

**`modules/secret-manager/`** — `for_each` over a list of secret IDs; no secret values are stored in Terraform state — only the secret resources themselves (versions are populated out-of-band).

**`modules/iam/`** — one service account per service with least-privilege role bindings (Secret Accessor, Cloud SQL Client, Log Writer, Metric Writer, Trace Agent).

---

### Root `Makefile` and `scripts/docker/build-images.sh`

A new root `Makefile` replaces the old per-service template approach. It loads `.env` (`-include .env` + `export`) and derives:

```makefile
REGISTRY   ?= $(GCP_REGION)-docker.pkg.dev
PROJECT    ?= $(GCP_PROJECT_ID)
REPOSITORY ?= $(GCP_REPOSITORY)
SERVICES   := user-service onboarding-service provisioning-service api-gateway
```

- `env-check` guards every docker/deploy target and fails fast with a clear message if `GCP_PROJECT_ID` / `GCP_REGION` / `GCP_REPOSITORY` are missing.
- Per-service targets are generated for `build`, `test`, `docker-build`, `docker-push`, and `deploy` (e.g. `make docker-push-api-gateway`), plus `users`/`onboarding`/`provisioning`/`apigw` quick-push shortcuts.
- `make build` / `test` / `clean` iterate Maven across all four backend services.

`scripts/docker/build-images.sh` was rewritten to fit this project (it previously targeted a different template's services):

- Sources `.env` from the repo root via `set -a; source "$ENV_FILE"; set +a`.
- Builds all four services via Spring Boot Buildpacks (`./mvnw spring-boot:build-image`), reading the version from each `pom.xml`.
- Tags and (with `--push`) pushes to `<region>-docker.pkg.dev/<project>/<repository>/<service>:<tag>`.
- Supports `--tag`, `--registry`, `--project`, `--repository` overrides and single-service invocation.

---

### `scripts/gcloud/deploy-service.sh`

New Cloud Run deploy script with two modes:

- **Rolling update (default)** — updates only the container image; all other configuration (secrets, IAM, scaling) is owned by Terraform. This is the mode CI/CD uses.
- **`--full-config`** — also sets the service account, `--set-secrets`, `--add-cloudsql-instances`, scaling, and `--set-env-vars`; used to bootstrap an environment before Terraform has provisioned the Cloud Run services.

Per-service configuration mirrors `infra/terraform/main.tf` so the two stay consistent:

```bash
SQL_SERVICES=(user-service onboarding-service provisioning-service)
declare -A SERVICE_DB=(["user-service"]="user_service" ["onboarding-service"]="onboarding_service" ["provisioning-service"]="provisioning_service")
declare -A SERVICE_SECRETS=(
    ["user-service"]="DB_PASSWORD=postgres-password:latest"
    ["onboarding-service"]="DB_PASSWORD=postgres-password:latest"
    ["provisioning-service"]="DB_PASSWORD=postgres-password:latest,GITHUB_TOKEN=github-token:latest,ATLASSIAN_API_KEY=atlassian-api-key:latest,ATLASSIAN_ACCOUNT_EMAIL=atlassian-account-email:latest"
    ["api-gateway"]=""
)
```

`api-gateway` is always deployed last; with `--full-config` the script resolves the other three services' URLs via `gcloud run services describe ... --format='value(status.url)'` and injects them as `USER_SERVICE_URL`, `ONBOARDING_SERVICE_URL`, `PROVISIONING_SERVICE_URL`. Supports `--dry-run` (prints `gcloud run deploy` commands without executing).

---

### Cross-platform install scripts

Per the "don't install CLI tools directly" convention, both `terraform` and `gcloud` get dedicated installers for Linux (`apt`/tarball fallback), macOS (`brew`/tarball fallback), and Windows (`winget`/`choco`/installer fallback):

- `scripts/terraform/install/{install-linux.sh, install-mac.sh, install-windows.ps1}`
- `scripts/gcloud/install/{install-linux.sh, install-mac.sh, install-windows.ps1}`

---

### `docker-compose.yml` and `.env.example`

Backend services (`api-gateway`, `user-service`, `onboarding-service`, and a new `provisioning-service` block) now pull their images from Artifact Registry, composed directly from the three GCP variables in each `image:` line:

```yaml
image: ${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT_ID}/${GCP_REPOSITORY}/user-service:latest
```

This is intentionally **not** routed through an intermediate `REGISTRY` variable in `.env` — Docker Compose does not expand nested `${...}` references inside `.env` files (each line loads as a literal `KEY=VALUE` pair), so `REGISTRY=${GCP_REGION}-docker.pkg.dev/...` would resolve to a string containing literal, unexpanded placeholders. Referencing `GCP_REGION`/`GCP_PROJECT_ID`/`GCP_REPOSITORY` individually lets Compose's own multi-variable interpolation resolve each one correctly — confirmed via `docker compose config`:

```
image: us-central1-docker.pkg.dev/onboarding-platform-dev/onboarding/user-service:latest
```

`provisioning-service` also gained `GITHUB_API_TOKEN`, `GITHUB_ORG`, `ATLASSIAN_API_KEY`, `ATLASSIAN_ACCOUNT_EMAIL` env vars; `api-gateway` gained `JWT_SECRET` / `FRONTEND_ORIGIN`. `.env.example` documents the GCP vars' dual role (driving both the Makefile/scripts and the compose image paths).

---

## Key design decisions

**No secret values in Terraform state.** `module.secrets` only creates the `google_secret_manager_secret` resources (IDs/replication policy); secret *versions* are populated out-of-band (e.g. via `gcloud secrets versions add` or the deploy script's bootstrap flow), so credentials never pass through `terraform plan`/`apply` output or state.

**`lifecycle { ignore_changes = [...image] }` on Cloud Run.** Terraform provisions the service shape (scaling, secrets, SA, networking) but deliberately ignores the container image field, so `gcloud run deploy` rolling updates (the CI/CD path) don't get reverted on the next `terraform apply`.

**Deploy script mirrors Terraform's per-service config.** `SERVICE_DB` / `SERVICE_SECRETS` / `SQL_SERVICES` in `deploy-service.sh` intentionally duplicate the shape of `infra/terraform/main.tf` rather than reading it dynamically — keeping the bootstrap (`--full-config`) and steady-state (rolling update) paths independently runnable without a Terraform state dependency, at the cost of needing to keep the two in sync by hand.

**Install scripts instead of inline `apt-get`/mocks.** Both `terraform` and `gcloud` get real, cross-platform installers rather than being installed ad hoc or stubbed for testing — consistent with how the rest of the toolchain is bootstrapped (see `scripts/*/install/`).

**Direct GCP-var interpolation over a composed `REGISTRY` variable.** Verified empirically with `docker compose config` that `.env` files don't expand nested `${VAR}` references — a templated `REGISTRY` value stays literal. Composing the path inline in `docker-compose.yml` from `GCP_REGION`/`GCP_PROJECT_ID`/`GCP_REPOSITORY` is the only approach that resolves correctly while keeping the registry path defined in one place (`.env`).

---

## Docs & references

- New: `docs/build-and-deploy.md` — full reference for `make` targets, `build-images.sh`, `deploy-service.sh`, and the image-naming/`.env` interpolation caveat.
- `README.md` — added a "Build, Image & Deploy" section and updated the project-structure tree.
- `CLAUDE.md` — corrected the `docker-compose.yml` location (moved to repo root pre-existing this branch but doc was stale), added a "Build, image & deploy commands" section, added `docs/build-and-deploy.md` to the docs-as-source-of-truth table, and updated delivery-phase status to mention EDP-74's Terraform scaffolding.

---

## How to verify locally

```bash
# Terraform — validate the scaffolded modules (no backend/state required)
cd infra/terraform
terraform init -backend=false
terraform validate   # → Success! The configuration is valid.

# Makefile / build pipeline
cp .env.example .env   # fill in GCP_PROJECT_ID, GCP_REGION, GCP_REPOSITORY
make help
make build                       # Maven build, all backend services
make docker-build                # local Buildpacks image build
./scripts/gcloud/deploy-service.sh --dry-run   # print gcloud run deploy commands

# docker-compose image resolution
GCP_PROJECT_ID=onboarding-platform-dev GCP_REGION=us-central1 GCP_REPOSITORY=onboarding \
  docker compose config | grep "image:.*docker.pkg.dev"
# → us-central1-docker.pkg.dev/onboarding-platform-dev/onboarding/<service>:latest

# Install scripts (run the one matching your OS)
./scripts/terraform/install/install-linux.sh
./scripts/gcloud/install/install-linux.sh
```