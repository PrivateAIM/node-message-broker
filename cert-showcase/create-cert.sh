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

rm -Rf $TMP_DIR
