# redeploy.ps1
Write-Host "=== 최신 이미지로 배포를 시작합니다 ===" -ForegroundColor Cyan

# 1. 백엔드 재시작
kubectl rollout restart deployment/log-app-deploy
Write-Host "=== Backend Restart Triggered ===" -ForegroundColor Green

# 2. 프론트엔드 재시작 (필요 없으면 주석 처리 #)
kubectl rollout restart deployment/log-web-deploy
Write-Host "=== Frontend Restart Triggered ===" -ForegroundColor Green

# 3. 상태 모니터링
Write-Host "=== 파드 교체 상황을 지켜봅니다 ===" -ForegroundColor Yellow
kubectl get pods -w