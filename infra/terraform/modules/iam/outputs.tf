output "service_account_emails" {
  description = "Map of service name to service account email."
  value       = { for k, v in google_service_account.services : k => v.email }
}

output "service_account_ids" {
  description = "Map of service name to service account unique ID."
  value       = { for k, v in google_service_account.services : k => v.unique_id }
}