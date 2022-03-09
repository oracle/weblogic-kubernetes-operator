# !/bin/sh
# Copyright (c) 2020, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

#
# Function to get server start policy at cluster level
# $1 - Domain resource in json format
# $2 - Name of cluster
# $3 - Return value for cluster level server start policy.
#      Legal return values are "NEVER" or "IF_NEEDED" or "".
#
getClusterPolicy() {
  local domainJson=$1
  local clusterName=$2
  local __clusterPolicy=$3
  local effectivePolicy=""

  clusterPolicyCmd="(.spec.clusters // empty | .[] \
    | select (.clusterName == \"${clusterName}\")).serverStartPolicy"
  effectivePolicy=$(echo ${domainJson} | jq "${clusterPolicyCmd}")
  if [ "${effectivePolicy}" == "null" ]; then
    effectivePolicy=""
  fi
  eval $__clusterPolicy=${effectivePolicy}
}

#
# Function to get server start policy at domain level
# $1 - Domain resource in json format
# $2 - Return value containing domain level server start policy.
#      Legal retrun values are "NEVER" or "IF_NEEDED" or "ADMIN_ONLY".
#
getDomainPolicy() {
  local domainJson=$1
  local __domainPolicy=$2
  local effectivePolicy=""

  eval $__domainPolicy="IF_NEEDED"
  domainPolicyCommand=".spec.serverStartPolicy"
  effectivePolicy=$(echo ${domainJson} | jq "${domainPolicyCommand}")
  if [[ "${effectivePolicy}" == "null" || "${effectivePolicy}" == "" ]]; then
    effectivePolicy="IF_NEEDED"
  fi
  eval $__domainPolicy=${effectivePolicy}
}

#
# Function to get effective start policy of server
# $1 - Domain resource in json format
# $2 - Name of server
# $3 - Name of cluster
# $4 - Return value containing effective server start policy
#      Legal retrun values are "NEVER" or "IF_NEEDED" or "ALWAYS".
#
getEffectivePolicy() {
  local domainJson=$1
  local serverName=$2
  local clusterName=$3
  local __currentPolicy=$4
  local currentPolicy=""

  getServerPolicy "${domainJson}" "${serverName}" currentPolicy
  if [ -z "${currentPolicy}" ]; then
    getClusterPolicy "${domainJson}" "${clusterName}" currentPolicy
    if [ -z "${currentPolicy}" ]; then
      # Start policy is not set at cluster level, check at domain level
      getDomainPolicy "${domainJson}" currentPolicy
    fi
  fi
  eval $__currentPolicy=${currentPolicy}
}

#
# Function to get effective start policy of admin server
# $1 - Domain resource in json format
# $2 - Return value containing effective server start policy
#      Legal retrun values are "NEVER" or "IF_NEEDED" or "ALWAYS".
#
getEffectiveAdminPolicy() {
  local domainJson=$1
  local __effectivePolicy=$2
  local __adminStartPolicy=""
  local __domainStartPolicy=""

  __adminStartPolicy=$(echo ${domainJson} | jq -cr '(.spec.adminServer.serverStartPolicy)')
  getDomainPolicy "${domainJson}" __domainStartPolicy
  if [[ "${__adminStartPolicy}" == "null" || "${__domainStartPolicy}" == "NEVER" ]]; then
    __adminStartPolicy="${__domainStartPolicy}"
  fi
  eval $__effectivePolicy="'${__adminStartPolicy}'"
}

#
# Function to get current start policy of server
# $1 - Domain resource in json format
# $2 - Name of server
# $3 - Return value containing current server start policy
#      Legal retrun values are "NEVER" or "IF_NEEDED", "ALWAYS" or "".
#
getServerPolicy() {
  local domainJson=$1
  local serverName=$2
  local __currentPolicy=$3
  local currentServerStartPolicy=""

  # Get server start policy for this server
  eval $__currentPolicy=""
  managedServers=$(echo ${domainJson} | jq -cr '(.spec.managedServers)')
  if [ "${managedServers}" != "null" ]; then
    extractPolicyCmd="(.spec.managedServers[] \
      | select (.serverName == \"${serverName}\") | .serverStartPolicy)"
    currentServerStartPolicy=$(echo ${domainJson} | jq "${extractPolicyCmd}")
    if [ "${currentServerStartPolicy}" == "null" ]; then
      currentServerStartPolicy=""
    fi
  fi
  eval $__currentPolicy=${currentServerStartPolicy}
}

