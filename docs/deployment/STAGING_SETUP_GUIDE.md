# EMR System Staging Setup Guide

## Overview

This guide provides comprehensive instructions for setting up the Mayo EMR system in a staging environment. The EMR system is a production-grade, offline-first Electronic Medical Record platform supporting Phase 1 (hospital integration, activity tracking, bidirectional sync), Phase 2 (family accounts, advanced compliance), and production deployment readiness.

## Prerequisites

- Docker & Docker Compose (v20.10+)
- Kubernetes cluster (v1.24+) with kubectl
- Helm (v3.9+)
- Git
- OpenSSL for certificate generation
- Access to required external services (see Environment Variables section)

## Architecture Overview

The EMR system consists of 8 microservices:
- **auth-service**: User authentication and device management
- **patient-record-service**: Patient data management
- **sync-service**: Bidirectional sync with conflict resolution
- **hospital-integration-service**: Hospital data integration and activity tracking
- **audit-service**: Compliance logging and reporting
- **notification-service**: Push notifications and alerts
- **gateway**: API Gateway with routing and security
- **common libraries**: Shared utilities (core, security, events)

## Environment Configuration

### 1. Environment Variables Setup

Create a `.env` file in the project root with the following variables:

```bash
# ==============================================
# DATABASE CONFIGURATION
# ==============================================
# PostgreSQL connection details
DB_HOST=staging-postgres.mayo-emr.internal
DB_PORT=5432
DB_NAME=mayo_emr_staging
DB_USER=mayo_app
DB_PASSWORD=<SECURE_PASSWORD_32_CHARS_MIN>
DB_SSL_MODE=require
DB_MAX_CONNECTIONS=50
DB_CONNECTION_TIMEOUT=30000

# Database admin credentials (for migrations only)
DB_ADMIN_USER=mayo_admin
DB_ADMIN_PASSWORD=<SECURE_ADMIN_PASSWORD>

# ==============================================
# REDIS CONFIGURATION
# ==============================================
REDIS_HOST=staging-redis.mayo-emr.internal
REDIS_PORT=6379
REDIS_PASSWORD=<SECURE_REDIS_PASSWORD>
REDIS_SSL=true
REDIS_MAX_CONNECTIONS=100
REDIS_TIMEOUT=5000

# Redis cluster configuration (if using cluster)
REDIS_CLUSTER_ENABLED=false
REDIS_CLUSTER_NODES=staging-redis-1:6379,staging-redis-2:6379

# ==============================================
# KAFKA CONFIGURATION
# ==============================================
KAFKA_BOOTSTRAP_SERVERS=staging-kafka-1.mayo-emr.internal:9093,staging-kafka-2.mayo-emr.internal:9093,staging-kafka-3.mayo-emr.internal:9093
KAFKA_SECURITY_PROTOCOL=SASL_SSL
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="mayo-user" password="<KAFKA_PASSWORD>";
KAFKA_SSL_TRUSTSTORE_LOCATION=/app/certs/kafka-truststore.jks
KAFKA_SSL_TRUSTSTORE_PASSWORD=<TRUSTSTORE_PASSWORD>
KAFKA_SSL_KEYSTORE_LOCATION=/app/certs/kafka-keystore.jks
KAFKA_SSL_KEYSTORE_PASSWORD=<KEYSTORE_PASSWORD>

# Kafka topics (auto-created if not exist)
KAFKA_TOPIC_PATIENT_EVENTS=patient.events
KAFKA_TOPIC_SYNC_EVENTS=sync.events
KAFKA_TOPIC_AUDIT_EVENTS=audit.events
KAFKA_TOPIC_NOTIFICATION_EVENTS=notification.events
KAFKA_TOPIC_COMPLIANCE_EVENTS=compliance.events

# ==============================================
# JWT CONFIGURATION
# ==============================================
# Generate with: openssl rand -base64 64
JWT_SECRET=<BASE64_ENCODED_512_BIT_SECRET>
JWT_ACCESS_TOKEN_EXPIRY=900000
JWT_REFRESH_TOKEN_EXPIRY=604800000
JWT_ISSUER=https://staging.mayo-emr.com
JWT_AUDIENCE=mayo-emr-staging

# ==============================================
# ENCRYPTION CONFIGURATION
# ==============================================
# AES-256 encryption key (generate with: openssl rand -hex 32)
ENCRYPTION_KEY=<64_CHARACTER_HEX_KEY>
ENCRYPTION_ALGORITHM=AES/GCM/NoPadding
ENCRYPTION_KEY_ROTATION_DAYS=90

# ==============================================
# SUPABASE CONFIGURATION (Optional - for prototyping)
# ==============================================
SUPABASE_ENABLED=false
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=<SUPABASE_ANON_KEY>
SUPABASE_SERVICE_ROLE_KEY=<SUPABASE_SERVICE_ROLE_KEY>

# ==============================================
# MINIO OBJECT STORAGE
# ==============================================
MINIO_ENDPOINT=https://staging-minio.mayo-emr.internal:9000
MINIO_ACCESS_KEY=<MINIO_ACCESS_KEY>
MINIO_SECRET_KEY=<MINIO_SECRET_KEY>
MINIO_BUCKET=mayo-medical-documents-staging
MINIO_REGION=us-east-1
MINIO_SSL=true

# ==============================================
# HASHICORP VAULT CONFIGURATION
# ==============================================
VAULT_ENABLED=true
VAULT_ADDR=https://staging-vault.mayo-emr.internal:8200
VAULT_TOKEN=<VAULT_TOKEN>
VAULT_PATH=database/creds/emr-app
VAULT_TLS_SKIP_VERIFY=false
VAULT_CERT_PATH=/app/certs/vault.crt

# ==============================================
# SERVICE PORTS AND ENDPOINTS
# ==============================================
GATEWAY_PORT=8080
GATEWAY_HOST=staging-gateway.mayo-emr.internal
AUTH_SERVICE_PORT=8081
AUTH_SERVICE_HOST=staging-auth.mayo-emr.internal
PATIENT_SERVICE_PORT=8082
PATIENT_SERVICE_HOST=staging-patient.mayo-emr.internal
MEDICAL_RECORD_SERVICE_PORT=8083
MEDICAL_RECORD_SERVICE_HOST=staging-record.mayo-emr.internal
HOSPITAL_SERVICE_PORT=8084
HOSPITAL_SERVICE_HOST=staging-hospital.mayo-emr.internal
SYNC_SERVICE_PORT=8085
SYNC_SERVICE_HOST=staging-sync.mayo-emr.internal
AUDIT_SERVICE_PORT=8086
AUDIT_SERVICE_HOST=staging-audit.mayo-emr.internal
NOTIFICATION_SERVICE_PORT=8087
NOTIFICATION_SERVICE_HOST=staging-notification.mayo-emr.internal

# ==============================================
# EXTERNAL API INTEGRATIONS
# ==============================================
# Firebase Cloud Messaging (for push notifications)
FCM_SERVER_KEY=<FCM_SERVER_KEY>
FCM_PROJECT_ID=mayo-emr-staging

# Apple Push Notification Service
APNS_CERTIFICATE_PATH=/app/certs/apns-cert.p12
APNS_CERTIFICATE_PASSWORD=<APNS_CERT_PASSWORD>
APNS_KEY_ID=<APNS_KEY_ID>
APNS_TEAM_ID=<APNS_TEAM_ID>
APNS_BUNDLE_ID=com.mayo.emr.staging

# Ghana Card API (for patient identification)
GHANA_CARD_API_URL=https://api.ghana.gov/cards/v1
GHANA_CARD_API_KEY=<GHANA_CARD_API_KEY>
GHANA_CARD_CLIENT_ID=<GHANA_CARD_CLIENT_ID>
GHANA_CARD_CLIENT_SECRET=<GHANA_CARD_CLIENT_SECRET>

# ==============================================
# HOSPITAL INTEGRATION
# ==============================================
# HL7 Interface
HL7_LISTENER_PORT=2575
HL7_FACILITY_ID=MAYO_STAGING
HL7_APPLICATION_ID=EMR_STAGING

# FHIR Server
FHIR_SERVER_URL=https://staging-fhir.mayo-emr.internal/fhir
FHIR_AUTH_TYPE=oauth2
FHIR_CLIENT_ID=<FHIR_CLIENT_ID>
FHIR_CLIENT_SECRET=<FHIR_CLIENT_SECRET>

# DICOM Configuration
DICOM_AE_TITLE=MAYO_EMR_STAGING
DICOM_PORT=104
DICOM_STORAGE_PATH=/app/dicom-storage

# ==============================================
# COMPLIANCE AND AUDIT
# ==============================================
COMPLIANCE_FRAMEWORKS=HIPAA,GDPR,GHANA_DPA
AUDIT_RETENTION_DAYS=2555
AUDIT_ENCRYPTION_ENABLED=true
AUDIT_IMMUTABLE_LOGS=true

# HIPAA Configuration
HIPAA_BUSINESS_ASSOCIATE_AGREEMENT=true
HIPAA_DATA_ENCRYPTION=true
HIPAA_AUDIT_LOGGING=true

# GDPR Configuration
GDPR_DATA_PROCESSING=true
GDPR_CONSENT_MANAGEMENT=true
GDPR_RIGHT_OF_ACCESS=true

# ==============================================
# MONITORING AND LOGGING
# ==============================================
# ELK Stack
ELASTICSEARCH_HOSTS=https://staging-elasticsearch.mayo-emr.internal:9200
ELASTICSEARCH_USERNAME=elastic
ELASTICSEARCH_PASSWORD=<ELASTIC_PASSWORD>
ELASTICSEARCH_SSL=true

# Prometheus
PROMETHEUS_PUSHGATEWAY_URL=http://staging-prometheus-pushgateway.mayo-emr.internal:9091

# Grafana
GRAFANA_URL=https://staging-grafana.mayo-emr.internal
GRAFANA_API_KEY=<GRAFANA_API_KEY>

# ==============================================
# LOGGING CONFIGURATION
# ==============================================
LOG_LEVEL=INFO
LOG_FORMAT=json
LOG_FILE_PATH=/app/logs
LOG_MAX_SIZE=100MB
LOG_MAX_FILES=30

# Structured logging
LOG_INCLUDE_TRACE_ID=true
LOG_INCLUDE_USER_ID=true
LOG_SENSITIVE_DATA_MASKING=true

# ==============================================
# SECURITY CONFIGURATION
# ==============================================
# TLS/SSL
TLS_CERT_PATH=/app/certs/server.crt
TLS_KEY_PATH=/app/certs/server.key
TLS_CA_CERT_PATH=/app/certs/ca.crt

# Certificate Authority
CA_CERT_PATH=/app/certs/ca.crt
CA_KEY_PATH=/app/certs/ca.key

# Security Headers
SECURITY_CSP=default-src 'self'; script-src 'self' 'unsafe-inline'
SECURITY_HSTS_MAX_AGE=31536000
SECURITY_X_FRAME_OPTIONS=DENY

# ==============================================
# RATE LIMITING
# ==============================================
RATE_LIMIT_REQUESTS_PER_MINUTE=1000
RATE_LIMIT_BURST_SIZE=2000
RATE_LIMIT_WINDOW_SECONDS=60

# ==============================================
# CACHE CONFIGURATION
# ==============================================
CACHE_TTL_SECONDS=3600
CACHE_MAX_SIZE_MB=512
CACHE_REDIS_PREFIX=mayo:staging:

# ==============================================
# BACKUP CONFIGURATION
# ==============================================
BACKUP_ENABLED=true
BACKUP_SCHEDULE=0 2 * * *
BACKUP_RETENTION_DAYS=30
BACKUP_ENCRYPTION_KEY=<BACKUP_ENCRYPTION_KEY>
BACKUP_S3_BUCKET=mayo-emr-staging-backups
BACKUP_S3_REGION=us-east-1

# ==============================================
# ENVIRONMENT METADATA
# ==============================================
ENVIRONMENT=staging
VERSION=2.0.0
BUILD_NUMBER=<BUILD_NUMBER>
DEPLOYMENT_TIMESTAMP=<DEPLOYMENT_TIMESTAMP>
REGION=us-east-1
AVAILABILITY_ZONE=us-east-1a
```

