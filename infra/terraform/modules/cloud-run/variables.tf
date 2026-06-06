variable "project_id" {
  type        = string
  description = "GCP project ID."
}

variable "region" {
  type        = string
  description = "GCP region to deploy the Cloud Run service."
}

variable "service_name" {
  type        = string
  description = "Name of the Cloud Run service."
}

variable "image" {
  type        = string
  description = "Container image URL (e.g. gcr.io/PROJECT/IMAGE:TAG)."
}

variable "service_account_email" {
  type        = string
  description = "Email of the service account the Cloud Run service runs as."
}

variable "min_instances" {
  type        = number
  description = "Minimum number of container instances."
  default     = 0
}

variable "max_instances" {
  type        = number
  description = "Maximum number of container instances."
  default     = 3
}

variable "allow_public_access" {
  type        = bool
  description = "When true, grant allUsers the run.invoker role (public internet access)."
  default     = false
}

variable "env_vars" {
  type        = map(string)
  description = "Plain-text environment variables injected into the container."
  default     = {}
}

variable "secret_env_vars" {
  type = map(object({
    secret  = string
    version = string
  }))
  description = "Environment variables sourced from Secret Manager. Key = env var name; value = {secret: resource_id, version: version}."
  default = {}
}

variable "cpu" {
  type        = string
  description = "CPU allocation (e.g. '1', '2')."
  default     = "1"
}

variable "memory" {
  type        = string
  description = "Memory allocation (e.g. '512Mi', '1Gi')."
  default     = "512Mi"
}