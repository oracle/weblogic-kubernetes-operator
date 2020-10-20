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

  This is a helper script for starting a cluster by patching
  it's 'spec.serverStartPolicy' field to 'IF_NEEDED'. This change will cause
  the operator to initiate startup of cluster's WebLogic server pods if the 
  pods are not already running.
 
  Usage:
 
    $(basename $0) -c mycluster [-n mynamespace] [-d mydomainuid]
  
    -c <cluster>        : Cluster name parameter is required.

    -d <domain_uid>     : Default is 'sample-domain1'.

    -n <namespace>      : Default is 'sample-domain1-ns'.

    -m <kubernetes_cli> : Kubernetes command line interface. Default is 'kubectl'.

    -h                  : This help.
   
EOF
exit $1
}

set -e

kubernetesCli=${KUBERNETES_CLI:-kubectl}
clusterName=""
domainUid="sample-domain1"
domainNamespace="sample-domain1-ns"

while getopts "c:n:m:d:h" opt; do
  case $opt in
    c) clusterName="${OPTARG}"
    ;;
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

#
# Function to perform validations, read files and initialize workspace
#
function initialize {

  # Validate the required files exist
  validateErrors=false

  validateKubernetesCliAvailable
  validateJqAvailable

  if [ -z "${clusterName}" ]; then
    validationError "Please specify cluster name using '-c' parameter e.g. '-c cluster-1'."
  fi

  failIfValidationErrors
}

initialize

# Get the domain in json format
domainJson=$(${kubernetesCli} get domain ${domainUid} -n ${domainNamespace} -o json)

# Get server start policy for this server
startPolicy=$(echo ${domainJson} | jq -r '(.spec.clusters[] | select (.clusterName == "'${clusterName}'") | .serverStartPolicy)')

if [ -z ${startPolicy} ]; then
  # cluster start policy doesn't exist, add a new IF_NEEDED policy
  echo "[INFO] Patching start policy of cluster '${clusterName}' to 'IF_NEEDED'."
  startPolicy=$(echo ${domainJson} | jq .spec.clusters | jq -c '.[.| length] |= . + {"clusterName":"'${clusterName}'","serverStartPolicy":"IF_NEEDED"}')
else 
  # Server start policy exists, set policy value to IF_NEEDED
  echo "[INFO]Patching start policy of cluster '${clusterName}' from '${startPolicy}' to 'IF_NEEDED'."
  startPolicy=$(echo ${domainJson} | jq '(.spec.clusters[] | select (.clusterName == "'${clusterName}'") | .serverStartPolicy) |= "IF_NEEDED"' | jq -cr '(.spec.clusters)')
fi

patchServerStartPolicy="{\"spec\": {\"clusters\": "${startPolicy}"}}"
${kubernetesCli} patch domain ${domainUid} -n ${domainNamespace} --type='merge' --patch "${patchServerStartPolicy}"

if [ $? != 0 ]; then
  exit $?
fi
echo "[INFO] Successfully patched cluster '${clusterName}' with 'IF_NEEDED' start policy!."