### 2. Obtaining Environment Variable Values

#### Database Credentials
- **Source**: Team infrastructure documentation or HashiCorp Vault
- **Location**: `infrastructure/secrets/database-credentials.md`
- **Generation**: Use password generator with 32+ characters, mixed case, numbers, symbols
- **Security**: Never commit to version control; rotate quarterly

#### JWT Secret
- **Generation**: `openssl rand -base64 64`
- **Requirements**: 512-bit (64 bytes) base64 encoded
- **Storage**: HashiCorp Vault at `secret/emr/staging/jwt`
- **Rotation**: Every 90 days

#### Encryption Key
- **Generation**: `openssl rand -hex 32`
- **Requirements**: 256-bit (32 bytes) hex encoded
- **Storage**: AWS KMS or HashiCorp Vault
- **Rotation**: Automated every 90 days

#### External API Keys
- **Firebase/APNs**: Obtained from respective developer consoles
- **Ghana Card API**: Government API portal
- **MinIO**: Generated during MinIO setup
- **Grafana**: Generated in Grafana admin panel

#### Certificates
- **Generation**: Use organization's CA or Let's Encrypt for staging
- **Storage**: Kubernetes secrets or HashiCorp Vault
- **Validation**: Ensure proper chain of trust

## Infrastructure Setup

