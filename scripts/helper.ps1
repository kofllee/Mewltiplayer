param(
    [ValidateSet('test', 'build')]
    [string]$Action = 'test'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$go = Join-Path $root '.toolchains/go/bin/go.exe'
if (!(Test-Path $go)) {
    throw "Go not found: $go"
}

Push-Location (Join-Path $root 'helper/tailcat')
try {
    if ($Action -eq 'test') {
        & $go test ./...
    } else {
        $outputDir = Join-Path $root 'dist/helper/windows-x64'
        New-Item -ItemType Directory -Force $outputDir | Out-Null
        & $go build -trimpath -o (Join-Path $outputDir 'mewltiplayer-helper.exe') .
    }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
