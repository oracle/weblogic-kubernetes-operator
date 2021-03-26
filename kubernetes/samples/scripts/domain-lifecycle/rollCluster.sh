# !/bin/sh
# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh
if [ "${debug}" == "true" ]; then set -x; fi;

function usage() {

  cat << EOF

  This script initiates a rolling restart of the WebLogic cluster server pods in a domain by updating
  the value of the 'spec.clusters[<cluster-name>].restartVersion' attribute of the domain resource.
 
  Usage:
 
    $(basename $0) -c mycluster [-n mynamespace] [-d mydomainuid] [-r restartVersion] [-m kubecli]
  
    -c <cluster-name>   : Cluster name (required parameter).

    -d <domain_uid>     : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>      : Domain namespace. Default is 'sample-domain1-ns'.

    -r <restartVersion> : Restart version. If this parameter is not provided, 
                          then the script will generate the 'restartVersion' 
                          value of the cluster by incrementing the existing 
                          value. If the 'restartVersion' value doesn't exist
                          for the cluster then it will use the incremented value of
                          domain 'restartVersion'. If the domain 'restartVersion' also
                          doesn't exist or effective value is non-numeric, then 
                          the script will set the 'restartVersion' value to '1'.

    -m <kubernetes_cli> : Kubernetes command line interface. Default is 'kubectl'
                          if KUBERNETES_CLI env variable is not set. Otherwise 
                          the default is the value of the KUBERNETES_CLI env variable.

    -v <verbose_mode>   : Enables verbose mode. Default is 'false'.

    -h                  : This help.
   
EOF
exit $1
}

kubernetesCli=${KUBERNETES_CLI:-kubectl}
clusterName=""
domainUid="sample-domain1"
domainNamespace="sample-domain1-ns"
verboseMode=false
patchJson=""
restartVersion=""

while getopts "vc:n:m:d:r:h" opt; do
  case $opt in
    c) clusterName="${OPTARG}"
    ;;
    n) domainNamespace="${OPTARG}"
    ;;
    d) domainUid="${OPTARG}"
    ;;
    r) restartVersion="${OPTARG}"
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

set -eu

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

# if the restartVersion is not provided, generate the value of restartVersion
if [ -z "${restartVersion}" ]; then
  generateClusterRestartVersion "${domainJson}" "${clusterName}" restartVersion 
fi

printInfo "Patching restartVersion for cluster '${clusterName}' to '${restartVersion}'."
createPatchJsonToUpdateClusterRestartVersion "${domainJson}" "${clusterName}" "${restartVersion}" patchJson

executePatchCommand "${kubernetesCli}" "${domainUid}" "${domainNamespace}" "${patchJson}" "${verboseMode}"

printInfo "Successfully patched restartVersion for cluster '${clusterName}'!"
