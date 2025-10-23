#!/bin/bash
# Copyright (c) 2017,2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# script parameters
scaling_action=""
wls_domain_uid=""
wls_cluster_name=""
wls_domain_namespace="default"
operator_service_name="internal-weblogic-operator-svc"
operator_namespace="weblogic-operator"
operator_service_account="weblogic-operator"
scaling_size=1
access_token=""
no_op=""
kubernetes_master="https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}"
log_file_name="scalingAction.log"

# timestamp
#   purpose:  echo timestamp in the form yyyy-mm-ddThh:mm:ss.nnnnnnZ
#   example:  2018-10-01T14:00:00.000001Z
timestamp() {
  local timestamp="$(date --utc '+%Y-%m-%dT%H:%M:%S.%NZ' 2>&1)"
  if [ ! "${timestamp/illegal/xyz}" = "${timestamp}" ]; then
    # old shell versions don't support %N or --utc
    timestamp="$(date -u '+%Y-%m-%dT%H:%M:%S.000000Z' 2>&1)"
  fi
  echo "${timestamp}"
}

trace() {
  echo "@[$(timestamp)][$wls_domain_namespace][$wls_domain_uid][$wls_cluster_name][INFO]" "$@" >> ${log_file_name}
}

print_usage() {
  echo "Usage: scalingAction.sh --action=[scaleUp | scaleDown] --domain_uid=<domain uid> --cluster_name=<cluster name> [--kubernetes_master=https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}] [--access_token=<access_token>] [--wls_domain_namespace=default] [--operator_namespace=weblogic-operator] [--operator_service_name=weblogic-operator] [--scaling_size=1] [--no_op]"
  echo "  where"
  echo "    action - scaleUp or scaleDown"
  echo "    domain_uid - WebLogic Domain Unique Identifier"
  echo "    cluster_name - WebLogic Cluster Name"
  echo "    kubernetes_master - Kubernetes master URL, default=https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}"
  echo "    access_token - Service Account Bearer token for authentication and authorization for access to REST Resources"
  echo "    wls_domain_namespace - Kubernetes name space WebLogic Domain is defined in, default=default"
  echo "    operator_service_name - WebLogic Operator Service name, default=internal-weblogic-operator-svc"
  echo "    operator_service_account - Kubernetes Service Account for WebLogic Operator, default=weblogic-operator"
  echo "    operator_namespace - WebLogic Operator Namespace, default=weblogic-operator"
  echo "    scaling_size - number of WebLogic server instances by which to scale up or down, default=1"
  echo "    no_op - if specified, returns without doing anything. For use by unit test to include methods in the script"
  exit 1
}

# Retrieve WebLogic Operator Service Account Token for Authorization
initialize_access_token() {
  if [ -z "$access_token" ]
  then
    access_token=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)
  fi
}

logScalingParameters() {
  trace "scaling_action: $scaling_action"
  trace "wls_domain_uid: $wls_domain_uid"
  trace "wls_cluster_name: $wls_cluster_name"
  trace "wls_domain_namespace: $wls_domain_namespace"
  trace "operator_service_name: $operator_service_name"
  trace "operator_service_account: $operator_service_account"
  trace "operator_namespace: $operator_namespace"
  trace "scaling_size: $scaling_size"
}

jq_available() {
  if [ -x "$(command -v jq)" ] && [ -z "$DONT_USE_JQ" ]; then
    return;
  fi
  false
}

# Query WebLogic Operator Service Port
get_operator_internal_rest_port() {
  local STATUS=$(curl \
    -v \
    --cacert /var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
    -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
    -X GET "$kubernetes_master"/api/v1/namespaces/$operator_namespace/services/$operator_service_name/status)
  if [ $? -ne 0 ]
  then
    trace "Failed to retrieve status of $operator_service_name in name space: $operator_namespace"
    trace "STATUS: $STATUS"
    exit 1
  fi

  local port
  if jq_available; then
    local extractPortCmd="(.spec.ports[] | select (.name == \"rest\") | .port)"
    port=$(echo "${STATUS}" | jq "${extractPortCmd}" 2>> ${log_file_name})
  else
cat > cmds-$$.py << INPUT
import sys, json
for i in json.load(sys.stdin)["spec"]["ports"]:
  if i["name"] == "rest":
    print((i["port"]))
INPUT
port=$(echo "${STATUS}" | python cmds-$$.py 2>> ${log_file_name})
  fi
  echo "$port"
}

