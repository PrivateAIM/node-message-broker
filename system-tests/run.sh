#!/usr/bin/env bash

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

teardown() {
  echo "### Tearing down system test environment"
  sh "$BASE_DIR"/environment/teardown.sh
}

echo "Setting up test environment"
sh "$BASE_DIR"/environment/setup.sh

ANALYSIS_ID=$(cat "$BASE_DIR"/environment/resources/hub/analysis-id.txt)
echo "Using analysis ID: $ANALYSIS_ID"

echo "Running system test for sending messages to dedicated recipients..."
"$BASE_DIR"/tests/mb-test-cli send-dedicated-messages \
  --analysis-id="$ANALYSIS_ID" \
  --bootstrap-nodes="http://localhost:18088,http://localhost:18089" \
  --node-auth-base-url="http://localhost:18080" \
  --node-auth-client-id="message-broker" \
  --node-auth-client-secret="thtiFoImj6rvrfTvKkiOlSigRcYLbQwf"

if [ $? -ne 0 ]; then
  echo "TEST FAILED"
  teardown
  exit 1
else
  echo "TEST SUCCEEDED"
fi

echo "Running system test for sending broadcast messages to various recipients..."
"$BASE_DIR"/tests/mb-test-cli send-broadcast-messages \
  --analysis-id="$ANALYSIS_ID" \
  --bootstrap-nodes="http://localhost:18088,http://localhost:18089,http://localhost:18090" \
  --node-auth-base-url="http://localhost:18080" \
  --node-auth-client-id="message-broker" \
  --node-auth-client-secret="thtiFoImj6rvrfTvKkiOlSigRcYLbQwf"

if [ $? -ne 0 ]; then
  echo "TEST FAILED"
  teardown
  exit 1
else
  echo "TEST SUCCEEDED"
fi

teardown
