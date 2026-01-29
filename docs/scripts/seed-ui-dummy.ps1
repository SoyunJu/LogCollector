param(
    [string]$BaseUrl = "http://localhost:8081/api",
    [int]$Count = 80,
    [int]$DelayMs = 150,
    [string]$ServicePrefix = "CAPTURE",
    [string]$HostPrefix = "pod"
)



$ErrorActionPreference = "Stop"

function New-RandFrom([string[]]$arr) {
    return $arr[(Get-Random -Minimum 0 -Maximum $arr.Length)]
}

$levels = @("WARN","ERROR","CRITICAL","FATAL")

$services = @(
    "$ServicePrefix-ORDER",
    "$ServicePrefix-PAYMENT",
    "$ServicePrefix-AUTH",
    "$ServicePrefix-NOTI",
    "$ServicePrefix-SEARCH",
    "$ServicePrefix-API"
)

$messages = @(
    "RedisCommandTimeoutException: Command timed out after 500ms",
    "Connection refused: DB pool exhausted",
    "NullPointerException at IncidentService.saveLog",
    "HTTP 502 Bad Gateway from upstream",
    "IllegalArgumentException: invalid status transition",
    "OutOfMemoryError: Java heap space",
    "Lock wait timeout exceeded; try restarting transaction",
    "Read timed out while calling external API"
)

# single-quoted here-string (PowerShell 변수 파싱 방지)
$stackTemplates = @(
    @'
org.springframework.data.redis.RedisSystemException: Redis command timed out
  at com.soyunju.logcollector.service.redis.RedisQueueService.enqueue(RedisQueueService.java:72)
  at com.soyunju.logcollector.api.LogController.create(LogController.java:41)
Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 500ms
'@,
    @'
java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available
  at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:196)
  at com.soyunju.logcollector.service.IncidentService.save(IncidentService.java:118)
'@,
    @'
java.lang.NullPointerException: Cannot invoke "String.length()" because "x" is null
  at com.soyunju.logcollector.service.KbArticleService.publish(KbArticleService.java:204)
'@,
    @'
org.springframework.web.client.HttpServerErrorException$BadGateway: 502 Bad Gateway
  at com.soyunju.logcollector.client.ExternalClient.call(ExternalClient.java:55)
'@
)

Write-Host "[seed] POST $Count logs to $BaseUrl/logs (delay=${DelayMs}ms)"

for ($i=1; $i -le $Count; $i++) {
    $svc = New-RandFrom $services
    $podName = "$HostPrefix-$(Get-Random -Minimum 1 -Maximum 6)"   # ← 변경됨
    $lvl = New-RandFrom $levels
    $msg = New-RandFrom $messages
    $stk = New-RandFrom $stackTemplates

    # 일부 로그는 stackTrace 없음
    if ((Get-Random -Minimum 0 -Maximum 100) -lt 25) {
        $stk = ""
    }

    $rid = [Guid]::NewGuid().ToString("N").Substring(0,8)

    $body = @{
        serviceName = $svc
        hostName    = $podName   # ← 변경됨
        logLevel    = $lvl
        message     = "[CAPTURE-$rid] $msg"
        stackTrace  = $stk
    } | ConvertTo-Json -Depth 10

    try {
        Invoke-WebRequest `
      -Method Post `
      -Uri "$BaseUrl/logs" `
      -ContentType "application/json" `
      -Body $body `
      | Out-Null
    } catch {
        Write-Host "[error] POST failed: $($_.Exception.Message)"
    }

    Start-Sleep -Milliseconds $DelayMs
}

# 큐 즉시 처리 (있으면)
try {
    Write-Host "[seed] drain queue"
    Invoke-WebRequest -Method Post -Uri "$BaseUrl/test/queue/drain" | Out-Null
} catch {
    Write-Host "[seed] drain skipped"
}

Write-Host "[seed] done"
