resource "kubernetes_deployment_v1" "log_app" {
  metadata {
    name = "log-app-deploy"
    labels = {
      app = "log-app"
    }
  }

  spec {
    replicas = 2

    selector {
      match_labels = {
        app = "log-app"
      }
    }

    template {
      metadata {
        labels = {
          app = "log-app"
        }
      }

      spec {
        container {
          name              = "log-app"
          image             = "logcollector-log-app:latest"
          image_pull_policy = "Never"

          port {
            container_port = 8081
          }

          # KOREA TIME
          env {
            name  = "TZ"
            value = "Asia/Seoul"
          }

          env {
            name  = "SPRING_PROFILES_ACTIVE"
            value = "docker"
          }

          # LC DB
          env {
            name  = "SPRING_LC_DATASOURCE_URL"
            value = "jdbc:mariadb://lc-db-mariadb:3306/logcollector?createDatabaseIfNotExist=true"
          }
          env {
            name  = "SPRING_LC_DATASOURCE_USERNAME"
            value = "root"
          }
          env {
            name = "SPRING_LC_DATASOURCE_PASSWORD"
            value_from {
              secret_key_ref {
                name = "lc-db-mariadb"
                key  = "mariadb-root-password"
              }
            }
          }

          # KB DB
          env {
            name  = "SPRING_KB_DATASOURCE_URL"
            value = "jdbc:mariadb://lc-db-mariadb:3306/knowledge_base?createDatabaseIfNotExist=true"
          }
          env {
            name  = "SPRING_KB_DATASOURCE_USERNAME"
            value = "root"
          }
          env {
            name = "SPRING_KB_DATASOURCE_PASSWORD"
            value_from {
              secret_key_ref {
                name = "lc-db-mariadb"
                key  = "mariadb-root-password"
              }
            }
          }

          # Redis
          env {
            name  = "SPRING_REDIS_HOST"
            value = "lc-redis-master"
          }

          # OpenAI API Key
          env {
            name = "OPENAI_API_KEY"
            value_from {
              secret_key_ref {
                name = "logcollector-secrets"
                key  = "openai-api-key"
              }
            }
          }
        }
      }
    }
  }
}

# 서비스
resource "kubernetes_service_v1" "log_app_service" {
  metadata {
    name = "log-app-service"
  }

  spec {
    type = "LoadBalancer" # localhost 접속 허용

    selector = {
      app = "log-app"
    }

    port {
      protocol    = "TCP"
      port        = 8081  # 브라우저 접속 포트
      target_port = 8081  # 컨테이너 내부 포트
    }
  }
}