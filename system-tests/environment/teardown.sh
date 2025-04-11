#!/usr/bin/env bash

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

echo "### Removing system test containers"
docker compose \
  -f "$BASE_DIR"/node/node-docker-compose.yml \
  down -v

docker compose \
  -f "$BASE_DIR"/hub/hub-docker-compose.yml \
  down -v

echo "### Cleaning up docker networks"
sh "$BASE_DIR"/resources/network/teardown-test-networks.sh
