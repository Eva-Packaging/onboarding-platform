variable "project_id" {
  type        = string
  description = "GCP project ID."
}

variable "region" {
  type        = string
  description = "GCP region for the Artifact Registry repository."
}

variable "repository_id" {
  type        = string
  description = "Repository ID (name) — must match GCP_REPOSITORY used by the build/push pipeline and docker-compose.yml."
}