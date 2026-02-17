# Phase 2 Infrastructure Deployment and Operations Guide

## Overview

This document provides comprehensive guidance for deploying and operating the Mayo EMR Phase 2 infrastructure, including family accounts, audit services, notifications, and CRDT synchronization across national-scale operations.

## Architecture Overview

### Multi-Region Infrastructure

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Ghana Primary │    │   EU Secondary  │    │   US Secondary  │
│   Region        │    │   Region        │    │   Region        │
├─────────────────┤    ├─────────────────┤    ├─────────────────┤
│ • API Gateway   │    │ • API Gateway   │    │ • API Gateway   │
│ • Auth Service  │    │ • Auth Service  │    │ • Auth Service  │
│ • Patient Svc   │◄──►│ • Patient Svc   │◄──►│ • Patient Svc   │
│ • Sync Service  │    │ • Sync Service  │    │ • Sync Service  │
│ • Audit Service │    │ • Audit Service │    │ • Audit Service │
│ • Notification  │    │ • Notification  │    │ • Notification  │
│ • PostgreSQL    │    │ • PostgreSQL    │    │ • PostgreSQL    │
│ • Redis         │    │ • Redis         │    │ • Redis         │
│ • Kafka         │    │ • Kafka         │    │ • Kafka         │
│ • Elasticsearch │    │ • Elasticsearch │    │ • Elasticsearch │
│ • MinIO         │    │ • MinIO         │    │ • MinIO         │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                        │                        │
         └────────────────────────┴────────────────────────┘
                          Global Load Balancer
```

## Prerequisites

### Cloud Infrastructure Requirements

#### Primary Region (Ghana)
- **Provider**: AWS/GCP/Azure Ghana region
- **Compute**: 50+ EC2 instances (c5.large to c5.4xlarge)
- **Storage**: 10TB+ EBS/GCS/Azure Disk
- **Network**: VPC with multi-AZ setup
- **Database**: RDS PostgreSQL (db.r5.large to db.r5.4xlarge)

#### Secondary Regions (EU/US)
- **Provider**: AWS EU-West-1 / US-East-1
- **Compute**: 25+ EC2 instances per region
- **Storage**: 5TB+ regional storage
- **Network**: VPC peering with primary region

### Software Requirements

#### Container Orchestration
- **Kubernetes**: v1.24+
- **Helm**: v3.9+
- **Istio**: v1.16+ (service mesh)

#### Infrastructure Tools
- **Terraform**: v1.3+
- **Ansible**: v2.12+
- **Prometheus**: v2.36+
- **Grafana**: v9.0+

#### Security Tools
- **Vault**: HashiCorp Vault for secrets management
- **Cert-Manager**: Let's Encrypt certificate automation
- **Falco**: Runtime security monitoring
- **Trivy**: Container vulnerability scanning

## Deployment Strategy

### Blue-Green Deployment

```yaml
# Blue-Green deployment configuration
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: mayo-emr-blue
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/mayo/emr-infrastructure
    targetRevision: HEAD
    path: charts/mayo-emr
  destination:
    server: https://kubernetes.default.svc
    namespace: mayo-blue
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

### Canary Deployment

```yaml
# Istio VirtualService for canary deployment
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: mayo-emr-canary
spec:
  http:
  - route:
    - destination:
        host: mayo-emr-blue
      weight: 90
    - destination:
        host: mayo-emr-green
      weight: 10
```

## Service Configuration

### Environment Variables

#### Auth Service Configuration
```yaml
env:
- name: SPRING_PROFILES_ACTIVE
  value: "production"
- name: DB_HOST
  valueFrom:
    secretKeyRef:
      name: db-credentials
      key: host
- name: JWT_SECRET
  valueFrom:
    secretKeyRef:
      name: jwt-secrets
      key: secret
- name: FAMILY_MAX_MEMBERS
  value: "20"
- name: OWNERSHIP_TRANSFER_EXPIRY_HOURS
  value: "168"
```

