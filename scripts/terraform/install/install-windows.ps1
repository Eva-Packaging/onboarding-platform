#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$TerraformVersion = "1.9.8",
    [string]$InstallDir = "$env:ProgramFiles\Terraform"
)

$ErrorActionPreference = "Stop"

# Try winget first (available on Windows 10 1709+ / Windows 11)
if (Get-Command winget -ErrorAction SilentlyContinue) {
    Write-Host "Installing Terraform via winget..."
    winget install --id HashiCorp.Terraform --exact --accept-source-agreements --accept-package-agreements
    Write-Host "Terraform installed via winget."
    exit 0
}

# Try Chocolatey second
if (Get-Command choco -ErrorAction SilentlyContinue) {
    Write-Host "Installing Terraform via Chocolatey..."
    choco install terraform --version $TerraformVersion -y
    Write-Host "Terraform installed via Chocolatey."
    exit 0
}

# Fall back to direct download
Write-Host "winget and choco not found; falling back to direct download..."

$Arch = if ([Environment]::Is64BitOperatingSystem) { "amd64" } else { "386" }
$Zip  = "terraform_${TerraformVersion}_windows_${Arch}.zip"
$Url  = "https://releases.hashicorp.com/terraform/${TerraformVersion}/${Zip}"
$SumsUrl = "https://releases.hashicorp.com/terraform/${TerraformVersion}/terraform_${TerraformVersion}_SHA256SUMS"

$TmpDir = Join-Path $env:TEMP "terraform-install-$([System.IO.Path]::GetRandomFileName())"
New-Item -ItemType Directory -Path $TmpDir | Out-Null

try {
    Write-Host "Downloading $Url..."
    Invoke-WebRequest -Uri $Url     -OutFile (Join-Path $TmpDir $Zip)
    Invoke-WebRequest -Uri $SumsUrl -OutFile (Join-Path $TmpDir "SHA256SUMS")

    # Verify checksum
    $Expected = (Get-Content (Join-Path $TmpDir "SHA256SUMS") | Where-Object { $_ -match $Zip }) -split "\s+",2 | Select-Object -First 1
    $Actual   = (Get-FileHash (Join-Path $TmpDir $Zip) -Algorithm SHA256).Hash.ToLower()
    if ($Expected -ne $Actual) {
        throw "Checksum mismatch: expected $Expected, got $Actual"
    }

    Expand-Archive -Path (Join-Path $TmpDir $Zip) -DestinationPath $TmpDir -Force

    if (-not (Test-Path $InstallDir)) {
        New-Item -ItemType Directory -Path $InstallDir | Out-Null
    }
    Copy-Item (Join-Path $TmpDir "terraform.exe") (Join-Path $InstallDir "terraform.exe") -Force

    # Add to PATH for current session if not already present
    if ($env:PATH -notlike "*$InstallDir*") {
        $env:PATH = "$InstallDir;$env:PATH"
        [Environment]::SetEnvironmentVariable("PATH", "$InstallDir;$([Environment]::GetEnvironmentVariable('PATH','Machine'))", "Machine")
        Write-Host "Added $InstallDir to system PATH."
    }

    Write-Host "Terraform $TerraformVersion installed to $InstallDir\terraform.exe"
}
finally {
    Remove-Item -Recurse -Force $TmpDir -ErrorAction SilentlyContinue
}