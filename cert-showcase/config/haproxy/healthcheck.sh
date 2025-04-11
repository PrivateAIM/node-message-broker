#!/usr/bin/env sh

wget -q --spider http://127.0.0.1:9999/stats
if [ $? -ne 0 ]; then
    exit 1
else
    exit 0
fi
