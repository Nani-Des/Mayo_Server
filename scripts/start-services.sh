#!/bin/bash

# Mayo Hospital Services Startup Script
# This script starts all infrastructure services for the Mayo Hospital system

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$SCRIPT_DIR/../infrastructure/docker"

echo "=========================================="
echo "Mayo Hospital Services - Startup Script"
echo "=========================================="
echo ""

# Change to docker compose directory
cd "$COMPOSE_DIR"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to check if a container is running
is_container_running() {
    docker ps --format '{{.Names}}' | grep -q "^$1$"
}

# Function to check container health
check_container_health() {
    local container_name=$1
    local health_status=$(docker inspect --format='{{.State.Health.Status}}' "$container_name" 2>/dev/null || echo "none")
    echo "$health_status"
}

# List of services to manage
SERVICES=("postgres" "redis" "zookeeper" "kafka" "minio" "pgadmin" "elasticsearch" "kibana")

echo "Checking current service status..."
echo ""

# Check and report status for each service
declare -A service_status
running_count=0
stopped_count=0

for service in "${SERVICES[@]}"; do
    container_name="mayo-$service"
    if is_container_running "$container_name"; then
        health=$(check_container_health "$container_name")
        if [ "$health" = "healthy" ]; then
            service_status["$service"]="running"
            ((running_count++))
            echo -e "${GREEN}✓ $service${NC} - Running (healthy)"
        else
            service_status["$service"]="running"
            ((running_count++))
            echo -e "${YELLOW}✓ $service${NC} - Running (starting)"
        fi
    else
        service_status["$service"]="stopped"
        ((stopped_count++))
        echo -e "${RED}✗ $service${NC} - Not running"
    fi
done

echo ""
echo "Summary: $running_count running, $stopped_count stopped"
echo ""

# Ask user what to do
if [ $stopped_count -gt 0 ]; then
    echo "What would you like to do?"
    echo "1) Start all stopped services"
    echo "2) Restart all services"
    echo "3) View detailed status"
    echo "4) Exit"
    echo ""
    read -p "Enter your choice (1-4): " choice

    case $choice in
        1)
            echo ""
            echo "Starting stopped services..."
            docker compose up -d
            ;;
        2)
            echo ""
            echo "Restarting all services..."
            docker compose restart
            ;;
        3)
            echo ""
            echo "Detailed Status:"
            echo ""
            docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep "mayo-"
            ;;
        4)
            echo "Exiting..."
            exit 0
            ;;
        *)
            echo "Invalid choice. Exiting..."
            exit 1
            ;;
    esac
else
    echo -e "${GREEN}All services are already running!${NC}"
    echo ""
    echo "Current Status:"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep "mayo-"
    echo ""
    echo "Options:"
    echo "1) Restart all services"
    echo "2) View detailed status"
    echo "3) Exit"
    echo ""
    read -p "Enter your choice (1-3): " choice

    case $choice in
        1)
            echo ""
            echo "Restarting all services..."
            docker compose restart
            ;;
        2)
            echo ""
            echo "Detailed Status:"
            echo ""
            docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep "mayo-"
            ;;
        3)
            echo "Exiting..."
            exit 0
            ;;
        *)
            echo "Invalid choice. Exiting..."
            exit 1
            ;;
    esac
fi

echo ""
echo "Waiting for services to become healthy..."
sleep 5

echo ""
echo "Final Status:"
echo ""
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep "mayo-"

echo ""
echo "=========================================="
echo "Service Endpoints:"
echo "=========================================="
echo "PostgreSQL: localhost:5433"
echo "Redis:      localhost:6379"
echo "Kafka:      localhost:9092"
echo "MinIO API:  localhost:9005"
echo "MinIO UI:   localhost:9006"
echo "pgAdmin:    localhost:5050"
echo "Elasticsearch: localhost:9200"
echo "Kibana:     localhost:5601"
echo "Kafka UI:   localhost:8081"
echo "=========================================="
