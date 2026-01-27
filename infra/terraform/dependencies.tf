############################
# 1) Secret 생성
############################
resource "kubernetes_secret_v1" "logcollector_secrets" {
  metadata {
    name      = "logcollector-secrets"
    namespace = "default"
  }

  data = {
    "db-root-password" = base64encode(var.db_password)
    "openai-api-key"   = base64encode(var.openai_api_key)
  }

  type = "Opaque"
}

############################
# 2) MariaDB
############################
resource "helm_release" "mariadb" {
  name       = "lc-db"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "mariadb"
  version    = "19.0.0"
  namespace  = "default"

  wait            = true
  timeout         = 900
  atomic          = true
  cleanup_on_fail = true

  values = [
    yamlencode({
      image = {
        registry   = "docker.io"
        repository = "bitnami/mariadb"
        tag        = "latest"
        pullPolicy = "IfNotPresent"
      }

      auth = {
        rootPassword = var.db_password
      }

      primary = {
        persistence = { enabled = false }

        # [추가] DB 시간대 설정 (Asia/Seoul)
        extraEnvVars = [
          {
            name  = "TZ"
            value = "Asia/Seoul"
          }
        ]
      }

      initdbScripts = {
        "create_dbs.sql" = <<-EOT
          CREATE DATABASE IF NOT EXISTS logcollector;
          CREATE DATABASE IF NOT EXISTS knowledge_base;
          GRANT ALL PRIVILEGES ON logcollector.* TO 'root'@'%';
          GRANT ALL PRIVILEGES ON knowledge_base.* TO 'root'@'%';
          FLUSH PRIVILEGES;
        EOT
      }
    })
  ]
}

############################
# 3) Redis
############################
resource "helm_release" "redis" {
  name       = "lc-redis"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "redis"
  version    = "19.0.0"
  namespace  = "default"

  wait            = true
  timeout         = 900
  atomic          = true
  cleanup_on_fail = true

  values = [
    yamlencode({
      image = {
        registry   = "docker.io"
        repository = "bitnami/redis"
        tag        = "latest"
        pullPolicy = "IfNotPresent"
      }

      auth = {
        enabled = false
      }

      architecture = "standalone"

      master = {
        persistence = { enabled = false }

        # [추가] Redis 시간대 설정 (Asia/Seoul)
        extraEnvVars = [
          {
            name  = "TZ"
            value = "Asia/Seoul"
          }
        ]
      }
    })
  ]
}

############################
# 4) 모니터링 (Prometheus + Grafana)
############################
resource "helm_release" "monitoring" {
  name             = "monitoring"
  repository       = "https://prometheus-community.github.io/helm-charts"
  chart            = "kube-prometheus-stack"
  namespace        = "monitoring"
  create_namespace = true

  wait            = true
  timeout         = 900
  atomic          = true
  cleanup_on_fail = true

  values = [
    yamlencode({
      grafana = {
        adminPassword = "admin"

        sidecar = {
          dashboards = {
            enabled = true
            label   = "grafana_dashboard"
            searchNamespace = "ALL"       # 모든 네임스페이스에서 검색
          }
        }
      }

      prometheus = {
        prometheusSpec = {
          # 타겟 지정
          additionalScrapeConfigs = [
            {
              job_name        = "logcollector-app"
              metrics_path    = "/actuator/prometheus"
              scrape_interval = "5s"
              static_configs = [
                {
                  # K8s DNS (서비스명.네임스페이스.svc:포트)
                  targets = ["log-app-service.default.svc.cluster.local:8081"]
                }
              ]
            }
          ]
        }
      }
    })
  ]
}