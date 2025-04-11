#!/usr/bin/env bash

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

HUB_AUTH_BASE_URL="http://localhost:3001"
HUB_API_BASE_URL="http://localhost:3000"

#### OVERALL SETTINGS ####
MASTER_REALM_NAME="master"
ADMIN_USER_NAME="admin"

ROBOT_A_NAME="system-test-node-a-robot"
ROBOT_A_SECRET=$(cat "$BASE_DIR"/../secrets/robot-secret-node-a.txt)
ROBOT_B_NAME="system-test-node-b-robot"
ROBOT_B_SECRET=$(cat "$BASE_DIR"/../secrets/robot-secret-node-b.txt)
ROBOT_C_NAME="system-test-node-c-robot"
ROBOT_C_SECRET=$(cat "$BASE_DIR"/../secrets/robot-secret-node-c.txt)

# PUBLIC KEYS in hex format (expected by the hub)
NODE_A_PUBLIC_KEY=$(cat "$BASE_DIR"/../secrets/pub-key-node-a.pem | xxd -p | tr -d '\n')
NODE_B_PUBLIC_KEY=$(cat "$BASE_DIR"/../secrets/pub-key-node-b.pem | xxd -p | tr -d '\n')
NODE_C_PUBLIC_KEY=$(cat "$BASE_DIR"/../secrets/pub-key-node-c.pem | xxd -p | tr -d '\n')

PROJECT_NAME="system-test-project"
ANALYSIS_NAME="system-test-analysis"

NODE_A_NAME="system-test-node-a"
NODE_B_NAME="system-test-node-b"
NODE_C_NAME="system-test-node-c"


# READ POST BODY TEMPLATES --------------------------------------------------------------
echo -n "Reading resource setup templates..."
ROBOT_ACCOUNT_SETUP_TEMPLATE=$(cat "${BASE_DIR}/robot-account-setup-template.json")
PROJECT_SETUP_TEMPLATE=$(cat "${BASE_DIR}/project-setup-template.json")
ANALYSIS_SETUP_TEMPLATE=$(cat "${BASE_DIR}/analysis-setup-template.json")
NODE_SETUP_TEMPLATE=$(cat "${BASE_DIR}/node-setup-template.json")
PROJECT_NODE_SETUP_TEMPLATE=$(cat "${BASE_DIR}/project-node-setup-template.json")
ANALYSIS_NODE_SETUP_TEMPLATE=$(cat "${BASE_DIR}/analysis-node-setup-template.json")
echo "OK"


