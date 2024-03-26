#!/usr/bin/env bash

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

MASTER_REALM_ID="b2d4b7f0-0228-4049-abe5-fc12176a067d"

ROBOT_A_NAME="Node A"
ROBOT_A_SECRET="koeNkROTDKpynvV02ajv"

ROBOT_B_NAME="Node B"
ROBOT_B_SECRET="f1bxI1RVboHy46UHECqS"

ROBOT_SETUP_TEMPLATE=$(cat "${BASE_DIR}/robot-account-setup-template.json")

ROBOT_A_SETUP=$(echo "${ROBOT_SETUP_TEMPLATE}" |\
    sed "s#<ROBOT_SECRET>#${ROBOT_A_SECRET}#" |\
    sed "s#<ROBOT_NAME>#${ROBOT_A_NAME}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#")

ROBOT_B_SETUP=$(echo "${ROBOT_SETUP_TEMPLATE}" |\
    sed "s#<ROBOT_SECRET>#${ROBOT_B_SECRET}#" |\
    sed "s#<ROBOT_NAME>#${ROBOT_B_NAME}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#")

echo "obtaining auth admin token..."
HUB_AUTH_ACCESS_TOKEN=$(curl -s --fail \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&username=admin&password=start123" \
    -X POST https://auth.privateaim.net/token |\
    jq -r '.access_token')

echo "${HUB_AUTH_ACCESS_TOKEN}"

echo "creating robot a..."
curl -s --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${ROBOT_A_SETUP}" \
    -X POST https://auth.privateaim.net/robots > /dev/null
if [ $? -ne 0 ]; then
    echo "failed to create robot a"
    exit 1
else
    echo "created robot a"
fi

echo "creating robot b..."
curl -s --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${ROBOT_B_SETUP}" \
    -X POST https://auth.privateaim.net/robots > /dev/null
if [ $? -ne 0 ]; then
    echo "failed to create robot b"
    exit 1
else
    echo "created robot b"
fi
