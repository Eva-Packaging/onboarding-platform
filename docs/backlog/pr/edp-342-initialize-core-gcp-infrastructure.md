## EDP-342 · Initialize core GCP infrastructure: project bootstrap, Terraform state, Artifact Registry for image builds

Stands up the minimal real GCP footprint the build/push pipeline already
assumes exists. EDP-74 scaffolded the Terraform modules under
`infra/terraform/` and validated them with `terraform validate`, but nothing
had been applied to a real project — `service_images` were placeholder stubs
and the GCS state backend was unconfigured. This PR adds: a scripted
project-bootstrap step (`scripts/gcloud/bootstrap-project.sh`) that enables the
required APIs and creates the Terraform state bucket; a new
`modules/artifact-registry` Terraform module wired into the root configuration
and applied via `environments/{dev,prod}.tfvars`; and least-privilege IAM
bindings granting push/pull access to the new repository. Cloud Run, Cloud SQL,
and Secret Manager rollout remain out of scope — deferred to the GCP-deployment
phase in `docs/roadmap.md` (Phase 9).

---

## Commits

| Commit    | Summary                                                                                  |
|-----------|------------------------------------------------------------------------------------------|
| `55c7ee1` | EDP-343 Script the GCP project bootstrap and Terraform state bucket creation             |
| `d101c4d` | EDP-344 Add and apply Artifact Registry Terraform module                                 |
| `671c583` | EDP-345 Grant push/pull access and validate the build pipeline against the real registry |

---

## What's in this PR

### `scripts/gcloud/bootstrap-project.sh` — one-time project bootstrap (EDP-343)

A new script, run **before** `terraform init`, that:

- Enables `artifactregistry.googleapis.com`, `iam.googleapis.com`, and
  `cloudresourcemanager.googleapis.com`.
- Creates a versioned GCS bucket to hold Terraform remote state — idempotently
  (skips creation if the bucket already exists).
- Prints the follow-up `terraform init -backend-config=...` command.

This stays a script rather than a Terraform resource because **Terraform
cannot create the bucket that stores its own state** — the same
bootstrap-before-Terraform gap that `deploy-service.sh --full-config` already
bridges for Cloud Run. It follows the same conventions as
`deploy-service.sh`: loads `.env`, supports `--project` / `--region` /
`--state-bucket` / `--dry-run` / `--help`, and uses the same colour-coded
`print_info` / `print_success` / `print_warning` / `print_error` helpers.

The bucket name is sourced from a new `GCP_TF_STATE_BUCKET` env var (added to
`.env.example`, alongside `GCP_PROJECT_ID` / `GCP_REGION` / `GCP_REPOSITORY`),
falling back to `<GCP_PROJECT_ID>-tf-state` if left blank — `--state-bucket`
overrides both for a one-off run.

```bash
./scripts/gcloud/bootstrap-project.sh                                # uses .env
./scripts/gcloud/bootstrap-project.sh --project onboarding-platform-dev
./scripts/gcloud/bootstrap-project.sh --state-bucket my-custom-bucket
./scripts/gcloud/bootstrap-project.sh --dry-run                      # print commands only
```

---

### `infra/terraform/modules/artifact-registry/` — new Terraform module (EDP-344)

A minimal module wrapping a single `google_artifact_registry_repository`
(`format = "DOCKER"`):

- **`variables.tf`** — `project_id`, `region`, `repository_id`.
- **`outputs.tf`** — `repository_id` and `repository_url`, the latter composed
  as `<region>-docker.pkg.dev/<project>/<repository>` — exactly the path shape
  `docker-compose.yml` and `scripts/docker/build-images.sh` already assume.

Wired into the root configuration:

- `infra/terraform/main.tf` — new `module "artifact_registry"` block, declared
  ahead of `module "iam"` so its `repository_id` output can be passed through
  for the IAM bindings added in EDP-345.
- `infra/terraform/variables.tf` — new `gcp_repository` variable, documented as
  needing to match `GCP_REPOSITORY` in `.env`.
- `infra/terraform/outputs.tf` — new `artifact_registry_url` root output.
- `environments/{dev,prod}.tfvars` — `gcp_repository = "onboarding"` (matches
  the repo name used in the EDP-74 PR examples and `docker-compose.yml`).

`terraform validate` → `Success! The configuration is valid.`

---