#### Audit Service Configuration
```yaml
env:
- name: ELASTICSEARCH_HOSTS
  value: "elasticsearch:9200"
- name: AUDIT_RETENTION_DAYS
  value: "2555"
- name: COMPLIANCE_RULES_PATH
  value: "/app/config/compliance-rules.json"
- name: MINIO_ENDPOINT
  value: "https://minio.mayo.com"
```

#### Notification Service Configuration
```yaml
env:
- name: FCM_PROJECT_ID
  value: "mayo-emr"
- name: APNS_KEY_ID
  valueFrom:
    secretKeyRef:
      name: apns-credentials
      key: key-id
- name: SENDGRID_API_KEY
  valueFrom:
    secretKeyRef:
      name: sendgrid-credentials
      key: api-key
- name: NOTIFICATION_RATE_LIMIT
  value: "1000"
```

#### Sync Service Configuration
```yaml
env:
- name: CRDT_REPLICA_ID
  valueFrom:
    fieldRef:
      fieldPath: metadata.name
- name: CONFLICT_RESOLUTION_STRATEGY
  value: "LAST_WRITE_WINS"
- name: SYNC_BATCH_SIZE
  value: "100"
- name: VECTOR_CLOCK_PRUNE_THRESHOLD
  value: "1000"
```

## Database Configuration

### PostgreSQL Setup

#### Primary Database
```sql
-- Create databases
CREATE DATABASE mayo_auth;
CREATE DATABASE mayo_patient;
CREATE DATABASE mayo_sync;
CREATE DATABASE mayo_audit;
CREATE DATABASE mayo_notification;

-- Create users with appropriate permissions
CREATE USER mayo_auth_user WITH ENCRYPTED PASSWORD 'secure_password';
GRANT ALL PRIVILEGES ON DATABASE mayo_auth TO mayo_auth_user;

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
```

#### Replication Configuration
```postgresql.conf
# Primary server settings
wal_level = replica
max_wal_senders = 10
max_replication_slots = 10

# Secondary server settings
primary_conninfo = 'host=primary-host port=5432 user=replication_user'
hot_standby = on
```

### Elasticsearch Configuration

#### Cluster Setup
```yaml
cluster.name: mayo-audit-cluster
node.name: audit-node-1
path.data: /var/lib/elasticsearch/data
path.logs: /var/log/elasticsearch

network.host: 0.0.0.0
http.port: 9200

discovery.seed_hosts: ["elasticsearch-1:9300", "elasticsearch-2:9300"]
cluster.initial_master_nodes: ["elasticsearch-1", "elasticsearch-2"]

xpack.security.enabled: true
xpack.security.transport.ssl.enabled: true
```

#### Index Templates
```json
{
  "index_patterns": ["audit-events-*"],
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "refresh_interval": "30s"
  },
  "mappings": {
    "properties": {
      "eventId": { "type": "keyword" },
      "timestamp": { "type": "date" },
      "userId": { "type": "keyword" },
      "action": { "type": "keyword" },
      "resourceType": { "type": "keyword" },
      "severity": { "type": "keyword" }
    }
  }
}
```

## Monitoring and Observability

### Prometheus Metrics

#### Service Metrics
```yaml
# Prometheus scrape configuration
scrape_configs:
  - job_name: 'mayo-services'
    static_configs:
      - targets: ['auth-service:8080', 'patient-service:8082', 'sync-service:8084']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

  - job_name: 'audit-service'
    static_configs:
      - targets: ['audit-service:8085']
    metrics_path: '/actuator/prometheus'

  - job_name: 'notification-service'
    static_configs:
      - targets: ['notification-service:8086']
    metrics_path: '/actuator/prometheus'
```

