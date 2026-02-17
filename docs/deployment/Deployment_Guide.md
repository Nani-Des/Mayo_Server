# EMR Backend System Deployment Guide

## Overview

This comprehensive deployment guide covers the complete lifecycle of deploying the EMR backend system, from local development to production multi-region operations. The guide references existing infrastructure configurations and provides step-by-step procedures for each deployment stage.

## Table of Contents

1. [Local Development Setup](#local-development-setup)
2. [Docker Containerization](#docker-containerization)
3. [Kubernetes Deployment](#kubernetes-deployment)
4. [Multi-Region Deployment](#multi-region-deployment)
5. [CI/CD Pipelines](#cicd-pipelines)
6. [Operational Procedures](#operational-procedures)

## Local Development Setup

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- Docker Desktop
- PostgreSQL 15+
- Redis 7+
- Kafka (via Docker)
- Git

### Environment Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/mayo/emr-backend.git
   cd emr-backend
   ```

2. **Configure environment variables:**
   ```bash
   cp .env.example .env
   # Edit .env with your local configuration
   ```

3. **Start infrastructure services:**
   ```bash
   docker-compose -f infrastructure/docker/docker-compose.yml up -d
   ```

   This starts the following services as defined in `infrastructure/docker/docker-compose.yml`:
   - PostgreSQL (port 5432)
   - Redis (port 6379)
   - Zookeeper (port 2181)
   - Kafka (port 9092)
   - MinIO (port 9000/9001)
   - pgAdmin (port 5050)
   - Elasticsearch (port 9200)
   - Kibana (port 5601)

4. **Build and run services:**
   ```bash
   # Build all services
   mvn clean install -DskipTests

   # Run individual services
   mvn spring-boot:run -pl services/auth-service
   mvn spring-boot:run -pl services/patient-service
   mvn spring-boot:run -pl services/sync-service
   ```

### Database Initialization

The database schema is automatically created using Flyway migrations. Key migration files include:
- `services/auth-service/src/main/resources/db/migration/V1__create_auth_tables.sql`
- `services/sync-service/src/main/resources/db/migration/V1__create_sync_tables.sql`

### Testing

```bash
# Run unit tests
mvn test

# Run integration tests
mvn verify -P integration-tests

# Run E2E tests
mvn test -Dtest=FamilyTransferJourneyTest -Dspring.profiles.active=e2e
```

## Docker Containerization

### Container Build Process

The system uses multi-stage Docker builds for optimized images. Services are containerized with the following key configurations:

#### Base Configuration (from `infrastructure/docker/docker-compose.yml`)

```yaml
# PostgreSQL with health checks
postgres:
  image: postgres:15-alpine
  environment:
    POSTGRES_DB: mayo_db
    POSTGRES_USER: mayo
    POSTGRES_PASSWORD: mayo_dev_password
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U mayo"]
    interval: 10s
    timeout: 5s
    retries: 5

# Redis with persistence
redis:
  image: redis:7-alpine
  command: redis-server --appendonly yes
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 10s
    timeout: 5s
    retries: 5

# Kafka cluster with Zookeeper
kafka:
  image: confluentinc/cp-kafka:7.5.0
  environment:
    KAFKA_BROKER_ID: 1
    KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092,PLAINTEXT_INTERNAL://kafka:9093
  healthcheck:
    test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
    interval: 30s
    timeout: 10s
    retries: 5
```

### Service Containerization

Each microservice follows this pattern:

```dockerfile
# Multi-stage build
FROM maven:3.8.6-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM openjdk:17-jre-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
```

### Image Security

- Base images are minimal (Alpine/slim variants)
- No root user execution
- Regular security scanning with Trivy
- Vulnerability assessments before deployment

## Kubernetes Deployment

### Cluster Architecture

The system deploys to Kubernetes using the configurations in `infrastructure/kubernetes/regional-deployment.yaml`.

#### Namespace and RBAC Setup

```yaml
# Namespace configuration
apiVersion: v1
kind: Namespace
metadata:
  name: mayo-emr
  labels:
    app: mayo-emr
    environment: production
    compliance: hipaa-gdpr

# Service Account with RBAC
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: mayo-service-role
rules:
- apiGroups: [""]
  resources: ["pods", "services", "configmaps", "secrets"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
```

### Service Deployments

#### Auth Service Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  namespace: mayo-emr
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    spec:
      containers:
      - name: auth-service
        image: ghcr.io/mayo-emr/auth-service:2.1.0
        ports:
        - containerPort: 8081
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production,accra"
        - name: REGION
          value: "accra"
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
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 20
```

### Horizontal Pod Autoscaling

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
  maxReplicas: 15
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

### Ingress Configuration

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: mayo-ingress
  annotations:
    kubernetes.io/ingress.class: "nginx"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  tls:
  - hosts:
    - api.mayo-health.gh
    secretName: mayo-tls-secret
  rules:
  - host: api.mayo-health.gh
    http:
      paths:
      - path: /api/auth
        pathType: Prefix
        backend:
          service:
            name: auth-service
            port:
              number: 80
```

## Multi-Region Deployment

### Regional Architecture

The system supports active-active multi-region deployment across Ghana's major cities as detailed in `infrastructure/Multi-Geo_Infrastructure_Design.md`.

#### Regional Topology

```
Primary Region (Accra)
├── API Gateway
├── Auth Service (3 replicas)
├── Patient Service (3 replicas)
├── PostgreSQL Primary
├── Redis Cluster (6 nodes)
└── Kafka Cluster (3 brokers)

Secondary Regions (Kumasi, Takoradi, Tamale)
├── API Gateway
├── Service Replicas
├── PostgreSQL Read Replicas
├── Redis Cluster
└── Kafka Mirror
```

### Geo-Aware Routing

#### Regional ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: regional-config
data:
  REGIONAL_ENDPOINTS: |
    {
      "accra": {
        "api-gateway": "gateway-service.mayo-emr.svc.cluster.local:8080",
        "postgres": "mayo-postgres.mayo-emr.svc.cluster.local:5432"
      },
      "kumasi": {
        "api-gateway": "gateway-service-kumasi.mayo-emr.svc.cluster.local:8080",
        "postgres": "mayo-postgres-kumasi.mayo-emr.svc.cluster.local:5432"
      }
    }
  GEO_ROUTING_RULES: |
    {
      "default_region": "accra",
      "region_mapping": {
        "GH-AA": "accra",
        "GH-AH": "accra",
        "GH-AF": "kumasi"
      }
    }
```

### Database Replication

#### PostgreSQL StatefulSet

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mayo-postgres
spec:
  serviceName: "mayo-postgres"
  replicas: 3
  template:
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        envFrom:
        - configMapRef:
            name: postgres-config
        volumeMounts:
        - name: postgres-data
          mountPath: /var/lib/postgresql/data
        resources:
          requests:
            cpu: "1000m"
            memory: "4Gi"
          limits:
            cpu: "4000m"
            memory: "16Gi"
  volumeClaimTemplates:
  - metadata:
      name: postgres-data
    spec:
      accessModes: [ "ReadWriteOnce" ]
      storageClassName: "ssd"
      resources:
        requests:
          storage: 100Gi
```

### Redis Multi-Region Cluster

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mayo-redis
spec:
  serviceName: "mayo-redis"
  replicas: 6
  template:
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        command: ["redis-server"]
        args: ["/etc/redis/redis.conf"]
        ports:
        - containerPort: 6379
        - containerPort: 16379
        volumeMounts:
        - name: redis-data
          mountPath: /data
        resources:
          requests:
            cpu: "500m"
            memory: "2Gi"
          limits:
            cpu: "2000m"
            memory: "8Gi"
```

### Kafka Cross-Region Replication

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaMirrorMaker2
metadata:
  name: mayo-kafka-mirror
spec:
  version: 3.4.0
  connectCluster: "accra-kafka"
  clusters:
  - alias: "accra"
    bootstrapServers: "accra-kafka-bootstrap:9092"
  - alias: "kumasi"
    bootstrapServers: "kumasi-kafka-bootstrap:9092"
  mirrors:
  - sourceCluster: "accra"
    targetCluster: "kumasi"
    sourceConnector:
      tasksMax: 4
      config:
        replication.factor: 3
```

## CI/CD Pipelines

### GitHub Actions Workflow

The CI/CD pipeline is defined in `ci-cd/github-actions.yml` and includes:

#### Build Stage

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v3

      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Cache Maven
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: maven-${{ hashFiles('**/pom.xml') }}

      - name: Build All Services
        run: mvn clean install -DskipTests

      - name: Run Unit Tests
        run: mvn test

      - name: Run Integration Tests
        run: mvn verify -P integration-tests
```

#### Security Testing

```yaml
- name: Run Security Tests
  run: |
    # Run OWASP ZAP baseline scan
    docker run --rm -v $(pwd):/zap/wrk owasp/zap2docker-stable zap-baseline.py \
      -t http://localhost:8080 \
      -r zap-report.html || true

- name: Run Performance Tests
  run: |
    # Run JMeter performance tests
    docker run --rm -v $(pwd)/testing/performance:/tests \
      -v $(pwd)/test-results:/results \
      justb4/jmeter:latest \
      -n -t /tests/national-scale-load-test.jmx \
      -l /results/results.jtl
```

#### Docker Build and Test

```yaml
- name: Build Docker Images
  run: |
    docker build -t mayo/auth-service services/auth-service
    docker build -t mayo/patient-service services/patient-service
    docker build -t mayo/sync-service services/sync-service

- name: Run E2E Tests
  run: |
    # Start services for E2E testing
    docker-compose up -d
    sleep 60
    mvn test -Dtest=FamilyTransferJourneyTest -Dspring.profiles.active=e2e
    docker-compose down
```

### Deployment Strategies

#### Blue-Green Deployment

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: mayo-emr-blue
spec:
  source:
    repoURL: https://github.com/mayo/emr-infrastructure
    targetRevision: HEAD
    path: charts/mayo-emr
  destination:
    server: https://kubernetes.default.svc
    namespace: mayo-blue
```

#### Canary Deployment

```yaml
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

## Operational Procedures

### Monitoring and Observability

#### Prometheus Configuration

```yaml
# Service monitoring
scrape_configs:
  - job_name: 'mayo-services'
    static_configs:
      - targets: ['auth-service:8080', 'patient-service:8082']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

  - job_name: 'audit-service'
    static_configs:
      - targets: ['audit-service:8085']
    metrics_path: '/actuator/prometheus'
```

#### Grafana Dashboards

Key dashboards include:
- System health and performance
- Business metrics (family transfers, audit events)
- Security monitoring
- Multi-region health overview

### Backup and Disaster Recovery

#### Database Backup Strategy

```bash
#!/bin/bash
# Daily backup script
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups"

# PostgreSQL backup
pg_dump -h $DB_HOST -U $DB_USER -d mayo_auth | gzip > $BACKUP_DIR/auth_$DATE.sql.gz
pg_dump -h $DB_HOST -U $DB_USER -d mayo_patient | gzip > $BACKUP_DIR/patient_$DATE.sql.gz

# Elasticsearch snapshot
curl -X PUT "elasticsearch:9200/_snapshot/audit_backup/snapshot_$DATE?wait_for_completion=true"

# Upload to MinIO
mc cp $BACKUP_DIR/*.gz minio/backup/
```

#### Disaster Recovery Procedures

As detailed in `infrastructure/operational-runbooks/Security_Incident_Response_Procedures.md`:

1. **Detection**: Monitoring alerts trigger incident response
2. **Containment**: Isolate affected systems
3. **Investigation**: Forensic analysis and root cause determination
4. **Recovery**: Restore from clean backups
5. **Lessons Learned**: Post-incident review and improvements

### Security Operations

#### Incident Response Phases

1. **Detection and Assessment** (0-15 minutes)
2. **Containment** (15-60 minutes)
3. **Investigation** (1-24 hours)
4. **Recovery** (Hours to days)
5. **Lessons Learned** (Post-incident)

#### Security Monitoring

- Real-time threat detection
- Automated alerting for security events
- Regular vulnerability assessments
- Compliance auditing

### Performance Optimization

#### Caching Strategy

```redis.conf
# Redis cluster configuration
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000

# Memory optimization
maxmemory 2gb
maxmemory-policy allkeys-lru
```

#### Database Optimization

Key indexes and query optimizations as documented in `infrastructure/Phase2_Deployment_Operations.md`.

### Compliance and Auditing

#### Data Residency Controls

Healthcare data must remain within Ghana's borders, with automated controls ensuring compliance.

#### Audit Trail Integrity

Daily integrity checks verify audit log continuity and detect any tampering attempts.

### Scaling Operations

#### Horizontal Pod Autoscaling

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
spec:
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

#### Database Scaling

Read replicas and connection pooling ensure high availability and performance.

## Conclusion

This deployment guide provides comprehensive procedures for operating the EMR backend system across all environments, from local development to national-scale production deployment. Regular updates to this guide should reflect infrastructure changes and operational learnings.

## References

- `infrastructure/docker/docker-compose.yml` - Local development setup
- `infrastructure/kubernetes/regional-deployment.yaml` - Kubernetes manifests
- `ci-cd/github-actions.yml` - CI/CD pipeline configuration
- `infrastructure/Multi-Geo_Infrastructure_Design.md` - Multi-region architecture
- `infrastructure/Phase2_Deployment_Operations.md` - Operational procedures
- `infrastructure/operational-runbooks/Security_Incident_Response_Procedures.md` - Security procedures