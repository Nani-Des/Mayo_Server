#!/bin/bash

# Certificate Management Script for Mayo EMR
# Generates TLS certificates and creates Kubernetes secrets

set -euo pipefail

# Configuration
NAMESPACE="mayo-emr"
CERT_DIR="certs"
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

log_error() {
    echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

# Check if certificates already exist
certificates_exist() {
    if kubectl get secret mayo-tls-secret -n $NAMESPACE >/dev/null 2>&1; then
        log_info "TLS certificates already exist in Kubernetes"
        return 0
    fi
    return 1
}

# Generate CA certificate
generate_ca() {
    log_info "Generating CA certificate..."

    mkdir -p "$CERT_DIR/ca"

    # Generate CA private key
    openssl genrsa -out "$CERT_DIR/ca/ca.key" 4096

    # Generate CA certificate
    openssl req -new -x509 -days 3650 -key "$CERT_DIR/ca/ca.key" -sha256 -out "$CERT_DIR/ca/ca.crt" \
        -subj "/C=GH/ST=Greater Accra/L=Accra/O=Mayo Clinic/OU=EMR/CN=Mayo EMR CA"

    log_success "CA certificate generated"
}

# Generate server certificate
generate_server_cert() {
    log_info "Generating server certificate..."

    mkdir -p "$CERT_DIR/server"

    # Generate server private key
    openssl genrsa -out "$CERT_DIR/server/server.key" 2048

    # Generate certificate signing request
    openssl req -subj "/C=GH/ST=Greater Accra/L=Accra/O=Mayo Clinic/OU=EMR/CN=api.mayo-health.gh" \
        -new -key "$CERT_DIR/server/server.key" -out "$CERT_DIR/server/server.csr"

    # Create server certificate extensions
    cat > "$CERT_DIR/server/server.ext" << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = api.mayo-health.gh
DNS.2 = gateway.mayo-emr.svc.cluster.local
DNS.3 = auth-service.mayo-emr.svc.cluster.local
DNS.4 = patient-service.mayo-emr.svc.cluster.local
DNS.5 = medical-record-service.mayo-emr.svc.cluster.local
DNS.6 = sync-service.mayo-emr.svc.cluster.local
DNS.7 = audit-service.mayo-emr.svc.cluster.local
DNS.8 = notification-service.mayo-emr.svc.cluster.local
DNS.9 = hospital-integration-service.mayo-emr.svc.cluster.local
DNS.10 = localhost
IP.1 = 127.0.0.1
EOF

    # Generate server certificate
    openssl x509 -req -days 365 -in "$CERT_DIR/server/server.csr" \
        -CA "$CERT_DIR/ca/ca.crt" -CAkey "$CERT_DIR/ca/ca.key" \
        -out "$CERT_DIR/server/server.crt" -sha256 -CAcreateserial \
        -extfile "$CERT_DIR/server/server.ext"

    # Create PKCS12 keystore for Java applications
    openssl pkcs12 -export -in "$CERT_DIR/server/server.crt" \
        -inkey "$CERT_DIR/server/server.key" \
        -out "$CERT_DIR/server/keystore.p12" \
        -name "server" -CAfile "$CERT_DIR/ca/ca.crt" \
        -caname "root" -password pass:changeit

    log_success "Server certificate generated"
}

# Generate device certificate template
generate_device_cert() {
    log_info "Generating device certificate template..."

    mkdir -p "$CERT_DIR/devices"

    # Generate device private key
    openssl genrsa -out "$CERT_DIR/devices/device.key" 2048

    # Generate certificate signing request
    openssl req -subj "/C=GH/ST=Greater Accra/L=Accra/O=Mayo Clinic/OU=EMR/CN=device-template" \
        -new -key "$CERT_DIR/devices/device.key" -out "$CERT_DIR/devices/device.csr"

    # Create device certificate extensions
    cat > "$CERT_DIR/devices/device.ext" << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
extendedKeyUsage = clientAuth
EOF

    # Generate device certificate
    openssl x509 -req -days 365 -in "$CERT_DIR/devices/device.csr" \
        -CA "$CERT_DIR/ca/ca.crt" -CAkey "$CERT_DIR/ca/ca.key" \
        -out "$CERT_DIR/devices/device.crt" -sha256 -CAcreateserial \
        -extfile "$CERT_DIR/devices/device.ext"

    log_success "Device certificate template generated"
}

# Create Kubernetes TLS secret
create_tls_secret() {
    log_info "Creating Kubernetes TLS secret..."

    if [ ! -f "$CERT_DIR/server/server.crt" ] || [ ! -f "$CERT_DIR/server/server.key" ]; then
        log_error "Server certificate files not found"
        return 1
    fi

    kubectl create secret tls mayo-tls-secret \
        --cert="$CERT_DIR/server/server.crt" \
        --key="$CERT_DIR/server/server.key" \
        -n $NAMESPACE

    log_success "TLS secret created in Kubernetes"
}

# Create certificate secrets for services
create_cert_secrets() {
    log_info "Creating certificate secrets for services..."

    # CA certificate secret
    kubectl create secret generic mayo-ca-secret \
        --from-file=ca.crt="$CERT_DIR/ca/ca.crt" \
        --from-file=ca.key="$CERT_DIR/ca/ca.key" \
        -n $NAMESPACE

    # Server certificate secret
    kubectl create secret generic mayo-server-cert \
        --from-file=server.crt="$CERT_DIR/server/server.crt" \
        --from-file=server.key="$CERT_DIR/server/server.key" \
        --from-file=keystore.p12="$CERT_DIR/server/keystore.p12" \
        -n $NAMESPACE

    # Device certificate template
    kubectl create secret generic mayo-device-cert \
        --from-file=device.crt="$CERT_DIR/devices/device.crt" \
        --from-file=device.key="$CERT_DIR/devices/device.key" \
        -n $NAMESPACE

    log_success "Certificate secrets created"
}

# Validate certificates
validate_certificates() {
    log_info "Validating certificates..."

    # Check certificate files exist
    local cert_files=(
        "$CERT_DIR/ca/ca.crt"
        "$CERT_DIR/ca/ca.key"
        "$CERT_DIR/server/server.crt"
        "$CERT_DIR/server/server.key"
        "$CERT_DIR/server/keystore.p12"
        "$CERT_DIR/devices/device.crt"
        "$CERT_DIR/devices/device.key"
    )

    for cert_file in "${cert_files[@]}"; do
        if [ ! -f "$cert_file" ]; then
            log_error "Certificate file missing: $cert_file"
            return 1
        fi
    done

    # Validate server certificate
    if ! openssl x509 -in "$CERT_DIR/server/server.crt" -text -noout >/dev/null 2>&1; then
        log_error "Invalid server certificate"
        return 1
    fi

    # Check Kubernetes secrets
    local secrets=("mayo-tls-secret" "mayo-ca-secret" "mayo-server-cert" "mayo-device-cert")
    for secret in "${secrets[@]}"; do
        if ! kubectl get secret $secret -n $NAMESPACE >/dev/null 2>&1; then
            log_error "Kubernetes secret missing: $secret"
            return 1
        fi
    done

    log_success "Certificate validation passed"
}

# Clean up local certificate files (optional)
cleanup_local_certs() {
    log_info "Cleaning up local certificate files..."

    if [ -d "$CERT_DIR" ]; then
        rm -rf "$CERT_DIR"
        log_info "Local certificates cleaned up"
    fi
}

# Main function
main() {
    log_info "Starting certificate management..."

    # Check if certificates already exist
    if certificates_exist; then
        log_info "Certificates already exist, validating..."
        validate_certificates
        log_success "Certificate management completed (existing certificates validated)"
        return 0
    fi

    # Generate certificates
    generate_ca
    generate_server_cert
    generate_device_cert

    # Create Kubernetes secrets
    create_tls_secret
    create_cert_secrets

    # Validate
    validate_certificates

    # Optional cleanup
    # cleanup_local_certs

    log_success "Certificate management completed successfully"
}

# Run main function
main "$@"