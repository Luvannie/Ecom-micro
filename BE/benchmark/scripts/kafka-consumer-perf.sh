#!/usr/bin/env bash
# Kafka consumer benchmark via kafka-consumer-perf-test.sh inside the
# kafka container. Reports throughput and end-to-end latency.
# Usage: ./kafka-consumer-perf.sh [num-messages]

set -euo pipefail

NUM_MESSAGES=${1:-50000}
TOPIC=${TOPIC:-bench-test}

CONTAINER=$(docker ps --filter "ancestor=apache/kafka:3.7.0" --format "{{.Names}}" | head -1)
if [[ -z "$CONTAINER" ]]; then
    echo "Kafka container not found" >&2
    exit 1
fi

mkdir -p "$(dirname "$0")/../baselines"

echo "=== Consumer perf test ==="
docker exec "$CONTAINER" //opt/kafka/bin/kafka-consumer-perf-test.sh \
    --topic "$TOPIC" \
    --messages "$NUM_MESSAGES" \
    --broker-list localhost:9092 2>&1 | tee \
    "$(dirname "$0")/../baselines/$(date +%Y-%m-%d)-kafka-consumer.txt"
