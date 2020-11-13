# !/bin/sh
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

#
# Function to get server start policy at cluster level
# $1 - Domain resource in json format
# $2 - Name of cluster
# $3 - Return value for cluster level server start policy.
#      Legal return values are "NEVER" or "IF_NEEDED" or "".
#
function getClusterPolicy {
  local domainJson=$1
  local clusterName=$2
  local __clusterPolicy=$3
  local effectivePolicy=""

  clusterPolicyCmd="(.spec.clusters[] \
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
function getDomainPolicy {
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
function getEffectivePolicy {
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
# Function to get current start policy of server
# $1 - Domain resource in json format
# $2 - Name of server
# $3 - Return value containing current server start policy
#      Legal retrun values are "NEVER" or "IF_NEEDED", "ALWAYS" or "".
#
function getServerPolicy {
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
function createServerStartPolicyPatch {
  local domainJson=$1
  local serverName=$2
  local policy=$3
  local __result=$4
  local currentStartPolicy=""

  # Get server start policy for this server
  getServerPolicy "${domainJson}" "${serverName}" currentStartPolicy
  if [ -z "${currentStartPolicy}" ]; then
    # Server start policy doesn't exist, add a new policy
    addPolicyCmd=".[.| length] |= . + {\"serverName\":\"${serverName}\", \
      \"serverStartPolicy\":\"${policy}\"}"
    serverStartPolicyPatch=$(echo ${domainJson} | jq .spec.managedServers | jq -c "${addPolicyCmd}")
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
function createPatchJsonToUnsetPolicyAndUpdateReplica {
  local domainJson=$1
  local serverName=$2
  local replicaPatch=$3
  local __result=$4

  replacePolicyCmd="[(.spec.managedServers[] \
    | select (.serverName != \"${serverName}\"))]"
  serverStartPolicyPatch=$(echo ${domainJson} | jq "${replacePolicyCmd}")
  patchJson="{\"spec\": {\"clusters\": "${replicaPatch}",\"managedServers\": "${serverStartPolicyPatch}"}}"
  eval $__result="'${patchJson}'"
}

#
# Function to create patch json string to update policy 
# $1 - String containing start policy info
# $2 - String containing json to patch domain resource
#
function createPatchJsonToUpdatePolicy {
  local startPolicy=$1
  local __result=$2
  patchJson="{\"spec\": {\"managedServers\": "${startPolicy}"}}"
  eval $__result="'${patchJson}'"
}

#
# Function to create patch json string to update replica 
# $1 - String containing replica
# $2 - String containing json to patch domain resource
#
function createPatchJsonToUpdateReplica {
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
function createPatchJsonToUpdateReplicaAndPolicy {
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
function createPatchJsonToUnsetPolicy {
  local domainJson=$1
  local serverName=$2
  local __result=$3

  replacePolicyCmd="[(.spec.managedServers[] \
    | select (.serverName != \"${serverName}\"))]"
  serverStartPolicyPatch=$(echo ${domainJson} | jq "${replacePolicyCmd}")
  patchJson="{\"spec\": {\"managedServers\": "${serverStartPolicyPatch}"}}"
  eval $__result="'${patchJson}'"
}

#
# Function to create patch json to update cluster server start policy
# $1 - Domain resource in json format
# $2 - Name of cluster whose policy will be patched
# $3 - policy value of "IF_NEEDED" or "NEVER"
# $4 - Return value containing patch json string
#
function createPatchJsonToUpdateClusterPolicy {
  local domainJson=$1
  local clusterName=$2
  local policy=$3
  local __result=$4
  
  replacePolicyCmd="(.spec.clusters[] | select (.clusterName == \"${clusterName}\") \
    | .serverStartPolicy) |= \"${policy}\""
  startPolicy=$(echo ${domainJson} | jq "${replacePolicyCmd}" | jq -cr '(.spec.clusters)')
  patchJson="{\"spec\": {\"clusters\": "${startPolicy}"}}"
  eval $__result="'${patchJson}'"
}

#
# Function to create patch json to update domain server start policy
# $1 - policy value of "IF_NEEDED" or "NEVER"
# $2 - Return value containing patch json string
#
function createPatchJsonToUpdateDomainPolicy {
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
function getSortedListOfServers {
  local domainJson=$1
  local serverName=$2
  local clusterName=$3
  local withPolicy=$4
  local policy=""
  local sortedServers=()
  local otherServers=()

  getConfigMap "${domainUid}" "${domainNamespace}" configMap
  topology=$(echo "${configMap}" | jq '.data["topology.yaml"]')
  jsonTopology=$(python -c \
    'import sys, yaml, json; print json.dumps(yaml.safe_load('"${topology}"'), indent=4)')
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
function getReplicaCount {
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
function checkStartedServers {
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
function shouldStart {
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
function isReplicaCountEqualToMinReplicas {
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
# Function to get minimum replica count for cluster
# $1 - Domain resource in json format
# $2 - Name of the cluster
# $3 - Return value containing minimum replica count
#
function getMinReplicas {
  local domainJson=$1
  local clusterName=$2
  local __result=$3

  eval $__result=0
  minReplicaCmd="(.status.clusters[] | select (.clusterName == \"${clusterName}\")) \
    | .minimumReplicas"
  minReplicas=$(echo ${domainJson} | jq "${minReplicaCmd}")
  eval $__result=${minReplicas}
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
function createReplicaPatch {
  local domainJson=$1
  local clusterName=$2
  local operation=$3
  local __result=$4
  local __replicaCount=$5
  local maxReplicas=""
  local infoMessage="Current replica count value is same as or greater than maximum number of replica count. \
Not increasing replica count value."

  maxReplicaCmd="(.status.clusters[] | select (.clusterName == \"${clusterName}\")) \
    | .maximumReplicas"
  getReplicaCount  "${domainJson}" "${clusterName}" replica
  if [ "${operation}" == "DECREMENT" ]; then
    replica=$((replica-1))
  elif [ "${operation}" == "INCREMENT" ]; then
    maxReplicas=$(echo ${domainJson} | jq "${maxReplicaCmd}")
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
function validateServerAndFindCluster {
  local domainUid=$1
  local domainNamespace=$2 
  local serverName=$3
  local __isValidServer=$4
  local __clusterName=$5
  local serverCount=""

  eval $__isValidServer=false
  eval $__clusterName=UNKNOWN
  getConfigMap "${domainUid}" "${domainNamespace}" configMap
  topology=$(echo "${configMap}" | jq '.data["topology.yaml"]')
  jsonTopology=$(python -c \
    'import sys, yaml, json; print json.dumps(yaml.safe_load('"${topology}"'), indent=4)')
  adminServer=$(echo $jsonTopology | jq -r .domain.adminServerName)
  if [ "${serverName}" == "${adminServer}" ]; then
    printError "Server '${serverName}' is administration server. The '${script}' script doesn't support starting or stopping administration server."
    exit 1
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
        if [ $(echo "${serverName}" | grep -c -Eo '[0-9]+$') -gt 0 ]; then
          serverCount=$(echo "${serverName}" | grep -Eo '[0-9]+$')
        fi
        if ! [[ $serverCount =~ $number ]] ; then
           printError "Server name is not valid for dynamic cluster."
           exit 1
        fi
        if [ "${serverCount}" -gt "${maxSize}" ]; then
          printError "Server name is outside the range of allowed servers. \
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
function validateClusterName {
  local domainUid=$1
  local domainNamespace=$2
  local clusterName=$3
  local __isValidCluster=$4

  getConfigMap "${domainUid}" "${domainNamespace}" configMap
  configMap=$(${kubernetesCli} get cm ${domainUid}-weblogic-domain-introspect-cm \
    -n ${domainNamespace} -o json)
  topology=$(echo "${configMap}" | jq '.data["topology.yaml"]')
  jsonTopology=$(python -c \
    'import sys, yaml, json; print json.dumps(yaml.safe_load('"${topology}"'), indent=4)')
  clusters=($(echo $jsonTopology | jq -cr .domain.configuredClusters[].name))
  if  checkStringInArray "${clusterName}" "${clusters[@]}" ; then
    eval $__isValidCluster=true
  else
    eval $__isValidCluster=false
  fi
}

function getConfigMap {
  local domainUid=$1
  local domainNamespace=$2
  local __result=$3 

  configMap=$(${kubernetesCli} get cm ${domainUid}-weblogic-domain-introspect-cm \
    -n ${domainNamespace} -o json --ignore-not-found)
  if [ -z "${configMap}" ]; then
    printError "Domain config map '${domainUid}-weblogic-domain-introspect-cm' not found. \
      This script requires that the introspector job for the specified domain ran \
      successfully and generated this config map. Exiting."
    exit 1
  fi
  eval $__result="'${configMap}'"
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
function validateJqAvailable {
  if ! [ -x "$(command -v jq)" ]; then
    validationError "jq is not installed"
  fi
}

# try to execute python to see whether python is available
function validatePythonAvailable {
  if ! [ -x "$(command -v python)" ]; then
    validationError "python is not installed"
  fi
}

# try to execute kubernetes cli to see whether cli is available
function validateKubernetesCliAvailable {
  if ! [ -x "$(command -v ${kubernetesCli})" ]; then
    validationError "${kubernetesCli} is not installed"
  fi
}

#
# Function to exit and print an error message
# $1 - text of message
function fail {
  printError $*
  exit 1
}

# Function to print an error message
function printError {
  echo [`timestamp`][ERROR] $*
}

# Function to print an error message
function printInfo {
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
function executePatchCommand {
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
#   purpose:  echo timestamp in the form yyyymmddThh:mm:ss.mmm ZZZ
#   example:  20181001T14:00:00.001 UTC
function timestamp() {
  timestamp="$(set +e && date --utc '+%Y-%m-%dT%H:%M:%S %N %s %Z' 2>&1 || echo illegal)"
  if [ ! "${timestamp/illegal/xyz}" = "${timestamp}" ]; then
    # old shell versions don't support %N or --utc
    timestamp="`date -u '+%Y-%m-%dT%H:%M:%S 000000 %s %Z' 2>&1`"
  fi
  local ymdhms="`echo $timestamp | awk '{ print $1 }'`"
  # convert nano to milli
  local milli="`echo $timestamp | awk '{ print $2 }' | sed 's/\(^...\).*/\1/'`"
  local secs_since_epoch="`echo $timestamp | awk '{ print $3 }'`"
  local millis_since_opoch="${secs_since_epoch}${milli}"
  local timezone="`echo $timestamp | awk '{ print $4 }'`"
  echo "${ymdhms}.${milli} ${timezone}"
}

#
# Function to note that a validate error has occurred
#
function validationError {
  printError $*
  validateErrors=true
}

#
# Function to cause the script to fail if there were any validation errors
#
function failIfValidationErrors {
  if [ "$validateErrors" = true ]; then
    fail 'The errors listed above must be resolved before the script can continue'
  fi
}
