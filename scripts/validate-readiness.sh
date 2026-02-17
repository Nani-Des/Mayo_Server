#!/bin/bash

# EMR Deployment Readiness Validation Script
# Validates all components before production deployment

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

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

# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Validate YAML syntax
validate_yaml() {
    local file="$1"
    log_info "Validating YAML syntax: $file"

    if ! command_exists python; then
        log_error "Python not found, cannot validate YAML"
        return 1
    fi

    if python -c "import yaml; yaml.safe_load(open('$file'))" 2>/dev/null; then
        log_success "YAML syntax valid: $file"
        return 0
    else
        log_error "YAML syntax invalid: $file"
        return 1
    fi
}

# Validate Bash script syntax
validate_bash_script() {
    local file="$1"
    log_info "Validating Bash script syntax: $file"

    if bash -n "$file" 2>/dev/null; then
        log_success "Bash syntax valid: $file"
        return 0
    else
        log_error "Bash syntax invalid: $file"
        return 1
    fi
}

# Validate Kubernetes manifests
validate_kubernetes_manifests() {
    log_info "Validating Kubernetes manifests..."

    local manifests=(
        "infrastructure/kubernetes/monitoring-deployment.yaml"
        "infrastructure/kubernetes/monitoring-security.yaml"
        "infrastructure/kubernetes/network-policies.yaml"
        "infrastructure/kubernetes/pod-security-standards.yaml"
        "infrastructure/kubernetes/rbac-enhancements.yaml"
        "infrastructure/kubernetes/regional-deployment.yaml"
    )

    for manifest in "${manifests[@]}"; do
        if [ -f "$PROJECT_ROOT/$manifest" ]; then
            validate_yaml "$PROJECT_ROOT/$manifest" || return 1

            # Additional kubectl dry-run validation if kubectl is available
            if command_exists kubectl; then
                log_info "Running kubectl dry-run for $manifest"
                if kubectl --dry-run=client -f "$PROJECT_ROOT/$manifest" >/dev/null 2>&1; then
                    log_success "kubectl dry-run passed: $manifest"
                else
                    log_error "kubectl dry-run failed: $manifest"
                    return 1
                fi
            fi
        else
            log_error "Manifest file not found: $manifest"
            return 1
        fi
    done

    log_success "Kubernetes manifests validation passed"
}

# Validate Docker configurations
validate_docker_configs() {
    log_info "Validating Docker configurations..."

    local docker_compose="$PROJECT_ROOT/infrastructure/docker/docker-compose.yml"

    if [ -f "$docker_compose" ]; then
        if command_exists docker-compose; then
            log_info "Running docker-compose config validation"
            if docker-compose -f "$docker_compose" config >/dev/null 2>&1; then
                log_success "Docker Compose configuration valid"
            else
                log_error "Docker Compose configuration invalid"
                return 1
            fi
        elif command_exists docker && docker compose version >/dev/null 2>&1; then
            log_info "Running docker compose config validation"
            if docker compose -f "$docker_compose" config >/dev/null 2>&1; then
                log_success "Docker Compose configuration valid"
            else
                log_error "Docker Compose configuration invalid"
                return 1
            fi
        else
            log_warn "Docker Compose not available, skipping config validation"
            validate_yaml "$docker_compose" || return 1
        fi
    else
        log_error "Docker Compose file not found"
        return 1
    fi
}

# Validate CI/CD pipelines
validate_ci_cd() {
    log_info "Validating CI/CD pipelines..."

    local github_actions="$PROJECT_ROOT/ci-cd/github-actions.yml"

    if [ -f "$github_actions" ]; then
        validate_yaml "$github_actions" || return 1

        # Check for actionlint if available
        if command_exists actionlint; then
            log_info "Running actionlint validation"
            if actionlint "$github_actions" 2>/dev/null; then
                log_success "GitHub Actions workflow valid"
            else
                log_error "GitHub Actions workflow invalid"
                return 1
            fi
        else
            log_warn "actionlint not available, skipping advanced validation"
        fi
    else
        log_error "GitHub Actions workflow not found"
        return 1
    fi

    log_success "CI/CD validation passed"
}