#
# Function to create server start policy patch string
# $1 - Domain resource in json format
# $2 - Name of server whose policy will be patched
# $3 - Policy value
# $4 - Return value containing server start policy patch string
#
createServerStartPolicyPatch() {
  local domainJson=$1
  local serverName=$2
  local policy=$3
  local __result=$4
  local currentStartPolicy=""
  local serverStartPolicyPatch=""

  # Get server start policy for this server
  getServerPolicy "${domainJson}" "${serverName}" currentStartPolicy
  managedServers=$(echo ${domainJson} | jq -cr '(.spec.managedServers)')
  if [[ -z "${currentStartPolicy}" && "${managedServers}" == "null" ]]; then
    # Server start policy doesn't exist, add a new policy
    addPolicyCmd=".[.| length] |= . + {\"serverName\":\"${serverName}\", \
      \"serverStartPolicy\":\"${policy}\"}"
    serverStartPolicyPatch=$(echo ${domainJson} | jq .spec.managedServers | jq -c "${addPolicyCmd}")
  elif [ "${managedServers}" != "null" ]; then
    extractSpecCmd="(.spec.managedServers)"
    mapCmd="\
      . |= (map(.serverName) | index (\"${serverName}\")) as \$idx | \
      if \$idx then \
      .[\$idx][\"serverStartPolicy\"] = \"${policy}\" \
      else .+  [{serverName: \"${serverName}\" , serverStartPolicy: \"${policy}\"}] end"
    serverStartPolicyPatch=$(echo ${domainJson} | jq "${extractSpecCmd}" | jq "${mapCmd}")
  else
    # Server start policy exists, replace policy value
    replacePolicyCmd="(.spec.managedServers[] \
      | select (.serverName == \"${serverName}\") | .serverStartPolicy) |= \"${policy}\""
    servers="(.spec.managedServers)"
    serverStartPolicyPatch=$(echo ${domainJson} | jq "${replacePolicyCmd}" | jq -cr "${servers}")
  fi
  eval $__result="'${serverStartPolicyPatch}'"
}

#
# Function to create patch json string to unset policy and update replica
# $1 - Domain resource in json format
# $2 - Name of server whose policy will be patched
# $3 - String containing replica patch string
# $4 - Return value containing patch json string
#
createPatchJsonToUnsetPolicyAndUpdateReplica() {
  local domainJson=$1
  local serverName=$2
  local replicaPatch=$3
  local __result=$4

  unsetServerStartPolicy "${domainJson}" "${serverName}" serverStartPolicyPatch
  patchJson="{\"spec\": {\"clusters\": "${replicaPatch}",\"managedServers\": "${serverStartPolicyPatch}"}}"
  eval $__result="'${patchJson}'"
}

#
# Function to create patch json string to update policy
# $1 - String containing start policy info
# $2 - String containing json to patch domain resource
#
createPatchJsonToUpdatePolicy() {
  local startPolicy=$1
  local __result=$2
  patchJson="{\"spec\": {\"managedServers\": "${startPolicy}"}}"
  eval $__result="'${patchJson}'"
}

#
# Function to create patch json string to update admin server start policy
# $1 - Domain resource in json format
# $2 - Policy value
# $3 - Return value containing server start policy patch string
#
createPatchJsonToUpdateAdminPolicy() {
  local domainJson=$1
  local policy=$2
  local __result=$3
  local __adminServer=""
  local __patchJson=""
  local __serverStartPolicyPatch=""

  eval $__result=""
  __adminServer=$(echo ${domainJson} | jq -cr '(.spec.adminServer)')
  if [ "${__adminServer}" == "null" ]; then
    # admin server specs does not exist, add new spec with server start policy
    addPolicyCmd="{\"serverStartPolicy\":\"${policy}\"}"
    __serverStartPolicyPatch=$(echo ${domainJson} | jq .spec.amdinServer | jq -c "${addPolicyCmd}")
  else
    addOrReplaceCmd="(.spec.adminServer) | .+  {\"serverStartPolicy\": \"${policy}\"}"
    __serverStartPolicyPatch=$(echo ${domainJson} | jq "${addOrReplaceCmd}")
  fi
  __patchJson="{\"spec\": {\"adminServer\": "${__serverStartPolicyPatch}"}}"
  eval $__result="'${__patchJson}'"
}

#
# Function to create patch json string to update replica
# $1 - String containing replica
# $2 - String containing json to patch domain resource
#
createPatchJsonToUpdateReplica() {
  local replicaInfo=$1
  local __result=$2
  patchJson="{\"spec\": {\"clusters\": "${replicaInfo}"}}"
  eval $__result="'${patchJson}'"
}

#
# Function to create patch json string to update replica and policy
# $1 - Domain resource in json format
# $2 - Name of server whose policy will be patched
# $3 - Return value containing patch json string
#
createPatchJsonToUpdateReplicaAndPolicy() {
  local replicaInfo=$1
  local startPolicy=$2
  local __result=$3

  patchJson="{\"spec\": {\"clusters\": "${replicaInfo}",\"managedServers\": "${startPolicy}"}}"
  eval $__result="'${patchJson}'"
}

#
# Function to create patch json string to unset policy
# $1 - Domain resource in json format
# $2 - Name of server whose policy will be patched
# $3 - Return value containing patch json string
#
createPatchJsonToUnsetPolicy() {
  local domainJson=$1
  local serverName=$2
  local __result=$3

  unsetServerStartPolicy "${domainJson}" "${serverName}" serverStartPolicyPatch
  patchJson="{\"spec\": {\"managedServers\": "${serverStartPolicyPatch}"}}"
  eval $__result="'${patchJson}'"
}

