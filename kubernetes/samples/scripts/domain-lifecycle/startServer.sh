# !/bin/sh
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

# This script starts a WebLogic managed server in a domain. 
# Internal code notes :-
# - If server start policy is ALWAYS or policy is IF_NEEDED and the server is selected 
#   to start based on the replica count, it means that server is already started or is
#   in the process of starting. In this case, script exits without making any changes.
#
# - If start policy of servers parent cluster or domain is 'NEVER', script
#   fails as server can't be started.
#
# - If server is part of a cluster and keep_replica_constant option is false (the default)
#   and the effective start policy of the server is IF_NEEDED and increasing replica count
#   will naturally start the server, the script increases the replica count. 
#
# - If server is part of a cluster and keep_replica_constant option is false (the default) 
#   and unsetting policy and increasing the replica count will start this server, script 
#   unsets the policy and increases replica count. For e.g. if replica count is 1 and 
#   start policy of server2 is NEVER, unsetting policy and increasing replica count will 
#   start server2.
#
# - If option to keep replica count constant ('-k') is selected and unsetting start policy
#   will naturally start the server, script will unset the policy. For e.g. if replica count
#   is 2 and start policy  of server2 is NEVER, unsetting policy will start server2.
#
# - If above conditions are not true, it implies that either start policy is NEVER or policy
#   is IF_NEEDED but server is not next in the order to start. In this case, script sets start 
#   policy to ALWAYS. For e.g. replica count is 3 and server10 needs to start. The script also 
#   increments the replica count by default. If option to keep replica count constant ('-k') 
#   is selected, it only sets the start policy to ALWAYS.
# 

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh
if [ "${debug}" == "true" ]; then set -x; fi;
set -eu

function usage() {

  cat << EOF

  This script starts a WebLogic server in a domain. For the managed servers, it either
  increases the value of 'spec.clusters[<cluster-name>].replicas' by '1' or updates the
  'spec.managedServers[<server-name>].serverStartPolicy' attribute of the domain
  resource or both as necessary for starting the server. For the administration server, it
  updates the value of 'spec.adminServer.serverStartPolicy' attribute of the domain resource.
  The 'spec.clusters[<cluster-name>].replicas' value can be kept constant by using '-k' option.
  Please see README.md for more details.

  Usage:

    $(basename $0) -s myserver [-n mynamespace] [-d mydomainuid] [-k] [-m kubecli] [-v]

    -s <server_name>           : The WebLogic server name (not the pod name). 
                                 This parameter is required.

    -d <domain_uid>            : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>             : Domain namespace. Default is 'sample-domain1-ns'.

    -k <keep_replica_constant> : Keep replica count constant for the clustered servers. The default behavior
                                 is to increment the replica count for the clustered servers. This parameter
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
withReplicas="CONSTANT"
withPolicy="CONSTANT"
managedServerPolicy=""
effectivePolicy=""
isValidServer=""
patchJson=""
serverStarted=""
startsByPolicyUnset=""
startsByReplicaIncreaseAndPolicyUnset=""
isAdminServer=false

while getopts "vkd:n:m:s:h" opt; do
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

  # Validate the required files exist
  validateErrors=false

  validateKubernetesCliAvailable
  validateJqAvailable

  # Validate that server name parameter is specified.
  if [ -z "${serverName}" ]; then
    validationError "Please specify a server name using '-s' parameter e.g. '-s managed-server1'."
  fi

  failIfValidationErrors
}

initialize

# Get the domain in json format
domainJson=$(${kubernetesCli} get domain.v8.weblogic.oracle ${domainUid} -n ${domainNamespace} -o json --ignore-not-found)
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

getClusterPolicy "${domainJson}" "${clusterName}" clusterPolicy
if [ "${clusterPolicy}" == 'NEVER' ]; then
  printError "Cannot start server '${serverName}', the server's parent cluster '.spec.clusters[?(clusterName=\"${clusterName}\"].serverStartPolicy' in the domain resource is set to 'NEVER'."
  exit 1
fi

getDomainPolicy "${domainJson}" domainPolicy
if [ "${domainPolicy}" == 'NEVER' ] || [[ "${domainPolicy}" == 'ADMIN_ONLY' && "${isAdminServer}" != 'true' ]]; then
  printError "Cannot start server '${serverName}', the .spec.serverStartPolicy in the domain resource is set to 'NEVER' or 'ADMIN_ONLY'."
  exit 1
fi

getEffectivePolicy "${domainJson}" "${serverName}" "${clusterName}" effectivePolicy
if [ "${isAdminServer}" == 'true' ]; then
    getEffectiveAdminPolicy "${domainJson}" effectivePolicy
    if [[ "${effectivePolicy}" == "IF_NEEDED" || "${effectivePolicy}" == "ALWAYS" ]]; then
      printInfo "No changes needed, exiting. Server should be already starting or started because effective sever start policy is '${effectivePolicy}'."
      exit 0
    fi