# Validate deployment scripts
validate_deployment_scripts() {
    log_info "Validating deployment scripts..."

    local scripts=(
        "scripts/deploy-initial.sh"
        "scripts/deploy-update.sh"
        "scripts/health-checks.sh"
        "scripts/init-database.sh"
        "scripts/manage-certificates.sh"
        "scripts/validate-deployment.sh"
    )

    for script in "${scripts[@]}"; do
        if [ -f "$PROJECT_ROOT/$script" ]; then
            validate_bash_script "$PROJECT_ROOT/$script" || return 1
        else
            log_error "Script not found: $script"
            return 1
        fi
    done

    log_success "Deployment scripts validation passed"
}

# Validate security configurations
validate_security_configs() {
    log_info "Validating security configurations..."

    local security_config="$PROJECT_ROOT/infrastructure/security-config.yml"

    if [ -f "$security_config" ]; then
        validate_yaml "$security_config" || return 1
    else
        log_error "Security config file not found"
        return 1
    fi

    # Validate certificates
    log_info "Validating certificates..."
    local cert_dir="$PROJECT_ROOT/certs"

    if [ -d "$cert_dir" ]; then
        if command_exists openssl; then
            # Check CA certificate
            if [ -f "$cert_dir/ca/ca.key" ]; then
                log_info "CA certificate found"
            else
                log_error "CA certificate not found"
                return 1
            fi

            # Check server certificate
            if [ -f "$cert_dir/server/server.ext" ]; then
                log_info "Server certificate found"
            else
                log_error "Server certificate not found"
                return 1
            fi

            # Check device certificates
            if [ -f "$cert_dir/devices/device.ext" ]; then
                log_info "Device certificate found"
            else
                log_error "Device certificate not found"
                return 1
            fi
        else
            log_warn "OpenSSL not available, skipping certificate validation"
        fi
    else
        log_error "Certificates directory not found"
        return 1
    fi

    log_success "Security configurations validation passed"
}

# Validate monitoring configurations
validate_monitoring_configs() {
    log_info "Validating monitoring configurations..."

    local monitoring_configs=(
        "monitoring/prometheus/prometheus.yml"
        "monitoring/prometheus/alert_rules.yml"
        "monitoring/grafana/application-dashboard.json"
        "monitoring/grafana/infrastructure-dashboard.json"
        "monitoring/grafana/security-dashboard.json"
        "monitoring/grafana/system-dashboard.json"
        "monitoring/elk/elasticsearch.yml"
        "monitoring/elk/kibana.yml"
        "monitoring/elk/logstash.conf"
        "monitoring/tracing/jaeger-config.yaml"
        "monitoring/tracing/otel-collector-config.yaml"
    )

    for config in "${monitoring_configs[@]}"; do
        if [ -f "$PROJECT_ROOT/$config" ]; then
            case "${config##*.}" in
                yml|yaml)
                    validate_yaml "$PROJECT_ROOT/$config" || return 1
                    ;;
                json)
                    if command_exists python; then
                        log_info "Validating JSON: $config"
                        if python -c "import json; json.load(open('$PROJECT_ROOT/$config'))" 2>/dev/null; then
                            log_success "JSON valid: $config"
                        else
                            log_error "JSON invalid: $config"
                            return 1
                        fi
                    else
                        log_warn "Python not available, skipping JSON validation for $config"
                    fi
                    ;;
                conf)
                    log_info "Logstash config found: $config"
                    # Basic syntax check - ensure it's not empty
                    if [ -s "$PROJECT_ROOT/$config" ]; then
                        log_success "Logstash config exists: $config"
                    else
                        log_error "Logstash config is empty: $config"
                        return 1
                    fi
                    ;;
            esac
        else
            log_warn "Monitoring config not found: $config"
        fi
    done

    log_success "Monitoring configurations validation passed"
}

