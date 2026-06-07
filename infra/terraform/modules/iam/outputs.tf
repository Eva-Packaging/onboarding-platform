output "image_builder_service_account_email" {
  description = "Email of the CI/CD service account granted roles/artifactregistry.writer (push access) on the Artifact Registry repository."
  value       = google_service_account.image_builder.email
}

output "service_account_emails" {
  description = "Map of service name to service account email."
  value       = { for k, v in google_service_account.services : k => v.email }
}

output "service_account_ids" {
  description = "Map of service name to service account unique ID."
  value       = { for k, v in google_service_account.services : k => v.unique_id }
}