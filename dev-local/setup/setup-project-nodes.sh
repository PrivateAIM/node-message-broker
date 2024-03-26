#!/usr/bin/env bash

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

MASTER_REALM_ID="b2d4b7f0-0228-4049-abe5-fc12176a067d"
REFERENCED_PROJECT_NAME="hub-node-comm-test-6412c97b-e1b6-4f88-97a2-372fa7496e25"
REFERENCED_NODE_A_NAME="Node A"
REFERENCED_NODE_B_NAME="Node B"

echo "obtaining auth admin token..."
HUB_AUTH_ACCESS_TOKEN=$(curl -s --fail-with-body \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&username=admin&password=start123" \
    -X POST https://auth.privateaim.net/token |\
    jq -r '.access_token')

echo "fetching referenced project id..."
REFEREBCED_PROJECT_ID=$(curl -s -g -G --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Accept: application/json" \
    --data-urlencode "filter[name]=${REFERENCED_PROJECT_NAME}" \
    -X GET "https://api.privateaim.net/projects" |\
    jq -r '.data[0].id')

echo "fetching referenced node a id..."
REFERENCED_NODE_A_ID=$(curl -s -g -G --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Accept: application/json" \
    --data-urlencode "filter[name]=${REFERENCED_NODE_A_NAME}" \
    -X GET "https://api.privateaim.net/nodes" |\
    jq -r '.data[0].id')

echo "fetching referenced node b id..."
REFERENCED_NODE_B_ID=$(curl -s -g -G --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Accept: application/json" \
    --data-urlencode "filter[name]=${REFERENCED_NODE_B_NAME}" \
    -X GET "https://api.privateaim.net/nodes" |\
    jq -r '.data[0].id')

if [ -z "${REFEREBCED_PROJECT_ID}" ]; then
    echo "ID for project is missing"
    exit 1
fi
if [ -z "${REFERENCED_NODE_A_ID}" ]; then
    echo "ID for node a is missing"
    exit 1
fi
if [ -z "${REFERENCED_NODE_B_ID}" ]; then
    echo "ID for node b is missing"
    exit 1
fi

PROJECT_NODES_SETUP_TEMPLATE=$(cat "${BASE_DIR}/project-nodes-setup-template.json")

PROJECT_NODE_A_SETUP=$(echo "${PROJECT_NODES_SETUP_TEMPLATE}" |\
    sed "s#<PROJECT_ID>#${REFEREBCED_PROJECT_ID}#" |\
    sed "s#<NODE_ID>#${REFERENCED_NODE_A_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#g")

PROJECT_NODE_B_SETUP=$(echo "${PROJECT_NODES_SETUP_TEMPLATE}" |\
    sed "s#<PROJECT_ID>#${REFEREBCED_PROJECT_ID}#" |\
    sed "s#<NODE_ID>#${REFERENCED_NODE_B_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#g")

echo "creating project - node a mapping..."
curl -s --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PROJECT_NODE_A_SETUP}" \
    -X POST https://api.privateaim.net/project-nodes > /dev/null
if [ $? -ne 0 ]; then
    echo "failed to create project - node a mapping"
    exit 1
else
    echo "created project - node a mapping"
fi

echo "creating project - node b mapping..."
curl -s --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PROJECT_NODE_B_SETUP}" \
    -X POST https://api.privateaim.net/project-nodes > /dev/null
if [ $? -ne 0 ]; then
    echo "failed to create project - node b mapping"
    exit 1
else
    echo "created project - node b mapping"
fi
