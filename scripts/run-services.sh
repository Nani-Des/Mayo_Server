#!/bin/bash

# Mayo Hospital Services - Build and Startup Script
# This script builds and starts all application microservices

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_DIR="$PROJECT_DIR/infrastructure/docker"

echo "=========================================="
echo "Mayo Hospital Services - Startup Script"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo -e "${RED}Java is not installed. Please install Java 17 first.${NC}"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}Java 17 or higher is required. Current version: $JAVA_VERSION${NC}"
    exit 1
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Maven is not installed. Please install Maven first.${NC}"
    exit 1
fi

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi

# List of microservices (only services with complete source code)
MICROSERVICES=(
    "gateway"
    "services/auth-service"
    "services/patient-service"
    "services/patient-record-service"
    "services/device-registry-service"
    "services/hospital-integration-service"
    "services/sync-service"
    "services/audit-service"
    "services/notification-service"
)

# Service ports (matching application.yml configurations)
declare -A SERVICE_PORTS
SERVICE_PORTS["gateway"]=8443
SERVICE_PORTS["services/auth-service"]=8451
SERVICE_PORTS["services/patient-service"]=8445
SERVICE_PORTS["services/patient-record-service"]=8445
SERVICE_PORTS["services/device-registry-service"]=8084
SERVICE_PORTS["services/hospital-integration-service"]=8447
SERVICE_PORTS["services/sync-service"]=8448
SERVICE_PORTS["services/audit-service"]=8449
SERVICE_PORTS["services/notification-service"]=8450

echo "Step 1: Checking infrastructure services..."
echo ""

# Check if infrastructure is running
cd "$COMPOSE_DIR"

# Check for required infrastructure containers
INFRA_SERVICES=("postgres" "redis" "zookeeper" "kafka" "minio")
INFRA_RUNNING=0
INFRA_TOTAL=${#INFRA_SERVICES[@]}

for service in "${INFRA_SERVICES[@]}"; do
    container_name="mayo-$service"
    if docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
        echo -e "${GREEN}✓ $service${NC} is running"
        ((INFRA_RUNNING++))
    else
        echo -e "${YELLOW}✗ $service${NC} is not running"
    fi
done

echo ""
if [ $INFRA_RUNNING -lt $INFRA_TOTAL ]; then
    echo "Starting infrastructure services..."
    docker compose up -d

    echo ""
    echo "Waiting for infrastructure services to be ready..."
    sleep 10

    # Wait for PostgreSQL
    echo "Waiting for PostgreSQL..."
    for i in {1..30}; do
        if docker exec mayo-postgres pg_isready -U mayo &> /dev/null; then
            echo "PostgreSQL is ready!"
            break
        fi
        sleep 1
    done

    # Wait for Kafka
    echo "Waiting for Kafka..."
    for i in {1..30}; do
        if docker exec mayo-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 &> /dev/null; then
            echo "Kafka is ready!"
            break
        fi
        sleep 1
    done

    echo ""
    echo "Infrastructure services started!"
else
    echo "All infrastructure services are already running."
fi

echo ""
echo "Step 2: Building microservices..."
echo ""

cd "$PROJECT_DIR"

# Build the project
echo "Building common modules first..."
mvn clean install -DskipTests -q

if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}Build successful!${NC}"

echo ""
echo "Step 3: Starting microservices..."
echo ""

# Create logs directory if it doesn't exist
mkdir -p "$PROJECT_DIR/logs"

# Function to check if a port is in use
is_port_in_use() {
    local port=$1
    if command -v netstat &> /dev/null; then
        netstat -tuln | grep -q ":$port "
    elif command -v ss &> /dev/null; then
        ss -tuln | grep -q ":$port "
    else
        # Fallback: try to connect to the port
        timeout 1 bash -c "echo > /dev/tcp/localhost/$port" 2>/dev/null
    fi
}

# Start each microservice in the background
PIDS=()

for service in "${MICROSERVICES[@]}"; do
    port=${SERVICE_PORTS[$service]}
    service_name=$(basename "$service")

    # Check if port is already in use
    if is_port_in_use $port; then
        echo -e "${YELLOW}⚠ $service_name is already running on port $port${NC}"
        continue
    fi

    echo "Starting $service_name on port $port..."

    cd "$PROJECT_DIR"

    # Start the service from project root using -pl (project list) and -am (also make)
    # This ensures dependencies are built first and we run from the correct context
    nohup mvn spring-boot:run -pl "$service" -am -Dspring-boot.run.jvmArguments="-Xmx512m" > "$PROJECT_DIR/logs/${service_name}.log" 2>&1 &

    PIDS+=($!)
    echo "  Started with PID: ${PIDS[-1]}"
done

echo ""
echo "Step 4: Waiting for services to start..."
echo ""

# Wait a bit for services to start
sleep 15

# Check which services are running
echo "Checking service status..."
echo ""

for service in "${MICROSERVICES[@]}"; do
    port=${SERVICE_PORTS[$service]}
    service_name=$(basename "$service")

    if is_port_in_use $port; then
        echo -e "${GREEN}✓ $service_name${NC} - Running on port $port"
    else
        echo -e "${YELLOW}✗ $service_name${NC} - Not responding on port $port"
        echo "  Check logs: tail -f $PROJECT_DIR/logs/${service_name}.log"
    fi
done

echo ""
echo "=========================================="
echo "Service Endpoints:"
echo "=========================================="
echo ""
for service in "${MICROSERVICES[@]}"; do
    port=${SERVICE_PORTS[$service]}
    service_name=$(basename "$service")
    echo "$service_name: http://localhost:$port"
done
echo ""
echo "=========================================="
echo "Logs location: $PROJECT_DIR/logs/"
echo "=========================================="
echo ""
echo "To view logs: tail -f logs/<service-name>.log"
echo ""
echo "To stop all services: pkill -f 'spring-boot:run'"
