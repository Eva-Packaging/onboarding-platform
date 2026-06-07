#!/usr/bin/env bash
# Bootstrap a GCP project for Terraform-managed infrastructure.
#
# Enables the APIs Terraform and the build pipeline depend on, and creates the
# GCS bucket that holds Terraform remote state. This stays a script rather than
# a Terraform resource because Terraform cannot create the bucket that stores
# its own state — the same bootstrap-before-Terraform gap that
# deploy-service.sh --full-config bridges for Cloud Run.
#
# Usage:
#   ./scripts/gcloud/bootstrap-project.sh [OPTIONS]
#
# Options:
#   --project PROJ        Override GCP_PROJECT_ID
#   --region REG          Override GCP_REGION
#   --state-bucket NAME   Override the Terraform state bucket name
#                         (default: GCP_TF_STATE_BUCKET, or <project>-tf-state)
#   --dry-run             Print gcloud/gsutil commands without executing them
#   --help                Show this help message
#
# Examples:
#   ./scripts/gcloud/bootstrap-project.sh
#   ./scripts/gcloud/bootstrap-project.sh --project onboarding-platform-dev --region us-central1
#   ./scripts/gcloud/bootstrap-project.sh --dry-run

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
STATE_BUCKET="${GCP_TF_STATE_BUCKET:-}"
DRY_RUN=false

REQUIRED_APIS=(
    artifactregistry.googleapis.com
    iam.googleapis.com
    cloudresourcemanager.googleapis.com
)

# ── Arg parsing ───────────────────────────────────────────────────────────────
show_help() { sed -n '4,25p' "$0" | sed 's/^# \?//'; exit 0; }

while [[ $# -gt 0 ]]; do
    case $1 in
        --project)      PROJECT="$2";      shift 2 ;;
        --region)       REGION="$2";       shift 2 ;;
        --state-bucket) STATE_BUCKET="$2"; shift 2 ;;
        --dry-run)      DRY_RUN=true;      shift ;;
        --help)         show_help ;;
        -*)
            print_error "Unknown option: $1"
            show_help
            ;;
        *)
            print_error "Unexpected argument: $1"
            show_help
            ;;
    esac
done

[[ -z "$STATE_BUCKET" ]] && STATE_BUCKET="${PROJECT}-tf-state"

# ── Pre-flight checks ─────────────────────────────────────────────────────────
if [[ -z "$PROJECT" ]]; then
    print_error "GCP_PROJECT_ID is not set. Copy .env.example to .env and fill in values, or pass --project."
    exit 1
fi
if ! command -v gcloud &>/dev/null; then
    print_error "gcloud CLI not found. Install via scripts/gcloud/install/."
    exit 1
fi
if ! command -v gsutil &>/dev/null; then
    print_error "gsutil not found. It ships with the gcloud CLI — reinstall via scripts/gcloud/install/."
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

bucket_exists() {
    gsutil ls -b "gs://$1" &>/dev/null
}

# ── Main ──────────────────────────────────────────────────────────────────────
print_info "Onboarding Platform – GCP Project Bootstrap"
print_info "Project:      ${PROJECT}"
print_info "Region:       ${REGION}"
print_info "State bucket: gs://${STATE_BUCKET}"
[[ "$DRY_RUN" == true ]] && print_warning "DRY RUN — no changes will be made"
echo ""

print_info "Enabling required APIs: ${REQUIRED_APIS[*]}"
run_cmd gcloud services enable "${REQUIRED_APIS[@]}" --project="$PROJECT"
print_success "APIs enabled"
echo ""

print_info "Creating Terraform state bucket gs://${STATE_BUCKET}..."
if [[ "$DRY_RUN" == false ]] && bucket_exists "$STATE_BUCKET"; then
    print_warning "Bucket gs://${STATE_BUCKET} already exists — skipping creation"
else
    run_cmd gsutil mb -p "$PROJECT" -l "$REGION" -b on "gs://${STATE_BUCKET}"
fi
run_cmd gsutil versioning set on "gs://${STATE_BUCKET}"
print_success "Terraform state bucket ready"
echo ""

print_success "Bootstrap complete!"
print_info "Next: initialize Terraform against the real backend, e.g."
echo ""
echo "  cd infra/terraform"
echo "  terraform init \\"
echo "    -backend-config=\"bucket=${STATE_BUCKET}\" \\"
echo "    -backend-config=\"prefix=terraform/state/dev\""