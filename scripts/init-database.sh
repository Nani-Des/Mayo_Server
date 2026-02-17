#!/bin/bash

# Database Initialization Script for Mayo EMR
# Initializes PostgreSQL database with schemas, tables, and seed data

set -euo pipefail

# Configuration
NAMESPACE="mayo-emr"
DB_HOST="mayo-postgres.$NAMESPACE.svc.cluster.local"
DB_PORT="5433"
DB_NAME="mayo_db"
DB_USER="mayo"
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

# Get database password from secret
get_db_password() {
    kubectl get secret mayo-db-secret -n $NAMESPACE -o jsonpath='{.data.mayo-password}' | base64 -d
}

# Wait for database to be ready
wait_for_db() {
    log_info "Waiting for database to be ready..."

    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if kubectl exec -n $NAMESPACE mayo-postgres-0 -- pg_isready -h localhost -U $DB_USER >/dev/null 2>&1; then
            log_success "Database is ready"
            return 0
        fi

        log_info "Attempt $attempt/$max_attempts: Database not ready yet, waiting..."
        sleep 10
        ((attempt++))
    done

    log_error "Database failed to become ready after $max_attempts attempts"
    return 1
}

# Execute SQL file
execute_sql_file() {
    local sql_file="$1"
    local description="$2"

    log_info "Executing $description..."

    if [ ! -f "$sql_file" ]; then
        log_error "SQL file not found: $sql_file"
        return 1
    fi

    local password=$(get_db_password)

    # Use kubectl exec to run psql
    kubectl exec -n $NAMESPACE mayo-postgres-0 -- bash -c "
        export PGPASSWORD='$password'
        psql -h localhost -U $DB_USER -d $DB_NAME -f /tmp/$(basename "$sql_file") < /tmp/$(basename "$sql_file")
    " || {
        log_error "Failed to execute $description"
        return 1
    }

    log_success "$description executed successfully"
}

# Copy SQL file to pod
copy_sql_to_pod() {
    local sql_file="$1"

    log_info "Copying SQL file to database pod..."
    kubectl cp "$sql_file" $NAMESPACE/mayo-postgres-0:/tmp/$(basename "$sql_file") || {
        log_error "Failed to copy SQL file to pod"
        return 1
    }
}

# Initialize database schemas
init_schemas() {
    log_info "Initializing database schemas..."

    local init_sql="$PROJECT_ROOT/database/schemas/init.sql"

    if [ ! -f "$init_sql" ]; then
        log_error "Database initialization SQL file not found: $init_sql"
        return 1
    fi

    copy_sql_to_pod "$init_sql"
    execute_sql_file "$init_sql" "database schema initialization"

    # Clean up
    kubectl exec -n $NAMESPACE mayo-postgres-0 -- rm -f /tmp/$(basename "$init_sql")
}

# Create database users and permissions
setup_database_users() {
    log_info "Setting up database users and permissions..."

    local password=$(get_db_password)

    # Create SQL for user setup
    cat > /tmp/setup-users.sql << EOF
-- Create application user if not exists
DO \$\$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$DB_USER') THEN
      CREATE USER $DB_USER WITH PASSWORD '$password';
   END IF;
END
\$\$;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;
GRANT ALL ON SCHEMA auth, patient, medical_record, hospital, sync, audit TO $DB_USER;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA auth, patient, medical_record, hospital, sync, audit TO $DB_USER;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA auth, patient, medical_record, hospital, sync, audit TO $DB_USER;

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA auth GRANT ALL ON TABLES TO $DB_USER;
ALTER DEFAULT PRIVILEGES IN SCHEMA patient GRANT ALL ON TABLES TO $DB_USER;
ALTER DEFAULT PRIVILEGES IN SCHEMA medical_record GRANT ALL ON TABLES TO $DB_USER;
ALTER DEFAULT PRIVILEGES IN SCHEMA hospital GRANT ALL ON TABLES TO $DB_USER;
ALTER DEFAULT PRIVILEGES IN SCHEMA sync GRANT ALL ON TABLES TO $DB_USER;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT ALL ON TABLES TO $DB_USER;
EOF

    copy_sql_to_pod "/tmp/setup-users.sql"
    execute_sql_file "/tmp/setup-users.sql" "database user setup"

    # Clean up
    rm -f /tmp/setup-users.sql
    kubectl exec -n $NAMESPACE mayo-postgres-0 -- rm -f /tmp/setup-users.sql
}