#### Custom Metrics
```java
// Family Service Metrics
@Bean
public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
    return registry -> registry.config()
        .commonTags("service", "family-service")
        .commonTags("region", System.getenv("REGION"));
}

// Audit Service Metrics
private final Counter auditEventsCounter;
private final Counter complianceViolationsCounter;

public AuditMetrics(MeterRegistry registry) {
    this.auditEventsCounter = Counter.builder("audit.events.total")
        .description("Total number of audit events processed")
        .register(registry);

    this.complianceViolationsCounter = Counter.builder("compliance.violations.total")
        .description("Total number of compliance violations detected")
        .register(registry);
}
```

### Grafana Dashboards

#### System Overview Dashboard
- Service health status
- Response times and throughput
- Error rates and alerts
- Resource utilization (CPU, memory, disk)

#### Business Metrics Dashboard
- Family account creation rate
- Ownership transfer success rate
- Audit event volume
- Notification delivery rates
- Sync operation performance

#### Security Dashboard
- Failed authentication attempts
- Suspicious activity alerts
- Compliance violation trends
- Data access patterns

## Backup and Disaster Recovery

### Database Backup Strategy

#### Automated Backups
```bash
#!/bin/bash
# Daily backup script
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups"

# PostgreSQL backup
pg_dump -h $DB_HOST -U $DB_USER -d mayo_auth | gzip > $BACKUP_DIR/auth_$DATE.sql.gz
pg_dump -h $DB_HOST -U $DB_USER -d mayo_patient | gzip > $BACKUP_DIR/patient_$DATE.sql.gz
pg_dump -h $DB_HOST -U $DB_USER -d mayo_audit | gzip > $BACKUP_DIR/audit_$DATE.sql.gz

# Elasticsearch snapshot
curl -X PUT "elasticsearch:9200/_snapshot/audit_backup/snapshot_$DATE?wait_for_completion=true"

# Upload to MinIO
mc cp $BACKUP_DIR/*.gz minio/backup/
```

#### Point-in-Time Recovery
```sql
-- Restore to specific timestamp
SELECT pg_create_restore_point('before_family_transfer');

-- In case of recovery
pg_basebackup -h primary-host -D /var/lib/postgresql/data -U replication_user -P --wal-method=stream
```

### Disaster Recovery Procedures

#### Regional Failover
```yaml
# Kubernetes failover configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: failover-config
data:
  primary-region: "ghana"
  secondary-regions: "eu-west,us-east"
  failover-timeout: "300s"
  health-check-interval: "30s"
```

#### Service Failover Steps
1. **Detect failure**: Monitoring alerts trigger failover
2. **Isolate region**: Route traffic away from failed region
3. **Promote secondary**: Promote read replica to primary
4. **Update DNS**: Update global load balancer
5. **Verify functionality**: Run automated tests
6. **Notify stakeholders**: Send alerts and status updates

## Security Configuration

### Network Security

#### VPC Configuration
```terraform
resource "aws_vpc" "mayo_vpc" {
  cidr_block = "10.0.0.0/16"

  tags = {
    Name = "mayo-emr-vpc"
  }
}

resource "aws_security_group" "service_sg" {
  name_prefix = "mayo-service-"
  vpc_id      = aws_vpc.mayo_vpc.id

  ingress {
    from_port   = 8080
    to_port     = 8090
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }
}
```

#### Service Mesh Security
```yaml
# Istio PeerAuthentication
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: mayo
spec:
  mtls:
    mode: STRICT
```

### Data Encryption

#### At Rest Encryption
```yaml
# PostgreSQL encryption
ssl = on
ssl_cert_file = '/etc/ssl/certs/postgresql.crt'
ssl_key_file = '/etc/ssl/private/postgresql.key'
ssl_ca_file = '/etc/ssl/certs/ca.crt'

# Elasticsearch encryption
xpack.security.transport.ssl.enabled: true
xpack.security.transport.ssl.verification_mode: certificate
xpack.security.transport.ssl.certificate: /usr/share/elasticsearch/config/certs/elastic.crt
xpack.security.transport.ssl.certificate_authorities: /usr/share/elasticsearch/config/certs/ca.crt
```