### IAM push/pull bindings & pipeline validation (EDP-345)

Extended `infra/terraform/modules/iam/` with repository-scoped
`google_artifact_registry_repository_iam_member` bindings (least-privilege —
scoped to the repository, not the project):

- **New `image-builder` service account** — granted `roles/artifactregistry.writer`
  on the repository. Its email is exposed as the
  `image_builder_service_account_email` output (both module-level and root) for
  use as the CI/CD push identity (`gcloud auth configure-docker` + `make docker-push`).
- **Each existing per-service Cloud Run service account** — granted
  `roles/artifactregistry.reader` on the repository, so each service's runtime
  identity can pull its own image.

New module variables `artifact_registry_location` / `artifact_registry_repository_id`
are passed from the root `module "iam"` block as `var.region` and
`module.artifact_registry.repository_id`.

`terraform validate` → `Success! The configuration is valid.`

**Pipeline validation:** confirmed via `docker compose config` that the
resolved image paths —
`us-central1-docker.pkg.dev/onboarding-platform-dev/onboarding/<service>:latest` —
exactly match the `repository_url` shape the new Terraform module/outputs
produce, so the IaC and the existing build/compose pipeline agree. Running
`make docker-push` and `docker compose pull` against a *real* registry requires
a bootstrapped project and real credentials, neither of which are available in
this environment — the same caveat that applies to `terraform apply` itself.

---

## Key design decisions

**Bootstrap stays scripted, not Terraform-managed.** Terraform can't create the
GCS bucket that holds its own state. Splitting the work this way — a small
`gcloud`/`gsutil` script for the one-time, imperative bootstrap step, and
Terraform for the persistent, evolvable resources (the registry repo, IAM
bindings) — avoids fighting that chicken-and-egg problem with `local-exec`
provisioners or a separate "meta" state, while keeping the things that need to
stay consistent across `dev`/`prod` (the registry shape, access bindings)
tracked as code.

**Repository-scoped IAM bindings over project-level.** `google_artifact_registry_repository_iam_member`
grants `roles/artifactregistry.writer`/`reader` only on the one repository
rather than project-wide — least privilege, and consistent with the existing
`iam` module's per-service, least-privilege role bindings (Secret Accessor,
Cloud SQL Client, Log Writer, Metric Writer, Trace Agent).

**Dedicated `image-builder` identity for pushes.** Rather than reusing one of
the per-service Cloud Run runtime accounts (which only need pull access) for
CI pushes, a separate service account keeps the push (write) capability
isolated from the runtime (read) capability — mirrors the "one service account
per concern" pattern already used for the per-service accounts.

---

## Docs & references

- New: `scripts/gcloud/bootstrap-project.sh` section in `docs/build-and-deploy.md`
  — usage, what it enables/creates, and why it stays scripted.
- New: "Artifact Registry access" section — documents the `image-builder` /
  per-service reader bindings and how to use them.
- New: "Bootstrap sequence for a fresh environment" section — the full
  `bootstrap-project.sh` → `terraform init`/`apply` → `make docker-push` →
  `docker compose pull`/`up` order of operations for standing up a new
  environment from scratch.
- `.env.example` — documented the new `GCP_TF_STATE_BUCKET` variable.
- `docs/backlog/epic-gcp-infra-initialization.json` — epic/story/task backlog
  this PR implements.

---

## How to verify locally

```bash
# Terraform — validate the new module and wiring (no backend/state required)
cd infra/terraform
terraform init -backend=false
terraform validate   # → Success! The configuration is valid.

# Bootstrap script — dry run (no GCP credentials required)
cd ../..
./scripts/gcloud/bootstrap-project.sh --project test-project --dry-run

# Confirm docker-compose still resolves to the expected Artifact Registry path
GCP_PROJECT_ID=onboarding-platform-dev GCP_REGION=us-central1 GCP_REPOSITORY=onboarding \
  docker compose config | grep "image:.*docker.pkg.dev"
# → us-central1-docker.pkg.dev/onboarding-platform-dev/onboarding/<service>:latest
```

Full end-to-end verification (`bootstrap-project.sh` for real,
`terraform apply`, `make docker-push`, `docker compose pull`) requires a real
GCP project and credentials — out of reach in this sandboxed environment, and
deferred to whoever runs the bootstrap sequence against `onboarding-platform-dev`.