### 1. Kubernetes Cluster Setup

```bash
# Create namespace
kubectl create namespace mayo-emr-staging

# Apply RBAC
kubectl apply -f infrastructure/kubernetes/rbac.yml

# Create secrets
kubectl create secret generic emr-secrets \
  --from-env-file=.env \
  --namespace=mayo-emr-staging

# Create configmaps
kubectl create configmap emr-config \
  --from-file=config/application-staging.yml \
  --namespace=mayo-emr-staging
```

### 2. Database Setup

```bash
# Deploy PostgreSQL
helm install postgres bitnami/postgresql \
  --namespace=mayo-emr-staging \
  --set auth.database=mayo_emr_staging \
  --set auth.username=mayo_app \
  --set persistence.size=50Gi

# Run migrations
kubectl run migration-job \
  --image=mayo/emr-migration:latest \
  --env-from=secret/emr-secrets \
  --restart=Never
```

### 3. Message Queue Setup

```bash
# Deploy Kafka
helm install kafka bitnami/kafka \
  --namespace=mayo-emr-staging \
  --set replicaCount=3 \
  --set persistence.size=100Gi

# Create topics
kubectl run kafka-setup \
  --image=bitnami/kafka:latest \
  --command -- \
  kafka-topics.sh --create --topic patient.events --bootstrap-server kafka:9092
```

