# !/bin/sh
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../common/utility.sh
source ${scriptDir}/../common/validate.sh
source ${scriptDir}/helper.sh

function usage() {

  cat << EOF

  This is a helper script for starting a domain by patching
  it's 'spec.serverStartPolicy' field to 'IF_NEEDED'. This change will cause
  the operator to initiate startup of domain's WebLogic pods if the pods 
  are not already running.
 
  Usage:
 
    $(basename $0) [-n mynamespace] [-d mydomainuid]
  
    -d <domain_uid>     : Default is 'sample-domain1'.

    -n <namespace>      : Default is 'sample-domain1-ns'.

    -m <kubernetes_cli> : Kubernetes command line interface. Default is 'kubectl'.

    -h                  : This help.
   
EOF
exit $1
}

set -e

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

echo "[INFO] Patching domain '${domainUid}' from serverStartPolicy='${serverStartPolicy}' to 'IF_NEEDED'."

${kubernetesCli} -n ${domainNamespace} patch domain ${domainUid} --type='json' \
  -p='[{"op": "replace", "path": "/spec/serverStartPolicy", "value": "IF_NEEDED" }]'

echo "[INFO] Successfully patched domain '${domainUid}' in namespace '${domainNamespace}' with 'IF_NEEDED' start policy!"
