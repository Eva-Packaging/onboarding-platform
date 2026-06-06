variable "project_id" {
  type        = string
  description = "GCP project ID where all resources will be created."
}

variable "region" {
  type        = string
  description = "GCP region for Cloud Run services and Cloud SQL."
  default     = "us-central1"
}

variable "environment" {
  type        = string
  description = "Deployment environment: dev or prod."
  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment must be 'dev' or 'prod'."
  }
}

variable "vpc_network" {
  type        = string
  description = "Self-link of the VPC network used for private Cloud SQL connectivity."
}

variable "db_tier" {
  type        = string
  description = "Cloud SQL machine tier (e.g. db-f1-micro for dev, db-custom-2-4096 for prod)."
  default     = "db-f1-micro"
}

variable "db_username" {
  type        = string
  description = "PostgreSQL application username."
  default     = "app"
}

variable "db_password" {
  type        = string
  description = "PostgreSQL application password. Provide via CI/CD secret injection — never commit a value."
  sensitive   = true
}

variable "deletion_protection" {
  type        = bool
  description = "Enable deletion protection on the Cloud SQL instance. Always true in prod."
  default     = false
}

variable "min_instances" {
  type        = number
  description = "Minimum Cloud Run instance count per service."
  default     = 0
}

variable "max_instances" {
  type        = number
  description = "Maximum Cloud Run instance count per service."
  default     = 3
}

variable "service_images" {
  type        = map(string)
  description = "Container image URL per service. Set via CI/CD — defaults are placeholder stubs."
  default = {
    "user-service"         = "gcr.io/cloudrun/placeholder"
    "onboarding-service"   = "gcr.io/cloudrun/placeholder"
    "provisioning-service" = "gcr.io/cloudrun/placeholder"
    "api-gateway"          = "gcr.io/cloudrun/placeholder"
    "web"                  = "gcr.io/cloudrun/placeholder"
  }
}