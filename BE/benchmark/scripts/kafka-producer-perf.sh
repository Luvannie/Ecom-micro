#!/usr/bin/env bash
# Kafka producer benchmark via kafka-producer-perf-test.sh inside the
# kafka container. Compares acks=1 (leader-only) vs acks=all (durable).
# Usage: ./kafka-producer-perf.sh [num-records] [record-size-bytes]

set -euo pipefail

NUM_RECORDS=${1:-50000}
RECORD_SIZE=${2:-1024}
TOPIC=${TOPIC:-bench-test}

CONTAINER=$(docker ps --filter "ancestor=apache/kafka:3.7.0" --format "{{.Names}}" | head -1)
if [[ -z "$CONTAINER" ]]; then
    echo "Kafka container not found" >&2
    exit 1
fi

mkdir -p "$(dirname "$0")/../baselines"

echo "=== acks=1 (leader-only, fire-and-forget) ==="
docker exec "$CONTAINER" //opt/kafka/bin/kafka-producer-perf-test.sh \
    --topic "$TOPIC" \
    --num-records "$NUM_RECORDS" \
    --record-size "$RECORD_SIZE" \
    --throughput -1 \
    --producer-props bootstrap.servers=localhost:9092 \
    acks=1 2>&1 | tee "$(dirname "$0")/../baselines/$(date +%Y-%m-%d)-kafka-producer-acks1.txt"

echo
echo "=== acks=all (durable, requires replication) ==="
docker exec "$CONTAINER" //opt/kafka/bin/kafka-producer-perf-test.sh \
    --topic "$TOPIC" \
    --num-records "$NUM_RECORDS" \
    --record-size "$RECORD_SIZE" \
    --throughput -1 \
    --producer-props bootstrap.servers=localhost:9092 \
    acks=all 2>&1 | tee "$(dirname "$0")/../baselines/$(date +%Y-%m-%d)-kafka-producer-acks-all.txt"