### 4. Monitoring Stack

```bash
# Deploy Prometheus
helm install prometheus prometheus-community/prometheus \
  --namespace=monitoring

# Deploy Grafana
helm install grafana grafana/grafana \
  --namespace=monitoring \
  --set adminPassword='<SECURE_PASSWORD>'

# Deploy ELK Stack
helm install elasticsearch elastic/elasticsearch \
  --namespace=monitoring
```

## Deployment Scripts

### 1. Initial Deployment

```bash
#!/bin/bash
# scripts/deploy-staging.sh

# Validate environment
./scripts/validate-deployment.sh

# Build and push images
docker build -t mayo/emr-gateway:staging ./gateway
docker push mayo/emr-gateway:staging

# Deploy to Kubernetes
kubectl apply -f infrastructure/kubernetes/staging/

# Wait for rollout
kubectl rollout status deployment/emr-gateway -n mayo-emr-staging

# Run health checks
curl -f https://staging.mayo-emr.com/health
```

### 2. Service Deployment

```bash
#!/bin/bash
# scripts/deploy-service.sh

SERVICE_NAME=$1
ENVIRONMENT=staging

# Build service
./mvnw clean package -DskipTests -pl services/${SERVICE_NAME}

# Build Docker image
docker build -t mayo/${SERVICE_NAME}:${ENVIRONMENT} services/${SERVICE_NAME}

# Deploy to Kubernetes
helm upgrade --install ${SERVICE_NAME} ./infrastructure/helm/${SERVICE_NAME} \
  --namespace=mayo-emr-${ENVIRONMENT} \
  --set image.tag=${ENVIRONMENT}
```