# ADMIN TOKEN ---------------------------------------------------------------------------
echo -n "Obtaining auth admin token..."
AUTH_TOKEN=$(curl -s --fail-with-body \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&username=admin&password=start123" \
    -X POST "${HUB_AUTH_BASE_URL}/token" |\
    jq -r '.access_token')
if [ -z "${AUTH_TOKEN}" ]; then
    echo "FAILED"
    exit 1;
else
    echo "OK"
fi

# MASTER REALM ID -----------------------------------------------------------------------
echo -n "Obtaining master realm id..."
MASTER_REALM_ID=$(curl -s -G --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Accept: application/json" \
    --data-urlencode "filter[name]=${MASTER_REALM_NAME}" \
    -X GET "${HUB_AUTH_BASE_URL}/realms" |\
    jq -r '.data[0].id')
if [ -z "${MASTER_REALM_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

# ADMIN USER ID -------------------------------------------------------------------------
echo -n "Obtaining admin user id..."
ADMIN_USER_ID=$(curl -s -G --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Accept: application/json" \
    --data-urlencode "filter[name]=${ADMIN_USER_NAME}" \
    -X GET "${HUB_AUTH_BASE_URL}/users" |\
    jq -r '.data[0].id')
if [ -z "${ADMIN_USER_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi


# SET UP ROBOT ACCOUNTS -----------------------------------------------------------------
ROBOT_A_SETUP=$(echo "${ROBOT_ACCOUNT_SETUP_TEMPLATE}" |\
    sed "s#<ROBOT_SECRET>#${ROBOT_A_SECRET}#" |\
    sed "s#<ROBOT_NAME>#${ROBOT_A_NAME}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#")

ROBOT_B_SETUP=$(echo "${ROBOT_ACCOUNT_SETUP_TEMPLATE}" |\
    sed "s#<ROBOT_SECRET>#${ROBOT_B_SECRET}#" |\
    sed "s#<ROBOT_NAME>#${ROBOT_B_NAME}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#")

ROBOT_C_SETUP=$(echo "${ROBOT_ACCOUNT_SETUP_TEMPLATE}" |\
    sed "s#<ROBOT_SECRET>#${ROBOT_C_SECRET}#" |\
    sed "s#<ROBOT_NAME>#${ROBOT_C_NAME}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#")

echo -n "Creating robot account a..."
ROBOT_A_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${ROBOT_A_SETUP}" \
    -X POST "${HUB_AUTH_BASE_URL}/robots" |\
    jq -r '.id')
if [ -z "${ROBOT_A_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo -n "Creating robot account b..."
ROBOT_B_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${ROBOT_B_SETUP}" \
    -X POST "${HUB_AUTH_BASE_URL}/robots" |\
    jq -r '.id')
if [ -z "${ROBOT_B_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo -n "Creating robot account c..."
ROBOT_C_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${ROBOT_C_SETUP}" \
    -X POST "${HUB_AUTH_BASE_URL}/robots" |\
    jq -r '.id')
if [ -z "${ROBOT_C_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo "ROBOT_A_ID: ${ROBOT_A_ID}"
echo "ROBOT_B_ID: ${ROBOT_B_ID}"
echo "ROBOT_C_ID: ${ROBOT_C_ID}"

# SET UP PROJECTS -----------------------------------------------------------------------
PROJECT_SETUP=$(echo "${PROJECT_SETUP_TEMPLATE}" |\
    sed "s#<PROJECT_NAME>#${PROJECT_NAME}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#" |\
    sed "s#<USER_ID>#${ADMIN_USER_ID}#")

echo -n "Creating project..."
PROJECT_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PROJECT_SETUP}" \
    -X POST "${HUB_API_BASE_URL}/projects" |\
    jq -r '.id')
if [ -z "${PROJECT_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo "PROJECT ID: ${PROJECT_ID}"

# SET UP ANALYSIS -----------------------------------------------------------------------
ANALYSIS_SETUP=$(echo "${ANALYSIS_SETUP_TEMPLATE}" |\
    sed "s#<ANALYSIS_NAME>#${ANALYSIS_NAME}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#" |\
    sed "s#<USER_ID>#${ADMIN_USER_ID}#" |\
    sed "s#<PROJECT_ID>#${PROJECT_ID}#"
)

echo -n "Creating analysis..."
ANALYSIS_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${ANALYSIS_SETUP}" \
    -X POST "${HUB_API_BASE_URL}/analyses" |\
    jq -r '.id')
if [ -z "${ANALYSIS_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo "ANALYSIS ID: ${ANALYSIS_ID}"

# SET UP NODES --------------------------------------------------------------------------
NODE_A_SETUP=$(echo "${NODE_SETUP_TEMPLATE}" |\
    sed "s#<NODE_NAME>#${NODE_A_NAME}#" |\
    sed "s#<ROBOT_ID>#${ROBOT_A_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#" |\
    sed "s#<PUBLIC_KEY>#${NODE_A_PUBLIC_KEY}#")

NODE_B_SETUP=$(echo "${NODE_SETUP_TEMPLATE}" |\
    sed "s#<NODE_NAME>#${NODE_B_NAME}#" |\
    sed "s#<ROBOT_ID>#${ROBOT_B_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#" |\
    sed "s#<PUBLIC_KEY>#${NODE_B_PUBLIC_KEY}#")

NODE_C_SETUP=$(echo "${NODE_SETUP_TEMPLATE}" |\
    sed "s#<NODE_NAME>#${NODE_C_NAME}#" |\
    sed "s#<ROBOT_ID>#${ROBOT_C_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#" |\
    sed "s#<PUBLIC_KEY>#${NODE_C_PUBLIC_KEY}#")

echo -n "Creating node a..."
NODE_A_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${NODE_A_SETUP}" \
    -X POST "${HUB_API_BASE_URL}/nodes" |\
    jq -r '.id')
if [ -z "${NODE_A_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo -n "Creating node b..."
NODE_B_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${NODE_B_SETUP}" \
    -X POST "${HUB_API_BASE_URL}/nodes" |\
    jq -r '.id')
if [ -z "${NODE_B_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo -n "Creating node c..."
NODE_C_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${NODE_C_SETUP}" \
    -X POST "${HUB_API_BASE_URL}/nodes" |\
    jq -r '.id')
if [ -z "${NODE_C_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo "NODE A ID: ${NODE_A_ID}"
echo "NODE B ID: ${NODE_B_ID}"
echo "NODE C ID: ${NODE_C_ID}"

# SET UP PROJECT NODES ------------------------------------------------------------------
PROJECT_NODE_A_SETUP=$(echo "${PROJECT_NODE_SETUP_TEMPLATE}" |\
    sed "s#<PROJECT_ID>#${PROJECT_ID}#" |\
    sed "s#<NODE_ID>#${NODE_A_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#g")

PROJECT_NODE_B_SETUP=$(echo "${PROJECT_NODE_SETUP_TEMPLATE}" |\
    sed "s#<PROJECT_ID>#${PROJECT_ID}#" |\
    sed "s#<NODE_ID>#${NODE_B_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#g")

PROJECT_NODE_C_SETUP=$(echo "${PROJECT_NODE_SETUP_TEMPLATE}" |\
    sed "s#<PROJECT_ID>#${PROJECT_ID}#" |\
    sed "s#<NODE_ID>#${NODE_C_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#g")

echo -n "Creating project node a..."
PROJECT_NODE_A_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PROJECT_NODE_A_SETUP}" \
    -X POST "${HUB_API_BASE_URL}/project-nodes" |\
    jq -r '.id')
if [ -z "${PROJECT_NODE_A_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo -n "Creating project node b..."
PROJECT_NODE_B_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PROJECT_NODE_B_SETUP}" \
    -X POST "${HUB_API_BASE_URL}/project-nodes" |\
    jq -r '.id')
if [ -z "${PROJECT_NODE_B_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo -n "Creating project node c..."
PROJECT_NODE_C_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PROJECT_NODE_C_SETUP}" \
    -X POST "${HUB_API_BASE_URL}/project-nodes" |\
    jq -r '.id')
if [ -z "${PROJECT_NODE_C_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo "PROJECT NODE A ID: ${PROJECT_NODE_A_ID}"
echo "PROJECT NODE B ID: ${PROJECT_NODE_B_ID}"
echo "PROJECT NODE C ID: ${PROJECT_NODE_C_ID}"

# SET UP ANALYSIS NODES -----------------------------------------------------------------
ANALYSIS_NODE_A_SETUP=$(echo "${ANALYSIS_NODE_SETUP_TEMPLATE}" |\
    sed "s#<ANALYSIS_ID>#${ANALYSIS_ID}#" |\
    sed "s#<NODE_ID>#${NODE_A_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#g")

ANALYSIS_NODE_B_SETUP=$(echo "${ANALYSIS_NODE_SETUP_TEMPLATE}" |\
    sed "s#<ANALYSIS_ID>#${ANALYSIS_ID}#" |\
    sed "s#<NODE_ID>#${NODE_B_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#g")

ANALYSIS_NODE_C_SETUP=$(echo "${ANALYSIS_NODE_SETUP_TEMPLATE}" |\
    sed "s#<ANALYSIS_ID>#${ANALYSIS_ID}#" |\
    sed "s#<NODE_ID>#${NODE_C_ID}#" |\
    sed "s#<REALM_ID>#${MASTER_REALM_ID}#g")

echo -n "Creating analysis node a..."
ANALYSIS_NODE_A_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${ANALYSIS_NODE_A_SETUP}" \
    -X POST "${HUB_API_BASE_URL}/analysis-nodes" |\
    jq -r '.id')
if [ -z "${ANALYSIS_NODE_A_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo -n "Creating analysis node b..."
ANALYSIS_NODE_B_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${ANALYSIS_NODE_B_SETUP}" \
    -X POST "${HUB_API_BASE_URL}/analysis-nodes" |\
    jq -r '.id')
if [ -z "${ANALYSIS_NODE_B_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo -n "Creating analysis node c..."
ANALYSIS_NODE_C_ID=$(curl -s --fail-with-body \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${ANALYSIS_NODE_C_SETUP}" \
    -X POST "${HUB_API_BASE_URL}/analysis-nodes" |\
    jq -r '.id')
if [ -z "${ANALYSIS_NODE_C_ID}" ]; then
    echo "FAILED"
    exit 1
else
    echo "OK"
fi

echo "ANALYSIS NODE A ID: ${ANALYSIS_NODE_A_ID}"
echo "ANALYSIS NODE B ID: ${ANALYSIS_NODE_B_ID}"
echo "ANALYSIS NODE C ID: ${ANALYSIS_NODE_C_ID}"

echo -n "$ROBOT_A_ID" > "$BASE_DIR"/robot-id-node-a.txt
echo -n "$ROBOT_B_ID" > "$BASE_DIR"/robot-id-node-b.txt
echo -n "$ROBOT_C_ID" > "$BASE_DIR"/robot-id-node-c.txt
echo -n "$ANALYSIS_ID" > "$BASE_DIR"/analysis-id.txt
