#!/bin/sh
# Copyright (c) 2020, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

# This script stops a WebLogic managed server in a domain. 
# Internal code notes :-
# - If server start policy is Never or policy is IfNeeded and the server is not
#   selected to start based on the replica count, it means that server is already 
#   stopped or is in the process of stopping. In this case, script exits without 
#   making any changes.
#
# - If server is part of a cluster and keep_replica_constant option is false (the default)
#   and the effective start policy of the server is IfNeeded and decreasing replica count
#   will naturally stop the server, the script decreases the replica count. 
#
# - If server is part of a cluster and keep_replica_constant option is false (the default)
#   and unsetting policy and decreasing the replica count will stop the server, script 
#   unsets the policy and decreases replica count. For e.g. if replica count is 2 and 
#   start policy of server2 is Always, unsetting policy and decreasing replica count will
#   stop server2.
#
# - If option to keep replica count constant ('-k') is selected and unsetting start policy
#   will naturally stop the server, script will unset the policy. For e.g. if replica count
#   is 1 and start policy  of server2 is Always, unsetting policy will stop server2.
#
# - If above conditions are not true, it implies that server policy is IfNeeded and server
#   is selected to start. In this case, script sets start policy to Never. For e.g. replica
#   count is 2 and server1 needs to be stopped. The script also decrements the replica count 
#   by default. If option to keep replica count constant ('-k') is selected, it only sets the 
#   start policy to Never.
# 

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh
if [ "${debug}" == "true" ]; then set -x; fi;
set -eu