#### In Transit Encryption
```yaml
# TLS configuration for all services
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: mayo-tls
spec:
  secretName: mayo-tls-secret
  issuerRef:
    name: letsencrypt-prod
    kind: ClusterIssuer
  dnsNames:
  - api.mayo.com
  - auth.mayo.com
  - patient.mayo.com
  - audit.mayo.com
```

## Scaling Configuration

### Horizontal Pod Autoscaling

#### CPU/Memory Based Scaling
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: auth-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: auth-service
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

#### Custom Metrics Scaling
```yaml
metrics:
- type: Pods
  pods:
    metric:
      name: http_requests_per_second
    target:
      type: AverageValue
      averageValue: 100
```

### Database Scaling

#### Read Replicas
```yaml
apiVersion: postgresql.k8s.enterprisedb.io/v1
kind: Cluster
metadata:
  name: mayo-db
spec:
  instances: 3
  storage:
    size: 1Ti
  replicas:
    enabled: true
    number: 2
```

#### Connection Pooling
```yaml
# PgBouncer configuration
[databases]
mayo_auth = host=postgres-primary port=5432 dbname=mayo_auth
mayo_patient = host=postgres-primary port=5432 dbname=mayo_patient

[pgbouncer]
listen_port = 6432
listen_addr = *
auth_type = md5
auth_file = /etc/pgbouncer/userlist.txt
pool_mode = transaction
max_client_conn = 1000
default_pool_size = 20
reserve_pool_size = 5
```

## Operational Procedures

### Daily Operations

#### Health Checks
```bash
#!/bin/bash
# Daily health check script

# Service health checks
curl -f http://auth-service:8080/actuator/health
curl -f http://patient-service:8082/actuator/health
curl -f http://audit-service:8085/actuator/health

# Database connectivity
psql -h postgres -U mayo -d mayo_auth -c "SELECT 1"

# Elasticsearch health
curl -f elasticsearch:9200/_cluster/health

# Kafka connectivity
kafka-console-producer --broker-list kafka:9092 --topic health-check <<< "ping"
```

