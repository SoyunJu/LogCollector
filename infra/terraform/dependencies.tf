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
    "openai-api-key" = base64encode(var.openai_api_key)
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
        registry    = "docker.io"
        repository  = "bitnami/mariadb"
        tag         = "latest"
        pullPolicy  = "IfNotPresent"
      }

      auth = {
        rootPassword = var.db_password
      }

      primary = {
        persistence = { enabled = false }
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
        registry    = "docker.io"
        repository  = "bitnami/redis"
        tag         = "latest"
        pullPolicy  = "IfNotPresent"
      }

      auth = {
        enabled = false
      }

      architecture = "standalone"

      master = {
        persistence = { enabled = false }
      }
    })
  ]
}

############################
# 4) 모니터링
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
}