# Validate Java services
validate_java_services() {
    log_info "Validating Java services..."

    if ! command_exists mvn; then
        log_error "Maven not found, cannot validate Java services"
        return 1
    fi

    local services=(
        "services/audit-service"
        "services/auth-service"
        "services/gateway"
        "services/hospital-integration-service"
        "services/medical-record-service"
        "services/notification-service"
        "services/patient-service"
        "services/sync-service"
        "common/core"
        "common/events"
        "common/security"
    )

    for service in "${services[@]}"; do
        if [ -d "$PROJECT_ROOT/$service" ] && [ -f "$PROJECT_ROOT/$service/pom.xml" ]; then
            log_info "Validating Maven dependencies for $service"
            cd "$PROJECT_ROOT/$service"
            if mvn dependency:resolve -q >/dev/null 2>&1; then
                log_success "Maven dependencies resolved for $service"
            else
                log_error "Maven dependency resolution failed for $service"
                cd "$PROJECT_ROOT"
                return 1
            fi
            cd "$PROJECT_ROOT"
        else
            log_warn "Service directory or pom.xml not found: $service"
        fi
    done

    log_success "Java services validation passed"
}

# Validate infrastructure readiness
validate_infrastructure_readiness() {
    log_info "Validating infrastructure readiness..."

    # Check required directories
    local required_dirs=(
        "database/schemas"
        "database/seed-data"
        "docs"
        "infrastructure"
        "monitoring"
        "services"
    )

    for dir in "${required_dirs[@]}"; do
        if [ -d "$PROJECT_ROOT/$dir" ]; then
            log_success "Required directory exists: $dir"
        else
            log_error "Required directory missing: $dir"
            return 1
        fi
    done

    # Check required files
    local required_files=(
        "pom.xml"
        "README.md"
        ".env.example"
        ".gitignore"
    )

    for file in "${required_files[@]}"; do
        if [ -f "$PROJECT_ROOT/$file" ]; then
            log_success "Required file exists: $file"
        else
            log_error "Required file missing: $file"
            return 1
        fi
    done

    log_success "Infrastructure readiness validation passed"
}

# Generate validation report
generate_validation_report() {
    log_info "Generating validation report..."

    local report_file="/tmp/deployment-readiness-$(date +%Y%m%d-%H%M%S).txt"

    {
        echo "EMR Deployment Readiness Validation Report"
        echo "=========================================="
        echo "Timestamp: $(date)"
        echo "Project Root: $PROJECT_ROOT"
        echo ""
        echo "Validation Summary:"
        echo "- Kubernetes Manifests: $([ $? -eq 0 ] && echo "PASSED" || echo "FAILED")"
        echo "- Docker Configurations: $([ $? -eq 0 ] && echo "PASSED" || echo "FAILED")"
        echo "- CI/CD Pipelines: $([ $? -eq 0 ] && echo "PASSED" || echo "FAILED")"
        echo "- Deployment Scripts: $([ $? -eq 0 ] && echo "PASSED" || echo "FAILED")"
        echo "- Security Configurations: $([ $? -eq 0 ] && echo "PASSED" || echo "FAILED")"
        echo "- Monitoring Configurations: $([ $? -eq 0 ] && echo "PASSED" || echo "FAILED")"
        echo "- Java Services: $([ $? -eq 0 ] && echo "PASSED" || echo "FAILED")"
        echo "- Infrastructure Readiness: $([ $? -eq 0 ] && echo "PASSED" || echo "FAILED")"
        echo ""
        echo "System Information:"
        echo "- OS: $(uname -s)"
        echo "- Bash Version: $BASH_VERSION"
        echo "- Working Directory: $(pwd)"
    } > "$report_file"

    log_info "Validation report saved to: $report_file"
}

# Main function
main() {
    log_info "Starting EMR deployment readiness validation..."

    local validation_failed=0

    # Run all validations
    validate_infrastructure_readiness || validation_failed=1
    validate_kubernetes_manifests || validation_failed=1
    validate_docker_configs || validation_failed=1
    validate_ci_cd || validation_failed=1
    validate_deployment_scripts || validation_failed=1
    validate_security_configs || validation_failed=1
    validate_monitoring_configs || validation_failed=1
    validate_java_services || validation_failed=1

    generate_validation_report

    if [ $validation_failed -eq 0 ]; then
        log_success "All deployment readiness validations passed successfully!"
        exit 0
    else
        log_error "Some validations failed. Please review the errors above."
        exit 1
    fi
}

# Run main function
main "$@"