usage() {

  cat << EOF

  This script stops a running WebLogic server in a domain. For managed servers, it either
  decreases the value of 'spec.clusters[<cluster-name>].replicas' or updates the
  'spec.managedServers[<server-name>].serverStartPolicy' attribute of the domain 
  resource or both as necessary to stop the server. For the administration server, it updates
  the value of 'spec.adminServer.serverStartPolicy' attribute of the domain resource. The
  'spec.clusters[<cluster-name>].replicas' value can be kept constant by using '-k' option.
  Please see README.md for more details.
 
  Usage:
 
    $(basename $0) -s myserver [-n mynamespace] [-d mydomainuid] [-k] [-m kubecli] [-v]
  
    -s <server_name>           : The WebLogic server name (not the pod name). 
                                 This parameter is required.

    -d <domain_uid>            : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>             : Domain namespace. Default is 'sample-domain1-ns'.
    
    -k <keep_replica_constant> : Keep replica count constant for the clustered servers. The default behavior
                                 is to decrement the replica count for the clustered servers. This parameter
                                 is ignored for the administration and non-clustered managed servers.

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
serverStartPolicy=Never
serverStarted=""
effectivePolicy=""
managedServerPolicy=""
stoppedWhenAlwaysPolicyReset=""
replicasEqualsMinReplicas=""
withReplicas="CONSTANT"
withPolicy="CONSTANT"
patchJson=""
isAdminServer=false
clusterJson=""
replicaPatch=""
clusterResource=""

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
initialize() {

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

# Get the domain in json format
domainJson=$(${kubernetesCli} get domain ${domainUid} -n ${domainNamespace} -o json --ignore-not-found)
if [ -z "${domainJson}" ]; then
  printError "Unable to get domain resource for domain '${domainUid}' in namespace '${domainNamespace}'. Please make sure the 'domain_uid' and 'namespace' specified by the '-d' and '-n' arguments are correct. Exiting."
  exit 1
fi

# Validate that specified server is either part of a cluster or is an independent managed server
validateServerAndFindCluster "${domainUid}" "${domainNamespace}" "${serverName}" isValidServer clusterName isAdminServer
if [ "${isValidServer}" != 'true' ]; then
  printError "Server ${serverName} is not part of any cluster and it's not an independent managed server. Please make sure that server name specified is correct."
  exit 1
fi

if [ -n "${clusterName}" ]; then
  getClusterResource "${domainJson}" "${domainNamespace}" "${clusterName}" clusterResource

  clusterJson=$(${kubernetesCli} get cluster ${clusterResource} -n ${domainNamespace} -o json --ignore-not-found)
  if [ -z "${clusterJson}" ]; then
    printError "Unable to get cluster resource for cluster '${clusterName}' in namespace '${domainNamespace}'. Please make sure that a Cluster exists for cluster '${clusterName}' and that this Cluster is referenced by the Domain."
    exit 1
  fi
fi


getEffectivePolicy "${domainJson}" "${clusterJson}" "${serverName}" "${clusterName}" effectivePolicy
if [ "${isAdminServer}" == 'true' ]; then
    getEffectiveAdminPolicy "${domainJson}" effectivePolicy
    if [ "${effectivePolicy}" == "Never" ]; then
      printInfo "No changes needed, exiting. Server should be already stopping or stopped because effective sever start policy is 'Never'."
      exit 0
    fi
fi

if [ -n "${clusterName}" ]; then
  # Server is part of a cluster, check currently started servers
  checkStartedServers "${domainJson}" "${clusterJson}" "${serverName}" "${clusterName}" "${withReplicas}" "${withPolicy}" serverStarted
  if [[ "${effectivePolicy}" == "Never" || "${effectivePolicy}" == "AdminOnly" || "${serverStarted}" != "true" ]]; then
    printInfo "No changes needed, exiting. Server should be already stopping or stopped. This is either because of the sever start policy or server is chosen to be stopped based on current replica count."
    exit 0
  fi
else
  # Server is an independent managed server. 
  if [ "${effectivePolicy}" == "Never" ] || [[ "${effectivePolicy}" == "AdminOnly" && "${isAdminServer}" != 'true' ]]; then
    printInfo "No changes needed, exiting. Server should be already stopping or stopped because effective sever start policy is 'Never' or 'AdminOnly'."
    exit 0
  fi
fi

if [[ -n "${clusterName}" && "${keepReplicaConstant}" == 'false' ]]; then
  # check if replica count can decrease below current value
  isReplicaCountEqualToMinReplicas "${domainJson}" "${clusterJson}" "${clusterName}" replicasEqualsMinReplicas
  if [ "${replicasEqualsMinReplicas}" == 'true' ]; then
    printInfo "Not decreasing the replica count value: it is at its minimum. \
      (See 'domain.spec.allowReplicasBelowMinDynClusterSize' and \
      'domain.status.clusters[].minimumReplicas' for details)."
    keepReplicaConstant=true
  fi
fi

# Create server start policy patch with Never value
createServerStartPolicyPatch "${domainJson}" "${serverName}" "${serverStartPolicy}" neverStartPolicyPatch
getServerPolicy "${domainJson}" "${serverName}" managedServerPolicy
if [ -n "${managedServerPolicy}" ]; then
  effectivePolicy=${managedServerPolicy}
fi
if [[ -n "${clusterName}" && "${effectivePolicy}" == "Always" ]]; then
  # Server is part of a cluster and start policy is Always.
  withReplicas="CONSTANT"
  withPolicy="UNSET"
  checkStartedServers "${domainJson}" "${clusterJson}" "${serverName}" "${clusterName}" "${withReplicas}" "${withPolicy}" startedWhenAlwaysPolicyReset
fi

if [[ -n "${clusterName}" && "${keepReplicaConstant}" != 'true' ]]; then
  # server is part of a cluster and replica count will decrease
  withReplicas="DECREASED"
  withPolicy="UNSET"
  checkStartedServers "${domainJson}" "${clusterJson}" "${serverName}" "${clusterName}" "${withReplicas}" "${withPolicy}" startedWhenRelicaReducedAndPolicyReset
  createReplicaPatch "${domainJson}" "${clusterJson}" "${clusterName}" "DECREMENT" replicaPatch replicaCount

  if [[ -n ${managedServerPolicy} && "${startedWhenRelicaReducedAndPolicyReset}" != "true" ]]; then
    # Server shuts down by unsetting start policy and decrementing replica count, unset and decrement 
    printInfo "Unsetting the current start policy '${managedServerPolicy}' for '${serverName}' \
      and decrementing replica count to ${replicaCount}."
    createPatchJsonToUnsetPolicy "${domainJson}" "${serverName}" patchJson
  elif [[ -z ${managedServerPolicy} && "${startedWhenRelicaReducedAndPolicyReset}" != "true" ]]; then
    # Start policy is not set, server shuts down by decrementing replica count, decrement replicas
    printInfo "Updating replica count for cluster ${clusterName} to ${replicaCount}."
  elif [[ ${managedServerPolicy} == "Always" && "${startedWhenAlwaysPolicyReset}" != "true" ]]; then
    # Server shuts down by unsetting the start policy, unset and decrement replicas
    printInfo "Unsetting the current start policy '${managedServerPolicy}' for '${serverName}' \
     and decrementing replica count to ${replicaCount}."
    createPatchJsonToUnsetPolicy "${domainJson}" "${serverName}" patchJson
  else
    # Patch server start policy to Never and decrement replica count
    printInfo "Patching start policy of server '${serverName}' from '${effectivePolicy}' to 'Never' \
      and decrementing replica count for cluster '${clusterName}' to ${replicaCount}."
    createPatchJsonToUpdatePolicy "${neverStartPolicyPatch}" patchJson
  fi
elif [[ -n ${clusterName} && "${keepReplicaConstant}" == 'true' ]]; then
  # Server is part of a cluster and replica count needs to stay constant
  if [[ ${managedServerPolicy} == "Always" && "${startedWhenAlwaysPolicyReset}" != "true" ]]; then
    # Server start policy is AlWAYS and server shuts down by unsetting the policy, unset policy
    printInfo "Unsetting the current start policy '${effectivePolicy}' for '${serverName}'."
    createPatchJsonToUnsetPolicy "${domainJson}" "${serverName}" patchJson
  else
    # Patch server start policy to Never
    printInfo "Patching start policy of '${serverName}' from '${effectivePolicy}' to 'Never'."
    createPatchJsonToUpdatePolicy "${neverStartPolicyPatch}" patchJson
  fi
elif [ "${isAdminServer}" == 'true' ]; then
  printInfo "Patching start policy of '${serverName}' from '${effectivePolicy}' to 'Never'."
  createPatchJsonToUpdateAdminPolicy "${domainJson}" "${serverStartPolicy}" patchJson
else
  # Server is an independent managed server, patch server start policy to Never
  printInfo "Patching start policy of '${serverName}' from '${effectivePolicy}' to 'Never'."
  createPatchJsonToUpdatePolicy "${neverStartPolicyPatch}" patchJson
fi

if [ ! -z "${replicaPatch}" ]; then
  executeClusterPatchCommand "${kubernetesCli}" "${clusterResource}" "${domainNamespace}" "${replicaPatch}" "${verboseMode}"
fi
if [ ! -z "${patchJson}" ]; then
  executePatchCommand "${kubernetesCli}" "${domainUid}" "${domainNamespace}" "${patchJson}" "${verboseMode}"
fi

printInfo "Patch command succeeded !"