# Retrieve the api version of the deployed Custom Resource Domain
get_domain_api_version() {
  # Retrieve Custom Resource Definition for WebLogic domain
  local APIS=$(curl \
    -v \
    --cacert /var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
    -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
    -X GET \
    "$kubernetes_master"/apis)
  if [ $? -ne 0 ]
    then
      trace "Failed to retrieve list of APIs from Kubernetes cluster"
      trace "APIS: $APIS"
      exit 1
  fi

# Find domain version
  local domain_api_version
  if jq_available; then
    local extractVersionCmd="(.groups[] | select (.name == \"weblogic.oracle\") | .preferredVersion.version)"
    domain_api_version=$(echo "${APIS}" | jq -r "${extractVersionCmd}" 2>> ${log_file_name})
  else
cat > cmds-$$.py << INPUT
import sys, json
for i in json.load(sys.stdin)["groups"]:
  if i["name"] == "weblogic.oracle":
    print((i["preferredVersion"]["version"]))
INPUT
domain_api_version=$(echo "${APIS}" | python cmds-$$.py 2>> ${log_file_name})
  fi
  echo "$domain_api_version"
}

# Retrieve Custom Resource Domain
get_custom_resource_domain() {
  local DOMAIN=$(curl \
    -v \
    --cacert /var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
    -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
    "$kubernetes_master"/apis/weblogic.oracle/"$domain_api_version"/namespaces/"$wls_domain_namespace"/domains/"$wls_domain_uid")
  if [ $? -ne 0 ]; then
    trace "Failed to retrieve WebLogic Domain Custom Resource Definition"
    exit 1
  fi
  echo "$DOMAIN"
}

# Retrieve Cluster Custom Resource with the resource name
# args:
# $1 Cluster Custom Resource name
get_custom_resource_cluster() {
  local cluster_resource_name="$1"
  local clusterJson=$(curl \
    -v -f \
    --cacert /var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
    -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
    "$kubernetes_master"/apis/weblogic.oracle/"$cluster_api_version"/namespaces/"$wls_domain_namespace"/clusters/"$cluster_resource_name")
  if [ $? -ne 0 ]; then
    trace "Failed to retrieve WebLogic Cluster Custom Resource Definition with cluster resource name '$cluster_resource_name'"
  fi
  echo "$clusterJson"
}

# Retrieve the Cluster custom resource with the resource name, and return it only
# if its spec.clusterName value matches the WebLogic cluster name
# args:
# $1 Cluster Custom Resource name
# $2 WebLogic cluster name
get_cluster_resource_if_cluster_name_matches() {
  local cluster_resource_name="$1"
  local wls_cluster_name="$2"
  local clusterJson
  local cluster_name
  clusterJson=$(get_custom_resource_cluster "$cluster_resource_name")
  if [ -n "$clusterJson" ]; then
    cluster_name=$(get_cluster_name_from_cluster "$clusterJson")
    if [[ "$cluster_name" == "$wls_cluster_name" ]]; then
      echo "$clusterJson"
    fi
  fi
}

# Find the Cluster custom resource with the WebLogic cluster name
# args:
# $1 Domain custom resource
# $2 WebLogic cluster name
find_cluster_resource_with_cluster_name() {
  local domainJson="$1"
  local wls_cluster_name="$2"
  local cluster_resource_names
  local clusterJson
  cluster_resource_names=$(get_cluster_resource_names_from_domain "$domainJson")
  # Try cluster resources with name that ends with the WebLogic cluster name first.
  # This can help save the number of GET cluster Kubernetes API calls when Cluster
  # resource name follows the naming pattern *"$wls_cluster_name, especially when
  # the Domain resource references a large number of Cluster resource.
  for name in $cluster_resource_names
  do
    if [[ "$name" == *"$wls_cluster_name" ]]; then
      clusterJson=$(get_cluster_resource_if_cluster_name_matches "$name" "$wls_cluster_name")
      if [ -n "$clusterJson" ]; then
        echo "$clusterJson"
        return
      fi
    fi
  done
  # Try the rest of the cluster resource names
  for name in $cluster_resource_names
  do
    if [[ "$name" != *"$wls_cluster_name" ]]; then
      clusterJson=$(get_cluster_resource_if_cluster_name_matches "$name" "$wls_cluster_name")
      if [ -n "$clusterJson" ]; then
        echo "$clusterJson"
        return
      fi
    fi
  done
}

