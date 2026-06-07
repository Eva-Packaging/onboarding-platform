resource "google_artifact_registry_repository" "repo" {
  project       = var.project_id
  location      = var.region
  repository_id = var.repository_id
  format        = "DOCKER"
  description   = "Docker images for the onboarding platform services (managed by Terraform)."

  labels = {
    managed-by = "terraform"
  }
}