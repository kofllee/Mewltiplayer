param(
    [string]$Target = 'fabric-26.3',
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs = @(':platform:runClient')
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$targetDir = Join-Path $root "targets/$Target"
if (!(Test-Path $targetDir)) {
    throw "Unknown target '$Target'. See targets/ for available targets."
}

$toolchains = Join-Path $root '.toolchains'
$jdk = Get-ChildItem $toolchains -Directory -ErrorAction SilentlyContinue |
    Where-Object { (Test-Path (Join-Path $_.FullName 'bin/java.exe')) -and $_.Name -like 'jdk-25*' } |
    Select-Object -First 1
if (!$jdk) {
    throw "JDK 25 not found in .toolchains/. Download a JDK 25 ZIP and extract it there."
}

$env:JAVA_HOME = $jdk.FullName
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
Write-Host "Mewltiplayer: $Target | Java: $env:JAVA_HOME"
& (Join-Path $root 'gradlew.bat') "-Ptarget=$Target" @GradleArgs
exit $LASTEXITCODE
