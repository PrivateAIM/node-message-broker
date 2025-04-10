#!/usr/bin/env bash

#
# Removes a docker network.
#
# Args (positional)
#   1 - network name
#
remove_network() {
  echo -n "Removing network '$1'..."
  docker network rm -f $1 >/dev/null 2>&1
  if [ $? -ne 0 ]; then
      echo "FAILED"
      exit 1
    else
      echo "OK"
    fi
}

remove_network "hub"
remove_network "node"
remove_network "hub-node-intercomm"
