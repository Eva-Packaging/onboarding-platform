output "secret_ids" {
  description = "Map of secret name to full Secret Manager resource ID (projects/PROJECT/secrets/NAME)."
  value       = { for k, v in google_secret_manager_secret.secrets : k => v.id }
}

output "secret_names" {
  description = "Map of secret name to short secret name as defined in Secret Manager."
  value       = { for k, v in google_secret_manager_secret.secrets : k => v.secret_id }
}