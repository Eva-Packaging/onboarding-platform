#!/usr/bin/env bash
# Deploy backend services to Cloud Run.
#
# For CI/CD rolling updates (the default), only the container image is updated;
# all other configuration (secrets, IAM, scaling) is managed by Terraform.
# Use --full-config for first-time deploys before Terraform has run.
#
# Usage:
#   ./scripts/gcloud/deploy-service.sh [OPTIONS] [SERVICE...]
#
# Options:
#   --tag TAG          Image tag to deploy (default: latest)
#   --project PROJ     Override GCP_PROJECT_ID
#   --region REG       Override GCP_REGION
#   --repository REPO  Override GCP_REPOSITORY
#   --app-env ENV      App environment profile: dev or prod (default: APP_ENVIRONMENT or dev)
#   --full-config      Set env vars, secrets, scaling, and service account in addition
#                      to the image — use for bootstrapping before Terraform has run
#   --dry-run          Print gcloud commands without executing them
#   --help             Show this help message
#
# Services (default: all):
#   user-service
#   onboarding-service
#   provisioning-service
#   api-gateway
#
# Examples:
#   ./scripts/gcloud/deploy-service.sh                               # rolling update, all services
#   ./scripts/gcloud/deploy-service.sh --tag v1.2.3                  # deploy specific tag
#   ./scripts/gcloud/deploy-service.sh user-service                  # single service
#   ./scripts/gcloud/deploy-service.sh --full-config --app-env prod  # bootstrap prod

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

# ── Load .env ─────────────────────────────────────────────────────────────────
ENV_FILE="${REPO_ROOT}/.env"
if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

# ── Defaults ──────────────────────────────────────────────────────────────────
PROJECT="${GCP_PROJECT_ID:-}"
REGION="${GCP_REGION:-us-central1}"
REPOSITORY="${GCP_REPOSITORY:-}"
APP_ENV="${APP_ENVIRONMENT:-dev}"
TAG="latest"
FULL_CONFIG=false
DRY_RUN=false

ALL_SERVICES=(user-service onboarding-service provisioning-service api-gateway)
SERVICES_TO_DEPLOY=()

# ── Per-service configuration (mirrors infra/terraform/main.tf) ───────────────

# Services that connect to Cloud SQL
SQL_SERVICES=(user-service onboarding-service provisioning-service)

# Database name per service
declare -A SERVICE_DB=(
    ["user-service"]="user_service"
    ["onboarding-service"]="onboarding_service"
    ["provisioning-service"]="provisioning_service"
)

# Secret Manager bindings per service: ENV_VAR=secret-name:version,...
declare -A SERVICE_SECRETS=(
    ["user-service"]="DB_PASSWORD=postgres-password:latest"
    ["onboarding-service"]="DB_PASSWORD=postgres-password:latest"
    ["provisioning-service"]="DB_PASSWORD=postgres-password:latest,GITHUB_TOKEN=github-token:latest,ATLASSIAN_API_KEY=atlassian-api-key:latest,ATLASSIAN_ACCOUNT_EMAIL=atlassian-account-email:latest"
    ["api-gateway"]=""
)

# ── Arg parsing ───────────────────────────────────────────────────────────────
show_help() { sed -n '4,30p' "$0" | sed 's/^# \?//'; exit 0; }

while [[ $# -gt 0 ]]; do
    case $1 in
        --tag)         TAG="$2";        shift 2 ;;
        --project)     PROJECT="$2";    shift 2 ;;
        --region)      REGION="$2";     shift 2 ;;
        --repository)  REPOSITORY="$2"; shift 2 ;;
        --app-env)     APP_ENV="$2";    shift 2 ;;
        --full-config) FULL_CONFIG=true; shift ;;
        --dry-run)     DRY_RUN=true;    shift ;;
        --help)        show_help ;;
        -*)
            print_error "Unknown option: $1"
            show_help
            ;;
        *)
            if [[ " ${ALL_SERVICES[*]} " =~ " $1 " ]]; then
                SERVICES_TO_DEPLOY+=("$1")
            else
                print_error "Unknown service: $1"
                print_info  "Available services: ${ALL_SERVICES[*]}"
                exit 1
            fi
            shift
            ;;
    esac
done

