locals {
  backend_services = ["user-service", "onboarding-service", "provisioning-service"]
  all_services     = concat(local.backend_services, ["api-gateway", "web"])

  common_env_vars = {
    ENVIRONMENT = var.environment
  }
}

module "iam" {
  source            = "./modules/iam"
  project_id        = var.project_id
  service_names     = local.all_services
  sql_service_names = local.backend_services
}

module "secrets" {
  source     = "./modules/secret-manager"
  project_id = var.project_id
  secret_ids = [
    "github-token",
    "atlassian-api-key",
    "atlassian-account-email",
    "postgres-password",
    "nextauth-secret",
    "github-oauth-id",
    "github-oauth-secret",
  ]
}

module "cloud_sql" {
  source              = "./modules/cloud-sql"
  project_id          = var.project_id
  region              = var.region
  instance_name       = "onboarding-postgres-${var.environment}"
  tier                = var.db_tier
  vpc_network         = var.vpc_network
  databases           = ["user_service", "onboarding_service", "provisioning_service"]
  db_username         = var.db_username
  db_password         = var.db_password
  deletion_protection = var.deletion_protection
}

module "cloud_run_user_service" {
  source                = "./modules/cloud-run"
  project_id            = var.project_id
  region                = var.region
  service_name          = "user-service"
  image                 = var.service_images["user-service"]
  service_account_email = module.iam.service_account_emails["user-service"]
  min_instances         = var.min_instances
  max_instances         = var.max_instances
  allow_public_access   = false
  env_vars = merge(local.common_env_vars, {
    SPRING_PROFILES_ACTIVE      = var.environment
    DB_INSTANCE_CONNECTION_NAME = module.cloud_sql.connection_name
    DB_NAME                     = "user_service"
    DB_USERNAME                 = var.db_username
  })
  secret_env_vars = {
    DB_PASSWORD = {
      secret  = module.secrets.secret_ids["postgres-password"]
      version = "latest"
    }
  }
}

module "cloud_run_onboarding_service" {
  source                = "./modules/cloud-run"
  project_id            = var.project_id
  region                = var.region
  service_name          = "onboarding-service"
  image                 = var.service_images["onboarding-service"]
  service_account_email = module.iam.service_account_emails["onboarding-service"]
  min_instances         = var.min_instances
  max_instances         = var.max_instances
  allow_public_access   = false
  env_vars = merge(local.common_env_vars, {
    SPRING_PROFILES_ACTIVE      = var.environment
    DB_INSTANCE_CONNECTION_NAME = module.cloud_sql.connection_name
    DB_NAME                     = "onboarding_service"
    DB_USERNAME                 = var.db_username
  })
  secret_env_vars = {
    DB_PASSWORD = {
      secret  = module.secrets.secret_ids["postgres-password"]
      version = "latest"
    }
  }
}

module "cloud_run_provisioning_service" {
  source                = "./modules/cloud-run"
  project_id            = var.project_id
  region                = var.region
  service_name          = "provisioning-service"
  image                 = var.service_images["provisioning-service"]
  service_account_email = module.iam.service_account_emails["provisioning-service"]
  min_instances         = var.min_instances
  max_instances         = var.max_instances
  allow_public_access   = false
  env_vars = merge(local.common_env_vars, {
    SPRING_PROFILES_ACTIVE      = var.environment
    DB_INSTANCE_CONNECTION_NAME = module.cloud_sql.connection_name
    DB_NAME                     = "provisioning_service"
    DB_USERNAME                 = var.db_username
  })
  secret_env_vars = {
    DB_PASSWORD = {
      secret  = module.secrets.secret_ids["postgres-password"]
      version = "latest"
    }
    GITHUB_TOKEN = {
      secret  = module.secrets.secret_ids["github-token"]
      version = "latest"
    }
    ATLASSIAN_API_KEY = {
      secret  = module.secrets.secret_ids["atlassian-api-key"]
      version = "latest"
    }
    ATLASSIAN_ACCOUNT_EMAIL = {
      secret  = module.secrets.secret_ids["atlassian-account-email"]
      version = "latest"
    }
  }
}

module "cloud_run_api_gateway" {
  source                = "./modules/cloud-run"
  project_id            = var.project_id
  region                = var.region
  service_name          = "api-gateway"
  image                 = var.service_images["api-gateway"]
  service_account_email = module.iam.service_account_emails["api-gateway"]
  min_instances         = var.min_instances
  max_instances         = var.max_instances
  allow_public_access   = false
  env_vars = merge(local.common_env_vars, {
    SPRING_PROFILES_ACTIVE        = var.environment
    USER_SERVICE_URL              = module.cloud_run_user_service.service_url
    ONBOARDING_SERVICE_URL        = module.cloud_run_onboarding_service.service_url
    PROVISIONING_SERVICE_URL      = module.cloud_run_provisioning_service.service_url
  })
  secret_env_vars = {}
}

module "cloud_run_web" {
  source                = "./modules/cloud-run"
  project_id            = var.project_id
  region                = var.region
  service_name          = "web"
  image                 = var.service_images["web"]
  service_account_email = module.iam.service_account_emails["web"]
  min_instances         = var.min_instances
  max_instances         = var.max_instances
  allow_public_access   = true
  env_vars = merge(local.common_env_vars, {
    BACKEND_API_URL = module.cloud_run_api_gateway.service_url
  })
  secret_env_vars = {
    NEXTAUTH_SECRET = {
      secret  = module.secrets.secret_ids["nextauth-secret"]
      version = "latest"
    }
    GITHUB_ID = {
      secret  = module.secrets.secret_ids["github-oauth-id"]
      version = "latest"
    }
    GITHUB_SECRET = {
      secret  = module.secrets.secret_ids["github-oauth-secret"]
      version = "latest"
    }
  }
}