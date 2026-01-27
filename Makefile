.PHONY: setup test clean

# 변수 설정
SCRIPTS_DIR := docs/scripts
K8S_DIR := infra/k8s

# 1. 인프라 배포 (Terraform)
setup:
	@echo ">>> [Setup] Deploying Infrastructure with Terraform..."
	cd infra/terraform && terraform init && terraform apply -auto-approve
	@echo ">>> [Setup] Deploying Frontend..."
	kubectl apply -f $(K8S_DIR)/06-frontend-deployment.yaml
	@echo ">>> ✅ Setup Complete! Please wait for pods to be ready."

# 2. 통합 테스트 실행 (핵심)
test:
	@echo ">>> [Test] Preparing Verification Scripts..."
	-kubectl delete configmap verify-scripts --ignore-not-found
	-kubectl delete job verify-job --ignore-not-found
	kubectl create configmap verify-scripts --from-file=$(SCRIPTS_DIR)/

	@echo ">>> [Test] Starting Verification Job..."
	kubectl apply -f $(K8S_DIR)/test-job.yaml

	@echo ">>> [Test] Following Logs (Press Ctrl+C to exit)..."
	@sleep 5
	kubectl logs -f job/verify-job

# 3. 리소스 정리
clean:
	@echo ">>> [Clean] Destroying Infrastructure..."
	kubectl delete -f $(K8S_DIR)/06-frontend-deployment.yaml
	cd infra/terraform && terraform destroy -auto-approve
	@echo ">>> ✅ Cleanup Complete!"