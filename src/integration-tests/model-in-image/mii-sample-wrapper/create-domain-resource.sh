#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script deploys a domain resource file.
#
# If "-predelete" is specified, it also kubectl deletes any existing
# domain and waits for the existing domain's pods to exit prior to deploying.
#
# Optional parameters:
#   -predelete                - Delete the existing domain (if any) and
#                               wait for its pods to exit before deploying.
#   -dryrun                   - Show but don't do. Show-and-tell output is
#                               prefixed with 'dryrun:'.
#
# Optional environment variables:
#   WORKDIR                   - Working directory for the sample with at least
#                               10GB of space. Defaults to 
#                               '/tmp/$USER/model-in-image-sample-work-dir'.
#   DOMAIN_UID                - Defaults to 'sample-domain1'
#   DOMAIN_NAMESPACE          - Defaults to 'sample-domain1-ns'
#   DOMAIN_RESOURCE_FILENAME  - Location of domain resource file.
#                               Defaults to WORKDIR/domain-resources/mii-DOMAIN_UID.yaml
#                            

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

source $SCRIPTDIR/env-init.sh

pre_delete=false
dry_run=false

while [ ! -z "${1:-}" ]; do
  case "${1:-}" in
    -predelete) pre_delete=true ;;
    -dryrun) dry_run=true ;;
    *) echo "@@ Error: Unknown parameter '$1'. Optionally pass '-predelete' or '-dryrun'." ; exit 1; ;;
  esac
  shift
done

#
# If "-predelete" is set, then delete the existing running domain (if any)
# and wait for its pods to exit.
#

if [ "$pre_delete" = "true" ]; then

# TBD remember to include instructions for 'predelete' in the sample (for safety - an old domain's pods interfering is a common problem)
  echo "@@ Info: Deleting WebLogic domain '${DOMAIN_UID}' if it already exists and waiting for its pods to exit."
  if [ "$dry_run" = "false" ]; then
    kubectl -n ${DOMAIN_NAMESPACE} delete domain ${DOMAIN_UID} --ignore-not-found
    echo "@@ Info: Calling $WORKDIR/utils/wl-pod-wait.sh -p 0 -d $DOMAIN_UID -n $DOMAIN_NAMESPACE -q"
    $WORKDIR/utils/wl-pod-wait.sh -p 0 -d $DOMAIN_UID -n $DOMAIN_NAMESPACE -q
  else
    echo dryrun: kubectl -n ${DOMAIN_NAMESPACE} delete domain ${DOMAIN_UID} --ignore-not-found
    echo dryrun: $WORKDIR/utils/wl-pod-wait.sh -p 0 -d $DOMAIN_UID -n $DOMAIN_NAMESPACE -q
  fi
fi

#
# Apply the domain resource.
#

echo "@@"
echo "@@ Info: Calling 'kubectl apply -f 'WORKDIR/$DOMAIN_RESOURCE_FILENAME'."

if [ "$dry_run" = "false" ]; then
  kubectl apply -f $WORKDIR/$DOMAIN_RESOURCE_FILENAME
else
  echo dryrun: kubectl apply -f $WORKDIR/$DOMAIN_RESOURCE_FILENAME
fi

#
# We're done, but let's print out some help.
#

function get_exec_command() {
  # Echo a kubectl exec command that invoke's the sample application
  # using a 'curl' from the admin server pod.
  local cluster_name=cluster-1
  local cluster_service_name=${DOMAIN_UID}-cluster-${cluster_name}
  local cluster_service_name=$(tr [A-Z_] [a-z-] <<< $cluster_service_name)
  local admin_name=admin-server
  local admin_service_name=${DOMAIN_UID}-${admin_name}
  local admin_service_name=$(tr [A-Z_] [a-z-] <<< $admin_service_name)
  echo "kubectl exec -n $DOMAIN_NAMESPACE $admin_service_name -- bash -c \"curl -s -S -m 10 http://$cluster_service_name:8001/myapp_war/index.jsp\""
}

# TBD consider adding a utility that generates the the
#     following help for the sample... see also
#     ingress help

cat << EOF
@@
@@ Info: Your Model in Image domain resource deployed!

   To wait until pods start and/or get their status:
      kubectl get pods -n ${DOMAIN_NAMESPACE} --watch 
       # (ctrl-c once all pods are running and ready)
             -or-
      'WORKDIR/utils/wl-pod-wait.sh -n $DOMAIN_NAMESPACE -d $DOMAIN_UID -p 3'

   To get the domain status:
      'kubectl -n $DOMAIN_NAMESPACE get domain $DOMAIN_UID -o yaml'
             -or-
      'kubectl -n $DOMAIN_NAMESPACE describe domain $DOMAIN_UID'

   To invoke the sample's web app via 'kubectl exec':
      - The sample's admin server pod must be running.
      - The sample's cluster pods must be running and ready.
      - Run: '$(get_exec_command)'

    NOTE! If the introspector job fails or you see unexpected issues, then
          see 'User Guide->Manage WebLogic Domains->Model in Image->Debugging'
          in the documentation."

@@
@@ Info: Done! Script '$(basename $0)' completed without errors.
@@
EOF
