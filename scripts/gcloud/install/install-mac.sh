#!/usr/bin/env bash
# Install the Google Cloud CLI (gcloud) on macOS via Homebrew.
# Falls back to the standalone tarball installer if Homebrew is unavailable.
set -euo pipefail

if command -v gcloud &>/dev/null; then
    echo "gcloud is already installed: $(gcloud --version | head -1)"
    exit 0
fi

if command -v brew &>/dev/null; then
    echo "Installing Google Cloud CLI via Homebrew..."
    brew install --cask google-cloud-sdk
    echo "gcloud installed: $(gcloud --version | head -1)"
    echo "Authenticate with: gcloud init"
    exit 0
fi

echo "Homebrew not found; falling back to standalone tarball installer..."

ARCH="$(uname -m)"
case "$ARCH" in
    x86_64) ARCH="x86_64" ;;
    arm64)  ARCH="arm" ;;
    *)
        echo "Unsupported architecture: $ARCH" >&2
        exit 1
        ;;
esac

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

URL="https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-cli-darwin-${ARCH}.tar.gz"
INSTALL_DIR="${INSTALL_DIR:-$HOME/google-cloud-sdk}"

echo "Downloading $URL..."
curl -fsSL "$URL" -o "${TMPDIR}/gcloud.tar.gz"
tar -xzf "${TMPDIR}/gcloud.tar.gz" -C "$(dirname "$INSTALL_DIR")"

"${INSTALL_DIR}/install.sh" --quiet --path-update true --command-completion true

echo ""
echo "gcloud installed to ${INSTALL_DIR}."
echo "Restart your shell, or run: source ${INSTALL_DIR}/path.bash.inc"
echo "Then authenticate with: gcloud init"