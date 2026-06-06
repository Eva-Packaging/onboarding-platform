#!/usr/bin/env bash
# Build (and optionally push) Docker images for all backend services using
# Spring Boot Buildpacks.
#
# Configuration is read from .env at the repo root; every value can be
# overridden with the flags below.
#
# Usage:
#   ./scripts/docker/build-images.sh [OPTIONS] [SERVICE...]
#
# Options:
#   --push              Push images to Artifact Registry after building
#   --tag TAG           Additional tag to apply (default: latest)
#   --registry REG      Override GCP_REGION-docker.pkg.dev
#   --project PROJ      Override GCP_PROJECT_ID
#   --repository REPO   Override GCP_REPOSITORY
#   --help              Show this help message
#
# Services (default: all):
#   user-service
#   onboarding-service
#   provisioning-service
#   api-gateway
#
# Examples:
#   ./scripts/docker/build-images.sh                          # build all
#   ./scripts/docker/build-images.sh --push                   # build + push all
#   ./scripts/docker/build-images.sh --push user-service      # single service
#   ./scripts/docker/build-images.sh --tag v1.2.0 api-gateway # custom tag

set -euo pipefail

# ── Colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
print_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

# ── Paths ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"

# ── Load .env ─────────────────────────────────────────────────────────────────
ENV_FILE="${REPO_ROOT}/.env"
if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

# ── Defaults (env vars take precedence; flags override everything) ─────────────
REGISTRY="${GCP_REGION:+${GCP_REGION}-docker.pkg.dev}"
REGISTRY="${REGISTRY:-us-central1-docker.pkg.dev}"
PROJECT="${GCP_PROJECT_ID:-}"
REPOSITORY="${GCP_REPOSITORY:-}"
PUSH=false
ADDITIONAL_TAG="latest"

ALL_SERVICES=(
    "user-service"
    "onboarding-service"
    "provisioning-service"
    "api-gateway"
)
SERVICES_TO_BUILD=()

# ── Arg parsing ───────────────────────────────────────────────────────────────
show_help() { sed -n '4,29p' "$0" | sed 's/^# \?//'; exit 0; }

while [[ $# -gt 0 ]]; do
    case $1 in
        --push)       PUSH=true; shift ;;
        --tag)        ADDITIONAL_TAG="$2"; shift 2 ;;
        --registry)   REGISTRY="$2"; shift 2 ;;
        --project)    PROJECT="$2"; shift 2 ;;
        --repository) REPOSITORY="$2"; shift 2 ;;
        --help)       show_help ;;
        -*)           print_error "Unknown option: $1"; show_help ;;
        *)
            if [[ " ${ALL_SERVICES[*]} " =~ " $1 " ]]; then
                SERVICES_TO_BUILD+=("$1")
            else
                print_error "Unknown service: $1"
                print_info  "Available services: ${ALL_SERVICES[*]}"
                exit 1
            fi
            shift
            ;;
    esac
done

[[ ${#SERVICES_TO_BUILD[@]} -eq 0 ]] && SERVICES_TO_BUILD=("${ALL_SERVICES[@]}")

# ── Pre-flight checks ─────────────────────────────────────────────────────────
if [[ -z "$PROJECT" ]]; then
    print_error "GCP_PROJECT_ID is not set. Copy .env.example to .env and fill in values, or pass --project."
    exit 1
fi
if [[ -z "$REPOSITORY" ]]; then
    print_error "GCP_REPOSITORY is not set. Copy .env.example to .env and fill in values, or pass --repository."
    exit 1
fi
if ! docker info > /dev/null 2>&1; then
    print_error "Docker is not running. Please start Docker and try again."
    exit 1
fi

# ── Helpers ───────────────────────────────────────────────────────────────────
get_version_from_pom() {
    local service_dir=$1
    if [[ ! -f "${service_dir}/pom.xml" ]]; then
        print_error "pom.xml not found in ${service_dir}"; return 1
    fi
    if [[ ! -x "${service_dir}/mvnw" ]]; then
        print_error "mvnw not found or not executable in ${service_dir}"; return 1
    fi
    cd "$service_dir" && ./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null
}

build_service_image() {
    local service=$1
    local service_dir="${BACKEND_DIR}/${service}"

    if [[ ! -d "$service_dir" ]]; then
        print_error "Service directory not found: ${service_dir}"; return 1
    fi

    print_info "Building ${service}..."

    local version
    version=$(get_version_from_pom "$service_dir")
    if [[ -z "$version" ]]; then
        print_error "Failed to extract version from pom.xml"; return 1
    fi
    print_info "Version: ${version}"

    local base_image="${REGISTRY}/${PROJECT}/${REPOSITORY}/${service}"
    local versioned_image="${base_image}:${version}"
    print_info "Image: ${versioned_image}"

    cd "$service_dir"
    if ! ./mvnw -Dimage.name="${versioned_image}" spring-boot:build-image -DskipTests; then
        print_error "Build failed for ${service}"; return 1
    fi
    print_success "Built ${versioned_image}"

    if [[ "$ADDITIONAL_TAG" != "$version" ]]; then
        local tagged_image="${base_image}:${ADDITIONAL_TAG}"
        docker tag "${versioned_image}" "${tagged_image}"
        print_success "Tagged as ${tagged_image}"
    fi

    if [[ "$PUSH" == true ]]; then
        print_info "Pushing ${versioned_image}..."
        if ! docker push "${versioned_image}"; then
            print_error "Push failed for ${versioned_image}"
            print_warning "Authenticate first: gcloud auth configure-docker ${REGISTRY}"
            return 1
        fi
        print_success "Pushed ${versioned_image}"

        if [[ "$ADDITIONAL_TAG" != "$version" ]]; then
            local tagged_image="${base_image}:${ADDITIONAL_TAG}"
            docker push "${tagged_image}" && print_success "Pushed ${tagged_image}"
        fi
    fi

    print_success "Completed ${service}"
    echo ""
}

# ── Main ──────────────────────────────────────────────────────────────────────
print_info "Onboarding Platform Image Builder"
print_info "Registry:   ${REGISTRY}/${PROJECT}/${REPOSITORY}"
print_info "Services:   ${SERVICES_TO_BUILD[*]}"
print_info "Extra tag:  ${ADDITIONAL_TAG}"
print_info "Push:       ${PUSH}"
echo ""

failed_services=()
for service in "${SERVICES_TO_BUILD[@]}"; do
    build_service_image "$service" || failed_services+=("$service")
done

echo ""
print_info "Build Summary"
echo "============="
if [[ ${#failed_services[@]} -eq 0 ]]; then
    print_success "All services built successfully!"
    if [[ "$PUSH" == true ]]; then
        print_success "Images pushed to ${REGISTRY}/${PROJECT}/${REPOSITORY}"
    else
        print_info "Images are local. Run with --push to publish."
        print_info "Auth: gcloud auth configure-docker ${REGISTRY}"
    fi
    exit 0
else
    print_error "Failed: ${failed_services[*]}"
    exit 1
fi