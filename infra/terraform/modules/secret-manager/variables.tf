variable "project_id" {
  type        = string
  description = "GCP project ID."
}

variable "secret_ids" {
  type        = list(string)
  description = "List of secret IDs (names) to create in Secret Manager. Secret values must be loaded separately (e.g. via CI/CD or gcloud)."
}