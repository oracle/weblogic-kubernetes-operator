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

  This script starts a WebLogic managed server in a domain either by increasing
  the value of 'spec.clusters[<cluster-name>].replicas' by '1' or by updating the
  'spec.managedServers[<server-name>].serverStartPolicy' attribute of the domain
  resource or both as needed. The 'spec.clusters[<cluster-name>].replicas' value can
  be kept constant by using '-k' option.

  Usage:

    $(basename $0) -s myserver [-n mynamespace] [-d mydomainuid] [-k] [-m kubecli] [-v]

    -s <server_name>           : Server name parameter is required.

    -d <domain_uid>            : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>             : Domain namespace. Default is 'sample-domain1-ns'.

    -k <keep_replica_constant> : Keep replica count constant. Default behavior is to increment replica count.

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
withRelicas="CONSTANT"
withPolicy="CONSTANT"
managedServerPolicy=""
action=""
isValidServer=""
patchJson=""
serverStarted=""
startsByPolicyUnset=""
startsByReplicaIncreaseAndPolicyUnset=""

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
domainJson=$(${kubernetesCli} get domain ${domainUid} -n ${domainNamespace} -o json)
if [ $? -ne 0 ]; then
  echo "[ERROR} Unable to get domain resource. Please make sure 'domain_uid' and 'namespace' provided with '-d' and '-n' arguments are correct."
  exit 1
fi

# Validate that specified server is either part of a cluster or is an independent managed server
validateServerAndFindCluster "${domainUid}" "${domainNamespace}" "${serverName}" isValidServer clusterName
if [ "${isValidServer}" != 'true' ]; then
  printError "Server ${serverName} is not part of any cluster and it's not an independent managed server. Please make sure that server name specified is correct."
  exit 1
fi

getClusterPolicy "${domainJson}" "${clusterName}" clusterPolicy
if [ "${clusterPolicy}" == 'NEVER' ]; then
  echo "The .spec.clusters[?(clusterName="${clusterName}"].serverStartPolicy of the domain resource is 'NEVER'. The $(basename $0) script will exit without starting server ${serverName}."
  exit 0
fi

getDomainPolicy "${domainJson}" domainPolicy
if [ "${domainPolicy}" == 'NEVER' ]; then
  echo "The .spec.serverStartPolicy of the domain resource is 'NEVER'. The $(basename $0) script will exit without starting server ${serverName}."
  exit 0
fi

getEffectivePolicy "${domainJson}" "${serverName}" "${clusterName}" effectivePolicy
if [ -n "${clusterName}" ]; then
  # Server is part of a cluster, check currently started servers
  checkStartedServers "${domainJson}" "${serverName}" "${clusterName}" "${withRelicas}" "${withPolicy}" serverStarted
  if [[ ${effectivePolicy} == "IF_NEEDED" && ${serverStarted} == "true" ]]; then
    echo "[INFO] The server should be already started or it's starting. The start policy for server ${serverName} is ${effectivePolicy} and server is chosen to be started based on current replica count."
    exit 0
  elif [[ "${effectivePolicy}" == "ALWAYS" && ${serverStarted} == "true" ]]; then
    echo "[INFO] The server should be already started or it's starting. The start policy for server ${serverName} is ${effectivePolicy}."
    exit 0
  fi
else 
  # Server is an independent managed server. 
  if [ "${effectivePolicy}" == "ALWAYS" ]; then
    echo "[INFO] The server should be already started or it's starting. The start policy for server ${serverName} is ${effectivePolicy}."
    exit 0
  fi
fi

getCurrentPolicy "${domainJson}" "${serverName}" managedServerPolicy
targetPolicy="ALWAYS"
createServerStartPolicyPatch "${domainJson}" "${serverName}" "${targetPolicy}" alwaysStartPolicyPatch 

