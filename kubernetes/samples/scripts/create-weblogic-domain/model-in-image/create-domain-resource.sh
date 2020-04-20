#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script deploys the domain in WORKDIR/k8s-domain.yaml. If "-predelete" is
# specified, it also deletes any existing domain and waits for the existing domain's
# pods to exit prior to deploying WORKDIR/k8s-domain.yaml.
#
#
# Optional parameter:
#   -predelete                - Delete the existing domain and wait
#                               for its pods to exit before deploying.
#
# Optional environment variables:
#   WORKDIR                   - Working directory for the sample with at least
#                               10g of space. Defaults to 
#                               '/tmp/$USER/model-in-image-sample-work-dir'.
#   DOMAIN_UID                - Defaults to 'sample-domain1'
#   DOMAIN_NAMESPACE          - Defaults to '${DOMAIN_UID}-ns'
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

source $SCRIPTDIR/env-init.sh

case "${1:-}" in
  -predelete) 
      echo "@@ Info: Deleting WebLogic domain '${DOMAIN_UID}' if it already exists."
      kubectl -n ${DOMAIN_NAMESPACE} delete domain ${DOMAIN_UID} --ignore-not-found
      $SCRIPTDIR/util-wl-pod-wait.sh -p 0
      ;;

  "") ;;

  *)  echo "@@ Error: Unknown parameter '$1'." ; exit 1; ;;
esac

echo "@@"
echo "@@ Info: Calling 'kubectl apply -f \$WORKDIR/k8s-domain.yaml'."

kubectl apply -f $WORKDIR/k8s-domain.yaml

echo "@@"
echo "@@ Info: Your Model in Image domain resource deployed!"
echo "@@"
echo "@@ Info: To watch pods start and get their status:"
echo "           - run 'kubectl get pods -n ${DOMAIN_NAMESPACE} --watch' and ctrl-c when done watching"
echo "           - or run 'SCRIPTDIR/util-wl-pod-wait.sh -n $DOMAIN_NAMESPACE -d $DOMAIN_UID -p 3 -v'"
echo "@@"
echo "@@ Info: If the introspector job fails or you see any other unexpected issue, see 'User Guide -> Manage WebLogic Domains -> Model in Image -> Debugging' in the documentation."
echo "@@"
