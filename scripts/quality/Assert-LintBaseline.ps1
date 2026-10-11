param(
    [string]$ReportPath,
    [string]$BaselinePath
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (-not $ReportPath) { $ReportPath = Join-Path $repositoryRoot 'app/build/reports/lint-results-debug.xml' }
if (-not $BaselinePath) { $BaselinePath = Join-Path $repositoryRoot 'docs/portfolio/week5-lint-baseline.json' }
[xml]$report = Get-Content -LiteralPath $ReportPath -Raw
$baseline = Get-Content -LiteralPath $BaselinePath -Raw | ConvertFrom-Json
$warnings = @($report.issues.issue | Where-Object severity -eq 'Warning')
$errors = @($report.issues.issue | Where-Object { $_.severity -in @('Error', 'Fatal') })

function Get-WarningKey($issue, [bool]$IsXml) {
    $locations = if ($IsXml) { @($issue.location) } else { @($issue.locations) }
    $files = @($locations | ForEach-Object {
        $file = ([string]$_.file).Replace('\', '/')
        $prefix = $repositoryRoot.Replace('\', '/') + '/'
        if ($file.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            $file = $file.Substring($prefix.Length)
        }
        $file
    } | Sort-Object)
    return (@([string]$issue.id, [string]$issue.message) + $files | ConvertTo-Json -Compress)
}

# Compare multiplicity too: removing an old warning cannot hide a new one.
# Line/column are kept in the inventory but excluded from identity to allow line shifts.
$allowed = @{}
foreach ($issue in $baseline.warnings) {
    $key = Get-WarningKey $issue $false
    if (-not $allowed.ContainsKey($key)) { $allowed[$key] = 0 }
    $allowed[$key]++
}
$newWarnings = @()
foreach ($issue in $warnings) {
    $key = Get-WarningKey $issue $true
    if ($allowed.ContainsKey($key) -and $allowed[$key] -gt 0) { $allowed[$key]-- }
    else { $newWarnings += $issue }
}
if ($errors.Count -gt 0 -or $warnings.Count -gt $baseline.warningCount -or $newWarnings.Count -gt 0) {
    $newWarnings | ForEach-Object { Write-Output "NEW LINT WARNING: $($_.id): $($_.message)" }
    throw "Lint gate failed: errors=$($errors.Count), warnings=$($warnings.Count), baseline=$($baseline.warningCount), new=$($newWarnings.Count)"
}
Write-Output "Lint gate passed: errors=0, warnings=$($warnings.Count), baseline=$($baseline.warningCount), new=0"
