# !/bin/sh
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/../common/utility.sh
source ${scriptDir}/../common/validate.sh
source ${scriptDir}/helper.sh
if [ "${debug}" == "true" ]; then set -x; fi;

function usage() {

  cat << EOF

  This is a helper script for starting a managed server in a domain by patching
  it's 'spec.serverStartPolicy' field to 'ALWAYS'. This change will cause
  the operator to initiate start of WebLogic managed server pod if the pod 
  is not already running.
 
  Usage:
 
    $(basename $0) -s managed-server1 [-n mynamespace] [-d mydomainuid] {-m kubecli]
  
    -s <server_name>           : Server name parameter is required.

    -d <domain_uid>            : Default is 'sample-domain1'.

    -n <namespace>             : Default is 'sample-domain1-ns'.

    -k <keep_replica_constant> : Keep replica count constant. Default behavior is to increment replica count.

    -m <kubernetes_cli>        : Kubernetes command line interface. Default is 'kubectl'.

    -h                         : This help.
   
EOF
exit $1
}

kubernetesCli=${KUBERNETES_CLI:-kubectl}
serverName=""
clusterName=""
domainName=""
domainUid="sample-domain1"
domainNamespace="sample-domain1-ns"
keepReplicaConstant=false

while getopts "kd:n:m:s:h:" opt; do
  case $opt in
    s) serverName="${OPTARG}"
    ;;
    n) domainNamespace="${OPTARG}"
    ;;
    d) domainUid="${OPTARG}"
    ;;
    k) keepReplicaConstant=true;
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

  if [ -z "${serverName}" ]; then
    validationError "Please specify server name using '-s' parameter e.g. '-s managed-server1'."
  fi

  failIfValidationErrors
}

initialize

# Get the domain in json format
domainJson=$(${kubernetesCli} get domain ${domainUid} -n ${domainNamespace} -o json)
if [ $? -ne 0 ]; then
  echo "[ERROR} Unable to get domain resource. Please make sure 'domain_uid' and 'namespace' provided with '-d' and '-n' arguments are correct."
  exit 1
fi

# check if server pod is already running
${kubernetesCli} get pod ${domainUid}-${serverName} -n ${domainNamespace} > /dev/null 2>&1
if [ $? -eq 0 ]; then
  echo "Server pod ${domainUid}-${serverName} already exists. Exiting."
  exit 1
fi

# Validate that specified server is either part of a cluster or is an independent managed server
validateServerAndFindCluster "${domainUid}" "${domainNamespace}" isValidServer clusterName
if [ "${isValidServer}" != 'true' ]; then
  echo "Server ${serverName} is not part of any cluster and it's not an independent managed server. Please make sure that server name specified is correct."
  exit 1
fi

# Create server start policy patch with ALWAYS value
serverStartPolicy=ALWAYS
createServerStartPolicyPatch "${domainJson}" "${serverName}" "${serverStartPolicy}" serverStartPolicyPatch policy

if [[ -n ${clusterName} && "${keepReplicaConstant}" != 'true' ]]; then
  # if server is part of a cluster and replica count needs to be updated, patch the replica count and server start policy
  operation="INCREMENT"
  createReplicaPatch "${domainJson}" "${clusterName}" "${operation}" replicaPatch replicaCount
  if [ "${replicaPatch}" == "MAX_REPLICA_COUNT_EXCEEDED" ]; then 
   exit 1
  fi
  patchJson="{\"spec\": {\"clusters\": "${replicaPatch}",\"managedServers\": "${serverStartPolicyPatch}"}}"
  echo "[INFO] Patching start policy of server '${serverName}' from '${policy}' to 'ALWAYS' and \
incrementing replica count for cluster '${clusterName}'."
else 
  # if server is an independent managed server or replica count needs to stay constant, only patch server start policy
  echo "[INFO] Patching start policy of '${serverName}' from '${policy}' to 'ALWAYS'."
  patchJson="{\"spec\": {\"managedServers\": "${serverStartPolicyPatch}"}}"
fi
${kubernetesCli} patch domain ${domainUid} -n ${domainNamespace} --type='merge' --patch "${patchJson}" 

if [ $? != 0 ]; then
  exit $?
fi

if [[ -n ${clusterName} && "${keepReplicaConstant}" != 'true' ]]; then
cat << EOF
[INFO] Successfully patched server '${serverName}' with 'ALWAYS' start policy!

       The replica count for cluster '${clusterName}' updated to ${replicaCount}.
EOF
else 
  echo "[INFO] Successfully patched server '${serverName}' with 'ALWAYS' start policy!"
fi
