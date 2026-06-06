#!/usr/bin/env bash
set -euo pipefail

TERRAFORM_VERSION="${TERRAFORM_VERSION:-1.9.8}"

if command -v brew &>/dev/null; then
  echo "Installing Terraform via Homebrew..."
  brew tap hashicorp/tap
  brew install hashicorp/tap/terraform
  echo "Terraform $(terraform version -json | python3 -c 'import sys,json; print(json.load(sys.stdin)["terraform_version"])') installed via Homebrew"
  exit 0
fi

echo "Homebrew not found; falling back to direct download..."

ARCH="$(uname -m)"
case "$ARCH" in
  x86_64)  ARCH="amd64" ;;
  arm64)   ARCH="arm64" ;;
  *)
    echo "Unsupported architecture: $ARCH" >&2
    exit 1
    ;;
esac

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

ZIP="terraform_${TERRAFORM_VERSION}_darwin_${ARCH}.zip"
URL="https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/${ZIP}"
SUMS_URL="https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_SHA256SUMS"

curl -fsSL "$URL"       -o "${TMPDIR}/${ZIP}"
curl -fsSL "$SUMS_URL"  -o "${TMPDIR}/SHA256SUMS"

cd "$TMPDIR"
grep "$ZIP" SHA256SUMS | shasum -a 256 --check --status
unzip -q "$ZIP" -d "$TMPDIR"

INSTALL_DIR="${INSTALL_DIR:-/usr/local/bin}"
install -m 755 "${TMPDIR}/terraform" "${INSTALL_DIR}/terraform"

echo "Terraform $(terraform version -json | python3 -c 'import sys,json; print(json.load(sys.stdin)["terraform_version"])') installed to ${INSTALL_DIR}/terraform"