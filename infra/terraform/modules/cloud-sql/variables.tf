variable "project_id" {
  type        = string
  description = "GCP project ID."
}

variable "region" {
  type        = string
  description = "GCP region for the Cloud SQL instance."
}

variable "instance_name" {
  type        = string
  description = "Name of the Cloud SQL instance."
}

variable "tier" {
  type        = string
  description = "Machine type / tier for the Cloud SQL instance (e.g. db-f1-micro, db-custom-2-4096)."
}

variable "vpc_network" {
  type        = string
  description = "Self-link of the VPC network for private IP connectivity."
}

variable "databases" {
  type        = list(string)
  description = "List of database names to create on the instance."
  default     = []
}

variable "db_username" {
  type        = string
  description = "Application database username."
  default     = "app"
}

variable "db_password" {
  type        = string
  description = "Application database password."
  sensitive   = true
}

variable "deletion_protection" {
  type        = bool
  description = "Prevent accidental deletion of the Cloud SQL instance."
  default     = false
}