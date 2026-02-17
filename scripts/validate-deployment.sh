#!/bin/bash

# Post-Deployment Validation Script for Mayo EMR
# Validates that all services are deployed correctly and functioning

set -euo pipefail

# Configuration
NAMESPACE="mayo-emr"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

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

# Validate Kubernetes resources
validate_kubernetes_resources() {
    log_info "Validating Kubernetes resources..."

    # Check namespace
    if ! kubectl get namespace $NAMESPACE >/dev/null 2>&1; then
        log_error "Namespace $NAMESPACE does not exist"
        return 1
    fi

    # Check deployments
    local deployments=("auth-service" "patient-service" "medical-record-service" "sync-service" "audit-service" "notification-service" "hospital-integration-service" "gateway")
    for deployment in "${deployments[@]}"; do
        if ! kubectl get deployment $deployment -n $NAMESPACE >/dev/null 2>&1; then
            log_error "Deployment $deployment does not exist"
            return 1
        fi

        # Check if deployment is ready
        local ready_replicas=$(kubectl get deployment $deployment -n $NAMESPACE -o jsonpath='{.status.readyReplicas}')
        local desired_replicas=$(kubectl get deployment $deployment -n $NAMESPACE -o jsonpath='{.spec.replicas}')

        if [ "$ready_replicas" != "$desired_replicas" ]; then
            log_error "Deployment $deployment is not ready: $ready_replicas/$desired_replicas replicas"
            return 1
        fi
    done

    # Check statefulsets
    local statefulsets=("mayo-postgres" "mayo-redis" "mayo-kafka")
    for sts in "${statefulsets[@]}"; do
        if ! kubectl get statefulset $sts -n $NAMESPACE >/dev/null 2>&1; then
            log_error "StatefulSet $sts does not exist"
            return 1
        fi

        local ready_replicas=$(kubectl get statefulset $sts -n $NAMESPACE -o jsonpath='{.status.readyReplicas}')
        local desired_replicas=$(kubectl get statefulset $sts -n $NAMESPACE -o jsonpath='{.spec.replicas}')

        if [ "$ready_replicas" != "$desired_replicas" ]; then
            log_error "StatefulSet $sts is not ready: $ready_replicas/$desired_replicas replicas"
            return 1
        fi
    done

    # Check services
    local services=("auth-service" "patient-service" "medical-record-service" "sync-service" "audit-service" "notification-service" "hospital-integration-service" "gateway" "mayo-postgres" "mayo-redis" "mayo-kafka")
    for service in "${services[@]}"; do
        if ! kubectl get service $service -n $NAMESPACE >/dev/null 2>&1; then
            log_error "Service $service does not exist"
            return 1
        fi
    done

    # Check ingress
    if ! kubectl get ingress mayo-ingress -n $NAMESPACE >/dev/null 2>&1; then
        log_error "Ingress mayo-ingress does not exist"
        return 1
    fi

    # Check secrets
    local secrets=("mayo-db-secret" "mayo-redis-secret" "mayo-kafka-secret" "mayo-app-secrets" "mayo-tls-secret" "mayo-ca-secret" "mayo-server-cert" "mayo-device-cert")
    for secret in "${secrets[@]}"; do
        if ! kubectl get secret $secret -n $NAMESPACE >/dev/null 2>&1; then
            log_error "Secret $secret does not exist"
            return 1
        fi
    done

    # Check configmaps
    local configmaps=("regional-config" "postgres-config" "redis-config" "kafka-config" "monitoring-config" "backup-config")
    for cm in "${configmaps[@]}"; do
        if ! kubectl get configmap $cm -n $NAMESPACE >/dev/null 2>&1; then
            log_error "ConfigMap $cm does not exist"
            return 1
        fi
    done

    log_success "Kubernetes resources validation passed"
}

