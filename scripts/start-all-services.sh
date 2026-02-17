#!/bin/bash

# Start All Services Script
# This script builds all services and starts them in the background with logging

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Building all services...${NC}"
echo -e "${GREEN}========================================${NC}"

# Build all services
mvn clean install -DskipTests -q

echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Starting all services...${NC}"
echo -e "${GREEN}========================================${NC}"

# Create logs directory if it doesn't exist
mkdir -p logs

# Function to start a service
start_service() {
    local service_name=$1
    local service_dir=$2
    local port=$3
    local log_file="logs/${service_name}.log"

    echo -e "${YELLOW}Starting ${service_name} on port ${port}...${NC}"

    cd "${service_dir}"
    nohup mvn spring-boot:run > "${log_file}" 2>&1 &
    cd - > /dev/null

    echo -e "${GREEN}${service_name} started in background (log: ${log_file})${NC}"
}

# Start all services in order
start_service "gateway" "gateway" "8080"
start_service "auth-service" "services/auth-service" "8081"
start_service "patient-service" "services/patient-service" "8082"
start_service "patient-record-service" "services/patient-record-service" "8083"
start_service "device-registry-service" "services/device-registry-service" "8084"
start_service "hospital-integration-service" "services/hospital-integration-service" "8085"
start_service "sync-service" "services/sync-service" "8086"
start_service "audit-service" "services/audit-service" "8087"
start_service "notification-service" "services/notification-service" "8088"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}All services started!${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "${YELLOW}You can monitor logs in the logs/ directory${NC}"
echo -e "${YELLOW}To follow a specific service log:${NC}"
echo -e "${YELLOW}  tail -f logs/<service-name>.log${NC}"