# Gets the names of Cluster custom resources referenced by the Domain custom resource
# args:
# $1 Domain custom resource
get_cluster_resource_names_from_domain() {
  local DOMAIN="$1"
  local clusters
  if jq_available; then
  clusters=$(echo "${DOMAIN}" | jq -r '.spec.clusters[].name'  2>> ${log_file_name})
  else
cat > cmds-$$.py << INPUT
import sys, json
for j in json.load(sys.stdin)["spec"]["clusters"]:
  print((j["name"]))
INPUT
  clusters=$(echo "${DOMAIN}" | python cmds-$$.py 2>> ${log_file_name})
  fi

  echo "$clusters"
}

# Gets the replicas value from the Cluster. Return -1 if no replicas configured in the Cluster.
# args:
# $1 Cluster Custom Resource
get_replicas_from_cluster() {
  local clusterJson="$1"
  local replicas
  if jq_available; then
    local numReplicasCmd=".spec.replicas"
    replicas=$(echo "${clusterJson}" | jq "${numReplicasCmd}" 2>> ${log_file_name} )
  else
cat > cmds-$$.py << INPUT
import sys, json
print((json.load(sys.stdin)["spec"]["replicas"]))
INPUT
  replicas=$(echo "${clusterJson}" | python cmds-$$.py 2>> ${log_file_name})
  fi

  if [ "${replicas}" == "null" ] || [ "${replicas}" == '' ] ; then
    replicas=-1
  fi

  echo "$replicas"
}

# Gets the replica count at the Domain level
# args:
# $1 Custom Resource Domain
get_num_ms_domain_scope() {
  local DOMAIN="$1"
  local num_ms
  if jq_available; then
    num_ms=$(echo "${DOMAIN}" | jq -r '.spec.replicas' 2>> ${log_file_name})
  else
cat > cmds-$$.py << INPUT
import sys, json
print((json.load(sys.stdin)["spec"]["replicas"]))
INPUT
  num_ms=$(echo "${DOMAIN}" | python cmds-$$.py 2>> ${log_file_name})
  fi

  if [ "${num_ms}" == "null" ] || [ "${num_ms}" == '' ] ; then
    # if not defined then default to 0
    num_ms=0
  fi

  echo "$num_ms"
}

#
# Function to get minimum replica count for cluster
# $1 - Domain resource in json format
# $2 - Name of the cluster
# $3 - Return value containing minimum replica count
#
get_min_replicas() {
  local domainJson=$1
  local clusterName=$2
  local __result=$3

  eval "$__result"=0
  if jq_available; then
    minReplicaCmd="(.status.clusters[] | select (.clusterName == \"${clusterName}\")) \
      | .minimumReplicas"
    minReplicas=$(echo "${domainJson}" | jq "${minReplicaCmd}"  2>> ${log_file_name})
  else
cat > cmds-$$.py << INPUT
import sys, json
for j in json.load(sys.stdin)["status"]["clusters"]:
  if j["clusterName"] == "$clusterName":
    print((j["minimumReplicas"]))
INPUT
  minReplicas=$(echo "${domainJson}" | python cmds-$$.py 2>> ${log_file_name})
  fi
  eval "$__result"="${minReplicas}"
}

# Function to get the clusterName from cluster resource.
# args:
# $1 Cluster resource in json format
get_cluster_name_from_cluster() {
  local clusterJson="$1"
  local clusterName
  if jq_available; then
    clusterName=$(echo "${clusterJson}" | jq -r ".spec.clusterName" 2>> ${log_file_name} )
  else
cat > cmds-$$.py << INPUT
import sys, json
print((json.load(sys.stdin)["spec"]["clusterName"]))
INPUT
  clusterName=$(echo "${clusterJson}" | python cmds-$$.py 2>> ${log_file_name})
  fi

  echo "$clusterName"
}

#
# Function to get minimum replica count from cluster resource
# $1 - Cluster resource in json format
# $2 - Return value containing minimum replica count, 0 if minReplicas not defined in Cluster resource
#
get_min_replicas_from_cluster() {
  local clusterJson=$1
  local __result=$2

  eval "$__result"=0
  if jq_available; then
    minReplicaCmd=".status.minimumReplicas"
    minReplicas=$(echo "${clusterJson}" | jq "${minReplicaCmd}"  2>> ${log_file_name})
  else
cat > cmds-$$.py << INPUT
import sys, json
print((json.load(sys.stdin)["status"]["minimumReplicas"]))
INPUT
  minReplicas=$(echo "${clusterJson}" | python cmds-$$.py 2>> ${log_file_name})
  fi
  eval "$__result"="${minReplicas:-0}"
}

