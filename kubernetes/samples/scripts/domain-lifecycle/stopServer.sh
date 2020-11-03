# !/bin/sh
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

# This script stops a WebLogic managed server in a domain. 
# Internal code notes :-
# - If server start policy is NEVER or policy is IF_NEEDED and the server is not 
#   selected to start based on the replica count, it means that server is already 
#   stopped or is in the process of stopping. In this case, script exits without 
#   making any changes.
#
# - If the effective start policy of the server is IF_NEEDED and decreasing replica 
#   count will naturally stop the server, the script decreases the replica count. 
#
# - If unsetting policy and decreasing the replica count will stop the server, script unsets
#   the policy and decreases replica count. For e.g. if replica count is 2 and start policy
#   of server2 is ALWAYS, unsetting policy and decreasing replica count will stop server2.
#
# - If option to keep replica count constant ('-k') is selected and unsetting start policy
#   will naturally stop the server, script will unset the policy. For e.g. if replica count
#   is 1 and start policy  of server2 is ALWAYS, unsetting policy will stop server2.
#
# - If above conditions are not true, it implies that server policy is IF_NEEDED and server 
#   is selected to start. In this case, script sets start policy to NEVER. For e.g. replica 
#   count is 2 and server1 needs to be stopped. The script also decrements the replica count 
#   by default. If option to keep replica count constant ('-k') is selected, it only sets the 
#   start policy to NEVER.
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
  can be kept constant by using '-k' option. Please see README.md for more details.
 
  Usage:
 
    $(basename $0) -s myserver [-n mynamespace] [-d mydomainuid] [-k] [-m kubecli] [-v]
  
    -s <server_name>           : Server name parameter is required.

    -d <domain_uid>            : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>             : Domain namespace. Default is 'sample-domain1-ns'.
    
    -k <keep_replica_constant> : Keep replica count constant. Default behavior is to decrement replica count.

    -m <kubernetes_cli>        : Kubernetes command line interface. Default is 'kubectl' if KUBERNETES_CLI env
                                 variable is not set. Otherwise default is the value of KUBERNETES_CLI env variable.

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
serverStarted=""
action=""
effectivePolicy=""
managedServerPolicy=""
stoppedWhenAlwaysPolicyReset=""
withReplicas="CONSTANT"
withPolicy="CONSTANT"

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
  # Server is part of a cluster, check currently started servers
  checkStartedServers "${domainJson}" "${serverName}" "${clusterName}" "${withReplicas}" "${withPolicy}" serverStarted
  if [[ "${effectivePolicy}" == "NEVER" || "${serverStarted}" != "true" ]]; then
    printInfo "No changes needed, exiting. Server should be already stopping or stopped. This is either because of the sever start policy or server is chosen to be stopped based on current replica count."
    exit 0
  fi
else
  # Server is an independent managed server. 
  if [ "${effectivePolicy}" == "NEVER" ]; then
    printInfo "No changes needed, exiting. Server should be already stopping or stopped because sever start policy is 'NEVER'."
    exit 0
  fi
fi

# Create server start policy patch with NEVER value
createServerStartPolicyPatch "${domainJson}" "${serverName}" "${serverStartPolicy}" neverStartPolicyPatch
getServerPolicy "${domainJson}" "${serverName}" managedServerPolicy
if [[ -n "${clusterName}" && "${effectivePolicy}" == "ALWAYS" ]]; then
  # Server is part of a cluster and start policy is ALWAYS. 
  withReplicas="CONSTANT"
  withPolicy="UNSET"
  checkStartedServers "${domainJson}" "${serverName}" "${clusterName}" "${withReplicas}" "${withPolicy}" startedWhenAlwaysPolicyReset
fi

