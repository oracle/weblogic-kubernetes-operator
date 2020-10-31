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

  This script stops a running WebLogic managed server in a domain either by
  decreasing the value of 'spec.clusters[<cluster-name>].replicas' or by updating 
  'spec.managedServers[<server-name>].serverStartPolicy' attribute of the domain 
  resource or both as necessary. The 'spec.clusters[<cluster-name>].replicas' value
  can be kept constant by using '-k' option.
 
  Usage:
 
    $(basename $0) -s myserver [-n mynamespace] [-d mydomainuid] [-k] [-m kubecli] [-v]
  
    -s <server_name>           : Server name parameter is required.

    -d <domain_uid>            : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>             : Domain namespace. Default is 'sample-domain1-ns'.
    
    -k <keep_replica_constant> : Keep replica count constant. Default behavior is to decrement replica count.

    -m <kubernetes_cli>        : Kubernetes command line interface. Default is 'kubectl'.

    -v <verbose_mode>          : Enables verbose mode. Default is 'false'.

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
verboseMode=false
serverStartPolicy=NEVER
started=""
action=""
effectivePolicy=""
managedServerPolicy=""
stoppedWhenAlwaysPolicyReset=""

while getopts "vks:m:n:d:h" opt; do
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

  # Validate that server name parameter is specified.
  if [ -z "${serverName}" ]; then
    validationError "Please specify the server name using '-s' parameter e.g. '-s managed-server1'."
  fi

  failIfValidationErrors
}

initialize

# Get the cluster name for current server 
clusterName=$(${kubernetesCli} get pod ${domainUid}-${serverName} -n ${domainNamespace} -o=jsonpath="{.metadata.labels['weblogic\.clusterName']}")

# Get the domain in json format
domainJson=$(${kubernetesCli} get domain ${domainUid} -n ${domainNamespace} -o json)

getEffectivePolicy "${domainJson}" "${serverName}" "${clusterName}" effectivePolicy
if [ -n "${clusterName}" ]; then
  checkServersStartedByCurrentReplicasAndPolicy "${domainJson}" "${serverName}" "${clusterName}" started
  if [[ "${effectivePolicy}" == "NEVER" || "${started}" != "true" ]]; then
    echo "[INFO] Server should be already stopping or stopped. This is either because of the sever start policy or server is chosen to be stopped based on current replica count."
    exit 0
  fi
else
  if [ "${effectivePolicy}" == "NEVER" ]; then
    echo "[INFO] Server should be already stopping or stopped because sever start policy is 'NEVER'."
    exit 0
  fi
fi

# Create server start policy patch with NEVER value
createServerStartPolicyPatch "${domainJson}" "${serverName}" "${serverStartPolicy}" neverStartPolicyPatch
getCurrentPolicy "${domainJson}" "${serverName}" managedServerPolicy


