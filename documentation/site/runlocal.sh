#!/bin/bash

# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# `./runlocal.sh <your local port> <your local ip address>` causes the
# site to be available at `http://<your local ip address>:<your local
# port>/weblogic-kubernetes-operator/`. This is useful when running on
# a LAN. `./runlocal.sh` with no arguments continues to operate as
# originally writen. That is, the site is available at
# http://localhost:1313/weblogic-kubernetes-operator/.

# 1313 is the hugo default port
port=${1:-1313}
host=${2-localhost}

if [[ $host != 'localhost'* ]]; then
  bind="--bind $host"
fi

hugo server $bind --baseURL http://$host:$port/weblogic-kubernetes-operator --buildDrafts -p $port
