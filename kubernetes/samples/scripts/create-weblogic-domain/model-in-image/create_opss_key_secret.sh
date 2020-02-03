# !/bin/sh
# Copyright (c) 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script can be used to create a secret for the OPSS Key passphrase for
# accessing a OPSS wallet that's associated with a JRF model-in-image operator image.
#
# The passphrase and wallet together allow an infrastructure database schema to be reused
# for lifecycle updates (for example shutting down a domain and restarting it) or for 
# sharing across different domains.
#
# TBD: NOTE FOR NOW Wallet can also be specified in this secret, but TBD will move
#            wallet to a dedicated secret...

set -eu

kubectl -n sample-domain1-ns delete secret sample-domain1-opss-key-passphrase-secret --ignore-not-found

kubectl -n sample-domain1-ns create secret generic sample-domain1-opss-key-passphrase-secret \
  --from-literal=passphrase=welcome1

#
# Set the weblogic.domainUID label. This is optional, but useful during cleanup
# and inventorying of resources specific to a particular domain.
#

kubectl -n sample-domain1-ns label secret sample-domain1-opss-key-passphrase-secret \
  weblogic.domainUID=domain1