#
# Function to create patch string with server start policy unset
# $1 - Domain resource in json format
# $2 - Name of server whose policy will be unset
# $3 - Return value containing patch string with server start policy unset
#
unsetServerStartPolicy() {
  local domainJson=$1
  local serverName=$2
  local __result=$3
  local unsetStartPolicyPatch=""
  local mapCmd=""
  local removeNullCmd=""
  local unsetStartPolicyPatchNoNulls=""

  unsetCmd="(.spec.managedServers[] | select (.serverName == \"${serverName}\") | del (.serverStartPolicy))"
  replacePolicyCmd=$(echo ${domainJson} | jq -cr "${unsetCmd}")
  replacePolicyCmdLen=$(echo "${replacePolicyCmd}" | jq -e keys_unsorted | jq length)
  if [ ${replacePolicyCmdLen} == 1 ]; then
    mapCmd=". |= map(if .serverName == \"${serverName}\" then del(.) else . end)"
  else
    mapCmd=". |= map(if .serverName == \"${serverName}\" then . = ${replacePolicyCmd} else . end)"
  fi
  unsetStartPolicyPatch=$(echo ${domainJson} | jq "(.spec.managedServers)" | jq "${mapCmd}")
  removeNullCmd="del(.[] | select(. == null))"
  unsetStartPolicyPatchNoNulls=$(echo "${unsetStartPolicyPatch}" | jq "${removeNullCmd}")
  eval $__result="'${unsetStartPolicyPatchNoNulls}'"
}

#
# Function to create patch json to update cluster server start policy
# $1 - Domain resource in json format
# $2 - Name of cluster whose policy will be patched
# $3 - policy value of "IF_NEEDED" or "NEVER"
# $4 - Return value containing patch json string
#
createPatchJsonToUpdateClusterPolicy() {
  local domainJson=$1
  local clusterName=$2
  local policy=$3
  local __result=$4
  local addClusterStartPolicyCmd=""
  local mapCmd=""
  local existingClusters=""
  local patchJsonVal=""
  local startPolicyPatch=""

  existingClusters=$(echo ${domainJson} | jq -cr '(.spec.clusters)')
  if [ "${existingClusters}" == "null" ]; then
    # cluster doesn't exist, add cluster with server start policy
    addClusterStartPolicyCmd=".[.| length] |= . + {\"clusterName\":\"${clusterName}\", \
      \"serverStartPolicy\":\"${policy}\"}"
    startPolicyPatch=$(echo ${existingClusters} | jq -c "${addClusterStartPolicyCmd}")
  else
    mapCmd="\
      . |= (map(.clusterName) | index (\"${clusterName}\")) as \$idx | \
      if \$idx then \
      .[\$idx][\"serverStartPolicy\"] = \"${policy}\" \
      else .+  [{clusterName: \"${clusterName}\" , serverStartPolicy: \"${policy}\"}] end"
    startPolicyPatch=$(echo ${existingClusters} | jq "${mapCmd}")
  fi

  patchJsonVal="{\"spec\": {\"clusters\": "${startPolicyPatch}"}}"
  eval $__result="'${patchJsonVal}'"
}

#
# Function to create patch json to update cluster replicas
# $1 - Domain resource in json format
# $2 - Name of cluster whose replicas will be patched
# $3 - replica count
# $4 - Return value containing patch json string
#
createPatchJsonToUpdateReplicas() {
  local domainJson=$1
  local clusterName=$2
  local replicas=$3
  local __result=$4
  local existingClusters=""
  local addClusterReplicasCmd=""
  local replicasPatch=""
  local mapCmd=""
  local patchJsonVal=""

  existingClusters=$(echo ${domainJson} | jq -cr '(.spec.clusters)')
  if [ "${existingClusters}" == "null" ]; then
    # cluster doesn't exist, add cluster with replicas
    addClusterReplicasCmd=".[.| length] |= . + {\"clusterName\":\"${clusterName}\", \
      \"replicas\":${replicas}}"
    replicasPatch=$(echo ${existingClusters} | jq -c "${addClusterReplicasCmd}")
  else
    mapCmd="\
      . |= (map(.clusterName) | index (\"${clusterName}\")) as \$idx | \
      if \$idx then \
      .[\$idx][\"replicas\"] = ${replicas} \
      else .+  [{clusterName: \"${clusterName}\" , replicas: ${replicas}}] end"
    replicasPatch=$(echo ${existingClusters} | jq "${mapCmd}")
  fi
  patchJsonVal="{\"spec\": {\"clusters\": "${replicasPatch}"}}"
  eval $__result="'${patchJsonVal}'"
}

#
# Function to create patch json to update domain server start policy
# $1 - policy value of "IF_NEEDED" or "NEVER"
# $2 - Return value containing patch json string
#
createPatchJsonToUpdateDomainPolicy() {
  local policy=$1
  local __result=$2

  patchServerStartPolicy="{\"spec\": {\"serverStartPolicy\": \"${policy}\"}}"
  eval $__result="'${patchServerStartPolicy}'"
}

