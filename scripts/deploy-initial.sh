#!/bin/bash

# Mayo EMR Production Initial Deployment Script
# This script performs initial deployment of the EMR system to production Kubernetes cluster
# Includes database initialization, secrets setup, certificate management, and validation

set -euo pipefail

# Configuration
NAMESPACE="mayo-emr"
REGION="${REGION:-accra}"
ENVIRONMENT="production"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

# Error handling
error_exit() {
    log_error "$1"
    rollback_deployment
    exit 1
}

# Rollback function
rollback_deployment() {
    log_warn "Initiating rollback procedure..."

    # Delete deployments in reverse order
    kubectl delete deployment --ignore-not-found=true -n $NAMESPACE \
        auth-service patient-service medical-record-service sync-service \
        audit-service notification-service hospital-integration-service gateway || true

    # Delete statefulsets
    kubectl delete statefulset --ignore-not-found=true -n $NAMESPACE \
        mayo-postgres mayo-redis mayo-kafka || true

    # Delete services
    kubectl delete service --ignore-not-found=true -n $NAMESPACE \
        auth-service patient-service medical-record-service sync-service \
        audit-service notification-service hospital-integration-service gateway \
        mayo-postgres mayo-redis mayo-kafka || true

    # Delete configmaps and secrets
    kubectl delete configmap,secret --ignore-not-found=true -n $NAMESPACE \
        regional-config postgres-config redis-config kafka-config \
        monitoring-config backup-config \
        mayo-db-secret mayo-redis-secret mayo-kafka-secret \
        mayo-tls-secret || true

    log_info "Rollback completed"
}

# Pre-deployment checks
pre_deployment_checks() {
    log_info "Performing pre-deployment checks..."

    # Check kubectl connectivity
    if ! kubectl cluster-info >/dev/null 2>&1; then
        error_exit "Cannot connect to Kubernetes cluster"
    fi

    # Check namespace exists or create it
    if ! kubectl get namespace $NAMESPACE >/dev/null 2>&1; then
        log_info "Creating namespace $NAMESPACE"
        kubectl create namespace $NAMESPACE || error_exit "Failed to create namespace"
    fi

    # Check required tools
    command -v openssl >/dev/null 2>&1 || error_exit "OpenSSL is required but not installed"
    command -v jq >/dev/null 2>&1 || error_exit "jq is required but not installed"

    log_success "Pre-deployment checks passed"
}

# Database initialization
initialize_database() {
    log_info "Initializing database..."

    # Create PostgreSQL secrets
    if ! kubectl get secret mayo-db-secret -n $NAMESPACE >/dev/null 2>&1; then
        log_info "Creating PostgreSQL secrets"
        # Generate secure passwords
        DB_PASSWORD=$(openssl rand -base64 32)
        DB_ROOT_PASSWORD=$(openssl rand -base64 32)

        kubectl create secret generic mayo-db-secret -n $NAMESPACE \
            --from-literal=postgres-password="$DB_PASSWORD" \
            --from-literal=postgres-root-password="$DB_ROOT_PASSWORD" \
            --from-literal=mayo-password="$DB_PASSWORD" || error_exit "Failed to create DB secrets"
    fi

    # Apply PostgreSQL configuration
    kubectl apply -f $PROJECT_ROOT/infrastructure/kubernetes/regional-deployment.yaml || error_exit "Failed to apply PostgreSQL config"

    # Wait for PostgreSQL to be ready
    log_info "Waiting for PostgreSQL to be ready..."
    kubectl wait --for=condition=ready pod -l app=postgres -n $NAMESPACE --timeout=600s || error_exit "PostgreSQL failed to start"

    # Run database initialization scripts
    log_info "Running database initialization scripts"
    "$SCRIPT_DIR/init-database.sh" || error_exit "Database initialization failed"

    log_success "Database initialized successfully"
}

