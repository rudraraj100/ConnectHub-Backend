# ConnectHub - SonarQube Analysis Runner
# Run from ConnectHub-Backend\ directory:
#   powershell -ExecutionPolicy Bypass -File ".\run-sonar-analysis.ps1" -SonarToken "sqa_..."

param(
    [string]$SonarToken = $env:SONAR_TOKEN,
    [string]$SonarUrl   = "http://localhost:9000"
)

if ([string]::IsNullOrEmpty($SonarToken)) {
    Write-Host ""
    Write-Host "ERROR: Sonar token not provided." -ForegroundColor Red
    Write-Host "  Pass it as a parameter:" -ForegroundColor Yellow
    Write-Host "  .\run-sonar-analysis.ps1 -SonarToken <your-token>" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

# Service definitions: projectKey => directory name
$services = [ordered]@{
    "connecthub-auth-service"         = "auth-service"
    "connecthub-room-service"         = "room-servcie"
    "connecthub-message-service"      = "message-service"
    "connecthub-media-service"        = "media-service"
    "connecthub-presence-service"     = "presence-service"
    "connecthub-notification-service" = "notification-service"
    "connecthub-payment-service"      = "payment-service"
}

$scriptDir      = $PSScriptRoot
$failedServices = @()
$passedServices = @()

Write-Host ""
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  ConnectHub - SonarQube Analysis Runner" -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  SonarQube URL : $SonarUrl"
Write-Host "  Services      : $($services.Count)"
Write-Host ""

$totalStart = Get-Date

foreach ($entry in $services.GetEnumerator()) {
    $projectKey = $entry.Key
    $serviceDir = Join-Path $scriptDir $entry.Value

    Write-Host "-------------------------------------------------" -ForegroundColor DarkGray
    Write-Host ">> Analysing: $projectKey" -ForegroundColor Yellow
    Write-Host "   Directory: $serviceDir"
    Write-Host ""

    if (-not (Test-Path $serviceDir)) {
        Write-Host "   SKIP - directory not found: $serviceDir" -ForegroundColor Magenta
        $failedServices += $projectKey
        continue
    }

    $start = Get-Date

    $mvnw = Join-Path $serviceDir "mvnw.cmd"
    if (-not (Test-Path $mvnw)) {
        Write-Host "   SKIP - mvnw.cmd not found in: $serviceDir" -ForegroundColor Magenta
        $failedServices += $projectKey
        continue
    }

    $mvnArgs = @(
        "clean", "verify", "sonar:sonar",
        "-Dsonar.projectKey=$projectKey",
        "-Dsonar.projectName=$projectKey",
        "-Dsonar.host.url=$SonarUrl",
        "-Dsonar.token=$SonarToken",
        "-Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml",
        "-Dsonar.exclusions=**/dto/**,**/entity/**,**/config/**,**/*Application.java,**/exception/**,**/constant/**",
        "-Dsonar.coverage.exclusions=**/dto/**,**/entity/**,**/config/**,**/*Application.java,**/exception/**",
        "--batch-mode"
    )

    Push-Location $serviceDir
    try {
        & $mvnw @mvnArgs
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    $elapsed = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)

    if ($exitCode -eq 0) {
        Write-Host ""
        Write-Host "   PASSED: $projectKey ($elapsed s)" -ForegroundColor Green
        $passedServices += $projectKey
    } else {
        Write-Host ""
        Write-Host "   FAILED: $projectKey (exit $exitCode, $elapsed s)" -ForegroundColor Red
        $failedServices += $projectKey
    }

    Write-Host ""
}

# Summary
$totalElapsed = [math]::Round(((Get-Date) - $totalStart).TotalSeconds, 1)

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  ANALYSIS COMPLETE" -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  Total time : $totalElapsed s"
Write-Host "  Passed ($($passedServices.Count)): $($passedServices -join ', ')" -ForegroundColor Green

if ($failedServices.Count -gt 0) {
    Write-Host "  Failed ($($failedServices.Count)): $($failedServices -join ', ')" -ForegroundColor Red
    Write-Host ""
    Write-Host "  View results: $SonarUrl/projects" -ForegroundColor Cyan
    exit 1
} else {
    Write-Host ""
    Write-Host "  All services passed!" -ForegroundColor Green
    Write-Host "  View results: $SonarUrl/projects" -ForegroundColor Cyan
    exit 0
}
