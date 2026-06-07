output "repository_id" {
  description = "Repository ID (name) as created in Artifact Registry."
  value       = google_artifact_registry_repository.repo.repository_id
}

output "repository_url" {
  description = "Full Docker registry path: <region>-docker.pkg.dev/<project>/<repository> — matches the path docker-compose.yml and the build/push scripts already compose."
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.repo.repository_id}"
}