# Setup secrets
setup_secrets() {
    log_info "Setting up secrets..."

    # Redis secrets
    if ! kubectl get secret mayo-redis-secret -n $NAMESPACE >/dev/null 2>&1; then
        REDIS_PASSWORD=$(openssl rand -base64 32)
        kubectl create secret generic mayo-redis-secret -n $NAMESPACE \
            --from-literal=redis-password="$REDIS_PASSWORD" || error_exit "Failed to create Redis secrets"
    fi

    # Kafka secrets
    if ! kubectl get secret mayo-kafka-secret -n $NAMESPACE >/dev/null 2>&1; then
        KAFKA_PASSWORD=$(openssl rand -base64 32)
        kubectl create secret generic mayo-kafka-secret -n $NAMESPACE \
            --from-literal=kafka-password="$KAFKA_PASSWORD" || error_exit "Failed to create Kafka secrets"
    fi

    # Application secrets (API keys, JWT secrets, etc.)
    if ! kubectl get secret mayo-app-secrets -n $NAMESPACE >/dev/null 2>&1; then
        JWT_SECRET=$(openssl rand -hex 32)
        ENCRYPTION_KEY=$(openssl rand -hex 32)
        API_KEY=$(openssl rand -hex 32)

        kubectl create secret generic mayo-app-secrets -n $NAMESPACE \
            --from-literal=jwt-secret="$JWT_SECRET" \
            --from-literal=encryption-key="$ENCRYPTION_KEY" \
            --from-literal=api-key="$API_KEY" || error_exit "Failed to create application secrets"
    fi

    log_success "Secrets setup completed"
}

# Certificate management
manage_certificates() {
    log_info "Managing certificates..."

    "$SCRIPT_DIR/manage-certificates.sh" || error_exit "Certificate management failed"

    log_success "Certificates managed successfully"
}

# Deploy infrastructure components
deploy_infrastructure() {
    log_info "Deploying infrastructure components..."

    # Apply Redis configuration
    kubectl apply -f $PROJECT_ROOT/infrastructure/kubernetes/regional-deployment.yaml || error_exit "Failed to deploy Redis"

    # Wait for Redis
    log_info "Waiting for Redis cluster..."
    kubectl wait --for=condition=ready pod -l app=redis -n $NAMESPACE --timeout=300s || error_exit "Redis failed to start"

    # Apply Kafka configuration
    kubectl apply -f $PROJECT_ROOT/infrastructure/kubernetes/regional-deployment.yaml || error_exit "Failed to deploy Kafka"

    # Wait for Kafka
    log_info "Waiting for Kafka cluster..."
    kubectl wait --for=condition=ready pod -l app=kafka -n $NAMESPACE --timeout=300s || error_exit "Kafka failed to start"

    # Apply monitoring configuration
    kubectl apply -f $PROJECT_ROOT/monitoring/prometheus/prometheus.yml || error_exit "Failed to deploy monitoring"
    kubectl apply -f $PROJECT_ROOT/monitoring/grafana/grafana.yml || error_exit "Failed to deploy Grafana"

    log_success "Infrastructure components deployed"
}

# Deploy application services
deploy_services() {
    log_info "Deploying application services..."

    # Deploy services in order
    SERVICES=("auth-service" "patient-service" "medical-record-service" "sync-service" "audit-service" "notification-service" "hospital-integration-service" "gateway")

    for service in "${SERVICES[@]}"; do
        log_info "Deploying $service..."

        # Apply service manifests
        kubectl apply -f $PROJECT_ROOT/infrastructure/kubernetes/regional-deployment.yaml || error_exit "Failed to deploy $service"

        # Wait for deployment to be ready
        kubectl wait --for=condition=available --timeout=300s deployment/$service -n $NAMESPACE || error_exit "$service failed to start"

        log_info "$service deployed successfully"
    done

    # Apply ingress
    kubectl apply -f $PROJECT_ROOT/infrastructure/kubernetes/regional-deployment.yaml || error_exit "Failed to deploy ingress"

    log_success "All services deployed successfully"
}

# Post-deployment validation
validate_deployment() {
    log_info "Running post-deployment validation..."

    "$SCRIPT_DIR/validate-deployment.sh" || error_exit "Deployment validation failed"

    log_success "Deployment validation passed"
}

# Health checks
run_health_checks() {
    log_info "Running health checks..."

    "$SCRIPT_DIR/health-checks.sh" || error_exit "Health checks failed"

    log_success "All health checks passed"
}

# Main deployment function
main() {
    log_info "Starting Mayo EMR initial deployment to $ENVIRONMENT environment in $REGION region"

    # Trap for cleanup on exit
    trap 'error_exit "Deployment interrupted"' INT TERM

    pre_deployment_checks
    initialize_database
    setup_secrets
    manage_certificates
    deploy_infrastructure
    deploy_services
    validate_deployment
    run_health_checks

    log_success "Mayo EMR initial deployment completed successfully!"
    log_info "Deployment summary:"
    log_info "- Namespace: $NAMESPACE"
    log_info "- Region: $REGION"
    log_info "- Environment: $ENVIRONMENT"
    log_info "- Services deployed: auth-service, patient-service, medical-record-service, sync-service, audit-service, notification-service, hospital-integration-service, gateway"
    log_info "- Infrastructure: PostgreSQL, Redis, Kafka, Monitoring"
}

# Run main function
main "$@"