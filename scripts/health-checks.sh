#!/bin/bash

# Health Checks Script for Mayo EMR
# Performs comprehensive health checks on all system components

set -euo pipefail

# Configuration
NAMESPACE="mayo-emr"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TIMEOUT=30
RETRY_COUNT=3

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

# Generic retry function
retry() {
    local n=1
    local max=$RETRY_COUNT
    local delay=5

    while true; do
        "$@" && break || {
            if [[ $n -lt $max ]]; then
                log_warn "Command failed. Attempt $n/$max. Retrying in $delay seconds..."
                ((n++))
                sleep $delay
            else
                log_error "Command failed after $n attempts"
                return 1
            fi
        }
    done
}

# Check pod health
check_pod_health() {
    local label="$1"
    local expected_count="${2:-1}"

    log_info "Checking pod health for $label..."

    local pod_count=$(kubectl get pods -n $NAMESPACE -l "$label" --no-headers | wc -l)
    local ready_count=$(kubectl get pods -n $NAMESPACE -l "$label" -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}' | grep -o "True" | wc -l)

    if [ "$pod_count" -ne "$expected_count" ]; then
        log_error "Expected $expected_count pods for $label, found $pod_count"
        return 1
    fi

    if [ "$ready_count" -ne "$expected_count" ]; then
        log_error "Expected $expected_count ready pods for $label, found $ready_count"
        return 1
    fi

    log_success "Pod health check passed for $label"
}

