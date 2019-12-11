# !/bin/sh
# Copyright (c) 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This script can be used to create a WebLogic domain credentials secret for a model-in-image operator image.
#
# All operator domains must specify a credentials secret.
#

set -eu

kubectl -n sample-domain1-ns delete secret sample-domain1-weblogic-credentials --ignore-not-found

kubectl -n sample-domain1-ns \
  create secret generic sample-domain1-weblogic-credentials \
  --from-literal=username=weblogic \
  --from-literal=password=welcome1

#
# Set the weblogic.domainUID label. This is optional, but useful during cleanup
# and inventorying of resources specific to a particular domain.
#

kubectl -n sample-domain1-ns \
  label secret sample-domain1-weblogic-credentials \
  weblogic.domainUID=domain1
