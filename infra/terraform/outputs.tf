output "web_url" {
  description = "Public URL of the web application Cloud Run service."
  value       = module.cloud_run_web.service_url
}

output "api_gateway_url" {
  description = "Internal URL of the api-gateway Cloud Run service."
  value       = module.cloud_run_api_gateway.service_url
}

output "user_service_url" {
  description = "Internal URL of the user-service Cloud Run service."
  value       = module.cloud_run_user_service.service_url
}

output "onboarding_service_url" {
  description = "Internal URL of the onboarding-service Cloud Run service."
  value       = module.cloud_run_onboarding_service.service_url
}

output "provisioning_service_url" {
  description = "Internal URL of the provisioning-service Cloud Run service."
  value       = module.cloud_run_provisioning_service.service_url
}

output "cloud_sql_connection_name" {
  description = "Cloud SQL instance connection name for use with Cloud SQL Auth Proxy."
  value       = module.cloud_sql.connection_name
}

output "service_account_emails" {
  description = "Map of service name to Cloud Run service account email."
  value       = module.iam.service_account_emails
}

output "secret_ids" {
  description = "Map of secret name to full Secret Manager resource ID."
  value       = module.secrets.secret_ids
}