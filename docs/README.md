# Mayo Server

Modular Spring Boot backend for an EMR-style system. Multi-module Maven project (gateway + services + shared common modules) built for reactive APIs, service discovery, JWT auth, gRPC integration and cloud-native deployment patterns.

What this is
- A multi-module Java 21 Spring Boot project that provides an API Gateway, domain services, shared libraries (core/security/events), and infrastructure/ops artifacts aimed at running an EMR backend (Postgres, Redis, Kafka, MinIO, etc.).
- Designed for local development and deployment in containerized or orchestrated environments; supports reactive gateway, service discovery (Eureka), circuit-breaking (Resilience4j), gRPC, and OpenAPI docs.

Stack (high‑level)
- Java 21, Spring Boot 3.4.x, Spring Cloud 2024.0.x
- Build: Maven
- Reactive gateway: Spring Cloud Gateway (Netty)
- Service discovery: Eureka (Netflix)
- Circuit breaker: Resilience4j
- Storage & messaging: PostgreSQL, Redis, Kafka, MinIO
- Security: JWT (jjwt)
- gRPC: grpc-spring-boot-starter
- Mapping: MapStruct
- Observability: Spring Boot Actuator, springdoc OpenAPI

Repository layout (most important)
```
pom.xml                     # parent multi-module POM (modules: common, gateway, services)
common/                     # shared modules (core, security, events)
  core/
  security/
  events/
gateway/                     # API Gateway (Spring Cloud Gateway)
services/                    # domain services (auth, patient, records, etc.)
database/                    # DB scripts / PLpgSQL (migrations or helpers)
infrastructure/              # infra manifests / k8s / docker (cluster/dev helpers)
ci-cd/                       # CI/CD pipelines / templates
scripts/                     # helper scripts (local workflow)
certs/                       # TLS certs / dev certs
docs/                        # design docs, API notes
.env.example                 # template for runtime environment variables
```

Requirements
- Java 21 (matching <java.version> in pom.xml)
- Maven 3.8+ (or newer)
- PostgreSQL (DB, port configurable)
- Redis (reactive) for caching/session/state
- Kafka (optional message bus)
- MinIO (S3-compatible storage) or alternative object store
- Docker (recommended for local infra)
- Recommended: 8+ GB RAM for running a few services locally; more for full stack

Quick build (local dev)
1. Clone and prepare Java/Maven:
```
git clone https://github.com/Nani-Des/Mayo_Server.git
cd Mayo_Server
# ensure JAVA_HOME points to a Java 21 JDK
java -version
mvn -v
```
2. Build all modules (skip tests for faster iteration):
```
mvn -T 1C clean install -DskipTests
```
- To build a single module (e.g., gateway) without building everything:
```
mvn -pl gateway -am clean package -DskipTests
```

Configuration (.env)
- Copy the provided template and fill values:
```
cp .env.example .env
# edit .env -> DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, JWT_SECRET, MINIO_ACCESS_KEY, etc.
```
- The project expects environment variables at runtime. For local shells you can export them:
```
export $(grep -v '^#' .env | xargs)
```
(If your .env contains comments or complex values, use a proper env loader like direnv or a process manager.)

Database setup
- Create the database and user matching values in `.env` (DB_NAME, DB_USER, DB_PASSWORD).
- The `database/` folder contains PL/pgSQL helper scripts; apply migrations or schema scripts as needed (project does not mandate a specific migration tool).
- Example (psql):
```
psql -h $DB_HOST -p $DB_PORT -U postgres -c "CREATE DATABASE mayo_db;"
psql -h $DB_HOST -p $DB_PORT -U postgres -c "CREATE ROLE mayo WITH LOGIN PASSWORD 'mayo_dev_password'; GRANT ALL PRIVILEGES ON DATABASE mayo_db TO mayo;"
```

Run locally (individual modules)
- API Gateway (dev):
```
# ensure env vars are exported
cd gateway
mvn spring-boot:run
# or run packaged jar
java -jar target/gateway-*.jar
```
- Services:
```
# start an individual service (example: services/auth)
cd services/auth
mvn spring-boot:run
```
Each service exposes its port per `.env` (GATEWAY_PORT, AUTH_SERVICE_PORT, etc.). Start discovery (Eureka) first if running a full set.

Docker / infrastructure
- The `infrastructure/` folder contains deployment artifacts (inspect for docker-compose or k8s manifests).
- Recommended local approach:
  - Start Postgres / Redis / Kafka / MinIO with docker-compose (or use local services).
  - Export `.env` values to your shell.
  - Start discovery (Eureka) if required, then start gateway and services.

Testing
- Unit tests:
```
mvn test
```
- Module-specific tests:
```
mvn -pl services/auth test
```

Observability & API docs
- OpenAPI UI (springdoc) is enabled — when the gateway/service is running, check `/swagger-ui.html` or `/v3/api-docs` on the service endpoint.
- Actuator endpoints are available (health, metrics). Configure management.port and exposure via Spring properties or environment.

gRPC
- gRPC server and client starters are included. If using gRPC:
  - Ensure port bindings and proto-generated classes are available during build.
  - Use the provided stubs or build tool to generate gRPC Java code if proto files are modified.

Security notes
- JWT_SECRET in `.env.example` is a placeholder — generate a secure secret for production (e.g., `openssl rand -base64 64`).
- Follow secure practice for secrets (do not commit `.env` to VCS; use secret manager in production).

Common build/run tips & troubleshooting
- Java compatibility: use the JDK version specified in `pom.xml` (21). Older JDKs will fail compilation.
- MapStruct / Lombok: IDE may require annotation processing enabled to compile generated mappers locally.
- If you see dependency resolution issues for Spring Cloud BOM or Py PI, run `mvn -U clean install`.
- Build errors: the repo includes `build_error*.txt` files — inspect them if your build fails for clues.
- If gateway fails to start due to service discovery timeouts, run Eureka/Discovery first or start gateway with `spring.cloud.discovery.enabled=false` for local single-service dev.

Developer workflow suggestions
- Use `mvn -T 1C` to parallelize builds.
- For quick API iteration, run service locally with `mvn spring-boot:run` and `spring-boot-devtools`.
- Use Docker Compose to boot dependent infra (Postgres/Redis/MinIO) in one command and keep app services running in local JVMs for faster code-edit cycles.

Project status
- Active multi-module Spring Boot codebase (snapshot / in-development). Review CI/CD in `ci-cd/` for pipeline examples and `infrastructure/` for deployment patterns.

Where to look next (key files)
- `pom.xml` — parent multi-module configuration and dependency management
- `gateway/pom.xml` — gateway dependencies and runtime behavior
- `common/` — shared modules (core/security/events)
- `database/` — DB stored procedures and PL/pgSQL helpers
- `.env.example` — required runtime environment variables and ports
- `infrastructure/` & `ci-cd/` — deployment and pipeline templates

Author
- Nani-Des — https://github.com/Nani-Des
