#!/usr/bin/env bash
set -euo pipefail

TERRAFORM_VERSION="${TERRAFORM_VERSION:-1.9.8}"
ARCH="$(uname -m)"

case "$ARCH" in
  x86_64)  ARCH="amd64" ;;
  aarch64) ARCH="arm64" ;;
  *)
    echo "Unsupported architecture: $ARCH" >&2
    exit 1
    ;;
esac

echo "Installing Terraform ${TERRAFORM_VERSION} (linux/${ARCH})..."

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

ZIP="terraform_${TERRAFORM_VERSION}_linux_${ARCH}.zip"
URL="https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/${ZIP}"
SUMS_URL="https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_SHA256SUMS"

curl -fsSL "$URL"       -o "${TMPDIR}/${ZIP}"
curl -fsSL "$SUMS_URL"  -o "${TMPDIR}/SHA256SUMS"

cd "$TMPDIR"
grep "$ZIP" SHA256SUMS | sha256sum --check --status
unzip -q "$ZIP" -d "$TMPDIR"

INSTALL_DIR="${INSTALL_DIR:-/usr/local/bin}"
if [[ ! -w "$INSTALL_DIR" ]]; then
  sudo install -m 755 "${TMPDIR}/terraform" "${INSTALL_DIR}/terraform"
else
  install -m 755 "${TMPDIR}/terraform" "${INSTALL_DIR}/terraform"
fi

echo "Terraform $(terraform version -json | grep -o '"terraform_version":"[^"]*"' | cut -d'"' -f4) installed to ${INSTALL_DIR}/terraform"