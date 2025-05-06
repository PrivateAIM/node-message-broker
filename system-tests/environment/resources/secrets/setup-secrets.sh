#!/usr/bin/env bash

BASE_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 || exit 1 ; pwd -P )"

#
# Creates an EC key pair using a X9.62/SECG curve over a 256 bit prime field.
#
# Args (positional)
#   1 - key pair suffix (used for output files)
#
create_ec_key_pair() {
  openssl ecparam -name prime256v1 -genkey -noout -out "$BASE_DIR"/priv-key-"$1".pem
  openssl ec -in "$BASE_DIR"/priv-key-"$1".pem -pubout -out "$BASE_DIR"/pub-key-"$1".pem

  truncate -s -1 "$BASE_DIR"/priv-key-"$1".pem
  # We can do this since it's a test environment...
  chmod 644 "$BASE_DIR"/priv-key-"$1".pem
}

#
# Creates a random robot secret.
#
# Args (positional)
#   1 - node suffix (used for output files)
#
create_robot_secret() {
  openssl rand -hex 12 > "$BASE_DIR"/robot-secret-"$1".txt
  truncate -s -1 "$BASE_DIR"/robot-secret-"$1".txt
}

create_ec_key_pair node-a
create_ec_key_pair node-b
create_ec_key_pair node-c

create_robot_secret node-a
create_robot_secret node-b
create_robot_secret node-c
