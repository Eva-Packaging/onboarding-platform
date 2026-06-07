resource "google_service_account" "image_builder" {
  account_id   = "image-builder"
  display_name = "CI/CD image build & push service account"
  project      = var.project_id
}

resource "google_artifact_registry_repository_iam_member" "image_builder_writer" {
  project    = var.project_id
  location   = var.artifact_registry_location
  repository = var.artifact_registry_repository_id
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${google_service_account.image_builder.email}"
}

resource "google_artifact_registry_repository_iam_member" "service_image_readers" {
  for_each   = toset(var.service_names)
  project    = var.project_id
  location   = var.artifact_registry_location
  repository = var.artifact_registry_repository_id
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${google_service_account.services[each.value].email}"
}

resource "google_service_account" "services" {
  for_each     = toset(var.service_names)
  account_id   = each.value
  display_name = "${each.value} Cloud Run service account"
  project      = var.project_id
}

resource "google_project_iam_member" "secret_accessor" {
  for_each = toset(var.service_names)
  project  = var.project_id
  role     = "roles/secretmanager.secretAccessor"
  member   = "serviceAccount:${google_service_account.services[each.value].email}"
}

resource "google_project_iam_member" "sql_client" {
  for_each = toset(var.sql_service_names)
  project  = var.project_id
  role     = "roles/cloudsql.client"
  member   = "serviceAccount:${google_service_account.services[each.value].email}"
}

resource "google_project_iam_member" "log_writer" {
  for_each = toset(var.service_names)
  project  = var.project_id
  role     = "roles/logging.logWriter"
  member   = "serviceAccount:${google_service_account.services[each.value].email}"
}

resource "google_project_iam_member" "metric_writer" {
  for_each = toset(var.service_names)
  project  = var.project_id
  role     = "roles/monitoring.metricWriter"
  member   = "serviceAccount:${google_service_account.services[each.value].email}"
}

resource "google_project_iam_member" "trace_agent" {
  for_each = toset(var.service_names)
  project  = var.project_id
  role     = "roles/cloudtrace.agent"
  member   = "serviceAccount:${google_service_account.services[each.value].email}"
}