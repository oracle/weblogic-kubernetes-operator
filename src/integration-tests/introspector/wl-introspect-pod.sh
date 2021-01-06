#!/bin/bash
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

/weblogic-operator/scripts/introspectDomain.sh
ret="$?"

echo "INTROSPECT_DOMAIN_EXIT=$ret"

if [ "$ret" = "0" ]; then
  # The pod's readiness probe check for this file
  touch /tmp/ready
fi

echo In "$0" SLEEPING

while [ 1 -eq 1 ]; do
  sleep 10
done