if [[ -n "${clusterName}" && "${keepReplicaConstant}" != 'true' ]]; then
  # server is part of a cluster and replica count needs to be updated
  checkServersStoppedByDecreasingReplicasAndUnsetPolicy "${domainJson}" "${serverName}" "${clusterName}" stoppedWhenRelicaReducedAndPolicyReset
  if [ "${effectivePolicy}" == "ALWAYS" ]; then
    checkServersStoppedByUnsetPolicyWhenAlways "${domainJson}" "${serverName}" "${clusterName}" stoppedWhenAlwaysPolicyReset
  fi

  operation="DECREMENT"
  createReplicaPatch "${domainJson}" "${clusterName}" "${operation}" replicaPatch replicaCount

  if [[ -n ${managedServerPolicy} && "${stoppedWhenRelicaReducedAndPolicyReset}" == "true" ]]; then
    # Server will be shut down by unsetting start policy and decrementing replica count, unset and decrement 
    echo "[INFO] Unsetting the current start policy '${managedServerPolicy}' for '${serverName}' and decrementing replica count."
    createPatchJsonToUnsetPolicyAndUpdateReplica "${domainJson}" "${serverName}" "${replicaPatch}" patchJson
    action="PATCH_REPLICA_AND_UNSET_POLICY"
  elif [[ -z ${managedServerPolicy} && "${stoppedWhenRelicaReducedAndPolicyReset}" == "true" ]]; then
    # Server will be shut down by decrementing replica count, decrement replicas
    echo "[INFO] Updating replica count for cluster ${clusterName} to ${replicaCount}."
    patchJson="{\"spec\": {\"clusters\": "${replicaPatch}"}}"
    action="PATCH_REPLICA"
  elif [[ ${managedServerPolicy} == "ALWAYS" && "${stoppedWhenAlwaysPolicyReset}" == "true" ]]; then
    # Server will be shut down by unsetting start policy and decrementing replica count, unset and decrement
    echo "[INFO] Unsetting the current start policy '${managedServerPolicy}' for '${serverName}' and decrementing replica count."
    createPatchJsonToUnsetPolicyAndUpdateReplica "${domainJson}" "${serverName}" "${replicaPatch}" patchJson
    action="PATCH_REPLICA_AND_UNSET_POLICY"
  else
    # Patch server start policy to NEVER and decrement replica count
    patchJson="{\"spec\": {\"clusters\": "${replicaPatch}",\"managedServers\": "${neverStartPolicyPatch}"}}"
    echo "[INFO] Patching start policy of server '${serverName}' from '${effectivePolicy}' to 'NEVER' and decrementing replica count for cluster '${clusterName}'."
    action="PATCH_REPLICA_AND_POLICY"
  fi
elif [[ -n ${clusterName} && "${keepReplicaConstant}" == 'true' ]]; then
  # Server is part of a cluster and replica count needs to stay constant
  if [[ ${managedServerPolicy} == "ALWAYS" && "${stoppedWhenAlwaysPolicyReset}" == "false" ]]; then
    # Server start policy is AlWAYS, unset the server start policy
    echo "[INFO] Unsetting the current start policy '${effectivePolicy}' for '${serverName}'."
    createPatchJsonToUnsetPolicy "${domainJson}" "${serverName}" patchJson
    action="UNSET_POLICY"
  else
    # Patch server start policy 
    patchJson="{\"spec\": {\"managedServers\": "${neverStartPolicyPatch}"}}"
    echo "[INFO] Patching start policy of '${serverName}' from '${effectivePolicy}' to 'NEVER'."
    action="PATCH_POLICY"
  fi
else
  # Server is an independent managed server, only patch server start policy
  patchJson="{\"spec\": {\"managedServers\": "${serverStartPolicyPatch}"}}"
  echo "[INFO] Patching start policy of '${serverName}' from '${effectivePolicy}' to 'NEVER'."
  action="PATCH_POLICY"
fi
if [ "${verboseMode}" == "true" ]; then
  echo "Patching domain with Json string -> ${patchJson}"
fi
${kubernetesCli} patch domain ${domainUid} -n ${domainNamespace} --type='merge' --patch "${patchJson}"

if [ "${action}" == "PATCH_REPLICA_AND_POLICY" ]; then
cat << EOF
[INFO] Successfully patched server '${serverName}' with 'NEVER' start policy!

       The replica count for cluster '${clusterName}' updated to ${replicaCount}.
EOF
elif [ "${action}" == "PATCH_POLICY" ]; then
  echo "[INFO] Successfully patched server '${serverName}' with 'NEVER' start policy!"
elif [ "${action}" == "PATCH_REPLICA" ]; then
  echo "[INFO] Successfully updated replica count for cluster '${clusterName}' to ${replicaCount}."
elif [ "${action}" == "PATCH_REPLICA_AND_UNSET_POLICY" ]; then 
  echo "[INFO] Successfully unset policy '${effectivePolicy}' and updated replica count for cluster '${clusterName}' to ${replicaCount}."
else
  echo "[INFO] Successfully unset policy '${effectivePolicy}'!"
fi
