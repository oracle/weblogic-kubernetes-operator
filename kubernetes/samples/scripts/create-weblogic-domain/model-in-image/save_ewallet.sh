#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Usage: save_ewallet.sh <domain uid> <namespace> <secret name> <opss paasphase> [<secret name>]
#
# default secret name is sample-domain1-opss-walletfile-secret}
#
#

# TBD 
#   - refactor - and move wallet to a dedicated secret
#   - advance script to have both save and restore options (allow specifying both)
#   - for save option, since the secret already exists we can extract the passphrase from the secret instead of needing to pass it in
#   - for save option, can deduce the secret and namespace from the domain resource - no need to pass them in

set -eu

if [ "$#" -lt 3 ]; then
    echo "Usage: save_ewallet.sh <domain uid> <namespace> <opss paasphase> <secret name>"
    exit 1
fi
domainuid=$1
namespace=$2
passphrase=$3
secret=${4:-sample-domain1-opss-walletfile-secret}

echo kubectl -n ${namespace} describe configmap ${domainuid}-weblogic-domain-introspect-cm | sed -n '/ewallet.p12/ {n;n;p}' > ewallet.p12
echo kubectl -n ${namespace} delete secret ${secret} --ignore-not-found
echo kubectl -n ${namespace} \
  create secret generic ${secret} \
  --from-file=ewallet.p12 
