# LogCollector Kubernetes 검증 가이드

본 문서는 `scenarios.md`에 정의된 시나리오를 Kubernetes 환경에서 검증하는 자동화된 방법을 안내합니다.

## 1. 검증 원리 (Test Runner)
로컬 PC에서 직접 DB나 API에 붙는 대신, **Test Runner Job**을 클러스터 내부에 생성하여 검증합니다.
- **ConfigMap**: 로컬의 테스트 스크립트(`tests/scripts/*.sh`)를 클러스터로 전달
- **Job**: `alpine` 컨테이너에 `curl`, `mysql-client`를 설치 후 스크립트 순차 실행
- **Network**: Service Name(`log-app-service`, `lc-db-mariadb`)을 통해 내부 통신

## 2. 사전 준비
- `run-k8s.md`에 따라 전체 시스템이 구동 중이어야 합니다 (`kubectl get pods`).
- 테스트 스크립트가 `tests/scripts/` 경로에 위치해야 합니다.
  (예: `tests/scripts/1_verify_scenario.docker.sh` 등)

## 3. 테스트 실행 (자동화)

### 3-1) 스크립트 ConfigMap 생성/갱신
로컬의 스크립트 파일을 쿠버네티스 설정(ConfigMap)으로 업로드합니다.

```bash
# 기존 설정 삭제 (있다면)
kubectl delete configmap verify-scripts --ignore-not-found

# 스크립트 폴더를 ConfigMap으로 생성
kubectl create configmap verify-scripts --from-file=tests/scripts/
```

### 3-2) Test Runner Job 실행
테스트를 수행할 일회성 Job을 실행합니다.

``` Bash
# 기존 Job 삭제 (재실행 시)
kubectl delete job verify-job --ignore-not-found

# Job 배포
kubectl apply -f infra/k8s/test-job.yaml
```

### 3-3) 결과 확인
Job이 실행되면서 발생하는 로그를 실시간으로 확인합니다.

``` Bash
# 로그 확인 (-f: 실시간 팔로우)
kubectl logs -f job/verify-job
```

성공 시: >>> ALL TESTS PASSED! 메시지와 함께 종료

실패 시: 에러 로그와 함께 종료

## 4. 테스트 종료 (Cleanup)
   테스트가 끝난 후 리소스를 정리합니다.

```Bash
kubectl delete job verify-job
kubectl delete configmap verify-scripts
```