#
# Function to get sorted list of servers in a cluster.
# The sorted list is created in 'sortedByAlwaysServers' array.
# $1 - Domain resource in json format
# $2 - Name of server
# $3 - Name of cluster
# $4 - Indicates if policy of current server would be unset.
#      valid values are "UNSET" and "CONSTANT"
#
getSortedListOfServers() {
  local domainJson=$1
  local serverName=$2
  local clusterName=$3
  local withPolicy=$4
  local policy=""
  local sortedServers=()
  local otherServers=()

  getTopology "${domainUid}" "${domainNamespace}" jsonTopology
  clusterTopology=$(echo ${jsonTopology} | jq -r '.domain | .configuredClusters[] | select (.name == '\"${clusterName}\"')')
  dynaCluster=$(echo ${clusterTopology} | jq .dynamicServersConfig)
  if [ "${dynaCluster}" == "null" ]; then
    # Cluster is a configured cluster, get server names
    servers=($(echo ${clusterTopology} | jq -r .servers[].name))
    # Sort server names in numero lexi order
    IFS=$'\n' sortedServers=($(sort --version-sort <<<"${servers[*]}" ))
    unset IFS
    clusterSize=${#sortedServers[@]}
  else
    # Cluster is a dynamic cluster, calculate server names
    prefix=$(echo ${dynaCluster} | jq -r .serverNamePrefix)
    clusterSize=$(echo ${dynaCluster} | jq .dynamicClusterSize)
    for (( i=1; i<=$clusterSize; i++ )); do
      localServerName=${prefix}$i
      sortedServers+=(${localServerName})
    done
  fi
  # Create arrays of ALWAYS policy servers and other servers
  for localServerName in ${sortedServers[@]:-}; do
    getEffectivePolicy "${domainJson}" "${localServerName}" "${clusterName}" policy
    # Update policy when server name matches current server and unsetting
    if [[ "${withPolicy}" == "UNSET" && "${serverName}" == "${localServerName}" ]]; then
      policy=UNSET
    fi
    if [ "${policy}" == "ALWAYS" ]; then
      sortedByAlwaysServers+=(${localServerName})
    else
      otherServers+=(${localServerName})
    fi
  done

  # append other servers to the list of servers with always policy
  for otherServer in ${otherServers[@]:-}; do
    sortedByAlwaysServers+=($otherServer)
  done
}

#
# Get replica count for a cluster
# $1 - Domain resource in json format
# $2 - Name of cluster
# $3 - Return value containing replica count
#
getReplicaCount() {
  local domainJson=$1
  local clusterName=$2
  local __replicaCount=$3

  replicasCmd="(.spec.clusters[] \
    | select (.clusterName == \"${clusterName}\")).replicas"
  replicaCount=$(echo ${domainJson} | jq "${replicasCmd}")
  if [[ -z "${replicaCount}" || "${replicaCount}" == "null" ]]; then
    replicaCount=$(echo ${domainJson} | jq .spec.replicas)
  fi
  if [[ -z "${replicaCount}" || "${replicaCount}" == "null" ]]; then
    replicaCount=0
  fi
  # check if replica count is less than minimum replicas
  getMinReplicas "${domainJson}" "${clusterName}" minReplicas
  if [ "${replicaCount}" -lt "${minReplicas}" ]; then
    # Reset current replica count to minimum replicas
    replicaCount=${minReplicas}
  fi
  eval $__replicaCount="'${replicaCount}'"

}

#
# Function to generate the domain restartVersion by incrementing the
# existing value. If the restartVersion doesn't exist or the restartVersion
# value is non-numeric, then return '1'.
# $1 - Domain resource in json format
# $2 - Return value containing the restart version.
#
generateDomainRestartVersion() {
  local domainJson=$1
  local __result=$2
  local __restartVersion=""

  eval $__result=""
  __restartVersion=$(echo ${domainJson} | jq -cr .spec.restartVersion)
  if ! [[ "$__restartVersion" =~ ^[0-9]+$ ]] ; then
   __restartVersion=0
  fi
  __restartVersion=$((__restartVersion+1))
  eval $__result=${__restartVersion}
}

#
# Function to generate the domain introspectVersion by incrementing the
# existing value. If the introspectVersion doesn't exist or the introspectVersion
# value is non-numeric, then return '1'.
# $1 - Domain resource in json format
# $2 - Return value containing the introspect version.
#
generateDomainIntrospectVersion() {
  local domainJson=$1
  local __result=$2
  local __introspectVersion=""

  eval $__result=""
  __introspectVersion=$(echo ${domainJson} | jq -cr .spec.introspectVersion)
  if ! [[ "$__introspectVersion" =~ ^[0-9]+$ ]] ; then
   __introspectVersion=0
  fi
  __introspectVersion=$((__introspectVersion+1))
  eval $__result=${__introspectVersion}
}

#
# Function to generate the cluster restartVersion by incrementing the
# existing value of cluster restartVersion. If the restartVersion
# value at the cluster level is non-numeric, then it returns 1.
# If the restartVersion doesn't exist at the cluster level, then it
# returns the incremented value of the domain level restartVersion.
# In this case, if the restartVersion value at the domain level is
# non-numeric, then it returns 1.
# $1 - Domain resource in json format
# $2 - Name of cluster
# $3 - Return value containing the restart version.
#
generateClusterRestartVersion() {
  local domainJson=$1
  local clusterName=$2
  local __result=$3
  local __restartVersionCmd=""
  local __restartVersion=""

  eval $__result=""
  __restartVersionCmd="(.spec.clusters // empty | .[] \
    | select (.clusterName == \"${clusterName}\")).restartVersion"
  __restartVersion=$(echo ${domainJson} | jq -cr "${__restartVersionCmd}")
  if [ "${__restartVersion}" == "null" ]; then
    __restartVersion=$(echo ${domainJson} | jq -cr .spec.restartVersion)
  fi
  if ! [[ "${__restartVersion}" =~ ^[0-9]+$ ]] ; then
   __restartVersion=0
  fi
  __restartVersion=$((__restartVersion+1))
  eval $__result=${__restartVersion}

}

#
# Function to create patch json to update domain restart version
# $1 - domain restart version
# $2 - Return value containing patch json string
#
createPatchJsonToUpdateDomainRestartVersion() {
  local restartVersion=$1
  local __result=$2
  local __restartVersionPatch=""

  __restartVersionPatch="{\"spec\": {\"restartVersion\": \"${restartVersion}\"}}"
  eval $__result="'${__restartVersionPatch}'"
}

#
# Function to create patch json to update domain introspect version
# $1 - domain introspect version
# $2 - Return value containing patch json string
#
createPatchJsonToUpdateDomainIntrospectVersion() {
  local introspectVersion=$1
  local __result=$2
  local __introspectVersionPatch=""

  __introspectVersionPatch="{\"spec\": {\"introspectVersion\": \"${introspectVersion}\"}}"
  eval $__result="'${__introspectVersionPatch}'"
}

#
# Function to create patch json to update cluster restartVersion
# $1 - Domain resource in json format
# $2 - Name of the cluster whose restartVersion will be patched
# $3 - restart version
# $4 - Return value containing patch json string
#
createPatchJsonToUpdateClusterRestartVersion() {

  local domainJson=$1
  local clusterName=$2
  local restartVersion=$3
  local __result=$4
  local __existingClusters=""
  local __addClusterReplicasCmd=""
  local __restartVersionPatch=""
  local __mapCmd=""
  local __patchJsonVal=""

  __existingClusters=$(echo ${domainJson} | jq -cr '(.spec.clusters)')
  if [ "${__existingClusters}" == "null" ]; then
    # cluster doesn't exist, add cluster with replicas
    __addClusterReplicasCmd=".[.| length] |= . + {\"clusterName\":\"${clusterName}\", \
      \"restartVersion\":\"${restartVersion}\"}"
    __restartVersionPatch=$(echo ${__existingClusters} | jq -c "${__addClusterReplicasCmd}")
  else
    __mapCmd="\
      . |= (map(.clusterName) | index (\"${clusterName}\")) as \$idx | \
      if \$idx then \
      .[\$idx][\"restartVersion\"] = \"${restartVersion}\" \
      else .+  [{clusterName: \"${clusterName}\" , restartVersion: \"${restartVersion}\"}] end"
    __restartVersionPatch=$(echo ${__existingClusters} | jq "${__mapCmd}")
  fi
  __patchJsonVal="{\"spec\": {\"clusters\": "${__restartVersionPatch}"}}"
  eval $__result="'${__patchJsonVal}'"
}

#
# Check servers started in a cluster based on server start policy and
# replica count.
# $1 - Domain resource in json format
# $2 - Name of server
# $3 - Name of cluster
# $4 - Indicates if replicas will stay constant, incremented or decremented.
#      Valid values are "CONSTANT", "INCREMENT" and "DECREMENT"
# $5 - Indicates if policy of current server will stay constant or unset.
#      Valid values are "CONSTANT" and "UNSET"
# $6 - Return value of "true" or "false" indicating if current server will be started
#
checkStartedServers() {
  local domainJson=$1
  local serverName=$2
  local clusterName=$3
  local withReplicas=$4
  local withPolicy=$5
  local __started=$6
  local localServerName=""
  local policy=""
  local replicaCount=0
  local currentReplicas=0
  local startedServers=()
  local sortedByAlwaysServers=()

  # Get sorted list of servers in 'sortedByAlwaysServers' array
  getSortedListOfServers "${domainJson}" "${serverName}" "${clusterName}" "${withPolicy}"
  getReplicaCount "${domainJson}" "${clusterName}" replicaCount
  # Increment or decrement the replica count based on 'withReplicas' input parameter
  if [ "${withReplicas}" == "INCREASED" ]; then
    replicaCount=$((replicaCount+1))
  elif [ "${withReplicas}" == "DECREASED" ]; then
    replicaCount=$((replicaCount-1))
  fi
  for localServerName in ${sortedByAlwaysServers[@]:-}; do
    getEffectivePolicy "${domainJson}" "${localServerName}" "${clusterName}" policy
    # Update policy when server name matches current server and unsetting
    if [[ "${serverName}" == "${localServerName}" && "${withPolicy}" == "UNSET" ]]; then
      policy=UNSET
    fi
    # check if server should start based on replica count, policy and current replicas
    shouldStart "${currentReplicas}" "${policy}" "${replicaCount}" result
    if [ "${result}" == 'true' ]; then
      # server should start, increment current replicas and add server to list of started servers
      currentReplicas=$((currentReplicas+1))
      startedServers+=(${localServerName})
    fi
  done
  startedSize=${#startedServers[@]}
  if [ ${startedSize} -gt 0 ]; then
    # check if current server is in the list of started servers
    if checkStringInArray ${serverName} ${startedServers[@]}; then
      eval $__started="true"
      return
    fi
  fi
  eval $__started="false"
}

#
# Function to check if server should start based on policy and current replicas
# $1 - Current number of replicas
# $2 - Server start policy
# $3 - Replica count
# $4 - Returns "true" or "false" indicating if server should start.
#
shouldStart() {
  local currentReplicas=$1
  local policy=$2
  local replicaCount=$3
  local __result=$4

  if [ "$policy" == "ALWAYS" ]; then
    eval $__result=true
  elif [ "$policy" == "NEVER" ]; then
    eval $__result=false
  elif [ "${currentReplicas}" -lt "${replicaCount}" ]; then
    eval $__result=true
  else
    eval $__result=false
  fi
}

#
# Function to check if cluster's replica count is same as min replicas
# $1 - Domain resource in json format
# $2 - Name of the cluster
# $3 - Returns "true" or "false" indicating if replica count is equal to
#      or greater than min replicas.
#
isReplicaCountEqualToMinReplicas() {
  local domainJson=$1
  local clusterName=$2
  local __result=$3

  eval $__result=false
  getMinReplicas "${domainJson}" "${clusterName}" minReplicas
  getReplicaCount  "${domainJson}" "${clusterName}" replica
  if [ ${replica} -eq ${minReplicas} ]; then
    eval $__result=true
  fi
}

#
# Function to check if provided replica count is in the allowed range
# $1 - Domain resource in json format
# $2 - Name of the cluster
# $3 - Replica count
# $4 - Returns "true" or "false" indicating if replica count is in
#      the allowed range
# $5 - Returns allowed range for replica count for the given cluster
#
isReplicasInAllowedRange() {
  local domainJson=$1
  local clusterName=$2
  local replicas=$3
  local __result=$4
  local __range=$5
  local rangeVal=""

  eval $__result=true
  getMinReplicas "${domainJson}" "${clusterName}" minReplicas
  getMaxReplicas "${domainJson}" "${clusterName}" maxReplicas
  rangeVal="${minReplicas} to ${maxReplicas}"
  eval $__range="'${rangeVal}'"
  if [ ${replicas} -lt ${minReplicas} ] || [ ${replicas} -gt ${maxReplicas} ]; then
    eval $__result=false
  fi
}

#
# Function to get minimum replica count for cluster
# $1 - Domain resource in json format
# $2 - Name of the cluster
# $3 - Return value containing minimum replica count
#
getMinReplicas() {
  local domainJson=$1
  local clusterName=$2
  local __result=$3
  local minReplicaCmd=""
  local minReplicasVal=""

  eval $__result=0
  minReplicaCmd="(.status.clusters[] | select (.clusterName == \"${clusterName}\")) \
    | .minimumReplicas"
  minReplicasVal=$(echo ${domainJson} | jq "${minReplicaCmd}")
  eval $__result=${minReplicasVal:-0}
}

#
# Function to get maximum replica count for cluster
# $1 - Domain resource in json format
# $2 - Name of the cluster
# $3 - Return value containing maximum replica count
#
getMaxReplicas() {
  local domainJson=$1
  local clusterName=$2
  local __result=$3
  local maxReplicaCmd=""
  local maxReplicasVal=""

  maxReplicaCmd="(.status.clusters[] | select (.clusterName == \"${clusterName}\")) \
    | .maximumReplicas"
  maxReplicasVal=$(echo ${domainJson} | jq "${maxReplicaCmd}")
  eval $__result=${maxReplicasVal:-0}
}

#
# Function to create patch string for updating replica count
# $1 - Domain resource in json format
# $2 - Name of cluster whose replica count will be patched
# $3 - operation string indicating whether to increment or decrement replica count.
#      Valid values are "INCREMENT" and "DECREMENT"
# $4 - Return value containing replica update patch string
# $5 - Return value containing updated replica count
#
createReplicaPatch() {
  local domainJson=$1
  local clusterName=$2
  local operation=$3
  local __result=$4
  local __replicaCount=$5
  local maxReplicas=""
  local infoMessage="Current replica count value is same as or greater than maximum number of replica count. \
Not increasing replica count value."

  getReplicaCount  "${domainJson}" "${clusterName}" replica
  if [ "${operation}" == "DECREMENT" ]; then
    replica=$((replica-1))
  elif [ "${operation}" == "INCREMENT" ]; then
    getMaxReplicas "${domainJson}" "${clusterName}" maxReplicas
    if [ ${replica} -ge ${maxReplicas} ]; then
      printInfo "${infoMessage}"
    else
      replica=$((replica+1))
    fi
  fi

  cmd="(.spec.clusters[] | select (.clusterName == \"${clusterName}\") \
    | .replicas) |= ${replica}"
  replicaPatch=$(echo ${domainJson} | jq "${cmd}" | jq -cr '(.spec.clusters)')
  eval $__result="'${replicaPatch}'"
  eval $__replicaCount="'${replica}'"
}

#
# Function to validate whether a server belongs to a  cluster or is an independent managed server
# $1 - Domain unique id.
# $2 - Domain namespace.
# $3 - Server name.
# $4 - Return value of "true" or "false" indicating if server is valid (i.e. if it's part of a cluster or independent server).
# $5 - Retrun value containting cluster name to which this server belongs.
#
validateServerAndFindCluster() {
  local domainUid=$1
  local domainNamespace=$2
  local serverName=$3
  local __isValidServer=$4
  local __clusterName=$5
  local __isAdminServer=$6
  local serverCount=""

  eval $__isValidServer=false
  eval $__isAdminServer=false
  eval $__clusterName=UNKNOWN
  getTopology "${domainUid}" "${domainNamespace}" jsonTopology
  adminServer=$(echo $jsonTopology | jq -r .domain.adminServerName)
  if [ "${serverName}" == "${adminServer}" ]; then
    eval $__isAdminServer=true
  fi
  servers=($(echo $jsonTopology | jq -r '.domain.servers[].name'))
  if  checkStringInArray "${serverName}" "${servers[@]}" ; then
    eval $__clusterName=""
    eval $__isValidServer=true
  else
    dynamicClause=".domain.configuredClusters[] | select (.dynamicServersConfig != null)"
    namePrefixSize=". | {name: .name, prefix:.dynamicServersConfig.serverNamePrefix, \
                 max:.dynamicServersConfig.maxDynamicClusterSize}"
    dynamicClusters=($(echo $jsonTopology | jq "${dynamicClause}" | jq -cr "${namePrefixSize}"))
    dynamicClustersSize=${#dynamicClusters[@]}
    for dynaClusterNamePrefix in ${dynamicClusters[@]:-}; do
      prefix=$(echo ${dynaClusterNamePrefix} | jq -r .prefix)
      if [[ "${serverName}" == "${prefix}"* ]]; then
        maxSize=$(echo ${dynaClusterNamePrefix} | jq -r .max)
        number='^[0-9]+$'
        serverCount=$(echo "${serverName:${#prefix}}")
        if ! [[ $serverCount =~ $number ]] ; then
           printError "Server name ${serverName} is not valid for dynamic cluster."
           exit 1
        fi
        if [ "${serverCount}" -lt 1 ] || [ "${serverCount}" -gt "${maxSize}" ]; then
          printError "Index of server name ${serverName} for dynamic cluster is outside \
            the allowed range of 1 to ${maxSize}. \
            Please make sure server name is correct."
          exit 1
        fi
        eval $__clusterName="'$(echo ${dynaClusterNamePrefix} | jq -r .name)'"
        eval $__isValidServer=true
        break
      fi
    done
    staticClause=".domain.configuredClusters[] | select (.dynamicServersConfig == null)"
    nameCmd=" . | {name: .name, serverName: .servers[].name}"
    configuredClusters=($(echo $jsonTopology | jq "${staticClause}" | jq -cr "${nameCmd}"))
    configuredClusterSize=${#configuredClusters[@]}
    for configuredClusterName in ${configuredClusters[@]:-}; do
      name=$(echo ${configuredClusterName} | jq -r .serverName)
      if [ "${serverName}" == "${name}" ]; then
        eval $__clusterName="'$(echo ${configuredClusterName} | jq -r .name)'"
        eval $__isValidServer=true
        break
      fi
    done
  fi
}

#
# Function to validate whether a cluster is valid and part of the domain
# $1 - Domain unique id.
# $2 - Domain namespace.
# $3 - cluster name
# $4 - Retrun value "true" or "false" indicating whether cluster name is valid
#
validateClusterName() {
  local domainUid=$1
  local domainNamespace=$2
  local clusterName=$3
  local __isValidCluster=$4

  getTopology "${domainUid}" "${domainNamespace}" jsonTopology
  clusters=($(echo $jsonTopology | jq -cr .domain.configuredClusters[].name))
  if  checkStringInArray "${clusterName}" "${clusters[@]}" ; then
    eval $__isValidCluster=true
  else
    eval $__isValidCluster=false
  fi
}

getTopology() {
  local domainUid=$1
  local domainNamespace=$2
  local __result=$3
  local __jsonTopology=""
  local __topology=""

  if [[ "$OSTYPE" == "darwin"* ]]; then
    configMap=$(${kubernetesCli} get cm ${domainUid}-weblogic-domain-introspect-cm \
      -n ${domainNamespace} -o yaml --ignore-not-found)
  else
    configMap=$(${kubernetesCli} get cm ${domainUid}-weblogic-domain-introspect-cm \
      -n ${domainNamespace} -o json --ignore-not-found)
  fi
  if [ -z "${configMap}" ]; then
    printError "Domain config map '${domainUid}-weblogic-domain-introspect-cm' not found. \
      This script requires that the introspector job for the specified domain ran \
      successfully and generated this config map. Exiting."
    exit 1
  else
    __jsonTopology=$(echo "${configMap}" | jq -r '.data["topology.json"]')
  fi
  if [ ${__jsonTopology} == null ]; then
    if [[ "$OSTYPE" == "darwin"* ]]; then
      if ! [ -x "$(command -v yq)" ]; then
        validationError "MacOS detected, the domain is hosted on a pre-3.2.0 version of \
          the Operator, and 'yq' is not installed locally. To fix this, install 'yq', \
          call the script from Linux instead of MacOS, or upgrade the Operator version."
        exit 1
      fi
      __jsonTopology=$(echo "${configMap}" | yq r - data.[topology.yaml] | yq r - -j)
    else
      if ! [ -x "$(command -v python)" ]; then
        validationError "Linux OS detected, the domain is hosted on a pre-3.2.0 version of \
          the Operator, and 'python' is not installed locally. To fix this, install 'python' \
          or upgrade the Operator version."
        exit 1
      fi
      __topology=$(echo "${configMap}" | jq '.data["topology.yaml"]')
      __jsonTopology=$(python -c \
      'import sys, yaml, json; print json.dumps(yaml.safe_load('"${__topology}"'), indent=4)')
    fi
  fi
  eval $__result="'${__jsonTopology}'"
}


#
# check if string passed as first argument is present in array passed as second argument
# $1 - string to check
# $2 - array
checkStringInArray() {
    local str=$1 arr
    shift
    for arr; do
      [[ $str = "$arr" ]] && return
    done
    return 1
}

# try to execute jq to see whether jq is available
validateJqAvailable() {
  if ! [ -x "$(command -v jq)" ]; then
    validationError "jq is not installed"
  fi
}

# try to execute kubernetes cli to see whether cli is available
validateKubernetesCliAvailable() {
  if ! [ -x "$(command -v ${kubernetesCli})" ]; then
    validationError "${kubernetesCli} is not installed"
  fi
}

# Function to print an error message
printError() {
  echo [`timestamp`][ERROR] $*
}

# Function to print an error message
printInfo() {
  echo [`timestamp`][INFO] $*
}

#
# Function to execute patch command and print verbose information
# $1 - Kubernetes command line interface
# $2 - Domain unique id
# $2 - Domain namespace
# $4 - Json string to be used in 'patch' command
# $5 - Verbose mode. Legal values are "true" or "false"
#
executePatchCommand() {
  local kubernetesCli=$1
  local domainUid=$2
  local domainNamespace=$3
  local patchJson=$4
  local verboseMode=$5

  if [ "${verboseMode}" == "true" ]; then
    printInfo "Executing command --> ${kubernetesCli} patch domain ${domainUid} \
      -n ${domainNamespace} --type=merge --patch \"${patchJson}\""
  fi
  ${kubernetesCli} patch domain ${domainUid} -n ${domainNamespace} --type=merge --patch "${patchJson}"
}

# timestamp
#   purpose:  echo timestamp in the form yyyy-mm-ddThh:mm:ss.nnnnnnZ
#   example:  2018-10-01T14:00:00.000001Z
timestamp() {
  local timestamp="`date --utc '+%Y-%m-%dT%H:%M:%S.%NZ' 2>&1`"
  if [ ! "${timestamp/illegal/xyz}" = "${timestamp}" ]; then
    # old shell versions don't support %N or --utc
    timestamp="`date -u '+%Y-%m-%dT%H:%M:%S.000000Z' 2>&1`"
  fi
  echo "${timestamp}"
}

#
# Function to note that a validate error has occurred
#
validationError() {
  printError $*
  validateErrors=true
}

#
# Function to cause the script to fail if there were any validation errors
#
failIfValidationErrors() {
  if [ "$validateErrors" = true ]; then
    printError 'The errors listed above must be resolved before the script can continue. Please see usage information below.'
    usage 1
  fi
}

#
# Function to lowercase a value and make it a legal DNS1123 name
# $1 - value to convert to DNS legal name
# $2 - return value containing DNS legal name.
toDNS1123Legal() {
  local name=$1
  local __result=$2
  local val=`echo "${name}" | tr "[:upper:]" "[:lower:]"`
  val=${val//"_"/"-"}
  eval $__result="'$val'"
}
