#!/bin/sh
# Copyright (c) 2021, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh
if [ "${debug}" == "true" ]; then set -x; fi;

usage() {

  cat << EOF

  This script scales a WebLogic cluster in a domain by patching the
  'spec.clusters[<cluster-name>].replicas' attribute of the domain
  resource. This change will cause the operator to perform a scaling
  operation for the WebLogic cluster based on the value of replica count.
 
  Usage:
 
    $(basename $0) -c mycluster -r replicas [-n mynamespace] [-d mydomainuid] [-m kubecli]
  
    -c <cluster-name>   : Cluster name parameter is required.

    -r <replicas>       : Replica count, parameter is required.

    -d <domain_uid>     : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>      : Domain namespace. Default is 'sample-domain1-ns'.

    -m <kubernetes_cli> : Kubernetes command line interface. Default is 'kubectl' if KUBERNETES_CLI env
                          variable is not set. Otherwise default is the value of KUBERNETES_CLI env variable.

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
replicas=""

while getopts "vc:n:m:d:r:h" opt; do
  case $opt in
    c) clusterName="${OPTARG}"
    ;;
    n) domainNamespace="${OPTARG}"
    ;;
    d) domainUid="${OPTARG}"
    ;;
    r) replicas="${OPTARG}"
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
initialize() {

  validateErrors=false

  validateKubernetesCliAvailable
  validateJqAvailable

  if [ -z "${clusterName}" ]; then
    validationError "Please specify cluster name using '-c' parameter e.g. '-c cluster-1'."
  fi

  if [ -z "${replicas}" ]; then
    validationError "Please specify replica count using '-r' parameter e.g. '-r 3'."
  fi

  failIfValidationErrors
}

initialize

# Get the cluster in json format. Changed made on 08/15/2022
#domainJson=$(${kubernetesCli} get domain.v8.weblogic.oracle ${domainUid} -n ${domainNamespace} -o json --ignore-not-found)

isValidCluster=""
validateClusterName "${domainUid}" "${domainNamespace}" "${clusterName}" isValidCluster
if [ "${isValidCluster}" != 'true' ]; then
  printError "cluster ${clusterName} is not part of domain ${domainUid} in namespace ${domainNamespace}. Please make sure that cluster name is correct."
  exit 1
fi

clusterJson=$(${kubernetesCli} get cluster ${clusterName} -n ${domainNamespace} -o json --ignore-not-found)
printInfo "clusterJson content before changes: ${clusterJson}"
if [ -z "${clusterJson}" ]; then
  printError "Unable to get cluster resource for cluster '${clusterName}' in namespace '${domainNamespace}'. Please make sure that a Cluster exists for cluster '${clusterName}' and that this Cluster is referenced by the Domain."
  exit 1
fi

# Changed made on 08/15/2022
#isReplicasInAllowedRange "${clusterJson}" "${clusterName}" "${replicas}" replicasInAllowedRange range
isClusterReplicasInAllowedRange "${clusterJson}" "${clusterName}" "${replicas}" replicasInAllowedRange range
if [ "${replicasInAllowedRange}" == 'false' ]; then
  printError "Replicas value is not in the allowed range of ${range}. Exiting."
  exit 1
fi

# Changed made on 08/15/2022
printInfo "Patching replicas for cluster: '${clusterName}' to '${replicas}'."
#createPatchJsonToUpdateReplicas "${clusterJson}" "${clusterName}" "${replicas}" patchJson
createPatchJsonToUpdateReplicasUsingClusterResource "${clusterJson}" "${clusterName}" "${replicas}" patchJson

# Changed made on 08/15/2022
#executePatchCommand "${kubernetesCli}" "${domainUid}" "${domainNamespace}" "${patchJson}" "${verboseMode}"
printInfo "Patch command to execute is: ${kubernetesCli} ${clusterName} ${domainNamespace} ${patchJson} ${verboseMode}"
executeClusterPatchCommand "${kubernetesCli}" "${clusterName}" "${domainNamespace}" "${patchJson}" "${verboseMode}"

clusterJson=$(${kubernetesCli} get cluster ${clusterName} -n ${domainNamespace} -o json --ignore-not-found)
printInfo "clusterJson content after changes: ${clusterJson}"

printInfo "Successfully patched replicas for cluster '${clusterName}'!"
