#!/usr/bin/env bash

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

MASTER_REALM_ID="b2d4b7f0-0228-4049-abe5-fc12176a067d"
ADMIN_USER_ID="5fd1234d-1e43-4cf1-8276-02ee8331c721"
ANALYSIS_NAME="hub-node-comm-test-analysis-145362b2-ebef-411c-9f21-8459bb33d83f"

REFERENCED_PROJECT_NAME="hub-node-comm-test-6412c97b-e1b6-4f88-97a2-372fa7496e25"

echo "obtaining auth admin token..."
HUB_AUTH_ACCESS_TOKEN=$(curl -s --fail-with-body \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&username=admin&password=start123" \
    -X POST https://auth.privateaim.net/token |\
    jq -r '.access_token')

echo "fetching referenced project id..."
REFERENCED_PROJECT_ID=$(curl -s -g --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Accept: application/json" \
    -X GET "https://api.privateaim.net/projects?filter[name]=${REFERENCED_PROJECT_NAME}" |\
    jq -r '.data[0].id')

ANALYSES_SETUP_TEMPLATE=$(cat "${BASE_DIR}/analyses-setup-template.json")

ANALYSES_SETUP=$(echo "${ANALYSES_SETUP_TEMPLATE}" |\
    sed "s#<ANALYSIS_NAME>#${ANALYSIS_NAME}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#" |\
    sed "s#<USER_ID>#${ADMIN_USER_ID}#" |\
    sed "s#<PROJECT_ID>#${REFERENCED_PROJECT_ID}#")

echo "creating analysis..."
curl -s --fail-with-body \
    -H "Authorization: Bearer ${HUB_AUTH_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${ANALYSES_SETUP}" \
    -X POST https://api.privateaim.net/analyses > /dev/null
if [ $? -ne 0 ]; then
    echo "failed to create analysis"
    exit 1
else
    echo "created analysis"
fi
