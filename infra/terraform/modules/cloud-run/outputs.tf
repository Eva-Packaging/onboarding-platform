output "service_url" {
  description = "The URL at which the Cloud Run service is available."
  value       = google_cloud_run_v2_service.service.uri
}

output "service_name" {
  description = "The deployed Cloud Run service name."
  value       = google_cloud_run_v2_service.service.name
}

output "latest_ready_revision" {
  description = "The name of the most recent ready revision."
  value       = google_cloud_run_v2_service.service.latest_ready_revision
}