#!/usr/bin/env bash

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

MASTER_REALM_ID="b2d4b7f0-0228-4049-abe5-fc12176a067d"
NODE_A_NAME="Node A"
NODE_B_NAME="Node B"

REFERENCED_ROBOT_A_NAME="Node A"
REFERENCED_ROBOT_B_NAME="Node B"

echo "obtaining auth admin token..."
HUB_AUTH_ACCESS_TOKEN=$(curl -s --fail-with-body \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&username=admin&password=start123" \
    -X POST https://auth.privateaim.net/token |\
    jq -r '.access_token')

echo "fetching referenced robot a id..."
REFERENCED_ROBOT_A_ID=$(curl -s -g -G --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Accept: application/json" \
    --data-urlencode "filter[name]=${REFERENCED_ROBOT_A_NAME}" \
    -X GET "https://auth.privateaim.net/robots" |\
    jq -r '.data[0].id')

echo "fetching referenced robot b id..."
REFERENCED_ROBOT_B_ID=$(curl -s -g -G --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Accept: application/json" \
    --data-urlencode "filter[name]=${REFERENCED_ROBOT_B_NAME}" \
    -X GET "https://auth.privateaim.net/robots" |\
    jq -r '.data[0].id')

if [ -z "${REFERENCED_ROBOT_A_ID}" ]; then
    echo "ID for robot a is missing"
    exit 1
fi
if [ -z "${REFERENCED_ROBOT_B_ID}" ]; then
    echo "ID for robot b is missing"
    exit 1
fi


NODE_SETUP_TEMPLATE=$(cat "${BASE_DIR}/node-setup-template.json")

NODE_A_SETUP=$(echo "${NODE_SETUP_TEMPLATE}" |\
    sed "s#<NODE_NAME>#${NODE_A_NAME}#" |\
    sed "s#<ROBOT_ID>#${REFERENCED_ROBOT_A_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#")

NODE_B_SETUP=$(echo "${NODE_SETUP_TEMPLATE}" |\
    sed "s#<NODE_NAME>#${NODE_B_NAME}#" |\
    sed "s#<ROBOT_ID>#${REFERENCED_ROBOT_B_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#")


echo "creating node a..."
curl -s --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${NODE_A_SETUP}" \
    -X POST https://api.privateaim.net/nodes > /dev/null
if [ $? -ne 0 ]; then
    echo "failed to create node a"
    exit 1
else
    echo "created node a"
fi


echo "creating node b..."
curl -s --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${NODE_B_SETUP}" \
    -X POST https://api.privateaim.net/nodes > /dev/null
if [ $? -ne 0 ]; then
    echo "failed to create node b"
    exit 1
else
    echo "created node b"
fi
