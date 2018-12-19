#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

/weblogic-operator/scripts/introspectDomain.sh

# the introspectTest.sh script looks for this exact line:
echo "INTROSPECT_DOMAIN_EXIT=$?"

echo In "$0" SLEEPING

while [ 1 -eq 1 ]; do 
  sleep 10
done
