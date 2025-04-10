#!/usr/bin/env bash

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

# The following is just necessary in a local context - not a problem during CI/CD runs since the machine is fresh
# everytime the test runs.
echo "### Cleaning up potentially left resources of previous runs"
if [ -f "$BASE_DIR"/node/node-docker-compose.yml ]; then
  docker compose -f "$BASE_DIR"/node/node-docker-compose.yml down -v
  docker compose -f "$BASE_DIR"/hub/hub-docker-compose.yml down -v
fi

echo "### Setting up test networks"
sh "$BASE_DIR"/resources/network/setup-test-networks.sh

echo "### Setting up secrets"
sh "$BASE_DIR"/resources/secrets/setup-secrets.sh

echo "### Setting up Hub instance"
docker compose -f "$BASE_DIR"/hub/hub-docker-compose.yml up -d

echo "### Waiting for Hub core component to enter 'healthy' state"
stateCore=""
until [ "$stateCore" = "healthy" ]
do
  containerName=$(docker compose -f "$BASE_DIR"/hub/hub-docker-compose.yml ps -q core)
  stateCore=$(docker inspect -f '{{.State.Health.Status}}' $containerName)
  sleep 5
done

echo "### Waiting for Hub auth component to enter 'healthy' state"
stateAuth=""
until [ "$stateAuth" = "healthy" ]
do
  containerName=$(docker compose -f "$BASE_DIR"/hub/hub-docker-compose.yml ps -q authup)
  stateAuth=$(docker inspect -f '{{.State.Health.Status}}' $containerName)
  sleep 5
done


echo "### Setting up Hub resources for test"
sh "$BASE_DIR"/resources/hub/setup-hub-resources.sh

echo "### Setting up Node resources for test"
robot_id_node_a=$(cat "$BASE_DIR"/resources/hub/robot-id-node-a.txt)
robot_id_node_b=$(cat "$BASE_DIR"/resources/hub/robot-id-node-b.txt)
robot_id_node_c=$(cat "$BASE_DIR"/resources/hub/robot-id-node-c.txt)
cat "$BASE_DIR"/node/node-docker-compose.tpl.yml |\
  sed "s#<ROBOT_ID_NODE_A>#${robot_id_node_a}#" |\
  sed "s#<ROBOT_ID_NODE_B>#${robot_id_node_b}#" |\
  sed "s#<ROBOT_ID_NODE_C>#${robot_id_node_c}#" > "$BASE_DIR"/node/node-docker-compose.yml |
  docker compose -f "$BASE_DIR"/node/node-docker-compose.yml up --build -d

echo "### Waiting for Node auth component (Keycloak) to enter 'healthy' state"
stateNodeAuth=""
until [ "$stateNodeAuth" = "healthy" ]
do
  containerName=$(docker compose -f "$BASE_DIR"/node/node-docker-compose.yml ps -q keycloak)
  stateNodeAuth=$(docker inspect -f '{{.State.Health.Status}}' $containerName)
  sleep 5
done

echo "### Waiting for Nodes to enter 'healthy' state"
stateNodes=""
until [ "$stateNodes" = "healthy" ]
do
    containerNameNodeA=$(docker compose -f "$BASE_DIR"/node/node-docker-compose.yml ps -q node-a)
    containerNameNodeB=$(docker compose -f "$BASE_DIR"/node/node-docker-compose.yml ps -q node-b)
    containerNameNodeC=$(docker compose -f "$BASE_DIR"/node/node-docker-compose.yml ps -q node-c)
    stateNodeA=$(docker inspect -f '{{.State.Health.Status}}' $containerNameNodeA)
    stateNodeB=$(docker inspect -f '{{.State.Health.Status}}' $containerNameNodeB)
    stateNodeC=$(docker inspect -f '{{.State.Health.Status}}' $containerNameNodeC)

    if [ "$stateNodeA" = "healthy" ] && [ "$stateNodeB" = "healthy" ] && [ "$stateNodeC" = "healthy" ]; then
      stateNodes="healthy"
    fi
done