# if server is part of a cluster and replica count will increase
if [[ -n ${clusterName} && "${keepReplicaConstant}" != 'true' ]]; then
  #check if server starts by increasing replicas and unsetting policy
  withRelicas="INCREASED"
  withPolicy="UNSET"
  checkStartedServers "${domainJson}" "${serverName}" "${clusterName}" "${withRelicas}" "${withPolicy}" startsByReplicaIncreaseAndPolicyUnset
  operation="INCREMENT"
  createReplicaPatch "${domainJson}" "${clusterName}" "${operation}" incrementReplicaPatch replicaCount
  if [ "${incrementReplicaPatch}" == "MAX_REPLICA_COUNT_EXCEEDED" ]; then 
   exit 1
  fi
  if [[ -n ${managedServerPolicy} && ${startsByReplicaIncreaseAndPolicyUnset} == "true" ]]; then
    # Server starts by increasing replicas and policy unset, increment and unset
    echo "[INFO] Unsetting the current start policy '${managedServerPolicy}' for '${serverName}' and incrementing replica count."
    createPatchJsonToUnsetPolicyAndUpdateReplica "${domainJson}" "${serverName}" "${incrementReplicaPatch}" patchJson
    action="PATCH_REPLICA_AND_UNSET_POLICY"
  elif [[ -z ${managedServerPolicy} && ${startsByReplicaIncreaseAndPolicyUnset} == "true" ]]; then
    # Start policy is not set, server starts by increasing replicas based on effective policy, increment replicas
    echo "[INFO] Updating replica count for cluster '${clusterName}' to ${replicaCount}."
    patchJson="{\"spec\": {\"clusters\": "${incrementReplicaPatch}"}}"
    action="PATCH_REPLICA"
  else
    # Patch server policy to always and increment replicas
    echo "[INFO] Patching start policy of server '${serverName}' from '${effectivePolicy}' to 'ALWAYS' and \
incrementing replica count for cluster '${clusterName}'."
    patchJson="{\"spec\": {\"clusters\": "${incrementReplicaPatch}",\"managedServers\": "${alwaysStartPolicyPatch}"}}"
    action="PATCH_REPLICA_AND_POLICY"
  fi
elif [[ -n ${clusterName} && "${keepReplicaConstant}" == 'true' ]]; then
  # Replica count needs to stay constant, check if server starts by unsetting policy
  withRelicas="CONSTANT"
  withPolicy="UNSET"
  checkStartedServers "${domainJson}" "${serverName}" "${clusterName}" "${withRelicas}" "${withPolicy}" startsByPolicyUnset
  if [[ "${effectivePolicy}" == "NEVER" && ${startsByPolicyUnset} == "true" ]]; then
    # Server starts by unsetting policy, unset policy
    echo "[INFO] Unsetting the current start policy '${effectivePolicy}' for '${serverName}'."
    createPatchJsonToUnsetPolicy "${domainJson}" "${serverName}" patchJson
    action="UNSET_POLICY"
  else
    # Patch server policy to always
    echo "[INFO] Patching start policy for '${serverName}' to '${targetPolicy}'."
    patchJson="{\"spec\": {\"managedServers\": "${alwaysStartPolicyPatch}"}}"
    action="PATCH_POLICY"
  fi
else
  # Server is an independent managed server, patch server start policy to ALWAYS
  patchJson="{\"spec\": {\"managedServers\": "${alwaysStartPolicyPatch}"}}"
  action="PATCH_POLICY"
fi

if [ "${verboseMode}" == "true" ]; then
  echo "Patching domain with Json string -> ${patchJson}"
fi
${kubernetesCli} patch domain ${domainUid} -n ${domainNamespace} --type='merge' --patch "${patchJson}" 

if [ ${action} == "PATCH_REPLICA_AND_POLICY" ]; then
cat << EOF
[INFO] Successfully patched server '${serverName}' with '${targetPolicy}' start policy!

       The replica count for cluster '${clusterName}' updated to ${replicaCount}.
EOF
elif [ ${action} == "PATCH_REPLICA_AND_UNSET_POLICY" ]; then
cat << EOF
[INFO] Successfully unset server policy '${effectivePolicy}' for '${serverName}' !

       The replica count for cluster '${clusterName}' updated to ${replicaCount}.
EOF
elif [ ${action} == "PATCH_POLICY" ]; then
  echo "[INFO] Successfully patched server '${serverName}' with '${targetPolicy}' start policy."
elif [ ${action} == "UNSET_POLICY" ]; then
  echo "[INFO] Successfully unset policy for server '${serverName}'."
else
  echo "[INFO] Successfully updated replica count for cluster '${clusterName}' to ${replicaCount}."
fi
