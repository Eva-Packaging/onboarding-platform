variable "project_id" {
  type        = string
  description = "GCP project ID."
}

variable "service_names" {
  type        = list(string)
  description = "Names of all Cloud Run services. A dedicated service account is created for each."
}

variable "sql_service_names" {
  type        = list(string)
  description = "Subset of service_names that require Cloud SQL client access. Must be a subset of service_names."
  default     = []
}

variable "artifact_registry_location" {
  type        = string
  description = "Location (region) of the Artifact Registry repository to grant push/pull access on."
}

variable "artifact_registry_repository_id" {
  type        = string
  description = "Repository ID of the Artifact Registry repository to grant push/pull access on."
}