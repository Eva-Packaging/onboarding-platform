# Onboarding System

This project is a GitHub-centered onboarding and access provisioning platform for an education application built with Next.js, Spring Boot, GCP, and Terraform. The system uses GitHub as the primary identity source, creates an internal user profile after registration, correlates the GitHub identity with an Atlassian identity when possible, and provisions access to both GitHub teams and Jira-access groups through asynchronous backend workflows.

## Goals

- Support GitHub-only registration from the frontend.
- Create internal user and onboarding records after successful registration.
- Provision the user into a target GitHub team and Jira/Atlassian group.
- Preserve step-level onboarding state for retries, support, and auditability.
- Correlate GitHub and Jira identities so development activity can be shown accurately in Jira when possible.

## Architecture

### Frontend

The frontend is a Next.js application responsible for the registration entry point, onboarding progress pages, and post-onboarding dashboard experience. It initiates sign-in, submits a registration request to the backend, and displays onboarding state such as pending GitHub invitation acceptance or Jira identity mismatch.

### Backend Services

The backend is composed of Spring Boot services with clear ownership boundaries:

- `user-service` manages internal user profiles and external identity records.
- `onboarding-service` manages onboarding requests, step state, retry logic, and orchestration.
- `provisioning-service` integrates with GitHub and Atlassian APIs for team and group assignment.
- `notification-service` is optional and can be added later for support alerts and admin notifications.

### Messaging

Kafka is used to decouple registration from external provisioning. This keeps the initial user flow responsive and allows retries, delayed processing, and audit-friendly event history when third-party API calls fail or remain pending.

### Infrastructure

The platform is intended for GCP deployment with Terraform-managed infrastructure. A typical deployment includes Cloud Run for Spring Boot services and the frontend, Cloud SQL PostgreSQL for persistence, Secret Manager for provider credentials, and environment-specific infrastructure definitions for development and production.

## Core Flow

1. A user starts registration from the Next.js application using GitHub as the identity source.
2. The frontend calls the backend registration endpoint after successful sign-in.
3. The backend creates a `user_profile`, stores GitHub identity data, creates an `onboarding_request`, and emits onboarding events.
4. Identity correlation attempts to link the GitHub user to an Atlassian account using email and other matching strategies.
5. GitHub team provisioning runs asynchronously through the provisioning service.
6. Jira or Atlassian group provisioning runs asynchronously through the provisioning service.
7. The frontend polls or fetches onboarding status until the request reaches a terminal state such as completed, partial success, or action required.

## Identity Strategy

GitHub is treated as the canonical external identity because the system needs reliable linkage between GitHub developer activity and Jira work tracking. Atlassian documentation indicates that GitHub author and reviewer information in Jira is displayed correctly when GitHub and Jira account emails match, which makes identity correlation a first-class concern in the onboarding model.

The system therefore stores separate records for GitHub identity, Atlassian identity, and the correlation result between them. Jira provisioning should depend on successful correlation when the product requires reliable development attribution inside Jira.

## Project Structure

A practical initial repository layout is shown below:

```text
apps/
  web/
services/
  user-service/
  onboarding-service/
  provisioning-service/
infra/
  terraform/
docs/
```

## Initial Setup

### Local Development

- Create the monorepo structure for the frontend, backend services, and infrastructure code.
- Bootstrap the Next.js app with TypeScript and basic onboarding pages.
- Bootstrap Spring Boot services with Web, Validation, Data JPA, Actuator, Kafka, and PostgreSQL support.
- Start PostgreSQL and Kafka locally with Docker Compose for development workflows.
- Configure environment variables for GitHub and Atlassian integration credentials using local secret management for development.

### Cloud Setup

- Enable required GCP services for Cloud Run, Cloud SQL, Secret Manager, and supporting networking.
- Provision Cloud SQL PostgreSQL for service persistence.
- Deploy Spring Boot services and the web application to Cloud Run.
- Store provider credentials in Secret Manager rather than in application configuration files.
- Manage all infrastructure changes through Terraform modules and environment-specific variable sets.

## Operational Notes

GitHub team assignment may return a pending state when the target user still needs to accept an invitation, so the onboarding model must support nonterminal external states rather than assuming immediate success.

Atlassian provisioning is best modeled as a managed user and group provisioning flow, especially when enterprise identity controls or SCIM-based user provisioning are used. This design keeps Jira access management consistent and aligns with Atlassian's provisioning guidance.