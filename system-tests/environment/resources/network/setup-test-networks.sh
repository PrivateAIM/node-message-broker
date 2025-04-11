#!/usr/bin/env bash

#
# Creates a docker network.
#
# Args (positional)
#   1 - network name
#   2 - subnet (CIDR)
#
create_network() {
  echo -n "Creating network '$1'..."
  docker network inspect $1 >/dev/null 2>&1 || \
    docker network create $1 --subnet=$2 > /dev/null 2>&1
  if [ $? -ne 0 ]; then
    echo "FAILED"
    exit 1
  else
    echo "OK"
  fi
}

create_network "hub" "172.99.0.0/24"
create_network "node" "172.99.10.0/24"
create_network "hub-node-intercomm" "172.99.20.0/24"


