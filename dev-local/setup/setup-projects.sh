#!/usr/bin/env bash

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

MASTER_REALM_ID="b2d4b7f0-0228-4049-abe5-fc12176a067d"
ADMIN_USER_ID="5fd1234d-1e43-4cf1-8276-02ee8331c721"

PROJECT_NAME="hub-node-comm-test-6412c97b-e1b6-4f88-97a2-372fa7496e25"

PROJECT_SETUP_TEMPLATE=$(cat "${BASE_DIR}/projects-setup-template.json")

PROJECT_SETUP=$(echo "${PROJECT_SETUP_TEMPLATE}" |\
    sed "s#<PROJECT_NAME>#${PROJECT_NAME}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#" |\
    sed "s#<USER_ID>#${ADMIN_USER_ID}#")

echo "obtaining auth admin token..."
HUB_AUTH_ACCESS_TOKEN=$(curl -s --fail-with-body \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&username=admin&password=start123" \
    -X POST https://auth.privateaim.net/token |\
    jq -r '.access_token')

echo "creating project..."
curl -s --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PROJECT_SETUP}" \
    -X POST https://api.privateaim.net/projects > /dev/null
if [ $? -ne 0 ]; then
    echo "failed to create project"
    exit 1
else
    echo "created project"
fi
