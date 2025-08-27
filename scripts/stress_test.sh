#!/bin/bash

# 🔥 QUICK STRESS TEST SCRIPT
# This script provides quick stress testing without JMeter

set -e

# Configuration
BASE_URL="http://localhost:8888"
MERCHANT_ID="merchant_001"
DURATION=60  # seconds
CONCURRENT_USERS=100
PAYMENT_ENDPOINT="/api/v1/payments"
HEALTH_ENDPOINT="/api/v1/health/detailed"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔥 STRESS TESTING STARTING 🔥${NC}"
echo -e "${YELLOW}Target: ${BASE_URL}${NC}"
echo -e "${YELLOW}Duration: ${DURATION} seconds${NC}"
echo -e "${YELLOW}Concurrent Users: ${CONCURRENT_USERS}${NC}"
echo ""

# Function to generate random payment data
generate_payment_data() {
    local amount=$((RANDOM % 1000 + 1)).$((RANDOM % 90 + 10))
    local ref_id="stress_$(date +%s)_$RANDOM"
    local payment_method="pm_test_$((RANDOM % 900000 + 100000))"
    
    echo "{\"merchantId\":\"${MERCHANT_ID}\",\"amount\":${amount},\"currency\":\"USD\",\"paymentMethodId\":\"${payment_method}\",\"description\":\"Stress test payment ${ref_id}\",\"referenceId\":\"${ref_id}\"}"
}

# Function to test payment creation
test_payment_creation() {
    local idempotency_key="stress_$(date +%s)_$RANDOM"
    local payment_data=$(generate_payment_data)
    
    local start_time=$(date +%s.%N)
    local response=$(curl -s -w "\n%{http_code}\n%{time_total}" \
        -X POST "${BASE_URL}${PAYMENT_ENDPOINT}" \
        -H "Content-Type: application/json" \
        -H "X-Merchant-ID: ${MERCHANT_ID}" \
        -H "Idempotency-Key: ${idempotency_key}" \
        -d "${payment_data}" \
        2>/dev/null)
    
    local end_time=$(date +%s.%N)
    local response_body=$(echo "$response" | head -n -2)
    local http_code=$(echo "$response" | tail -n 2 | head -n 1)
    local response_time=$(echo "$response" | tail -n 1)
    
    local duration=$(echo "$end_time - $start_time" | bc -l)
    
    if [ "$http_code" -eq 201 ] || [ "$http_code" -eq 200 ]; then
        echo -e "${GREEN}✅ Payment Created${NC} | HTTP: $http_code | Time: ${response_time}s | ID: $idempotency_key"
    else
        echo -e "${RED}❌ Payment Failed${NC} | HTTP: $http_code | Time: ${response_time}s | ID: $idempotency_key | Response: $response_body"
    fi
}

# Function to test health check
test_health_check() {
    local start_time=$(date +%s.%N)
    local response=$(curl -s -w "\n%{http_code}\n%{time_total}" \
        "${BASE_URL}${HEALTH_ENDPOINT}" \
        2>/dev/null)
    
    local end_time=$(date +%s.%N)
    local response_body=$(echo "$response" | head -n -2)
    local http_code=$(echo "$response" | tail -n 2 | head -n 1)
    local response_time=$(echo "$response" | tail -n 1)
    
    local duration=$(echo "$end_time - $start_time" | bc -l)
    
    if [ "$http_code" -eq 200 ]; then
        echo -e "${GREEN}✅ Health Check OK${NC} | HTTP: $http_code | Time: ${response_time}s"
    else
        echo -e "${RED}❌ Health Check Failed${NC} | HTTP: $http_code | Time: ${response_time}s | Response: $response_body"
    fi
}

# Function to run concurrent tests
run_concurrent_tests() {
    local test_type=$1
    local count=$2
    local duration=$3
    
    echo -e "${BLUE}🚀 Starting ${test_type} stress test with ${count} concurrent users for ${duration} seconds...${NC}"
    
    # Start background processes
    for i in $(seq 1 $count); do
        if [ "$test_type" = "payment" ]; then
            test_payment_creation &
        else
            test_health_check &
        fi
        sleep 0.1  # Small delay to stagger requests
    done
    
    # Wait for specified duration
    sleep $duration
    
    # Kill all background processes
    pkill -P $$ 2>/dev/null || true
    
    echo -e "${BLUE}✅ ${test_type} stress test completed${NC}"
}

# Function to monitor system resources
monitor_resources() {
    echo -e "${YELLOW}📊 Monitoring system resources...${NC}"
    
    # Monitor in background
    (
        while true; do
            echo "--- $(date) ---"
            echo "CPU Usage: $(top -l 1 | grep "CPU usage" | awk '{print $3}' | sed 's/%//')"
            echo "Memory Usage: $(top -l 1 | grep PhysMem | awk '{print $2}' | sed 's/[A-Z]//')"
            echo "Java Processes: $(pgrep -c java || echo 0)"
            echo "Network Connections: $(netstat -an | grep :8888 | wc -l | tr -d ' ')"
            echo ""
            sleep 5
        done
    ) &
    
    local monitor_pid=$!
    
    # Store PID for cleanup
    echo $monitor_pid > /tmp/stress_test_monitor.pid
}

# Function to cleanup
cleanup() {
    echo -e "${YELLOW}🧹 Cleaning up...${NC}"
    
    # Kill monitoring process
    if [ -f /tmp/stress_test_monitor.pid ]; then
        kill $(cat /tmp/stress_test_monitor.pid) 2>/dev/null || true
        rm -f /tmp/stress_test_monitor.pid
    fi
    
    # Kill any remaining background processes
    pkill -P $$ 2>/dev/null || true
    
    echo -e "${GREEN}✅ Cleanup completed${NC}"
}

# Trap cleanup on exit
trap cleanup EXIT

# Check if required tools are available
if ! command -v curl &> /dev/null; then
    echo -e "${RED}❌ curl is required but not installed${NC}"
    exit 1
fi

if ! command -v bc &> /dev/null; then
    echo -e "${RED}❌ bc is required but not installed${NC}"
    exit 1
fi

# Check if application is running
echo -e "${YELLOW}🔍 Checking if application is running...${NC}"
if ! curl -s "${BASE_URL}${HEALTH_ENDPOINT}" > /dev/null; then
    echo -e "${RED}❌ Application is not running at ${BASE_URL}${NC}"
    echo -e "${YELLOW}Please start your application first: ./gradlew bootRun${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Application is running${NC}"
echo ""

# Start resource monitoring
monitor_resources

# Run stress tests
echo -e "${BLUE}🎯 Starting comprehensive stress test...${NC}"
echo ""

# Test 1: Payment Creation Stress
run_concurrent_tests "payment" $CONCURRENT_USERS $DURATION

# Test 2: Health Check Stress
run_concurrent_tests "health" $((CONCURRENT_USERS / 2)) $DURATION

# Test 3: Mixed Load
echo -e "${BLUE}🔄 Running mixed load test...${NC}"
for i in $(seq 1 $((DURATION / 10))); do
    echo -e "${YELLOW}Round $i of $((DURATION / 10))${NC}"
    
    # Start payment tests
    for j in $(seq 1 20); do
        test_payment_creation &
    done
    
    # Start health checks
    for j in $(seq 1 10); do
        test_health_check &
    done
    
    sleep 10
done

echo ""
echo -e "${GREEN}🎉 All stress tests completed!${NC}"
echo -e "${YELLOW}Check the output above for results and any errors.${NC}"
echo -e "${YELLOW}Monitor your application logs for additional details.${NC}"
