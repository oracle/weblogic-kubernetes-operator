# !/bin/sh
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

function usage() {

  cat << EOF

  This is a helper script for changing the 'spec.restartVersion' field
  of a deployed domain. This change will cause the operator to initiate
  a rolling restart of the resource's WebLogic pods if the pods are
  already running.
 
  Usage:
 
    $(basename $0) [-n mynamespace] [-d mydomainuid]
  
    -d <domain_uid>     : Default is 'sample-domain1'.

    -n <namespace>      : Default is 'sample-domain1-ns'.

    -?                  : This help.
   
EOF
}

set -e

DOMAIN_UID="sample-domain1"
DOMAIN_NAMESPACE="sample-domain1-ns"

while [ ! "$1" = "" ]; do
  if [ ! "$1" = "-?" ] && [ "$2" = "" ]; then
    echo "Syntax Error. Pass '-?' for usage."
    exit 1
  fi
  case "$1" in
    -n) DOMAIN_NAMESPACE="${2}"
        ;;
    -d) DOMAIN_UID="${2}"
        ;;
    -?) usage
        exit 1
        ;;
    *)  echo "Syntax Error. Pass '-?' for usage."
        exit 1
        ;;
  esac
  shift
  shift
done

set -eu
set -o pipefail

currentRV=`kubectl -n ${DOMAIN_NAMESPACE} get domain ${DOMAIN_UID} -o=jsonpath='{.spec.restartVersion}'`

nextRV=$((currentRV + 1))

echo "@@ Info: Patching domain '${DOMAIN_UID}' in namespace '${DOMAIN_NAMESPACE}' from restartVersion='${currentRV}' to restartVersion='${nextRV}'."

kubectl -n ${DOMAIN_NAMESPACE} patch domain ${DOMAIN_UID} --type='json' \
  -p='[{"op": "replace", "path": "/spec/restartVersion", "value": "'${nextRV}'" }]'

cat << EOF
@@
@@ Info: Domain '${DOMAIN_UID}' in namespace '${DOMAIN_NAMESPACE}' successfully patched with restartVersion '${nextRV}'!"

   To wait until pods reach the new restart version and/or to get their status:

      kubectl get pods -n ${DOMAIN_NAMESPACE} --watch
       # (ctrl-c once all pods are running and ready)

             -or-

      utils/wl-pod-wait.sh -n $DOMAIN_NAMESPACE -d $DOMAIN_UID -p 3 

   Expect the operator to restart the domain's pods until all of them
   have label 'weblogic.domainRestartVersion="$nextRV"."

@@ Done.
EOF
