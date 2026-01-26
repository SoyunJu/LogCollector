#  DB
variable "db_password" {
  description = "MariaDB Root Password"
  type        = string
  sensitive   = true # 로그에 안 찍히게 가림
}

# OpenAI API Key
variable "openai_api_key" {
  description = "OpenAI API Key"
  type        = string
  sensitive   = true
}

# 3. Docker Hub
variable "docker_username" {
  description = "Docker Hub Username"
  type        = string
}