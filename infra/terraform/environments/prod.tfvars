# Prod environment — non-secret, environment-specific values.
# Secret values (db_password, tokens) are injected via CI/CD and never committed.

project_id          = "onboarding-platform-prod"
region              = "us-central1"
environment         = "prod"
vpc_network         = "projects/onboarding-platform-prod/global/networks/default"

db_tier             = "db-custom-2-4096"
db_username         = "app"
deletion_protection = true

min_instances = 1
max_instances = 10

service_images = {
  "user-service"         = "gcr.io/cloudrun/placeholder"
  "onboarding-service"   = "gcr.io/cloudrun/placeholder"
  "provisioning-service" = "gcr.io/cloudrun/placeholder"
  "api-gateway"          = "gcr.io/cloudrun/placeholder"
  "web"                  = "gcr.io/cloudrun/placeholder"
}