if [[ -n "${clusterName}" && "${keepReplicaConstant}" != 'true' ]]; then
  # server is part of a cluster and replica count will decrease
  withReplicas="DECREASED"
  withPolicy="UNSET"
  checkStartedServers "${domainJson}" "${serverName}" "${clusterName}" "${withReplicas}" "${withPolicy}" startedWhenRelicaReducedAndPolicyReset

  operation="DECREMENT"
  createReplicaPatch "${domainJson}" "${clusterName}" "${operation}" replicaPatch replicaCount

  if [[ -n ${managedServerPolicy} && "${startedWhenRelicaReducedAndPolicyReset}" != "true" ]]; then
    # Server shuts down by unsetting start policy and decrementing replica count, unset and decrement 
    printInfo "Unsetting the current start policy '${managedServerPolicy}' for '${serverName}' and decrementing replica count."
    createPatchJsonToUnsetPolicyAndUpdateReplica "${domainJson}" "${serverName}" "${replicaPatch}" patchJson
    action="PATCH_REPLICA_AND_UNSET_POLICY"
  elif [[ -z ${managedServerPolicy} && "${startedWhenRelicaReducedAndPolicyReset}" != "true" ]]; then
    # Start policy is not set, server shuts down by decrementing replica count, decrement replicas
    printInfo "Updating replica count for cluster ${clusterName} to ${replicaCount}."
    createPatchJsonToUpdateReplica "${replicaPatch}" patchJson
    action="PATCH_REPLICA"
  elif [[ ${managedServerPolicy} == "ALWAYS" && "${startedWhenAlwaysPolicyReset}" != "true" ]]; then
    # Server shuts down by unsetting the start policy, unset and decrement replicas
    printInfo "Unsetting the current start policy '${managedServerPolicy}' for '${serverName}' and decrementing replica count."
    createPatchJsonToUnsetPolicyAndUpdateReplica "${domainJson}" "${serverName}" "${replicaPatch}" patchJson
    action="PATCH_REPLICA_AND_UNSET_POLICY"
  else
    # Patch server start policy to NEVER and decrement replica count
    printInfo "Patching start policy of server '${serverName}' from '${effectivePolicy}' to 'NEVER' and decrementing replica count for cluster '${clusterName}'."
    createPatchJsonToUpdateReplicaAndPolicy "${replicaPatch}" "${neverStartPolicyPatch}" patchJson
    action="PATCH_REPLICA_AND_POLICY"
  fi
elif [[ -n ${clusterName} && "${keepReplicaConstant}" == 'true' ]]; then
  # Server is part of a cluster and replica count needs to stay constant
  if [[ ${managedServerPolicy} == "ALWAYS" && "${startedWhenAlwaysPolicyReset}" != "true" ]]; then
    # Server start policy is AlWAYS and server shuts down by unsetting the policy, unset policy
    printInfo "Unsetting the current start policy '${effectivePolicy}' for '${serverName}'."
    createPatchJsonToUnsetPolicy "${domainJson}" "${serverName}" patchJson
    action="UNSET_POLICY"
  else
    # Patch server start policy to NEVER
    printInfo "Patching start policy of '${serverName}' from '${effectivePolicy}' to 'NEVER'."
    createPatchJsonToUpdatePolicy "${neverStartPolicyPatch}" patchJson
    action="PATCH_POLICY"
  fi
else
  # Server is an independent managed server, patch server start policy to NEVER
  printInfo "Patching start policy of '${serverName}' from '${effectivePolicy}' to 'NEVER'."
  createPatchJsonToUpdatePolicy "${neverStartPolicyPatch}" patchJson
  action="PATCH_POLICY"
fi

if [ "${verboseMode}" == "true" ]; then
  printInfo "Patching domain with Json string -> ${patchJson}"
fi

${kubernetesCli} patch domain ${domainUid} -n ${domainNamespace} --type='merge' --patch "${patchJson}"

case ${action} in
  "PATCH_REPLICA_AND_POLICY")
    printInfo "Successfully patched server '${serverName}' with 'NEVER' start policy!"
    printInfo "The replica count for cluster '${clusterName}' updated to ${replicaCount}."
    ;;
  "PATCH_REPLICA_AND_UNSET_POLICY")
    printInfo "Successfully unset server policy '${effectivePolicy}' for '${serverName}' !"
    printInfo "The replica count for cluster '${clusterName}' updated to ${replicaCount}."
    ;;
  "PATCH_POLICY")
    printInfo "Successfully patched server '${serverName}' with 'NEVER' start policy."
    ;;
  "PATCH_REPLICA")
    printInfo "Successfully updated replica count for cluster '${clusterName}' to ${replicaCount}."
    ;;
  *)
    printInfo "Successfully unset policy '${effectivePolicy}' for server '${serverName}'."
    ;;
esac
