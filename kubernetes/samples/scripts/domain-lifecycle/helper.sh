# !/bin/sh
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

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
  local __currentPolicy=$5
  local currentServerStartPolicy=""

  # Get server start policy for this server
  managedServers=$(echo ${domainJson} | jq -cr '(.spec.managedServers)')
  if [ "${managedServers}" != "null" ]; then
    extractPolicyCmd="(.spec.managedServers[] \
      | select (.serverName == \"${serverName}\") | .serverStartPolicy)"
    currentServerStartPolicy=$(echo ${domainJson} | jq "${extractPolicyCmd}")
  fi
  if [ -z "${currentServerStartPolicy}" ]; then
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
  eval $__currentPolicy=${currentServerStartPolicy}
}

#
# Function to create patch string for updating replica count
# $1 - Domain resource in json format
# $2 - Name of cluster whose replica count will be patched
# $3 - operatation string indicating whether to increment or decrement count
# $4 - Return value containing replica update patch string
# $5 - Retrun value containing updated replica count
#
function createReplicaPatch {
  local domainJson=$1
  local clusterName=$2
  local operation=$3
  local __result=$4
  local __relicaCount=$5
  local errorMessage="@@ ERROR: Maximum number of servers allowed (maxReplica = ${maxReplicas}) \
are already running. Please increase cluster size to start new servers."
  maxReplicas=""

  replicasCmd="(.spec.clusters[] \
    | select (.clusterName == \"${clusterName}\")).replicas"
  maxReplicaCmd="(.status.clusters[] | select (.clusterName == \"${clusterName}\")) \
    | .maximumReplicas"
  replica=$(echo ${domainJson} | jq "${replicasCmd}")
  if [[ -z "${replica}" || "${replica}" == "null" ]]; then
    replica=$(echo ${domainJson} | jq .spec.replicas)
  fi
  if [ "${operation}" == "DECREMENT" ]; then
    replica=$((replica-1))
    if [ ${replica} -lt 0 ]; then
      replica=0
    fi
  elif [ "${operation}" == "INCREMENT" ]; then
    replica=$((replica+1))
    maxReplicas=$(echo ${domainJson} | jq "${maxReplicaCmd}")
    if [ ${replica} -gt ${maxReplicas} ]; then
      echo "${errorMessage}"
      eval $__result="MAX_REPLICA_COUNT_EXCEEDED"
      return
    fi
  fi

  cmd="(.spec.clusters[] | select (.clusterName == \"${clusterName}\") \
    | .replicas) |= ${replica}"
  replicaPatch=$(echo ${domainJson} | jq "${cmd}" | jq -cr '(.spec.clusters)')
  eval $__result="'${replicaPatch}'"
  eval $__relicaCount="'${replica}'"
}

#
# Function to validate whether a server belongs to a  cluster or is an independent managed server
# $1 - Domain unique id.
# $2 - Domain namespace.
# $3 - Return value indicating if server is valid (i.e. if it's part of a cluster or independent server).
# $4 - Retrun value containting cluster name to which this server belongs.
#
function validateServerAndFindCluster {
  local domainUid=$1
  local domainNamespace=$2 
  local __isValidServer=$3
  local __clusterName=$4
  local errorMessage="Server name is outside the range of allowed servers. \
Please make sure server name is correct."

  configMap=$(${kubernetesCli} get cm ${domainUid}-weblogic-domain-introspect-cm \
    -n ${domainNamespace} -o json)
  topology=$(echo "${configMap}" | jq '.data["topology.yaml"]')
  jsonTopology=$(python -c \
    'import sys, yaml, json; print json.dumps(yaml.safe_load('"${topology}"'), indent=4)')
  servers=($(echo $jsonTopology | jq -r '.domain.servers[].name'))
  if  checkStringInArray "${serverName}" "${servers[@]}" ; then
    eval $__clusterName=""
    eval $__isValidServer=true
  else
    dynamicClause=".domain.configuredClusters[] | select (.dynamicServersConfig != null)"
    namePrefixSize=". | {name: .name, prefix:.dynamicServersConfig.serverNamePrefix, \
                 max:.dynamicServersConfig.maxDynamicClusterSize}"
    dynamicClusters=($(echo $jsonTopology | jq "${dynamicClause}" | jq -cr "${namePrefixSize}"))
    for dynaClusterNamePrefix in "${dynamicClusters[@]}"; do
      prefix=$(echo ${dynaClusterNamePrefix} | jq -r .prefix)
      if [[ "${serverName}" == "${prefix}"* ]]; then
        serverCount=$(echo "${serverName: -1}")
        maxSize=$(echo ${dynaClusterNamePrefix} | jq -r .max)
        if [ ${serverCount} -gt ${maxSize} ]; then
          printError "${errorMessage}"
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
    for configuredClusterName in "${configuredClusters[@]}"; do
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
# $4 - Retrun value indicating whether cluster name is valid
#
function validateClusterName {
  local domainUid=$1
  local domainNamespace=$2
  local clusterName=$3
  local __isValidCluster=$4
  local errorMessage="Server name is outside the range of allowed servers. \
Please make sure server name is correct."

  configMap=$(${kubernetesCli} get cm ${domainUid}-weblogic-domain-introspect-cm \
    -n ${domainNamespace} -o json)
  topology=$(echo "${configMap}" | jq '.data["topology.yaml"]')
  jsonTopology=$(python -c \
    'import sys, yaml, json; print json.dumps(yaml.safe_load('"${topology}"'), indent=4)')
  clusters=($(echo $jsonTopology | jq -cr .domain.configuredClusters[].name))
  if  checkStringInArray "${clusterName}" "${clusters[@]}" ; then
    eval $__isValidCluster=true
  fi
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
  echo [ERROR] $*
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