#
# Function to get maximum replica count from cluster resource
# $1 - Cluster resource in json format
# $2 - Return value containing maximum replica count, value not set if maxReplicas not defined in
#      Cluster resource
#
get_max_replicas_from_cluster() {
  local clusterJson=$1
  local __result=$2

  eval "$__result"=0
  if jq_available; then
    maxReplicaCmd=".status.maximumReplicas"
    maxReplicas=$(echo "${clusterJson}" | jq "${maxReplicaCmd}"  2>> ${log_file_name})
  else
cat > cmds-$$.py << INPUT
import sys, json
print((json.load(sys.stdin)["status"]["maximumReplicas"]))
INPUT
  maxReplicas=$(echo "${clusterJson}" | python cmds-$$.py 2>> ${log_file_name})
  fi
  eval "$__result"="${maxReplicas}"
}

# Get the current replica count for the WLS cluster if defined in the Cluster
# resource. If not defined in the Cluster resource, return the default
# replica count from the Domain resource.
# Returns replica count = 0 if no replica count found.
# args:
# $1 Cluster Custom Resource
# $2 Domain Custom Resource
get_replica_count_from_resources() {
  local clusterJson="$1"
  local domainJson="$2"
  local replicas
  replicas=$(get_replicas_from_cluster "$clusterJson")
  if [ "$replicas" == -1 ]
  then
    replicas=$(get_num_ms_domain_scope "$domainJson")
    trace "replicas for $wls_cluster_name is not specified in Cluster resource. Using replicas value of $replicas from Domain resource."
  else
    trace "replicas for $wls_cluster_name from Cluster resource is $replicas"
  fi

  echo "$replicas"
}

# Determine the number of managed servers to scale
# args:
# $1 scaling action (scaleUp or scaleDown)
# $2 current replica count
# $3 scaling increment value
calculate_new_replica_count() {
  local scaling_action="$1"
  local current_replica_count="$2"
  local scaling_size="$3"
  local new_replica
  if [ "$scaling_action" == "scaleUp" ];
  then
    # Scale up by specified scaling size
    # shellcheck disable=SC2004
    new_replica=$((current_replica_count + scaling_size))
  else
    # Scale down by specified scaling size
    new_replica=$((current_replica_count - scaling_size))
  fi
  echo "$new_replica"
}

# Verify if requested managed server scaling count is less than the configured
# minimum replica count for the cluster.
# args:
# $1 Managed server count
# $2 Cluster Custom Resource
verify_minimum_replicas_for_cluster() {
  local new_replica="$1"
  local clusterJson="$2"
  # check if replica count is less than minimum replicas
  get_min_replicas_from_cluster "${clusterJson}" minReplicas
  if [ "${new_replica}" -lt "${minReplicas}" ]; then
    trace "Scaling request to new managed server count $new_replica is less than configured minimum \
replica count of $minReplicas. Exiting."
    exit 1
  fi
}

# Verify if requested managed server scaling count is greater than the configured
# maximum replica count for the cluster.
# args:
# $1 Managed server count
# $3 Cluster name
verify_maximum_replicas_for_cluster() {
  local new_replica="$1"
  local clusterJson="$2"
  # check if replica count is less than minimum replicas
  get_max_replicas_from_cluster "${clusterJson}" maxReplicas
  if [ -n "$maxReplicas" ] && [ "${new_replica}" -gt "${maxReplicas}" ]; then
    trace "Scaling request to new managed server count $new_replica is greater than configured maximum \
replica count of $maxReplicas. Exiting."
    exit 1
  fi
}

# Create the REST endpoint CA certificate in PEM format
# args:
# $1 certificate file name to create
create_ssl_certificate_file() {
  local pem_filename="$1"
  if [ "${INTERNAL_OPERATOR_CERT}" ];
  then
    echo "${INTERNAL_OPERATOR_CERT}" | base64 --decode >  "$pem_filename"
  else
    trace "Operator Cert File not found"
    exit 1
  fi
}

# Create request body for scaling request
# args:
# $1 replica count
get_request_body() {
local new_replica="$1"
local request_body=$(cat <<EOF
{
  "spec":
  {
    "replicas": $new_replica
  }
}
EOF
)
echo "$request_body"
}

