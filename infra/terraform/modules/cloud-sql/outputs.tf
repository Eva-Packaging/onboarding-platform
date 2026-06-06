output "connection_name" {
  description = "Cloud SQL instance connection name (PROJECT:REGION:INSTANCE) for use with Cloud SQL Auth Proxy."
  value       = google_sql_database_instance.postgres.connection_name
}

output "instance_name" {
  description = "Name of the Cloud SQL instance."
  value       = google_sql_database_instance.postgres.name
}

output "private_ip" {
  description = "Private IP address of the Cloud SQL instance."
  value       = google_sql_database_instance.postgres.private_ip_address
}

output "database_names" {
  description = "List of database names created on the instance."
  value       = [for db in google_sql_database.databases : db.name]
}