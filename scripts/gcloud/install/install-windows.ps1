#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$InstallDir = "$env:LOCALAPPDATA\Google\Cloud SDK"
)

$ErrorActionPreference = "Stop"

if (Get-Command gcloud -ErrorAction SilentlyContinue) {
    Write-Host "gcloud is already installed: $(gcloud --version | Select-Object -First 1)"
    exit 0
}

# Try winget first (available on Windows 10 1709+ / Windows 11)
if (Get-Command winget -ErrorAction SilentlyContinue) {
    Write-Host "Installing Google Cloud CLI via winget..."
    winget install --id Google.CloudSDK --exact --accept-source-agreements --accept-package-agreements
    Write-Host "Installed via winget. Restart your shell, then run: gcloud init"
    exit 0
}

# Try Chocolatey second
if (Get-Command choco -ErrorAction SilentlyContinue) {
    Write-Host "Installing Google Cloud CLI via Chocolatey..."
    choco install gcloudsdk -y
    Write-Host "Installed via Chocolatey. Restart your shell, then run: gcloud init"
    exit 0
}

# Fall back to the official installer executable
Write-Host "winget and choco not found; falling back to the official installer..."

$Url = "https://dl.google.com/dl/cloudsdk/channels/rapid/GoogleCloudSDKInstaller.exe"
$TmpDir = Join-Path $env:TEMP "gcloud-install-$([System.IO.Path]::GetRandomFileName())"
New-Item -ItemType Directory -Path $TmpDir | Out-Null

try {
    $Installer = Join-Path $TmpDir "GoogleCloudSDKInstaller.exe"
    Write-Host "Downloading $Url..."
    Invoke-WebRequest -Uri $Url -OutFile $Installer

    Write-Host "Launching installer (interactive)..."
    Start-Process -FilePath $Installer -ArgumentList "/S", "/D=$InstallDir" -Wait

    Write-Host "gcloud installed to $InstallDir."
    Write-Host "Restart your shell, then run: gcloud init"
}
finally {
    Remove-Item -Recurse -Force $TmpDir -ErrorAction SilentlyContinue
}