#### Main ####

# Parse arguments/parameters
for arg in "$@"
do
  case $arg in
    --action=*)
    scaling_action="${arg#*=}"
    shift # past argument=value
    ;;
    --domain_uid=*)
    wls_domain_uid="${arg#*=}"
    shift # past argument=value
    ;;
    --cluster_name=*)
    wls_cluster_name="${arg#*=}"
    shift # past argument=value
    ;;
    --wls_domain_namespace=*)
    wls_domain_namespace="${arg#*=}"
    shift # past argument=value
    ;;
    --operator_service_name=*)
    operator_service_name="${arg#*=}"
    shift # past argument=value
    ;;
    --operator_service_account=*)
    operator_service_account="${arg#*=}"
    shift # past argument=value
    ;;
    --operator_namespace=*)
    operator_namespace="${arg#*=}"
    shift # past argument=value
    ;;
    --scaling_size=*)
    scaling_size="${arg#*=}"
    shift # past argument=value
    ;;
    --kubernetes_master=*)
    kubernetes_master="${arg#*=}"
    shift # past argument=value
    ;;
    --access_token=*)
    access_token="${arg#*=}"
    shift # past argument=value
    ;;
    --no_op)
    no_op="true"
    ;;
    *)
          # unknown option
    ;;
  esac
done

if [ "${no_op}" = "true" ]; then
  echo "no_op is set, returning"
  return
fi

# Verify required parameters
if [ -z "$scaling_action" ] || [ -z "$wls_domain_uid" ] || [ -z "$wls_cluster_name" ]
then
  print_usage
fi

# Initialize the client access token
initialize_access_token

# Log the script input parameters for debugging
logScalingParameters

# Retrieve the operator's REST endpoint port
port=$(get_operator_internal_rest_port)
trace "port: $port"

# Retrieve the api version of the deployed Domain Resource
domain_api_version=$(get_domain_api_version)
trace "domain_api_version: $domain_api_version"

# Retrieve the Domain configuration
DOMAIN=$(get_custom_resource_domain)

# API version of cluster resource hard coded to "v1" in this release
cluster_api_version="v1"
CLUSTER=$(find_cluster_resource_with_cluster_name "$DOMAIN" "$wls_cluster_name")

if [ -z "${CLUSTER}" ]; then
  trace "Cluster resource for ${wls_cluster_name} not found. Exiting."
  exit 1
fi

# Retrieve replica count from Cluster or Domain Resource.
current_replica_count=$(get_replica_count_from_resources "$CLUSTER" "$DOMAIN")
trace "current number of managed servers is $current_replica_count"

# Calculate new replica count
new_replica=$(calculate_new_replica_count "$scaling_action" "$current_replica_count" "$scaling_size")

# Verify the requested new managed server count
verify_minimum_replicas_for_cluster "$new_replica" "$CLUSTER"
verify_maximum_replicas_for_cluster "$new_replica" "$CLUSTER"

# Cleanup cmds-$$.py
[ -e cmds-$$.py ] && rm cmds-$$.py

# Create the scaling request body
request_body=$(get_request_body "$new_replica")

content_type="Content-Type: application/json"
requested_by="X-Requested-By: WLDF"
authorization="Authorization: Bearer $access_token"
pem_filename="weblogic_operator-$$.pem"

# Create PEM file for Operator SSL Certificate
create_ssl_certificate_file "$pem_filename"

# Operator Service REST URL for scaling
operator_url="https://$operator_service_name.$operator_namespace.svc.cluster.local:$port/operator/v1/domains/$wls_domain_uid/clusters/$wls_cluster_name/scale"

trace "domainName: $wls_domain_uid | clusterName: $wls_cluster_name | action: $scaling_action | port: $port | apiVer: $domain_api_version | oldReplica: $current_replica_count | newReplica: $new_replica | operator_url: $operator_url "

# send REST request to Operator
if [ -e $pem_filename ]
then
  result=$(curl \
    -v \
    --cacert $pem_filename \
    -X POST \
    -H "$content_type" \
    -H "$requested_by" \
    -H "$authorization" \
    -d "$request_body" \
    "$operator_url")
else
  trace "Operator PEM formatted file not found"
  exit 1
fi

if [ $? -ne 0 ]
then
  trace "Failed scaling request to WebLogic Operator"
  trace "$result"
  exit 1
fi

# Cleanup generated operator PEM file
[ -e $pem_filename ] && rm $pem_filename
