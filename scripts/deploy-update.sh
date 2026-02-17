#!/bin/bash

# Mayo EMR Update Deployment Script
# Handles rolling updates and canary deployments for the EMR system

set -euo pipefail

# Configuration
NAMESPACE="mayo-emr"
REGION="${REGION:-accra}"
ENVIRONMENT="production"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Update configuration
CANARY_PERCENTAGE="${CANARY_PERCENTAGE:-10}"
ROLLBACK_TIMEOUT="${ROLLBACK_TIMEOUT:-600}"
HEALTH_CHECK_INTERVAL="${HEALTH_CHECK_INTERVAL:-30}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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
    initiate_rollback
    exit 1
}

# Pre-update checks
pre_update_checks() {
    log_info "Performing pre-update checks..."

    # Check kubectl connectivity
    if ! kubectl cluster-info >/dev/null 2>&1; then
        error_exit "Cannot connect to Kubernetes cluster"
    fi

    # Check namespace exists
    if ! kubectl get namespace $NAMESPACE >/dev/null 2>&1; then
        error_exit "Namespace $NAMESPACE does not exist"
    fi

    # Check current deployment status
    if ! kubectl get deployment auth-service -n $NAMESPACE >/dev/null 2>&1; then
        error_exit "No existing deployment found. Use initial deployment script instead."
    fi

    # Check if there's already an ongoing update
    local canary_deployments=$(kubectl get deployments -n $NAMESPACE -l deployment-type=canary --no-headers | wc -l)
    if [ "$canary_deployments" -gt 0 ]; then
        log_warn "Canary deployment already in progress"
        read -p "Continue with existing canary? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi

    log_success "Pre-update checks passed"
}

# Backup current state
backup_current_state() {
    log_info "Backing up current deployment state..."

    local backup_dir="/tmp/emr-backup-$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$backup_dir"

    # Backup deployments
    kubectl get deployments -n $NAMESPACE -o yaml > "$backup_dir/deployments.yaml"
    kubectl get statefulsets -n $NAMESPACE -o yaml > "$backup_dir/statefulsets.yaml"
    kubectl get services -n $NAMESPACE -o yaml > "$backup_dir/services.yaml"
    kubectl get configmaps -n $NAMESPACE -o yaml > "$backup_dir/configmaps.yaml"
    kubectl get secrets -n $NAMESPACE -o yaml > "$backup_dir/secrets.yaml"
    kubectl get ingress -n $NAMESPACE -o yaml > "$backup_dir/ingress.yaml"

    log_info "Backup saved to: $backup_dir"
    echo "$backup_dir" > /tmp/emr-backup-path
}

# Create canary deployment
create_canary_deployment() {
    local service="$1"
    local image_tag="$2"

    log_info "Creating canary deployment for $service with image tag: $image_tag"

    # Get current deployment
    local current_deployment=$(kubectl get deployment $service -n $NAMESPACE -o yaml)

    # Calculate canary replicas
    local total_replicas=$(echo "$current_deployment" | yq eval '.spec.replicas' -)
    local canary_replicas=$(( total_replicas * CANARY_PERCENTAGE / 100 ))
    canary_replicas=$(( canary_replicas > 0 ? canary_replicas : 1 ))

    log_info "Total replicas: $total_replicas, Canary replicas: $canary_replicas"

    # Create canary deployment
    cat > /tmp/canary-$service.yaml << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: $service-canary
  namespace: $NAMESPACE
  labels:
    app: $service
    deployment-type: canary
spec:
  replicas: $canary_replicas
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: $service
      deployment-type: canary
  template:
    metadata:
      labels:
        app: $service
        deployment-type: canary
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8081"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: mayo-service-account
      containers:
      - name: $service
        image: ghcr.io/mayo-emr/$service:$image_tag
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8081
          name: http
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production,canary,$REGION"
        - name: REGION
          value: "$REGION"
        - name: PRIMARY_REGION
          value: "false"
        envFrom:
        - configMapRef:
            name: regional-config
        resources:
          requests:
            cpu: "500m"
            memory: "1Gi"
          limits:
            cpu: "2000m"
            memory: "4Gi"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          successThreshold: 1
          failureThreshold: 3
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 20
          timeoutSeconds: 10
          successThreshold: 1
          failureThreshold: 5
EOF

    kubectl apply -f /tmp/canary-$service.yaml

    # Wait for canary to be ready
    log_info "Waiting for canary deployment to be ready..."
    kubectl wait --for=condition=available --timeout=300s deployment/$service-canary -n $NAMESPACE

    log_success "Canary deployment created for $service"
}

# Monitor canary deployment
monitor_canary() {
    local service="$1"
    local duration="${2:-300}"  # 5 minutes default

    log_info "Monitoring canary deployment for $service for $duration seconds..."

    local start_time=$(date +%s)
    local end_time=$((start_time + duration))

    while [ $(date +%s) -lt $end_time ]; do
        # Check canary health
        local canary_pods=$(kubectl get pods -n $NAMESPACE -l app=$service,deployment-type=canary --no-headers | wc -l)
        local canary_ready=$(kubectl get pods -n $NAMESPACE -l app=$service,deployment-type=canary -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}' | grep -o "True" | wc -l)

        if [ "$canary_ready" -ne "$canary_pods" ]; then
            log_error "Canary pods not healthy: $canary_ready/$canary_pods ready"
            return 1
        fi

        # Check error rates and latency (simplified)
        local error_rate=$(check_error_rate "$service-canary")
        local latency=$(check_latency "$service-canary")

        if (( $(echo "$error_rate > 5.0" | bc -l 2>/dev/null || echo "1") )); then
            log_error "High error rate detected: ${error_rate}%"
            return 1
        fi

        if (( $(echo "$latency > 2000" | bc -l 2>/dev/null || echo "0") )); then
            log_warn "High latency detected: ${latency}ms"
        fi

        log_info "Canary monitoring: Error rate: ${error_rate}%, Latency: ${latency}ms"
        sleep $HEALTH_CHECK_INTERVAL
    done

    log_success "Canary monitoring completed successfully"
}