fi

if [ -n "${clusterName}" ]; then
  # Server is part of a cluster, check currently started servers
  checkStartedServers "${domainJson}" "${serverName}" "${clusterName}" "${withReplicas}" "${withPolicy}" serverStarted
  if [[ ${effectivePolicy} == "IF_NEEDED" && ${serverStarted} == "true" ]]; then
    printInfo "No changes needed, exiting. The server should be already started or it's in the process of starting. The start policy for server ${serverName} is ${effectivePolicy} and server is chosen to be started based on current replica count."
    exit 0
  elif [[ "${effectivePolicy}" == "ALWAYS" && ${serverStarted} == "true" ]]; then
    printInfo "No changes needed, exiting. The server should be already started or it's in the process of starting. The start policy for server ${serverName} is ${effectivePolicy}."
    exit 0
  fi
else 
  # Server is an independent managed server. 
  if [[ "${effectivePolicy}" == "ALWAYS" || "${effectivePolicy}" == "IF_NEEDED" ]]; then
    printInfo "No changes needed, exiting. The server should be already started or it's in the process of starting. The start policy for server ${serverName} is ${effectivePolicy}."
    exit 0
  fi
fi

getServerPolicy "${domainJson}" "${serverName}" managedServerPolicy
createServerStartPolicyPatch "${domainJson}" "${serverName}" "ALWAYS" alwaysStartPolicyPatch 

# if server is part of a cluster and replica count will increase
if [[ -n ${clusterName} && "${keepReplicaConstant}" != 'true' ]]; then
  #check if server starts by increasing replicas and unsetting policy
  withReplicas="INCREASED"
  withPolicy="UNSET"
  checkStartedServers "${domainJson}" "${serverName}" "${clusterName}" "${withReplicas}" "${withPolicy}" startsByReplicaIncreaseAndPolicyUnset
  createReplicaPatch "${domainJson}" "${clusterName}" "INCREMENT" incrementReplicaPatch replicaCount
  if [[ -n ${managedServerPolicy} && ${startsByReplicaIncreaseAndPolicyUnset} == "true" ]]; then
    # Server starts by increasing replicas and policy unset, increment and unset
    printInfo "Unsetting the current start policy '${managedServerPolicy}' for '${serverName}' and incrementing replica count ${replicaCount}."
    createPatchJsonToUnsetPolicyAndUpdateReplica "${domainJson}" "${serverName}" "${incrementReplicaPatch}" patchJson
  elif [[ -z ${managedServerPolicy} && ${startsByReplicaIncreaseAndPolicyUnset} == "true" ]]; then
    # Start policy is not set, server starts by increasing replicas based on effective policy, increment replicas
    printInfo "Updating replica count for cluster '${clusterName}' to ${replicaCount}."
    createPatchJsonToUpdateReplica "${incrementReplicaPatch}" patchJson
  else
    # Patch server policy to always and increment replicas
    printInfo "Patching start policy of server '${serverName}' from '${effectivePolicy}' to 'ALWAYS' and \
incrementing replica count for cluster '${clusterName}' to ${replicaCount}."
    createPatchJsonToUpdateReplicaAndPolicy "${incrementReplicaPatch}" "${alwaysStartPolicyPatch}" patchJson
  fi
elif [[ -n ${clusterName} && "${keepReplicaConstant}" == 'true' ]]; then
  # Replica count needs to stay constant, check if server starts by unsetting policy
  withReplicas="CONSTANT"
  withPolicy="UNSET"
  checkStartedServers "${domainJson}" "${serverName}" "${clusterName}" "${withReplicas}" "${withPolicy}" startsByPolicyUnset
  if [[ "${effectivePolicy}" == "NEVER" && ${startsByPolicyUnset} == "true" ]]; then
    # Server starts by unsetting policy, unset policy
    printInfo "Unsetting the current start policy '${effectivePolicy}' for '${serverName}'."
    createPatchJsonToUnsetPolicy "${domainJson}" "${serverName}" patchJson
  else
    # Patch server policy to always
    printInfo "Patching start policy for '${serverName}' to 'ALWAYS'."
    createPatchJsonToUpdatePolicy "${alwaysStartPolicyPatch}" patchJson
  fi
elif [ "${isAdminServer}" == 'true' ]; then
  printInfo "Patching start policy of '${serverName}' from '${effectivePolicy}' to 'IF_NEEDED'."
  createPatchJsonToUpdateAdminPolicy "${domainJson}" "IF_NEEDED" patchJson
else
  # Server is an independent managed server
  printInfo "Unsetting the current start policy '${effectivePolicy}' for '${serverName}'."
  createPatchJsonToUnsetPolicy "${domainJson}" "${serverName}" patchJson
fi

executePatchCommand "${kubernetesCli}" "${domainUid}" "${domainNamespace}" "${patchJson}" "${verboseMode}"

printInfo "Patch command succeeded !"
