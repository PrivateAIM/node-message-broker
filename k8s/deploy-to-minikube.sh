#!/usr/bin/env bash

# Mandatory environment variables:
#
# - AUTH_JWKS_URL
# - HUB_AUTH_ROBOT_ID
# - ROBOT_SECRET
# - NODE_PRIVATE_KEY
# - NODE_MESSAGE_BROKER_HOST
# - NAMESPACE

# Optional environment variables:
#
# - HUB_AUTH_BASE_URL
# - HUB_BASE_URL
# - HUB_MESSENGER_BASE_URL

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

if [[ -z "${AUTH_JWKS_URL}" || -z "${HUB_AUTH_ROBOT_ID}" || -z "${ROBOT_SECRET}" || -z "${NODE_PRIVATE_KEY}" || -z "${NODE_MESSAGE_BROKER_HOST}" || -z "${NAMESPACE}" ]]; then
    echo "One or more mandatory environment variables are not set!"
    echo "Mandatory environment variables are:"
    echo ""
    echo " - AUTH_JWKS_URL"
    echo " - HUB_AUTH_ROBOT_ID"
    echo " - ROBOT_SECRET"
    echo " - NODE_PRIVATE_KEY"
    echo " - NODE_MESSAGE_BROKER_HOST"
    echo " - NAMESPACE"
    exit 1
fi

checkSuccessOrFailWithCode() {
    if [ $1 -ne 0 ]; then
        echo "FAILED"
        exit $2
    else
        echo "OK"
    fi
}

echo -n "Creating temporary working directory..."
WORK_DIR=`mktemp -d -p "${BASE_DIR}"`
checkSuccessOrFailWithCode $? 2

echo -n "Copying k8s manifest files..."
for f in "${BASE_DIR}"/manifests/*.yml; do
    cp "${f}" "${WORK_DIR}"
done
checkSuccessOrFailWithCode $? 3

echo -n "Preparing broker deployment..."
sed -i  -e "s#<AUTH_JWKS_URL>#${AUTH_JWKS_URL}#" \
        -e "s#<HUB_AUTH_ROBOT_ID>#${HUB_AUTH_ROBOT_ID}#" \
        -e "s#<HUB_AUTH_BASE_URL>#${HUB_AUTH_BASE_URL:-"https://auth.privateaim.dev"}#" \
        -e "s#<HUB_BASE_URL>#${HUB_BASE_URL:-"https://core.privateaim.dev"}#" \
        -e "s#<HUB_MESSENGER_BASE_URL>#${HUB_MESSENGER_BASE_URL:-"https://messenger.privateaim.dev"}#" \
        "${WORK_DIR}/broker-deployment.yml"
checkSuccessOrFailWithCode $? 4

echo -n "Preparing hub auth secret..."
sed -i  -e "s#<ROBOT_SECRET>#$(echo -n ${ROBOT_SECRET} | base64)#" \
        "${WORK_DIR}/hub-auth-secret.yml"
checkSuccessOrFailWithCode $? 5

echo -n "Preparing node private key..."
sed -i -e "s#<NODE_PRIVATE_KEY>#$(echo -n ${NODE_PRIVATE_KEY} | base64)#" \
        "${WORK_DIR}/node-secret.yml"
checkSuccessOrFailWithCode $? 6

echo -n "Preparing ingress..."
sed -i  -e "s#<NODE_MESSAGE_BROKER_HOST>#${NODE_MESSAGE_BROKER_HOST}#" \
        "${WORK_DIR}/ingress.yml"
checkSuccessOrFailWithCode $? 7

echo -n "Deleting previous image..."
minikube image rm docker.io/flame/node-message-broker:latest >/dev/null 2>&1
checkSuccessOrFailWithCode $? 8

echo -n "Creating Docker image..."
minikube image build -t docker.io/flame/node-message-broker:latest "${BASE_DIR}/.." >/dev/null 2>&1
checkSuccessOrFailWithCode $? 9

echo -n "Applying manifest files..."
minikube kubectl -- --namespace "${NAMESPACE}" apply -f "${WORK_DIR}/hub-auth-secret.yml" \
    -f "${WORK_DIR}/node-secret.yml" \
    -f "${WORK_DIR}/broker-db-service.yml" \
    -f "${WORK_DIR}/broker-db-statefulset.yml" \
    -f "${WORK_DIR}/broker-service.yml" \
    -f "${WORK_DIR}/broker-deployment.yml" \
    -f "${WORK_DIR}/ingress.yml" >/dev/null 2>&1
checkSuccessOrFailWithCode $? 10

echo -n "Deleting temporary working directory..."
rm -Rf "${WORK_DIR}"
checkSuccessOrFailWithCode $? 11