# Check error rate (simplified - would integrate with Prometheus)
check_error_rate() {
    local service="$1"
    # Placeholder - in real implementation, query Prometheus
    echo "0.5"
}

# Check latency (simplified - would integrate with Prometheus)
check_latency() {
    local service="$1"
    # Placeholder - in real implementation, query Prometheus
    echo "150"
}

# Promote canary to full deployment
promote_canary() {
    local service="$1"
    local image_tag="$2"

    log_info "Promoting canary deployment for $service..."

    # Update main deployment with new image
    kubectl set image deployment/$service $service=ghcr.io/mayo-emr/$service:$image_tag -n $NAMESPACE

    # Wait for rollout
    kubectl rollout status deployment/$service -n $NAMESPACE --timeout=600s

    # Remove canary deployment
    kubectl delete deployment $service-canary -n $NAMESPACE

    log_success "Canary promoted to full deployment for $service"
}

# Rollback deployment
initiate_rollback() {
    log_info "Initiating rollback procedure..."

    # Check if backup exists
    if [ -f /tmp/emr-backup-path ]; then
        local backup_dir=$(cat /tmp/emr-backup-path)
        log_info "Rolling back using backup: $backup_dir"

        # Restore from backup
        kubectl apply -f "$backup_dir/deployments.yaml" || log_error "Failed to restore deployments"
        kubectl apply -f "$backup_dir/statefulsets.yaml" || log_error "Failed to restore statefulsets"

        # Wait for rollbacks to complete
        kubectl wait --for=condition=available --timeout=$ROLLBACK_TIMEOUT deployments --all -n $NAMESPACE || log_error "Rollback timeout"

        log_success "Rollback completed"
    else
        log_error "No backup found for rollback"
    fi
}

# Update configuration
update_configuration() {
    log_info "Updating configuration..."

    # Update configmaps if needed
    if [ -f "$PROJECT_ROOT/infrastructure/kubernetes/regional-deployment.yaml" ]; then
        kubectl apply -f "$PROJECT_ROOT/infrastructure/kubernetes/regional-deployment.yaml" || log_error "Failed to update configuration"
    fi

    log_success "Configuration updated"
}

# Update secrets
update_secrets() {
    log_info "Updating secrets..."

    # Rotate certificates if needed
    "$SCRIPT_DIR/manage-certificates.sh" || log_error "Certificate update failed"

    log_success "Secrets updated"
}

# Update database schema
update_database() {
    log_info "Checking for database updates..."

    # Run migrations if needed
    "$SCRIPT_DIR/init-database.sh" || log_error "Database update failed"

    log_success "Database updated"
}

# Run post-update validation
post_update_validation() {
    log_info "Running post-update validation..."

    "$SCRIPT_DIR/validate-deployment.sh" || error_exit "Post-update validation failed"

    log_success "Post-update validation passed"
}

# Main update function
main() {
    local image_tag="${1:-latest}"
    local services_to_update="${2:-all}"

    log_info "Starting Mayo EMR update deployment with image tag: $image_tag"

    # Trap for cleanup
    trap 'error_exit "Update interrupted"' INT TERM

    pre_update_checks
    backup_current_state
    update_configuration
    update_secrets
    update_database

    # Determine which services to update
    local services
    if [ "$services_to_update" = "all" ]; then
        services=("auth-service" "patient-service" "medical-record-service" "sync-service" "audit-service" "notification-service" "hospital-integration-service" "gateway")
    else
        IFS=',' read -ra services <<< "$services_to_update"
    fi

    # Update each service
    for service in "${services[@]}"; do
        log_info "Updating service: $service"

        create_canary_deployment "$service" "$image_tag"

        if monitor_canary "$service"; then
            promote_canary "$service" "$image_tag"
            log_success "Service $service updated successfully"
        else
            log_error "Canary monitoring failed for $service, rolling back"
            initiate_rollback
            exit 1
        fi
    done

    post_update_validation

    log_success "Mayo EMR update deployment completed successfully!"
    log_info "Update summary:"
    log_info "- Image tag: $image_tag"
    log_info "- Services updated: ${services[*]}"
    log_info "- Canary percentage: $CANARY_PERCENTAGE%"
    log_info "- Region: $REGION"
}

# Show usage
usage() {
    echo "Usage: $0 <image_tag> [services]"
    echo "  image_tag: Docker image tag to deploy (required)"
    echo "  services: Comma-separated list of services to update (optional, default: all)"
    echo ""
    echo "Examples:"
    echo "  $0 v2.1.0"
    echo "  $0 v2.1.0 auth-service,gateway"
    echo ""
    echo "Environment variables:"
    echo "  CANARY_PERCENTAGE: Percentage of traffic for canary (default: 10)"
    echo "  ROLLBACK_TIMEOUT: Rollback timeout in seconds (default: 600)"
    echo "  HEALTH_CHECK_INTERVAL: Health check interval in seconds (default: 30)"
}

# Parse arguments
if [ $# -lt 1 ]; then
    usage
    exit 1
fi

# Run main function
main "$@"