# Check service health endpoint
check_service_health() {
    local service="$1"
    local port="$2"
    local path="${3:-/actuator/health}"

    log_info "Checking health endpoint for $service..."

    local pod=$(kubectl get pods -n $NAMESPACE -l app=$service -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

    if [ -z "$pod" ]; then
        log_error "No pod found for service $service"
        return 1
    fi

    local health_response=$(kubectl exec -n $NAMESPACE $pod -- timeout $TIMEOUT curl -f -s "http://localhost:$port$path" 2>/dev/null || echo "failed")

    if [ "$health_response" = "failed" ]; then
        log_error "Health check failed for $service"
        return 1
    fi

    # Parse health response (assuming JSON)
    local status=$(echo "$health_response" | jq -r '.status' 2>/dev/null || echo "unknown")

    if [ "$status" != "UP" ] && [ "$status" != "unknown" ]; then
        log_error "Service $service health status: $status"
        return 1
    fi

    log_success "Health check passed for $service"
}

# Check database health
check_database_health() {
    log_info "Checking database health..."

    local db_password=$(kubectl get secret mayo-db-secret -n $NAMESPACE -o jsonpath='{.data.mayo-password}' | base64 -d)
    local postgres_pod=$(kubectl get pods -n $NAMESPACE -l app=postgres -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

    if [ -z "$postgres_pod" ]; then
        log_error "PostgreSQL pod not found"
        return 1
    fi

    # Check PostgreSQL responsiveness
    local db_check=$(kubectl exec -n $NAMESPACE $postgres_pod -- timeout $TIMEOUT bash -c "
        export PGPASSWORD='$db_password'
        psql -h localhost -U mayo -d mayo_db -c 'SELECT 1;' >/dev/null 2>&1 && echo 'success' || echo 'failed'
    ")

    if [ "$db_check" != "success" ]; then
        log_error "Database health check failed"
        return 1
    fi

    # Check database size and connections
    local db_stats=$(kubectl exec -n $NAMESPACE $postgres_pod -- bash -c "
        export PGPASSWORD='$db_password'
        psql -h localhost -U mayo -d mayo_db -c \"
            SELECT
                pg_size_pretty(pg_database_size('mayo_db')) as size,
                count(*) as connections
            FROM pg_stat_activity
            WHERE datname = 'mayo_db';
        \" -t
    ")

    log_info "Database stats: $db_stats"

    log_success "Database health check passed"
}

# Check Redis health
check_redis_health() {
    log_info "Checking Redis health..."

    local redis_pod=$(kubectl get pods -n $NAMESPACE -l app=redis -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

    if [ -z "$redis_pod" ]; then
        log_error "Redis pod not found"
        return 1
    fi

    # Check Redis ping
    local redis_ping=$(kubectl exec -n $NAMESPACE $redis_pod -- timeout $TIMEOUT redis-cli ping 2>/dev/null || echo "failed")

    if [ "$redis_ping" != "PONG" ]; then
        log_error "Redis ping failed"
        return 1
    fi

    # Check Redis info
    local redis_info=$(kubectl exec -n $NAMESPACE $redis_pod -- redis-cli info stats 2>/dev/null | grep -E "(total_connections_received|total_commands_processed)" || echo "")

    if [ -n "$redis_info" ]; then
        log_info "Redis stats: $redis_info"
    fi

    log_success "Redis health check passed"
}

# Check Kafka health
check_kafka_health() {
    log_info "Checking Kafka health..."

    local kafka_pod=$(kubectl get pods -n $NAMESPACE -l app=kafka -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

    if [ -z "$kafka_pod" ]; then
        log_error "Kafka pod not found"
        return 1
    fi

    # Check Kafka broker API
    local kafka_check=$(kubectl exec -n $NAMESPACE $kafka_pod -- timeout $TIMEOUT bash -c "
        kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1 && echo 'success' || echo 'failed'
    ")

    if [ "$kafka_check" != "success" ]; then
        log_error "Kafka health check failed"
        return 1
    fi

    # Check topic count
    local topic_count=$(kubectl exec -n $NAMESPACE $kafka_pod -- kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | wc -l)

    log_info "Kafka topics: $topic_count"

    log_success "Kafka health check passed"
}

# Check ingress health
check_ingress_health() {
    log_info "Checking ingress health..."

    local ingress_ip=$(kubectl get ingress mayo-ingress -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    local ingress_hostname=$(kubectl get ingress mayo-ingress -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")

    if [ -z "$ingress_ip" ] && [ -z "$ingress_hostname" ]; then
        log_warn "Ingress not yet assigned external IP/hostname"
        return 0
    fi

    local endpoint=""
    if [ -n "$ingress_ip" ]; then
        endpoint="$ingress_ip"
    else
        endpoint="$ingress_hostname"
    fi

    log_info "Ingress endpoint: $endpoint"

    # Test HTTPS connectivity (if certs are set up)
    local https_check=$(curl -k -s -o /dev/null -w "%{http_code}" "https://$endpoint/health" 2>/dev/null || echo "000")

    if [ "$https_check" = "200" ] || [ "$https_check" = "404" ]; then
        log_success "Ingress HTTPS check passed (status: $https_check)"
    else
        log_warn "Ingress HTTPS check returned status: $https_check"
    fi
}

# Check monitoring health
check_monitoring_health() {
    log_info "Checking monitoring health..."

    # Check Prometheus
    if kubectl get pods -n monitoring -l app=prometheus >/dev/null 2>&1; then
        local prom_pod=$(kubectl get pods -n monitoring -l app=prometheus -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

        if [ -n "$prom_pod" ]; then
            local prom_health=$(kubectl exec -n monitoring $prom_pod -- curl -f -s http://localhost:9090/-/healthy 2>/dev/null || echo "failed")

            if [ "$prom_health" = "failed" ]; then
                log_warn "Prometheus health check failed"
            else
                log_success "Prometheus is healthy"
            fi
        fi
    fi

    # Check Grafana
    if kubectl get pods -n monitoring -l app=grafana >/dev/null 2>&1; then
        local grafana_pod=$(kubectl get pods -n monitoring -l app=grafana -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

        if [ -n "$grafana_pod" ]; then
            local grafana_health=$(kubectl exec -n monitoring $grafana_pod -- curl -f -s http://localhost:3000/api/health 2>/dev/null || echo "failed")

            if [ "$grafana_health" = "failed" ]; then
                log_warn "Grafana health check failed"
            else
                log_success "Grafana is healthy"
            fi
        fi
    fi
}

# Check resource usage
check_resource_usage() {
    log_info "Checking resource usage..."

    # Check pod resource usage
    log_info "Pod resource usage:"
    kubectl top pods -n $NAMESPACE --no-headers 2>/dev/null || log_warn "kubectl top not available"

    # Check node resources
    log_info "Node resource usage:"
    kubectl top nodes --no-headers 2>/dev/null || log_warn "kubectl top not available"
}

# Check for alerts
check_alerts() {
    log_info "Checking for active alerts..."

    # Check Prometheus alerts
    if kubectl get pods -n monitoring -l app=prometheus >/dev/null 2>&1; then
        local prom_pod=$(kubectl get pods -n monitoring -l app=prometheus -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

        if [ -n "$prom_pod" ]; then
            local alerts=$(kubectl exec -n monitoring $prom_pod -- curl -s http://localhost:9090/api/v1/alerts 2>/dev/null | jq -r '.data.alerts[] | select(.state == "firing") | .labels.alertname' 2>/dev/null || echo "")

            if [ -n "$alerts" ]; then
                log_warn "Active alerts found:"
                echo "$alerts"
            else
                log_success "No active alerts"
            fi
        fi
    fi
}

# Generate health report
generate_health_report() {
    log_info "Generating health check report..."

    local report_file="/tmp/health-check-$(date +%Y%m%d-%H%M%S).txt"

    {
        echo "Mayo EMR Health Check Report"
        echo "============================"
        echo "Timestamp: $(date)"
        echo "Namespace: $NAMESPACE"
        echo ""
        echo "Pod Status:"
        kubectl get pods -n $NAMESPACE --no-headers -o custom-columns=NAME:.metadata.name,STATUS:.status.phase,RESTARTS:.status.containerStatuses[0].restartCount
        echo ""
        echo "Service Status:"
        kubectl get services -n $NAMESPACE --no-headers
        echo ""
        echo "Resource Usage:"
        kubectl top pods -n $NAMESPACE --no-headers 2>/dev/null || echo "kubectl top not available"
    } > "$report_file"

    log_info "Health report saved to: $report_file"
}

# Main function
main() {
    log_info "Starting comprehensive health checks..."

    # Run all health checks
    retry check_pod_health "app=auth-service" 3
    retry check_pod_health "app=patient-service" 3
    retry check_pod_health "app=medical-record-service" 3
    retry check_pod_health "app=sync-service" 3
    retry check_pod_health "app=audit-service" 3
    retry check_pod_health "app=notification-service" 3
    retry check_pod_health "app=hospital-integration-service" 3
    retry check_pod_health "app=gateway" 3
    retry check_pod_health "app=postgres" 3
    retry check_pod_health "app=redis" 6
    retry check_pod_health "app=kafka" 3

    retry check_service_health "auth-service" "8081"
    retry check_service_health "patient-service" "8082"
    retry check_service_health "medical-record-service" "8083"
    retry check_service_health "sync-service" "8084"
    retry check_service_health "audit-service" "8085"
    retry check_service_health "notification-service" "8086"
    retry check_service_health "hospital-integration-service" "8087"
    retry check_service_health "gateway" "8080"

    retry check_database_health
    retry check_redis_health
    retry check_kafka_health

    check_ingress_health
    check_monitoring_health
    check_resource_usage
    check_alerts

    generate_health_report

    log_success "All health checks completed successfully"
}

# Run main function
main "$@"