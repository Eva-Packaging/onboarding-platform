# Dev environment — non-secret, environment-specific values.
# Secret values (db_password, tokens) are injected via CI/CD and never committed.

project_id          = "onboarding-platform-dev"
region              = "us-central1"
environment         = "dev"
vpc_network         = "projects/onboarding-platform-dev/global/networks/default"

db_tier             = "db-f1-micro"
db_username         = "app"
deletion_protection = false

min_instances = 0
max_instances = 3

service_images = {
  "user-service"         = "gcr.io/cloudrun/placeholder"
  "onboarding-service"   = "gcr.io/cloudrun/placeholder"
  "provisioning-service" = "gcr.io/cloudrun/placeholder"
  "api-gateway"          = "gcr.io/cloudrun/placeholder"
  "web"                  = "gcr.io/cloudrun/placeholder"
}