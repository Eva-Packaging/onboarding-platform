# Onboarding Platform – root Makefile
# Copy .env.example to .env and fill in values before running docker/deploy targets.

-include .env
export

# ── Configuration ─────────────────────────────────────────────────────────────
# Resolved from .env; can be overridden on the command line:
#   make docker-push GCP_PROJECT_ID=my-project
REGISTRY   ?= $(GCP_REGION)-docker.pkg.dev
PROJECT    ?= $(GCP_PROJECT_ID)
REPOSITORY ?= $(GCP_REPOSITORY)

SERVICES := user-service onboarding-service provisioning-service api-gateway

.PHONY: help env-check \
        build test clean \
        docker-build docker-push deploy \
        $(foreach s,$(SERVICES), \
          build-$(s) test-$(s) \
          docker-build-$(s) docker-push-$(s) \
          deploy-$(s))

# ── Help ──────────────────────────────────────────────────────────────────────
help:
	@echo "Onboarding Platform – Build Commands"
	@echo "======================================"
	@echo ""
	@echo "Setup:"
	@echo "  cp .env.example .env        Copy env template and fill in values"
	@echo ""
	@echo "Maven:"
	@echo "  make build                  Build all services (skip tests)"
	@echo "  make test                   Run tests for all services"
	@echo "  make clean                  Clean all services"
	@echo ""
	@echo "Docker (Spring Boot Buildpacks):"
	@echo "  make docker-build           Build Docker images locally"
	@echo "  make docker-push            Build and push images to Artifact Registry"
	@echo ""
	@echo "Per-service targets:"
	@echo "  make build-<service>        e.g. make build-user-service"
	@echo "  make test-<service>         e.g. make test-onboarding-service"
	@echo "  make docker-build-<service> e.g. make docker-build-api-gateway"
	@echo "  make docker-push-<service>  e.g. make docker-push-provisioning-service"
	@echo "  make deploy-<service>       e.g. make deploy-user-service"
	@echo ""
	@echo "Available services: $(SERVICES)"
	@echo ""
	@echo "Registry: $(REGISTRY)/$(PROJECT)/$(REPOSITORY)"

# ── Env guard ─────────────────────────────────────────────────────────────────
env-check:
	@test -n "$(GCP_PROJECT_ID)"  || (echo "ERROR: GCP_PROJECT_ID is not set. Copy .env.example to .env and fill in values." && exit 1)
	@test -n "$(GCP_REGION)"      || (echo "ERROR: GCP_REGION is not set."      && exit 1)
	@test -n "$(GCP_REPOSITORY)"  || (echo "ERROR: GCP_REPOSITORY is not set."  && exit 1)

# ── Maven – all services ──────────────────────────────────────────────────────
build:
	@for service in $(SERVICES); do \
		echo "Building $$service..."; \
		cd backend/$$service && ./mvnw clean install -DskipTests || exit 1; \
		cd ../..; \
	done

test:
	@for service in $(SERVICES); do \
		echo "Testing $$service..."; \
		cd backend/$$service && ./mvnw test || exit 1; \
		cd ../..; \
	done

clean:
	@for service in $(SERVICES); do \
		echo "Cleaning $$service..."; \
		cd backend/$$service && ./mvnw clean || exit 1; \
		cd ../..; \
	done

# ── Docker – all services ─────────────────────────────────────────────────────
docker-build: env-check
	@scripts/docker/build-images.sh

docker-push: env-check
	@scripts/docker/build-images.sh --push

deploy: env-check
	@scripts/gcloud/deploy-service.sh

# ── user-service ──────────────────────────────────────────────────────────────
build-user-service:
	cd backend/user-service && ./mvnw clean install
test-user-service:
	cd backend/user-service && ./mvnw test
docker-build-user-service: env-check
	scripts/docker/build-images.sh user-service
docker-push-user-service: env-check
	scripts/docker/build-images.sh --push user-service
deploy-user-service: env-check
	scripts/gcloud/deploy-service.sh user-service

# ── onboarding-service ────────────────────────────────────────────────────────
build-onboarding-service:
	cd backend/onboarding-service && ./mvnw clean install
test-onboarding-service:
	cd backend/onboarding-service && ./mvnw test
docker-build-onboarding-service: env-check
	scripts/docker/build-images.sh onboarding-service
docker-push-onboarding-service: env-check
	scripts/docker/build-images.sh --push onboarding-service
deploy-onboarding-service: env-check
	scripts/gcloud/deploy-service.sh onboarding-service

# ── provisioning-service ──────────────────────────────────────────────────────
build-provisioning-service:
	cd backend/provisioning-service && ./mvnw clean install
test-provisioning-service:
	cd backend/provisioning-service && ./mvnw test
docker-build-provisioning-service: env-check
	scripts/docker/build-images.sh provisioning-service
docker-push-provisioning-service: env-check
	scripts/docker/build-images.sh --push provisioning-service
deploy-provisioning-service: env-check
	scripts/gcloud/deploy-service.sh provisioning-service

# ── api-gateway ───────────────────────────────────────────────────────────────
build-api-gateway:
	cd backend/api-gateway && ./mvnw clean install
test-api-gateway:
	cd backend/api-gateway && ./mvnw test
docker-build-api-gateway: env-check
	scripts/docker/build-images.sh api-gateway
docker-push-api-gateway: env-check
	scripts/docker/build-images.sh --push api-gateway
deploy-api-gateway: env-check
	scripts/gcloud/deploy-service.sh api-gateway

# ── Quick shortcuts ───────────────────────────────────────────────────────────
users:         docker-push-user-service
onboarding:    docker-push-onboarding-service
provisioning:  docker-push-provisioning-service
apigw:         docker-push-api-gateway