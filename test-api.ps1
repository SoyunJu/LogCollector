# LogCollector API Test Script (PowerShell)
# local Port 8082 / Docker Port 8081

$BaseUrl = "http://localhost:8082/api"

Write-Host "1. Sending Error Log..." -ForegroundColor Cyan
$LogBody = @{
    serviceName = "order-service"
    logLevel    = "ERROR"
    message     = "NullPointerException at OrderController.createOrder"
    hostName    = "aws-ec2-kr-1"
} | ConvertTo-Json

# 로그 전송 (202 Accepted)
Invoke-RestMethod -Method Post -Uri "$BaseUrl/logs" -Body $LogBody -ContentType "application/json"
Write-Host "Log sent. Waiting 2 seconds for async processing..." -ForegroundColor Yellow
Start-Sleep -Seconds 2

Write-Host "`n2. Fetching Incidents..." -ForegroundColor Cyan
$Response = Invoke-RestMethod -Method Get -Uri "$BaseUrl/incidents"
$Incidents = $Response.content
if ($Incidents.Count -gt 0) {
    $LatestIncident = $Incidents[0]
    $LogHash = $LatestIncident.logHash
    Write-Host "Found Incident! Hash: $LogHash" -ForegroundColor Green
    Write-Host "Message: $($LatestIncident.representativeMessage)"

    Write-Host "`n3. Analyzing Incident ($LogHash)..." -ForegroundColor Cyan
    try {
        $Analysis = Invoke-RestMethod -Method Post -Uri "$BaseUrl/logs/analyze/$LogHash"
        Write-Host "Analysis Result: $($Analysis.analysisResult)"
    } catch {
        Write-Host "Analysis failed or mock not enabled." -ForegroundColor Red
    }
} else {
    Write-Host "No incidents found. Check Redis/DB connection." -ForegroundColor Red
}