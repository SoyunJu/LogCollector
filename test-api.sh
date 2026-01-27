#!/bin/bash

# local Port 8082 / Docker Port 8081

BASE_URL="http://localhost:8082/api"

echo "1. Sending Error Log..."
curl -X POST "$BASE_URL/logs" \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "auth-service",
    "logLevel": "ERROR",
    "message": "Invalid token signature detected",
    "hostName": "auth-pod-01"
  }'

echo -e "\n\nLog sent. Waiting 2 seconds for processing..."
sleep 2

echo -e "\n2. Fetching Incidents..."

curl -s "$BASE_URL/incidents"

echo -e "\n\nTest Finished."