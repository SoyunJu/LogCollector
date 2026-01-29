#!/bin/bash
set -euo pipefail

# Use Port
# local : 8082
# Docker : 8080

BASE_URL="${BASE_URL:-http://localhost:8080/api}"

echo "BASE_URL=$BASE_URL"
echo

echo "1. Sending Error Log..."
curl -sS -X POST "$BASE_URL/logs" \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "auth-service",
    "logLevel": "ERROR",
    "message": "Invalid token signature detected",
    "hostName": "auth-pod-01"
  }' | cat

echo -e "\n\nLog sent. Waiting 2 seconds for processing..."
sleep 2

echo -e "\n2. Fetching Incidents..."
curl -sS "$BASE_URL/incidents" | cat

echo -e "\n\n✅ Test Finished."