### 3. Database Migration

```bash
#!/bin/bash
# scripts/run-migrations.sh

# Run Flyway migrations
kubectl run migration \
  --image=mayo/emr-migration:latest \
  --env-from=secret/emr-secrets \
  --restart=Never \
  --command -- ./flyway migrate
```

## CI/CD Pipeline Configuration

### GitHub Actions Workflow

```yaml
# .github/workflows/staging-deployment.yml
name: Staging Deployment

on:
  push:
    branches: [staging]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run Tests
        run: ./mvnw test

  security-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Security Scan
        uses: snyk/actions/maven@master
        env:
          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}

  build-and-push:
    needs: [test, security-scan]
    runs-on: ubuntu-latest
    steps:
      - name: Build and Push
        run: |
          docker build -t mayo/emr:${{ github.sha }} .
          docker push mayo/emr:${{ github.sha }}

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Staging
        run: |
          kubectl set image deployment/emr-gateway emr-gateway=mayo/emr:${{ github.sha }}
          kubectl rollout status deployment/emr-gateway
```

## Monitoring and Alerting Setup

### Prometheus Configuration

```yaml
# infrastructure/monitoring/prometheus.yml
global:
  scrape_interval: 15s

rule_files:
  - alert_rules.yml

scrape_configs:
  - job_name: 'emr-gateway'
    static_configs:
      - targets: ['staging-gateway.mayo-emr.internal:8080']
    metrics_path: '/actuator/prometheus'

  - job_name: 'emr-services'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names: ['mayo-emr-staging']
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
```

### Alert Rules

```yaml
# infrastructure/monitoring/alert_rules.yml
groups:
  - name: emr_alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"

      - alert: DatabaseConnectionIssues
        expr: pg_stat_activity_count{datname="mayo_emr_staging"} < 1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Database connection issues"
```

### Grafana Dashboards

Import the following dashboards:
- EMR System Overview (`infrastructure/monitoring/grafana/emr-overview.json`)
- Security Monitoring (`infrastructure/monitoring/grafana/security-dashboard.json`)
- Performance Metrics (`infrastructure/monitoring/grafana/performance-dashboard.json`)

## Validation and Testing

### Health Checks

```bash
# Check all services
curl -f https://staging.mayo-emr.com/health

# Check database connectivity
kubectl exec -it postgres-0 -- psql -U mayo_app -d mayo_emr_staging -c "SELECT 1"

# Check Kafka topics
kubectl exec -it kafka-0 -- kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Functional Testing

```bash
# Test user registration
curl -X POST https://staging.mayo-emr.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@staging.com","password":"TestPass123!","fullName":"Test User","userType":"PATIENT","deviceType":"MOBILE"}'

# Test patient record creation
curl -X POST https://staging.mayo-emr.com/api/patients \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","dateOfBirth":"1990-01-01"}'
```

## Security Considerations

### Certificate Management
- Use staging certificates from Let's Encrypt or internal CA
- Rotate certificates every 90 days
- Store certificates in Kubernetes secrets

### Access Control
- Implement RBAC for staging environment
- Use separate service accounts for CI/CD
- Enable audit logging for all access

### Data Protection
- Encrypt all sensitive data at rest and in transit
- Use data classification labels for PHI data
- Implement automated data retention policies

### Network Security
- Use internal load balancers for service communication
- Implement network policies to restrict pod-to-pod communication
- Enable mutual TLS for service mesh communication

## Cross-References to Documentation

### Phase 1 Features
- **Hospital Integration**: See `docs/api/Hospital_Integration_Service_API.yaml` and `docs/architecture/Service_Interaction_Diagrams.md`
- **Activity Tracking**: Reference `docs/api/Audit_Service_API.yaml` for activity logging endpoints
- **Bidirectional Sync**: See `docs/api/Sync_Service_API.yaml` and conflict resolution documentation
- **Enhanced Security**: Refer to `docs/security/Security_Guidelines.md` for implementation details

### Phase 2 Features
- **Family Account Management**: See `docs/api/Auth_Service_API.yaml` for family-related endpoints
- **GDPR/HIPAA Automation**: Reference `docs/api/Audit_Service_API.yaml` for compliance reporting
- **Offline-First Capabilities**: See `docs/sync-engine/` documentation for sync protocols

### Production Deployment Readiness
- **Infrastructure**: Refer to `EMR_Backend_Architecture_Roadmap.md` for scaling considerations
- **Monitoring**: See `docs/security/Security_Guidelines.md` for security monitoring
- **Compliance**: Reference `docs/database/Database_Schema_Documentation.md` for data retention

## Troubleshooting

### Common Issues

#### Database Connection Failures
```bash
# Check database connectivity
kubectl exec -it postgres-0 -n mayo-emr-staging -- psql -U mayo_app -d mayo_emr_staging -c "SELECT version();"

