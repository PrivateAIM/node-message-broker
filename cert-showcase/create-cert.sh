#!/usr/bin/env bash

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

TMP_DIR=$(mktemp -d)

cd $TMP_DIR
openssl req -x509 -newkey rsa:4096 -sha256 -days 3650 \
  -nodes -keyout privateaim.key -out privateaim.pem -subj "/CN=privateaim.internal" \
  -addext "subjectAltName=DNS:*.privateaim.internal" && \
cat privateaim.pem > "$BASE_DIR"/config/self-signed-cert.pem && \
cat privateaim.pem > "$BASE_DIR"/config/haproxy/self-signed.pem && \
cat privateaim.key >> "$BASE_DIR"/config/haproxy/self-signed.pem

docker cp $(docker create --name truststore eclipse-temurin:21-jre-alpine@sha256:2a0bbb1db6d8db42c66ed00c43d954cf458066cc37be12b55144781da7864fdf):/opt/java/openjdk/lib/security/cacerts . && docker rm truststore

docker run --name cert-setup \
  --rm \
  -v ./cacerts:/tmp/cacerts \
  -v "$BASE_DIR"/config/self-signed-cert.pem:/tmp/custom-certs/self-signed-cert.pem:ro \
  --entrypoint=/bin/sh \
  eclipse-temurin:21-jre-alpine@sha256:2a0bbb1db6d8db42c66ed00c43d954cf458066cc37be12b55144781da7864fdf \
  -c \
  'chown 0:0 /tmp/cacerts && /opt/java/openjdk/bin/keytool -import -file /tmp/custom-certs/self-signed-cert.pem -alias privateaim -keystore /tmp/cacerts -trustcacerts -storepass changeit -noprompt'

cp ./cacerts "$BASE_DIR"/truststore-with-added-certs

rm -Rf $TMP_DIR
