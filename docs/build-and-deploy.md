# Build & Deploy

How backend services are built, containerized, and deployed using the root `Makefile`
and its supporting scripts under `scripts/`.

## Overview

```
make build / test          → Maven (per service, via mvnw)
make docker-build / push   → scripts/docker/build-images.sh  (Spring Boot Buildpacks → Artifact Registry)
make deploy                → scripts/gcloud/deploy-service.sh (Cloud Run)
```

All three layers read shared configuration from a single `.env` file at the repo
root (copied from `.env.example`), so the project id, region, and Artifact
Registry repository are defined once and reused everywhere.

## Prerequisites

```bash
cp .env.example .env
# fill in GCP_PROJECT_ID, GCP_REGION, GCP_REPOSITORY, etc.
```

| Tool        | Needed for                             | Install script               |
|-------------|----------------------------------------|------------------------------|
| Docker      | `docker-build` / `docker-push`         | —                            |
| `gcloud`    | `docker-push` (auth), `deploy`         | `scripts/gcloud/install/`    |
| `terraform` | Provisioning infra (`infra/terraform`) | `scripts/terraform/install/` |

The install scripts are cross-platform (`install-linux.sh`, `install-mac.sh`,
`install-windows.ps1`) — run the one matching your OS rather than installing the
CLI manually.

## The Makefile

The root `Makefile` loads `.env` (`-include .env` + `export`) and derives:

```makefile
REGISTRY   ?= $(GCP_REGION)-docker.pkg.dev
PROJECT    ?= $(GCP_PROJECT_ID)
REPOSITORY ?= $(GCP_REPOSITORY)
SERVICES   := user-service onboarding-service provisioning-service api-gateway
```

Every value can be overridden on the command line, e.g. `make docker-push GCP_PROJECT_ID=my-project`.

### Targets

**Maven** (run for all `SERVICES`, or a single one with the `-<service>` suffix):

```bash
make build              # ./mvnw clean install -DskipTests
make test               # ./mvnw test
make clean              # ./mvnw clean
make build-user-service
make test-onboarding-service
```

**Docker** (delegates to `scripts/docker/build-images.sh`, requires `env-check`):

```bash
make docker-build                     # build images locally for all services
make docker-push                      # build + push to Artifact Registry
make docker-build-api-gateway         # single service
make docker-push-provisioning-service
```

**Deploy** (delegates to `scripts/gcloud/deploy-service.sh`, requires `env-check`):

```bash
make deploy                # rolling image update, all services
make deploy-user-service   # single service
```

**Quick shortcuts** — push a single service's image:

```bash
make users          # → docker-push-user-service
make onboarding     # → docker-push-onboarding-service
make provisioning   # → docker-push-provisioning-service
make apigw          # → docker-push-api-gateway
```

The `env-check` target guards docker/deploy targets, failing fast with a clear
message if `GCP_PROJECT_ID`, `GCP_REGION`, or `GCP_REPOSITORY` are unset.

## scripts/docker/build-images.sh

Builds (and optionally pushes) container images for the four backend services
using Spring Boot Buildpacks (`./mvnw spring-boot:build-image`). Configuration
is loaded from `.env` and can be overridden with flags.

```bash
./scripts/docker/build-images.sh                          # build all
./scripts/docker/build-images.sh --push                   # build + push all
./scripts/docker/build-images.sh --push user-service      # single service
./scripts/docker/build-images.sh --tag v1.2.0 api-gateway # custom tag
```

For each service it:
1. Reads the project version from `pom.xml` (`./mvnw help:evaluate -Dexpression=project.version`).
2. Builds `<region>-docker.pkg.dev/<project>/<repository>/<service>:<version>` via Buildpacks.
3. Tags it with an additional tag (default `latest`).
4. With `--push`, pushes both tags to Artifact Registry (run
   `gcloud auth configure-docker <registry>` first if push fails).

## scripts/gcloud/bootstrap-project.sh

One-time bootstrap for a fresh GCP project, run **before** `terraform init`.

```bash
./scripts/gcloud/bootstrap-project.sh                                            # uses GCP_PROJECT_ID / GCP_REGION from .env
./scripts/gcloud/bootstrap-project.sh --project onboarding-platform-dev          # override the project
./scripts/gcloud/bootstrap-project.sh --state-bucket my-custom-tf-state-bucket   # override the state bucket name
./scripts/gcloud/bootstrap-project.sh --dry-run                                  # print gcloud/gsutil commands only
```

It enables the APIs Terraform and the build pipeline depend on
(`artifactregistry.googleapis.com`, `iam.googleapis.com`,
`cloudresourcemanager.googleapis.com`) and creates a versioned GCS bucket to
hold Terraform remote state, named from `GCP_TF_STATE_BUCKET` in `.env`
(falling back to `<GCP_PROJECT_ID>-tf-state` if left blank — same default/override
pattern as `GCP_PROJECT_ID`/`GCP_REGION`/`GCP_REPOSITORY`). `--state-bucket`
overrides both for a one-off run.

This step stays a script rather than a Terraform resource because Terraform
cannot create the bucket that stores its own state — the same
bootstrap-before-Terraform gap that `deploy-service.sh --full-config` bridges
for Cloud Run. Once it's run, point Terraform at the bucket:

```bash
cd infra/terraform
terraform init \
  -backend-config="bucket=<GCP_PROJECT_ID>-tf-state" \
  -backend-config="prefix=terraform/state/dev"
terraform plan -var-file=environments/dev.tfvars
```

## scripts/gcloud/deploy-service.sh

Deploys backend services to Cloud Run.

```bash
./scripts/gcloud/deploy-service.sh                               # rolling update, all services
./scripts/gcloud/deploy-service.sh --tag v1.2.3                  # deploy a specific tag
./scripts/gcloud/deploy-service.sh user-service                  # single service
./scripts/gcloud/deploy-service.sh --full-config --app-env prod  # bootstrap prod
./scripts/gcloud/deploy-service.sh --dry-run                     # print gcloud commands only
```

Two modes:

- **Rolling update (default)** — only updates the container image. All other
  configuration (secrets, IAM, scaling) is expected to already be managed by
  Terraform (`infra/terraform/`). This is the mode CI/CD should use.
- **`--full-config`** — also sets the service account, Secret Manager bindings,
  Cloud SQL connection, scaling, and environment variables. Use this for the
  first deploy of an environment, before Terraform has provisioned the Cloud Run
  services.

Per-service configuration (Cloud SQL membership, database name, Secret Manager
bindings) mirrors `infra/terraform/main.tf` so the script and the Terraform
modules stay consistent. `api-gateway` is always deployed last — with
`--full-config` the script resolves the other three services' URLs via
`gcloud run services describe` and injects them as `USER_SERVICE_URL`,
`ONBOARDING_SERVICE_URL`, `PROVISIONING_SERVICE_URL`.

## Image naming convention

Both the build and deploy scripts, the Terraform `cloud-run` modules, and
`docker-compose.yml` agree on the same Artifact Registry path shape:

```
<GCP_REGION>-docker.pkg.dev/<GCP_PROJECT_ID>/<GCP_REPOSITORY>/<service>:<tag>
```

`docker-compose.yml` builds this path directly from the three `GCP_*`
variables in each service's `image:` line:

```yaml
image: ${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT_ID}/${GCP_REPOSITORY}/user-service:latest
```