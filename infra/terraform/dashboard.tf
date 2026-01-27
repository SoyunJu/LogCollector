resource "kubernetes_config_map" "logcollector_dashboard" {
  metadata {
    name      = "logcollector-dashboard"
    namespace = "monitoring"
    labels = {
      grafana_dashboard = "1"
    }
  }

  data = {
    # 로컬 파일을 읽어서 ConfigMap으로 생성
    "logcollector.json" = file("${path.module}/../../docs/monitoring/dashboard/logcollector-dashboard.json")
  }
}