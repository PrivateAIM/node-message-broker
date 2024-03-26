#!/usr/bin/env bash

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

MASTER_REALM_ID="b2d4b7f0-0228-4049-abe5-fc12176a067d"

REFERENCED_ANALYSIS_NAME="hub-node-comm-test-analysis-145362b2-ebef-411c-9f21-8459bb33d83f"
REFERENCED_NODE_A_NAME="Node A"
REFERENCED_NODE_B_NAME="Node B"

echo "obtaining auth admin token..."
HUB_AUTH_ACCESS_TOKEN=$(curl -s --fail-with-body \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&username=admin&password=start123" \
    -X POST https://auth.privateaim.net/token |\
    jq -r '.access_token')

echo "fetching referenced analysis id..."
REFERENCED_ANALYSIS_ID=$(curl -s -g -G --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Accept: application/json" \
    --data-urlencode "filter[name]=${REFERENCED_ANALYSIS_NAME}" \
    -X GET "https://api.privateaim.net/analyses" |\
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

if [ -z "${REFERENCED_ANALYSIS_ID}" ]; then
    echo "ID for analysis is missing"
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

ANALYSIS_NODES_SETUP_TEMPLATE=$(cat "${BASE_DIR}/analysis-nodes-setup-template.json")

ANALYSIS_NODES_A_SETUP=$(echo "${ANALYSIS_NODES_SETUP_TEMPLATE}" |\
    sed "s#<ANALYSIS_ID>#${REFERENCED_ANALYSIS_ID}#" |\
    sed "s#<NODE_ID>#${REFERENCED_NODE_A_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#g")

ANALYSIS_NODES_B_SETUP=$(echo "${ANALYSIS_NODES_SETUP_TEMPLATE}" |\
    sed "s#<ANALYSIS_ID>#${REFERENCED_ANALYSIS_ID}#" |\
    sed "s#<NODE_ID>#${REFERENCED_NODE_B_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#g")

echo "creating analysis to node a mapping..."
curl -s --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${ANALYSIS_NODES_A_SETUP}" \
    -X POST https://api.privateaim.net/analysis-nodes > /dev/null
if [ $? -ne 0 ]; then
    echo "failed to create analysis to node a mapping"
    exit 1
else
    echo "created analysis to node a mapping"
fi

echo "creating analysis to node b mapping..."
curl -s --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${ANALYSIS_NODES_B_SETUP}" \
    -X POST https://api.privateaim.net/analysis-nodes > /dev/null
if [ $? -ne 0 ]; then
    echo "failed to create analysis to node b mapping"
    exit 1
else
    echo "created analysis to node b mapping"
fi