# Validate service health endpoints
validate_service_health() {
    log_info "Validating service health endpoints..."

    local services=(
        "auth-service:8081"
        "patient-service:8082"
        "medical-record-service:8083"
        "sync-service:8084"
        "audit-service:8085"
        "notification-service:8086"
        "hospital-integration-service:8087"
        "gateway:8080"
    )

    for service_info in "${services[@]}"; do
        local service=$(echo $service_info | cut -d: -f1)
        local port=$(echo $service_info | cut -d: -f2)

        log_info "Checking health of $service..."

        # Get pod name
        local pod=$(kubectl get pods -n $NAMESPACE -l app=$service -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

        if [ -z "$pod" ]; then
            log_error "No pod found for service $service"
            return 1
        fi

        # Check health endpoint
        local health_check=$(kubectl exec -n $NAMESPACE $pod -- curl -f -s http://localhost:$port/actuator/health 2>/dev/null || echo "failed")

        if [ "$health_check" = "failed" ]; then
            log_error "Health check failed for $service"
            return 1
        fi

        log_info "$service is healthy"
    done

    log_success "Service health validation passed"
}

# Validate database connectivity
validate_database_connectivity() {
    log_info "Validating database connectivity..."

    # Check PostgreSQL
    local postgres_pod=$(kubectl get pods -n $NAMESPACE -l app=postgres -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

    if [ -z "$postgres_pod" ]; then
        log_error "PostgreSQL pod not found"
        return 1
    fi

    # Test database connection
    local db_password=$(kubectl get secret mayo-db-secret -n $NAMESPACE -o jsonpath='{.data.mayo-password}' | base64 -d)

    local db_test=$(kubectl exec -n $NAMESPACE $postgres_pod -- bash -c "
        export PGPASSWORD='$db_password'
        psql -h localhost -U mayo -d mayo_db -c 'SELECT 1;' >/dev/null 2>&1 && echo 'success' || echo 'failed'
    ")

    if [ "$db_test" != "success" ]; then
        log_error "Database connectivity test failed"
        return 1
    fi

    log_success "Database connectivity validation passed"
}

# Validate Redis connectivity
validate_redis_connectivity() {
    log_info "Validating Redis connectivity..."

    local redis_pod=$(kubectl get pods -n $NAMESPACE -l app=redis -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

    if [ -z "$redis_pod" ]; then
        log_error "Redis pod not found"
        return 1
    fi

    # Test Redis connection
    local redis_test=$(kubectl exec -n $NAMESPACE $redis_pod -- redis-cli ping 2>/dev/null || echo "failed")

    if [ "$redis_test" != "PONG" ]; then
        log_error "Redis connectivity test failed"
        return 1
    fi

    log_success "Redis connectivity validation passed"
}

# Validate Kafka connectivity
validate_kafka_connectivity() {
    log_info "Validating Kafka connectivity..."

    local kafka_pod=$(kubectl get pods -n $NAMESPACE -l app=kafka -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

    if [ -z "$kafka_pod" ]; then
        log_error "Kafka pod not found"
        return 1
    fi

    # Test Kafka connection
    local kafka_test=$(kubectl exec -n $NAMESPACE $kafka_pod -- bash -c "
        kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1 && echo 'success' || echo 'failed'
    ")

    if [ "$kafka_test" != "success" ]; then
        log_error "Kafka connectivity test failed"
        return 1
    fi

    log_success "Kafka connectivity validation passed"
}

# Validate service-to-service communication
validate_service_communication() {
    log_info "Validating service-to-service communication..."

    # Test gateway to auth-service communication
    local gateway_pod=$(kubectl get pods -n $NAMESPACE -l app=gateway -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

    if [ -z "$gateway_pod" ]; then
        log_error "Gateway pod not found"
        return 1
    fi

    # Test internal service call through gateway
    local comm_test=$(kubectl exec -n $NAMESPACE $gateway_pod -- curl -f -s http://auth-service.mayo-emr.svc.cluster.local:8081/actuator/health 2>/dev/null || echo "failed")

    if [ "$comm_test" = "failed" ]; then
        log_error "Service-to-service communication test failed"
        return 1
    fi

    log_success "Service communication validation passed"
}

# Validate monitoring setup
validate_monitoring() {
    log_info "Validating monitoring setup..."

    # Check Prometheus
    if kubectl get deployment prometheus-server -n monitoring >/dev/null 2>&1; then
        local prom_status=$(kubectl get pods -n monitoring -l app=prometheus -o jsonpath='{.items[0].status.phase}' 2>/dev/null || echo "NotFound")

        if [ "$prom_status" != "Running" ]; then
            log_warn "Prometheus is not running (status: $prom_status)"
        else
            log_info "Prometheus is running"
        fi
    else
        log_warn "Prometheus deployment not found"
    fi

    # Check Grafana
    if kubectl get deployment grafana -n monitoring >/dev/null 2>&1; then
        local grafana_status=$(kubectl get pods -n monitoring -l app=grafana -o jsonpath='{.items[0].status.phase}' 2>/dev/null || echo "NotFound")

        if [ "$grafana_status" != "Running" ]; then
            log_warn "Grafana is not running (status: $grafana_status)"
        else
            log_info "Grafana is running"
        fi
    else
        log_warn "Grafana deployment not found"
    fi

    log_success "Monitoring validation completed"
}

# Generate validation report
generate_report() {
    log_info "Generating deployment validation report..."

    local report_file="/tmp/deployment-validation-$(date +%Y%m%d-%H%M%S).txt"

    {
        echo "Mayo EMR Deployment Validation Report"
        echo "====================================="
        echo "Timestamp: $(date)"
        echo "Namespace: $NAMESPACE"
        echo ""
        echo "Kubernetes Resources:"
        kubectl get deployments,statefulsets,services,ingress,secrets,configmaps -n $NAMESPACE --no-headers | wc -l | xargs echo "Total resources:"
        echo ""
        echo "Pod Status:"
        kubectl get pods -n $NAMESPACE --no-headers -o custom-columns=NAME:.metadata.name,STATUS:.status.phase
        echo ""
        echo "Service Endpoints:"
        kubectl get services -n $NAMESPACE --no-headers -o custom-columns=NAME:.metadata.name,TYPE:.spec.type,CLUSTER-IP:.spec.clusterIP,PORTS:.spec.ports[*].port
    } > "$report_file"

    log_info "Validation report saved to: $report_file"
}

# Main function
main() {
    log_info "Starting post-deployment validation..."

    validate_kubernetes_resources
    validate_service_health
    validate_database_connectivity
    validate_redis_connectivity
    validate_kafka_connectivity
    validate_service_communication
    validate_monitoring
    generate_report

    log_success "Post-deployment validation completed successfully"
}

# Run main function
main "$@"