# Seed initial data
seed_initial_data() {
    log_info "Seeding initial data..."

    # Check if seed data directory exists and has files
    if [ -d "$PROJECT_ROOT/database/seed-data" ] && [ "$(ls -A $PROJECT_ROOT/database/seed-data)" ]; then
        for sql_file in $PROJECT_ROOT/database/seed-data/*.sql; do
            if [ -f "$sql_file" ]; then
                copy_sql_to_pod "$sql_file"
                execute_sql_file "$sql_file" "seed data $(basename "$sql_file")"
                kubectl exec -n $NAMESPACE mayo-postgres-0 -- rm -f /tmp/$(basename "$sql_file")
            fi
        done
    else
        log_info "No seed data files found, skipping..."
    fi
}

# Run database migrations (if using Flyway or Liquibase)
run_migrations() {
    log_info "Running database migrations..."

    # This would be handled by the application services with JPA/flyway
    # But we can check if tables exist
    local password=$(get_db_password)

    kubectl exec -n $NAMESPACE mayo-postgres-0 -- bash -c "
        export PGPASSWORD='$password'
        psql -h localhost -U $DB_USER -d $DB_NAME -c '\dt' | grep -q 'No relations found' && echo 'No tables found - migrations will be handled by services' || echo 'Tables exist'
    "
}

# Validate database setup
validate_database() {
    log_info "Validating database setup..."

    local password=$(get_db_password)

    # Check schemas
    local schemas=$(kubectl exec -n $NAMESPACE mayo-postgres-0 -- bash -c "
        export PGPASSWORD='$password'
        psql -h localhost -U $DB_USER -d $DB_NAME -c \"SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('auth', 'patient', 'medical_record', 'hospital', 'sync', 'audit');\" -t
    ")

    local expected_schemas=("auth" "patient" "medical_record" "hospital" "sync" "audit")
    local missing_schemas=()

    for schema in "${expected_schemas[@]}"; do
        if ! echo "$schemas" | grep -q "$schema"; then
            missing_schemas+=("$schema")
        fi
    done

    if [ ${#missing_schemas[@]} -gt 0 ]; then
        log_error "Missing schemas: ${missing_schemas[*]}"
        return 1
    fi

    # Check enum types
    local enums=$(kubectl exec -n $NAMESPACE mayo-postgres-0 -- bash -c "
        export PGPASSWORD='$password'
        psql -h localhost -U $DB_USER -d $DB_NAME -c \"SELECT typname FROM pg_type WHERE typname IN ('user_type', 'device_type', 'device_status', 'gender', 'blood_type', 'clinical_status', 'medication_status', 'employment_status', 'sync_status', 'audit_action');\" -t
    ")

    local expected_enums=("user_type" "device_type" "device_status" "gender" "blood_type" "clinical_status" "medication_status" "employment_status" "sync_status" "audit_action")
    local missing_enums=()

    for enum in "${expected_enums[@]}"; do
        if ! echo "$enums" | grep -q "$enum"; then
            missing_enums+=("$enum")
        fi
    done

    if [ ${#missing_enums[@]} -gt 0 ]; then
        log_error "Missing enum types: ${missing_enums[*]}"
        return 1
    fi

    log_success "Database validation passed"
}

# Main function
main() {
    log_info "Starting database initialization..."

    wait_for_db
    init_schemas
    setup_database_users
    seed_initial_data
    run_migrations
    validate_database

    log_success "Database initialization completed successfully"
}

# Run main function
main "$@"