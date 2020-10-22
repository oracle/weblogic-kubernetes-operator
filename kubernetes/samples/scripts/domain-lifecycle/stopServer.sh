# !/bin/sh
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh
if [ "${debug}" == "true" ]; then set -x; fi;
set -eu

function usage() {

  cat << EOF

  This script stops a running WebLogic managed server in a domain by
  patching it's 'serverStartPolicy' field to 'NEVER'. This change will
  cause the operator to initiate shutdown of the WebLogic managed server
  pod if the pod is running.
 
  Usage:
 
    $(basename $0) -s myserver [-n mynamespace] [-d mydomainuid] [-k] [-m kubecli]
  
    -s <server_name>           : Server name parameter is required.

    -d <domain_uid>            : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>             : Domain namespace. Default is 'sample-domain1-ns'.
    
    -k <keep_replica_constant> : Keep replica count constant. Default behavior is to decrement replica count.

    -m <kubernetes_cli>        : Kubernetes command line interface. Default is 'kubectl'.

    -h                         : This help.
   
EOF
exit $1
}

kubernetesCli=${KUBERNETES_CLI:-kubectl}
serverName=""
clusterName=""
domainUid="sample-domain1"
domainNamespace="sample-domain1-ns"
keepReplicaConstant=false

while getopts "ks:m:n:d:h" opt; do
  case $opt in
    s) serverName="${OPTARG}"
    ;;
    n) domainNamespace="${OPTARG}"
    ;;
    m) kubernetesCli="${OPTARG}"
    ;;
    d) domainUid="${OPTARG}"
    ;;
    k) keepReplicaConstant=true;
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

  # Validate that server name parameter is specified.
  if [ -z "${serverName}" ]; then
    validationError "Please specify name of server to start using '-s' parameter e.g. '-s managed-server1'."
  fi

  failIfValidationErrors
}

initialize

# Get the cluster name for current server 
clusterName=$(${kubernetesCli} get pod ${domainUid}-${serverName} -n ${domainNamespace} -o=jsonpath="{.metadata.labels['weblogic\.clusterName']}")

# Get the domain in json format
domainJson=$(${kubernetesCli} get domain ${domainUid} -n ${domainNamespace} -o json)

# Create server start policy patch with NEVER value
currentPolicy=""
serverStartPolicy=NEVER
createServerStartPolicyPatch "${domainJson}" "${serverName}" "${serverStartPolicy}" serverStartPolicyPatch currentPolicy

if [[ -n "${clusterName}" && "${keepReplicaConstant}" != 'true' ]]; then
  # if server is part of a cluster and replica count needs to be updated, update replica count and patch server start policy
  operation="DECREMENT"
  createReplicaPatch "${domainJson}" "${clusterName}" "${operation}" replicaPatch replicaCount
  patchJson="{\"spec\": {\"clusters\": "${replicaPatch}",\"managedServers\": "${serverStartPolicyPatch}"}}"
  echo "[INFO] Patching start policy of server '${serverName}' from '${currentPolicy}' to 'NEVER' and decrementing replica count for cluster '${clusterName}'."
else
  # if server is an independent managed server or replica count needs to stay constant, only patch server start policy
  patchJson="{\"spec\": {\"managedServers\": "${serverStartPolicyPatch}"}}"
  echo "[INFO] Patching start policy of '${serverName}' from '${currentPolicy}' to 'NEVER'."
fi
${kubernetesCli} patch domain ${domainUid} -n ${domainNamespace} --type='merge' --patch "${patchJson}"

if [[ -n ${clusterName} && "${keepReplicaConstant}" != 'true' ]]; then
cat << EOF
[INFO] Successfully patched server '${serverName}' with 'NEVER' start policy!

       The replica count for cluster '${clusterName}' updated to ${replicaCount}.
EOF
else 
  echo "[INFO] Successfully patched server '${serverName}' with 'NEVER' start policy!"
fi
