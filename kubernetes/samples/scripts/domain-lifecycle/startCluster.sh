# !/bin/sh
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh

function usage() {

  cat << EOF

  This script starts a WebLogic cluster in a domain by patching
  'spec.clusters[<cluster-name>].serverStartPolicy' attribute of the domain
  resource to 'IF_NEEDED'. This change will cause the operator to initiate
  startup of cluster's WebLogic server instance pods if the pods are not
  already running.
 
  Usage:
 
    $(basename $0) -c mycluster [-n mynamespace] [-d mydomainuid] [-m kubecli]
  
    -c <cluster-name>   : Cluster name parameter is required.

    -d <domain_uid>     : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>      : Domain namespace. Default is 'sample-domain1-ns'.

    -m <kubernetes_cli> : Kubernetes command line interface. Default is 'kubectl'.

    -h                  : This help.
   
EOF
exit $1
}

set -eu

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

  isValidCluster=""
  validateClusterName "${domainUid}" "${domainNamespace}" "${clusterName}" isValidCluster

  if [ "${isValidCluster}" != 'true' ]; then
    validationError "cluster ${clusterName} is not part of domain ${domainUid} in namespace ${domainNamespace}."
  fi

  failIfValidationErrors
}

initialize

# Get the domain in json format
domainJson=$(${kubernetesCli} get domain ${domainUid} -n ${domainNamespace} -o json)

# Get server start policy for this cluster
startPolicy=$(echo ${domainJson} | jq -r '(.spec.clusters[] | select (.clusterName == "'${clusterName}'") | .serverStartPolicy)')
if [ "${startPolicy}" == "null" ]; then
  startPolicy=$(echo ${domainJson} | jq -r .spec.serverStartPolicy)
fi

if [ "${startPolicy}" == 'IF_NEEDED' ]; then 
  echo "[INFO] The cluster '${clusterName}' is already started or starting. The effective value of 'spec.clusters[?(clusterName="${clusterName}"].serverStartPolicy' attribute on the domain resource is 'IF_NEEDED'. The $(basename $0) script will exit without making any changes."
  exit 0
fi

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

echo "[INFO] Successfully patched cluster '${clusterName}' with 'IF_NEEDED' start policy!."
