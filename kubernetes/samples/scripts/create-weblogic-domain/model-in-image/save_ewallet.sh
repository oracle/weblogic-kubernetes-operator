#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Usage: save_ewallet.sh <domain uid> <namespace> <secret name> <opss paasphase>
#
#
if [ "$#" -ne 4 ]; then
    echo "Usage: save_ewallet.sh <domain uid> <namespace> <secret name> <opss paasphase>"
    exit 1
fi
domainuid=$1
namespace=$2
secret=$3
passphrase=$4

kubectl -n ${namespace} describe configmap ${domainuid}-weblogic-domain-introspect-cm | sed -n '/ewallet.p12/ {n;n;p}' > ewallet.p12
kubectl -n ${namespace} delete secret ${secret}
kubectl -n ${namespace} \
  create secret generic ${secret} \
  --from-literal=passphrase=${passphrase} \
  --from-file=ewallet.p12 
