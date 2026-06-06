# Remote state is stored in GCS.
# Supply bucket via -backend-config flag or a backend.hcl file — never hardcode here.
#
#   terraform init \
#     -backend-config="bucket=<your-state-bucket>" \
#     -backend-config="prefix=terraform/state/${var.environment}"
terraform {
  backend "gcs" {
    prefix = "terraform/state"
  }
}