[[ ${#SERVICES_TO_DEPLOY[@]} -eq 0 ]] && SERVICES_TO_DEPLOY=("${ALL_SERVICES[@]}")

REGISTRY="${REGION}-docker.pkg.dev"

# ── Pre-flight checks ─────────────────────────────────────────────────────────
if [[ -z "$PROJECT" ]]; then
    print_error "GCP_PROJECT_ID is not set. Copy .env.example to .env and fill in values, or pass --project."
    exit 1
fi
if [[ -z "$REPOSITORY" ]]; then
    print_error "GCP_REPOSITORY is not set. Copy .env.example to .env and fill in values, or pass --repository."
    exit 1
fi
if ! command -v gcloud &>/dev/null; then
    print_error "gcloud CLI not found. Install from https://cloud.google.com/sdk/docs/install"
    exit 1
fi
if [[ "$DRY_RUN" == false ]] && ! gcloud auth print-access-token &>/dev/null 2>&1; then
    print_error "Not authenticated with gcloud. Run: gcloud auth login"
    exit 1
fi

# ── Helpers ───────────────────────────────────────────────────────────────────
run_cmd() {
    if [[ "$DRY_RUN" == true ]]; then
        echo "[DRY-RUN]" "$@"
    else
        "$@"
    fi
}

is_sql_service() { [[ " ${SQL_SERVICES[*]} " =~ " $1 " ]]; }

get_service_url() {
    gcloud run services describe "$1" \
        --project="$PROJECT" \
        --region="$REGION" \
        --format='value(status.url)' 2>/dev/null || true
}

# ── Deploy ────────────────────────────────────────────────────────────────────
deploy_service() {
    local service=$1
    # Optional extra env vars injected by the caller (used for api-gateway upstream URLs)
    local extra_env_vars="${2:-}"

    local image="${REGISTRY}/${PROJECT}/${REPOSITORY}/${service}:${TAG}"
    local sql_instance="${PROJECT}:${REGION}:onboarding-postgres-${APP_ENV}"

    print_info "Deploying ${service}..."
    print_info "Image: ${image}"

    local args=(
        gcloud run deploy "$service"
        --image="$image"
        --region="$REGION"
        --project="$PROJECT"
        --quiet
    )

    if [[ "$FULL_CONFIG" == true ]]; then
        local sa_email="${service}@${PROJECT}.iam.gserviceaccount.com"
        args+=(
            --service-account="$sa_email"
            --no-allow-unauthenticated
            --min-instances=0
            --max-instances=3
        )

        local secrets="${SERVICE_SECRETS[$service]:-}"
        [[ -n "$secrets" ]] && args+=("--set-secrets=${secrets}")

        # Build plain env var string
        local env_vars="SPRING_PROFILES_ACTIVE=${APP_ENV},ENVIRONMENT=${APP_ENV}"

        if is_sql_service "$service"; then
            env_vars+=",DB_INSTANCE_CONNECTION_NAME=${sql_instance}"
            env_vars+=",DB_NAME=${SERVICE_DB[$service]}"
            env_vars+=",DB_USERNAME=${DATABASE_USER:-app}"
            args+=("--add-cloudsql-instances=${sql_instance}")
        fi

        [[ -n "$extra_env_vars" ]] && env_vars+=",${extra_env_vars}"

        args+=("--set-env-vars=${env_vars}")
    fi

    run_cmd "${args[@]}"

    if [[ "$DRY_RUN" == false ]]; then
        local url
        url=$(get_service_url "$service")
        print_success "Deployed ${service} → ${url:-<url pending>}"
    else
        print_success "Dry-run complete for ${service}"
    fi
    echo ""
}

# ── Determine deploy order ────────────────────────────────────────────────────
# api-gateway is always last so upstream URLs are available when --full-config
# is used and the gateway's env vars need to reference the other services.
upstream_services=()
deploy_gateway=false
for s in "${SERVICES_TO_DEPLOY[@]}"; do
    if [[ "$s" == "api-gateway" ]]; then
        deploy_gateway=true
    else
        upstream_services+=("$s")
    fi
done

# ── Main ──────────────────────────────────────────────────────────────────────
print_info "Onboarding Platform – Cloud Run Deploy"
print_info "Project:     ${PROJECT}"
print_info "Region:      ${REGION}"
print_info "Registry:    ${REGISTRY}/${PROJECT}/${REPOSITORY}"
print_info "Tag:         ${TAG}"
print_info "App env:     ${APP_ENV}"
print_info "Services:    ${SERVICES_TO_DEPLOY[*]}"
print_info "Full config: ${FULL_CONFIG}"
[[ "$DRY_RUN" == true ]] && print_warning "DRY RUN — no changes will be made"
echo ""

failed_services=()

# Deploy upstream services first
for service in "${upstream_services[@]}"; do
    deploy_service "$service" || failed_services+=("$service")
done

# Deploy api-gateway last, wiring upstream URLs when --full-config is set
if [[ "$deploy_gateway" == true ]]; then
    gateway_extra_env=""

    if [[ "$FULL_CONFIG" == true ]]; then
        print_info "Resolving upstream URLs for api-gateway..."

        user_url=$(get_service_url "user-service")
        onboarding_url=$(get_service_url "onboarding-service")
        provisioning_url=$(get_service_url "provisioning-service")

        if [[ -z "$user_url" || -z "$onboarding_url" || -z "$provisioning_url" ]]; then
            print_warning "One or more upstream service URLs could not be resolved."
            print_warning "Deploy upstream services first, or configure api-gateway URLs manually."
        fi

        [[ -n "$user_url" ]]        && gateway_extra_env+="USER_SERVICE_URL=${user_url},"
        [[ -n "$onboarding_url" ]]  && gateway_extra_env+="ONBOARDING_SERVICE_URL=${onboarding_url},"
        [[ -n "$provisioning_url" ]] && gateway_extra_env+="PROVISIONING_SERVICE_URL=${provisioning_url},"
        gateway_extra_env="${gateway_extra_env%,}"  # trim trailing comma
    fi

    deploy_service "api-gateway" "$gateway_extra_env" || failed_services+=("api-gateway")
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
print_info "Deploy Summary"
echo "=============="
if [[ ${#failed_services[@]} -eq 0 ]]; then
    print_success "All services deployed successfully!"
    exit 0
else
    print_error "Failed: ${failed_services[*]}"
    exit 1
fi