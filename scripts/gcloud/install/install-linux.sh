#!/usr/bin/env bash
# Install the Google Cloud CLI (gcloud) on Linux via the official apt repository.
# Falls back to the standalone tarball installer if apt is unavailable.
set -euo pipefail

if command -v gcloud &>/dev/null; then
    echo "gcloud is already installed: $(gcloud --version | head -1)"
    exit 0
fi

if command -v apt-get &>/dev/null; then
    echo "Installing Google Cloud CLI via apt..."

    sudo apt-get update -y
    sudo apt-get install -y apt-transport-https ca-certificates gnupg curl

    echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" \
        | sudo tee /etc/apt/sources.list.d/google-cloud-sdk.list > /dev/null

    curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg \
        | sudo gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg

    sudo apt-get update -y
    sudo apt-get install -y google-cloud-cli

    echo "gcloud installed: $(gcloud --version | head -1)"
    exit 0
fi

echo "apt-get not found; falling back to standalone tarball installer..."

ARCH="$(uname -m)"
case "$ARCH" in
    x86_64)  ARCH="x86_64" ;;
    aarch64) ARCH="arm" ;;
    *)
        echo "Unsupported architecture: $ARCH" >&2
        exit 1
        ;;
esac

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

URL="https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-cli-linux-${ARCH}.tar.gz"
INSTALL_DIR="${INSTALL_DIR:-$HOME/google-cloud-sdk}"

echo "Downloading $URL..."
curl -fsSL "$URL" -o "${TMPDIR}/gcloud.tar.gz"
tar -xzf "${TMPDIR}/gcloud.tar.gz" -C "$(dirname "$INSTALL_DIR")"

"${INSTALL_DIR}/install.sh" --quiet --path-update true --command-completion true

echo ""
echo "gcloud installed to ${INSTALL_DIR}."
echo "Restart your shell, or run: source ${INSTALL_DIR}/path.bash.inc"
echo "Then authenticate with: gcloud init"