# !/bin/sh
# Copyright (c) 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script can be used to create a WDT encryption passphrase Kubernetes secret
# for a model-in-image operator image.
#
# When a WDT model is encrypted, a WDT encryption passphrase is required to allow WDT
# to encrypt/decrypt passwords that are stored directly in the model.
#
# NOTE: A WDT encryption passphrase is rarely needed. Instead, you can put
#       private WDT model data in a Kubernetes secret and access the secret 
#       using a model macro.
#

set -eu

kubectl -n sample-domain1-ns delete secret wdt-encrypt-passphrase-secret --ignore-not-found

kubectl -n sample-domain1-ns create secret generic wdt-encrypt-passphrase-secret \
  --from-literal=passphrase=welcome1

#
# Set the weblogic.domainUID label. This is optional, but useful during cleanup
# and inventorying of resources specific to a particular domain.
#

kubectl -n sample-domain1-ns label secret wdt-encrypt-passphrase-secret \
  weblogic.domainUID=domain1



