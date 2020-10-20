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

  This is a helper script for stopping a cluster in a domain by patching
  it's 'spec.serverStartPolicy' field to 'NEVER'. This change will cause
  the operator to initiate shutdown of cluster's WebLogic pods if the pods 
  are running.
 
  Usage:
 
    $(basename $0) [-n mynamespace] [-d mydomainuid]
  
    -c <cluster>        : Cluster name parameter is required.

    -d <domain_uid>     : Default is 'sample-domain1'.

    -n <namespace>      : Default is 'sample-domain1-ns'.

    -m <kubernetes_cli> : Kubernetes command line interface. Default is 'kubectl'.

    -?                  : This help.
   
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
  # cluster start policy doesn't exist, add a new NEVER policy
  echo "[INFO] Patching start policy of cluster '${clusterName}' to 'NEVER'."
  startPolicy=$(echo ${domainJson} | jq .spec.clusters | jq -c '.[.| length] |= . + {"clusterName":"'${clusterName}'","serverStartPolicy":"NEVER"}')
else 
  # Server start policy exists, set policy value to NEVER
  echo "[INFO] Patching start policy of cluster '${clusterName}' from '${startPolicy}' to 'NEVER'."
  startPolicy=$(echo ${domainJson} | jq '(.spec.clusters[] | select (.clusterName == "'${clusterName}'") | .serverStartPolicy) |= "NEVER"' | jq -cr '(.spec.clusters)')
fi

patch="{\"spec\": {\"clusters\": "${startPolicy}"}}"
${kubernetesCli} patch domain ${domainUid} -n ${domainNamespace} --type='merge' --patch "${patch}"

if [ $? != 0 ]; then
  exit $?
fi
echo "[INFO] Successfully patched cluster '${clusterName}' with 'NEVER' start policy!"
