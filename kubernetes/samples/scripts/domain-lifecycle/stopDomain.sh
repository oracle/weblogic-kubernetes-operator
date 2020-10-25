# !/bin/sh
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh

function usage() {

  cat << EOF

  This script stops a deployed WebLogic domain by patching
  'spec.serverStartPolicy' attribute of domain resource to 'NEVER'.
  This change will cause the operator to initiate shutdown of the
  domain's WebLogic server instance pods if the pods are running.
 
  Usage:
 
    $(basename $0) [-n mynamespace] [-d mydomainuid] [-m kubecli]
  
    -d <domain_uid>     : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>      : Domain namespace. Default is 'sample-domain1-ns'.

    -m <kubernetes_cli> : Kubernetes command line interface. Default is 'kubectl'.

    -h                  : This help.
   
EOF
exit $1
}

kubernetesCli=${KUBERNETES_CLI:-kubectl}
domainUid="sample-domain1"
domainNamespace="sample-domain1-ns"

while getopts "n:d:m:h" opt; do
  case $opt in
    n) domainNamespace="${OPTARG}"
    ;;
    d) domainUid="${OPTARG}"
    ;;
    m) kubernetesCli="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

set -eu
set -o pipefail

if ! [ -x "$(command -v ${kubernetesCli})" ]; then
  fail "${kubernetesCli} is not installed"
fi

serverStartPolicy=`${kubernetesCli} -n ${domainNamespace} get domain ${domainUid} -o=jsonpath='{.spec.serverStartPolicy}'`

if [ "${serverStartPolicy}" == 'NEVER' ]; then 
  echo "[INFO] The domain '${domainUid}' is already stopped or stopping. The value of 'spec.serverStartPolicy' attribute on the domain resource is 'NEVER'. The $(basename $0) script will exit without making any changes."
  exit 0
fi

echo "[INFO] Patching domain '${domainUid}' in namespace '${domainNamespace}' from serverStartPolicy='${serverStartPolicy}' to 'NEVER'."

${kubernetesCli} -n ${domainNamespace} patch domain ${domainUid} --type='json' \
  -p='[{"op": "replace", "path": "/spec/serverStartPolicy", "value": "NEVER" }]'

echo "[INFO] Successfully patched domain '${domainUid}' in namespace '${domainNamespace}' with 'NEVER' start policy!"
