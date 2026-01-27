# LogCollector Kubernetes & Terraform 실행 가이드

## 0. 전제 (Prerequisites)
- **Docker Desktop** (Kubernetes 활성화됨)
- **Terraform** 설치 완료
- **kubectl** 설치 완료
- **Helm** 설치 완료 (Terraform helm provider 사용)
- 프로젝트 루트 경로 기준

---

## 1. 인프라 및 백엔드 배포 (Terraform)
> DB(MariaDB), Redis, Monitoring(Prometheus/Grafana), 그리고 **Backend API(Spring Boot)** 를 Terraform으로 통합 배포합니다.

### 1-1) Terraform 초기화 및 적용
```bash
# 테라폼 디렉토리로 이동
cd infra/terraform

# 초기화 (최초 1회)
terraform init

# 계획 확인 및 적용 (yes 입력)
terraform apply
```

### 1-2) 배포 확인
모든 리소스가 생성될 때까지 잠시 대기합니다.
```bash
kubectl get pods
```
**예상 출력:**
- `lc-db-mariadb-0` (Running)
- `lc-redis-master-0` (Running)
- `log-app-deploy-xxx` (Running)
- `prometheus-xxx` / `grafana-xxx` (Running)

---

## 2. 프론트엔드 배포 (Kubectl)
> 프론트엔드(React)는 현재 Kubernetes YAML 파일을 사용하여 배포합니다.

```bash
# 프로젝트 루트 기준
kubectl apply -f infra/k8s/06-frontend-deployment.yaml
```

> **ℹ️ 참고: 프론트엔드의 역할**
> 본 프론트엔드는 **편리한 기능 검증(시나리오 모드 등)**을 위해 함께 배포되는 **테스트용 UI**입니다.
> - **Scenario Mode:** 장애 상황(DB 연결 실패, 대량 트래픽 등)을 시뮬레이션하여 로그를 발생시키는 기능
> - **Logs 탭:** 수집된 로그가 잘 들어왔는지 확인하기 위한 **검증용 화면**입니다.
    >   - *주의: 실제 LogCollector 백엔드(lc Domain)는 화면이 제공되지 않는 순수 수집 모듈(API)입니다.*

---

## 3. 서비스 접속 정보
배포가 완료되면 아래 주소로 접속할 수 있습니다.
(`LoadBalancer` 타입을 통해 `localhost`로 노출됩니다.)

| 서비스 | URL | 설명 |
|---|---|---|
| **Frontend** | [http://localhost](http://localhost) | 웹 대시보드 (Port 80) |
| **Backend API** | [http://localhost:8081](http://localhost:8081) | API 서버 / Swagger |
| **Grafana** | [http://localhost:3000](http://localhost:3000) | 모니터링 대시보드 (*) |

> **(*) Grafana 접속 및 사용 가이드:**
> Grafana는 기본적으로 ClusterIP로 생성되므로, 접속하려면 포트 포워딩이 필요합니다.
> ```bash
> # 모니터링 네임스페이스의 그라파나 서비스 포워딩
> kubectl port-forward svc/monitoring-grafana 3000:80 -n monitoring
> ```
> - **ID/PW:** `admin` / `admin`
> - **대시보드 확인 방법:**
    >   1. 좌측 메뉴 **Dashboards** 클릭
>   2. 목록에서 **LogCollector** 대시보드 선택
>   3. 상단 **Instance** 필터(드롭다운)에서 특정 파드를 선택하여 파드별 상세 메트릭 확인 가능

---

## 4. 주요 관리 명령어

### 4-1) 로그 확인
시간대(Timezone)는 `Asia/Seoul`로 설정되어 있습니다.

```bash
# 백엔드 로그 (실시간)
kubectl logs -f -l app=log-app

# DB 로그
kubectl logs -f lc-db-mariadb-0

# 프론트엔드 로그
kubectl logs -f -l app=log-web
```

### 4-2) 앱 재시작 (설정 변경 시)
설정 변경 후 파드를 삭제하면 Deployment가 새 설정으로 자동 재생성합니다.

```bash
# 백엔드 재시작
kubectl delete pod -l app=log-app

# 프론트엔드 재시작
kubectl delete pod -l app=log-web
```

---

## 5. 리소스 정리 (Destroy)
시스템을 종료하고 모든 리소스를 삭제합니다.

```bash
# 1. 프론트엔드 삭제
kubectl delete -f infra/k8s/06-frontend-deployment.yaml

# 2. 인프라 및 백엔드 삭제 (Terraform)
cd infra/terraform
terraform destroy
```