[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$OutputRoot = (Join-Path $env:USERPROFILE "PhoneForensics\Cases"),

    [Parameter(Mandatory = $false)]
    [string]$AdbPath = "adb"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-TextFile {
    param([string]$Path, [string]$Text)
    $Text | Out-File -LiteralPath $Path -Encoding utf8
}

# This collector intentionally uses non-destructive ADB queries only.
# It does not install, delete, modify, unlock, root, or extract private app data.

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$caseDir = Join-Path $OutputRoot "case-$timestamp"
$deviceDir = Join-Path $caseDir "device"
New-Item -ItemType Directory -Path $deviceDir -Force | Out-Null

$logPath = Join-Path $caseDir "collection-log.txt"
$log = [System.Collections.Generic.List[string]]::new()
$log.Add("Collection started (local time): $(Get-Date -Format o)")
$log.Add("Computer: $env:COMPUTERNAME")
$log.Add("Collector: Collect-AndroidBaseline.ps1")
$log.Add("Mode: read-only ADB queries; device changes are still possible from connection/authorization.")
$log.Add("")

try {
    $adbVersion = & $AdbPath version 2>&1 | Out-String
    Write-TextFile -Path (Join-Path $deviceDir "adb-version.txt") -Text $adbVersion
    $log.Add("ADB version command completed.")
} catch {
    $log.Add("ERROR: ADB could not run. Confirm Android Platform-Tools is installed and AdbPath is correct.")
    $log.Add($_.Exception.Message)
    Write-TextFile -Path $logPath -Text ($log -join [Environment]::NewLine)
    throw
}

$devicesOutput = & $AdbPath devices -l 2>&1 | Out-String
Write-TextFile -Path (Join-Path $deviceDir "devices.txt") -Text $devicesOutput
$log.Add("ADB device list captured.")
if ($devicesOutput -notmatch "(?m)^\S+\s+device(?:\s|$)") {
    $log.Add("STOP: No authorized device state of 'device' was detected. No device properties were collected.")
    $log.Add("Connect your own device, review the phone screen, and approve the intended computer if appropriate.")
    Write-TextFile -Path $logPath -Text ($log -join [Environment]::NewLine)
    Write-Output "No authorized ADB device detected. See: $caseDir"
    exit 2
}

$getprop = & $AdbPath shell getprop 2>&1 | Out-String
Write-TextFile -Path (Join-Path $deviceDir "getprop.txt") -Text $getprop
$log.Add("Full getprop output captured. Review before sharing; it may contain device-identifying details.")

$properties = [ordered]@{
    "manufacturer" = "ro.product.manufacturer"
    "model" = "ro.product.model"
    "device" = "ro.product.device"
    "android_release" = "ro.build.version.release"
    "sdk_level" = "ro.build.version.sdk"
    "security_patch" = "ro.build.version.security_patch"
    "build_fingerprint" = "ro.build.fingerprint"
    "build_type" = "ro.build.type"
}
$selected = [System.Collections.Generic.List[string]]::new()
$selected.Add("Collected (local time): $(Get-Date -Format o)")
foreach ($entry in $properties.GetEnumerator()) {
    $value = & $AdbPath shell getprop $entry.Value 2>&1 | Out-String
    $selected.Add("$($entry.Key) [$($entry.Value)]: $($value.Trim())")
}
Write-TextFile -Path (Join-Path $deviceDir "selected-properties.txt") -Text ($selected -join [Environment]::NewLine)
$log.Add("Selected device properties captured.")
$log.Add("Collection completed (local time): $(Get-Date -Format o)")
Write-TextFile -Path $logPath -Text ($log -join [Environment]::NewLine)

# Create a SHA-256 manifest for collected files. The manifest excludes itself.
$manifestPath = Join-Path $caseDir "SHA256SUMS.csv"
$manifest = Get-ChildItem -LiteralPath $caseDir -File -Recurse |
    Where-Object { $_.FullName -ne $manifestPath } |
    Sort-Object FullName |
    ForEach-Object {
        $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
        [PSCustomObject]@{
            RelativePath = $_.FullName.Substring($caseDir.Length + 1)
            Algorithm = "SHA256"
            Hash = $hash.Hash
            Bytes = $_.Length
        }
    }
$manifest | Export-Csv -LiteralPath $manifestPath -NoTypeInformation -Encoding utf8

Write-Output "Baseline collection complete: $caseDir"
Write-Output "Review the raw outputs and SHA256SUMS.csv. Protect device identifiers before sharing."
