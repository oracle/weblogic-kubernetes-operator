# !/bin/sh
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

function usage() {

  cat << EOF

  This is a helper script for changing the 'spec.configuration.model.onlineUpdate.enabled' field
  of a deployed domain to 'true'. 
 
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

echo "@@ Info: Patching domain '${DOMAIN_UID}' in namespace '${DOMAIN_NAMESPACE}' to enable onlineUpdate to 'true'."
kubectl -n ${DOMAIN_NAMESPACE} patch domain ${DOMAIN_UID} --type='json' \
  -p='[{"op": "replace", "path": "/spec/configuration/model/onlineUpdate", "value": {"enabled" : 'true'} }]'

cat << EOF
@@
@@ Info: Domain '${DOMAIN_UID}' in namespace '${DOMAIN_NAMESPACE}' successfully patched with onlineUpdate/enabled set to 'true'!"
@@ Done.
EOF
