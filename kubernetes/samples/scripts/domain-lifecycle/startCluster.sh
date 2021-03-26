# !/bin/sh
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh
if [ "${debug}" == "true" ]; then set -x; fi;

function usage() {

  cat << EOF

  This script starts a WebLogic cluster in a domain by patching
  'spec.clusters[<cluster-name>].serverStartPolicy' attribute of the domain
  resource to 'IF_NEEDED'. This change will cause the operator to initiate
  startup of cluster's WebLogic server instance pods if the pods are not
  already running and the spec.replicas or
  'spec.clusters[<cluster-name>].serverStartPolicy' is set higher than zero.
 
  Usage:
 
    $(basename $0) -c mycluster [-n mynamespace] [-d mydomainuid] [-m kubecli]
  
    -c <cluster-name>   : Cluster name (required parameter).

    -d <domain_uid>     : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>      : Domain namespace. Default is 'sample-domain1-ns'.

    -m <kubernetes_cli> : Kubernetes command line interface. Default is 'kubectl' if KUBERNETES_CLI env
                          variable is not set. Otherwise default is the value of KUBERNETES_CLI env variable.

    -v <verbose_mode>   : Enables verbose mode. Default is 'false'.

    -h                  : This help.
   
EOF
exit $1
}

set -eu

kubernetesCli=${KUBERNETES_CLI:-kubectl}
clusterName=""
domainUid="sample-domain1"
domainNamespace="sample-domain1-ns"
verboseMode=false
patchJson=""

while getopts "vc:n:m:d:h" opt; do
  case $opt in
    c) clusterName="${OPTARG}"
    ;;
    n) domainNamespace="${OPTARG}"
    ;;
    d) domainUid="${OPTARG}"
    ;;
    m) kubernetesCli="${OPTARG}"
    ;;
    v) verboseMode=true;
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
domainJson=$(${kubernetesCli} get domain ${domainUid} -n ${domainNamespace} -o json --ignore-not-found)
if [ -z "${domainJson}" ]; then
  printError "Unable to get domain resource for domain '${domainUid}' in namespace '${domainNamespace}'. Please make sure the 'domain_uid' and 'namespace' specified by the '-d' and '-n' arguments are correct. Exiting."
  exit 1
fi

isValidCluster=""
validateClusterName "${domainUid}" "${domainNamespace}" "${clusterName}" isValidCluster
if [ "${isValidCluster}" != 'true' ]; then
  printError "cluster ${clusterName} is not part of domain ${domainUid} in namespace ${domainNamespace}. Please make sure that cluster name is correct."
  exit 1
fi

getDomainPolicy "${domainJson}" domainStartPolicy
# Fail if effective start policy of domain is NEVER or ADMIN_ONLY
if [[ "${domainStartPolicy}" == 'NEVER' || "${domainStartPolicy}" == 'ADMIN_ONLY' ]]; then
  printError "Cannot start cluster '${clusterName}', the domain is configured with a 'spec.serverStartPolicy' attribute on the domain resource of 'NEVER' or 'ADMIN_ONLY'."
  exit 1
fi

# Get server start policy for this cluster
getClusterPolicy "${domainJson}" "${clusterName}" startPolicy
if [ -z "${startPolicy}" ]; then
  startPolicy=${domainStartPolicy}
fi

if [ "${startPolicy}" == 'IF_NEEDED' ]; then 
  printInfo "No changes needed, exiting. The cluster '${clusterName}' is already started or starting. The effective value of 'spec.clusters[?(clusterName=\"${clusterName}\"].serverStartPolicy' attribute on the domain resource is 'IF_NEEDED'."
  exit 0
fi

# Set policy value to IF_NEEDED
printInfo "Patching start policy of cluster '${clusterName}' from '${startPolicy}' to 'IF_NEEDED'."
createPatchJsonToUpdateClusterPolicy "${domainJson}" "${clusterName}" "IF_NEEDED" patchJson

executePatchCommand "${kubernetesCli}" "${domainUid}" "${domainNamespace}" "${patchJson}" "${verboseMode}"

printInfo "Successfully patched cluster '${clusterName}' with 'IF_NEEDED' start policy!."