#### Log Rotation
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: logrotate-config
data:
  logrotate.conf: |
    /var/log/mayo/*.log {
        daily
        rotate 30
        compress
        missingok
        notifempty
        create 644 mayo mayo
        postrotate
          docker-compose restart logging
        endscript
    }
```

### Incident Response

#### Alert Configuration
```yaml
# Prometheus alerting rules
groups:
- name: mayo.alerts
  rules:
  - alert: HighErrorRate
    expr: rate(http_requests_total{status=~"5.."}[5m]) / rate(http_requests_total[5m]) > 0.05
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "High error rate detected"

  - alert: DatabaseConnectionIssues
    expr: pg_up == 0
    for: 2m
    labels:
      severity: critical
    annotations:
      summary: "Database connection lost"
```

#### Runbooks

##### Service Restart Procedure
1. Check service logs for errors
2. Verify dependencies (database, cache, message queue)
3. Scale down problematic pods
4. Update deployment with fix
5. Scale up and verify health
6. Monitor for 30 minutes

##### Database Failover Procedure
1. Confirm primary database failure
2. Promote read replica to primary
3. Update connection strings
4. Verify data consistency
5. Rebuild failed primary
6. Test application functionality

##### Security Incident Response
1. Isolate affected systems
2. Gather evidence and logs
3. Assess impact and data exposure
4. Notify relevant stakeholders
5. Implement remediation
6. Conduct post-mortem analysis

## Performance Optimization

### Caching Strategy

#### Redis Configuration
```redis.conf
# Redis cluster configuration
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000

# Memory optimization
maxmemory 2gb
maxmemory-policy allkeys-lru

# Persistence
save 900 1
save 300 10
save 60 10000
```

#### Cache Keys
```
# Family data
family:{familyId} -> Family object
family:members:{familyId} -> List of members
family:permissions:{userId} -> User permissions

# Patient data
patient:{patientId} -> Patient object
patient:owner:{patientId} -> Owner information

# Audit data
audit:recent:{userId} -> Recent audit events
audit:stats:daily -> Daily statistics

# Sync data
sync:state:{userId}:{deviceId} -> Sync state
sync:vector:{userId} -> Vector clock
```

### Database Optimization

#### Indexing Strategy
```sql
-- Family accounts indexes
CREATE INDEX idx_family_created_by ON families(created_by);
CREATE INDEX idx_family_members_family_user ON family_members(family_id, user_id);
CREATE INDEX idx_family_members_user ON family_members(user_id);

-- Ownership transfers indexes
CREATE INDEX idx_ownership_transfers_patient ON ownership_transfers(patient_id);
CREATE INDEX idx_ownership_transfers_status ON ownership_transfers(status);
CREATE INDEX idx_ownership_transfers_dates ON ownership_transfers(initiated_at, completed_at);

-- Audit events indexes
CREATE INDEX idx_audit_events_timestamp ON audit_events(timestamp DESC);
CREATE INDEX idx_audit_events_user ON audit_events(user_id);
CREATE INDEX idx_audit_events_resource ON audit_events(resource_type, resource_id);
CREATE INDEX idx_audit_events_action ON audit_events(action);
```

#### Query Optimization
```sql
-- Optimized family member query
SELECT fm.*, u.email, u.full_name
FROM family_members fm
JOIN users u ON fm.user_id = u.id
WHERE fm.family_id = $1 AND fm.status = 'ACTIVE'
ORDER BY fm.joined_at;

-- Optimized audit query with pagination
SELECT * FROM audit_events
WHERE user_id = $1 AND timestamp >= $2 AND timestamp <= $3
ORDER BY timestamp DESC
LIMIT $4 OFFSET $5;
```

## Compliance and Auditing

### Data Residency Controls

#### Geographic Data Routing
```java
@Service
public class DataResidencyService {

    private final String primaryRegion = "ghana";

    public boolean isDataAllowedInRegion(String dataType, String region) {
        // Healthcare data must stay in Ghana
        if ("HEALTHCARE".equals(dataType)) {
            return "ghana".equals(region);
        }
        return true;
    }

    public String getDataRegion(String dataType, UUID dataId) {
        if ("HEALTHCARE".equals(dataType)) {
            return primaryRegion;
        }
        return "global";
    }
}
```

### Audit Trail Verification

#### Integrity Checks
```bash
#!/bin/bash
# Daily audit integrity check

# Verify audit log continuity
LATEST_EVENT=$(psql -h $DB_HOST -U $DB_USER -d mayo_audit -t -c "
    SELECT MAX(id) FROM audit_events WHERE date(timestamp) = CURRENT_DATE - 1")

if [ -z "$LATEST_EVENT" ]; then
    echo "WARNING: No audit events found for yesterday"
    exit 1
fi

# Check for gaps in sequence
GAPS=$(psql -h $DB_HOST -U $DB_USER -d mayo_audit -t -c "
    SELECT count(*) FROM (
        SELECT id + 1 as gap_start
        FROM audit_events t1
        WHERE NOT EXISTS (
            SELECT 1 FROM audit_events t2 WHERE t2.id = t1.id + 1
        )
    ) gaps")

if [ "$GAPS" -gt 0 ]; then
    echo "CRITICAL: Audit log gaps detected: $GAPS"
    exit 2
fi
```

This comprehensive infrastructure guide ensures reliable, secure, and scalable operation of the Mayo EMR Phase 2 system across national-scale deployments while maintaining compliance with healthcare regulations and data protection standards.