# Verify environment variables
kubectl get secret emr-secrets -n mayo-emr-staging -o yaml
```

#### Service Startup Failures
```bash
# Check pod logs
kubectl logs -f deployment/emr-gateway -n mayo-emr-staging

# Check service health
kubectl describe pod <pod-name> -n mayo-emr-staging
```

#### Kafka Connection Issues
```bash
# Test Kafka connectivity
kubectl exec -it kafka-0 -n mayo-emr-staging -- kafka-console-producer.sh --topic test --bootstrap-server localhost:9092

# Check topic creation
kubectl exec -it kafka-0 -n mayo-emr-staging -- kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Performance Tuning

#### Database Optimization
- Monitor slow queries using pg_stat_statements
- Implement proper indexing based on query patterns
- Configure connection pooling with PgBouncer

#### Cache Optimization
- Monitor Redis memory usage and hit rates
- Implement cache warming strategies
- Configure appropriate TTL values

#### Service Scaling
- Set up horizontal pod autoscaling based on CPU/memory usage
- Implement circuit breakers for external service calls
- Configure proper resource limits and requests

## Backup and Recovery

### Database Backups
```bash
# Manual backup
kubectl exec postgres-0 -n mayo-emr-staging -- pg_dump -U mayo_app mayo_emr_staging > backup.sql

# Automated backups using cron job
kubectl apply -f infrastructure/kubernetes/backup-cronjob.yml
```

### Configuration Backups
- Backup Kubernetes manifests and Helm charts
- Store environment variables securely in HashiCorp Vault
- Document infrastructure-as-code changes

### Disaster Recovery
- Implement multi-region deployment for high availability
- Set up automated failover procedures
- Test recovery procedures regularly

## Compliance Validation

### HIPAA Compliance Checklist
- [ ] Data encryption at rest and in transit
- [ ] Access logging for all PHI interactions
- [ ] Business Associate Agreements with third parties
- [ ] Incident response procedures documented
- [ ] Regular security assessments conducted

### GDPR Compliance Checklist
- [ ] Data processing inventory maintained
- [ ] Consent management implemented
- [ ] Data subject rights procedures documented
- [ ] Data Protection Impact Assessment completed
- [ ] Breach notification procedures in place

### SOC 2 Compliance Checklist
- [ ] Security controls documented and tested
- [ ] Change management procedures implemented
- [ ] Incident response plan tested annually
- [ ] Third-party risk assessments completed

## Support and Maintenance

### Regular Maintenance Tasks
- **Weekly**: Review error logs and performance metrics
- **Monthly**: Update dependencies and security patches
- **Quarterly**: Conduct security assessments and penetration testing
- **Annually**: Review and update compliance procedures

### Contact Information
- **Development Team**: dev@mayo-emr.com
- **Infrastructure Team**: infra@mayo-emr.com
- **Security Team**: security@mayo-emr.com
- **Compliance Team**: compliance@mayo-emr.com

### Emergency Contacts
- **On-call Engineer**: +1-555-EMR-ONCALL
- **Security Incident Response**: +1-555-EMR-SECURITY
- **Infrastructure Emergency**: +1-555-EMR-INFRA

---

*This guide is based on the EMR_Backend_Architecture_Roadmap.md and incorporates all Phase 1, Phase 2, and production deployment features. For detailed API specifications, refer to the `docs/api/` directory. For security guidelines, see `docs/security/Security_Guidelines.md`.*
- Implement data masking for logs