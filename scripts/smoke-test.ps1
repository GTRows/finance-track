# ============================================================
# FinTrack Pro -- Automated Smoke Test (Windows / PowerShell)
#
# End-to-end API check: register, login, settings round-trip,
# portfolio, holding, dashboard, logout.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
#   powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1 -BaseUrl http://localhost:8080
# ============================================================

param(
    [string]$BaseUrl = "http://localhost"
)

$ErrorActionPreference = "Stop"
$Api = ($BaseUrl.TrimEnd("/")) + "/api/v1"
$Ts  = [int][double]::Parse((Get-Date -UFormat %s))
$User = "smoke_$Ts"
$Email = "smoke_$Ts@fintrack.test"
$Password = "SmokeTest!$Ts"

function Info($msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }
function Pass($msg) { Write-Host "  [ OK ] $msg" -ForegroundColor Green }
function Fail($msg) { Write-Host "  [FAIL] $msg" -ForegroundColor Red; exit 1 }

function Call {
    param($Method, $Path, $Body, $Token)
    $headers = @{ "Accept-Language" = "en" }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $args = @{
        Method = $Method
        Uri = "$Api$Path"
        Headers = $headers
        ContentType = "application/json"
    }
    if ($Body) { $args["Body"] = $Body }
    try {
        return Invoke-RestMethod @args
    } catch {
        Write-Host $_.Exception.Message -ForegroundColor Red
        if ($_.ErrorDetails) { Write-Host $_.ErrorDetails.Message -ForegroundColor Red }
        Fail "$Method $Path"
    }
}

Info "Target: $BaseUrl"

Info "Waiting for backend /health (up to 90s)"
$ready = $false
for ($i = 1; $i -le 45; $i++) {
    try {
        Invoke-RestMethod -Uri "$Api/health" -Method Get -TimeoutSec 2 | Out-Null
        Pass "backend healthy after $i checks"
        $ready = $true
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $ready) { Fail "backend did not become healthy in time" }

Info "Register a fresh user ($User)"
$reg = Call POST "/auth/register" (@{username=$User;email=$Email;password=$Password} | ConvertTo-Json -Compress)
Pass "POST /auth/register"
$access = $reg.accessToken
$refresh = $reg.refreshToken
if (-not $access) { Fail "no accessToken in register response" }

Info "GET /auth/me"
Call GET "/auth/me" $null $access | Out-Null
Pass "GET /auth/me"

Info "GET /settings"
Call GET "/settings" $null $access | Out-Null
Pass "GET /settings"

Info "PUT /settings (currency -> USD)"
$upd = Call PUT "/settings" (@{currency="USD";language="en";theme="dark";timezone="Europe/Istanbul"} | ConvertTo-Json -Compress) $access
if ($upd.currency -ne "USD") { Fail "currency did not update (got '$($upd.currency)')" }
Pass "PUT /settings"

Info "POST /portfolios"
$pf = Call POST "/portfolios" (@{name="Smoke Portfolio";type="INDIVIDUAL"} | ConvertTo-Json -Compress) $access
$portfolioId = $pf.id
if (-not $portfolioId) { Fail "portfolio id missing" }
Pass "POST /portfolios"

Info "GET /assets?type=CRYPTO"
$assets = Call GET "/assets?type=CRYPTO" $null $access
$assetId = $assets[0].id
if (-not $assetId) { Fail "no crypto assets seeded" }
Pass "GET /assets"

Info "POST /portfolios/{id}/holdings"
Call POST "/portfolios/$portfolioId/holdings" (@{assetId=$assetId;quantity=0.05;averageCost=1000000} | ConvertTo-Json -Compress) $access | Out-Null
Pass "POST holding"

Info "GET /dashboard"
Call GET "/dashboard" $null $access | Out-Null
Pass "GET /dashboard"

Info "POST /auth/logout"
Call POST "/auth/logout" (@{refreshToken=$refresh} | ConvertTo-Json -Compress) $access | Out-Null
Pass "POST /auth/logout"

Write-Host ""
Write-Host "All smoke-test checks passed." -ForegroundColor Green
