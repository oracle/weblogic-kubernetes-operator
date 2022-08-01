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
  local clusterJson=$(curl \
    -v \
    --cacert /var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
    -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
    "$kubernetes_master"/apis/weblogic.oracle/"$domain_api_version"/namespaces/"$wls_domain_namespace"/domains/"$wls_domain_uid")
  if [ $? -ne 0 ]; then
    trace "Failed to retrieve WebLogic Domain Custom Resource Definition"
    exit 1
  fi
  echo "$clusterJson"
}

# Retrieve Custom Resource Cluster
# args:
# $1 Cluster name (assumed to be same as name of the Cluster Resource)
get_custom_resource_cluster() {
  local cluster_id="$1"
  local clusterJson=$(curl \
    -v -f \
    --cacert /var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
    -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
    "$kubernetes_master"/apis/weblogic.oracle/"$cluster_api_version"/namespaces/"$wls_domain_namespace"/clusters/"$cluster_id")
  if [ $? -ne 0 ]; then
    trace "Failed to retrieve WebLogic Cluster Custom Resource Definition with cluster name '$cluster_id'"
    exit 1
  fi
  echo "$clusterJson"
}

# Verify if cluster is defined in clusters of the Custom Resource Domain
# args:
# $1 Custom Resource Domain
is_defined_in_clusters() {
  local DOMAIN="$1"
  local in_cluster_startup="False"

  if jq_available; then
    local inClusterStartupCmd="(.spec.clusters[] | select (.clusterName == \"${wls_cluster_name}\"))"
    local clusterDefinedInCRD=$(echo "${DOMAIN}" | jq "${inClusterStartupCmd}"  2>> ${log_file_name})
    if [ "${clusterDefinedInCRD}" != "" ]; then
      in_cluster_startup="True"
    fi
  else
cat > cmds-$$.py << INPUT
import sys, json
outer_loop_must_break = False
for j in json.load(sys.stdin)["spec"]["clusters"]:
  if j["clusterName"] == "$wls_cluster_name":
    outer_loop_must_break = True
    print (True)
    break
if outer_loop_must_break == False:
  print (False)
INPUT
in_cluster_startup=$(echo "${DOMAIN}" | python cmds-$$.py 2>> ${log_file_name})
  fi
  echo "$in_cluster_startup"
}

# Gets the current replica count of the cluster
# args:
# $1 Custom Resource Domain
get_num_ms_in_cluster() {
  local clusterJson="$1"
  local num_ms
  if jq_available; then
  local numManagedServersCmd="(.spec.clusters[] | select (.clusterName == \"${wls_cluster_name}\") | .replicas)"
  num_ms=$(echo "${clusterJson}" | jq "${numManagedServersCmd}"  2>> ${log_file_name})
  else
cat > cmds-$$.py << INPUT
import sys, json
for j in json.load(sys.stdin)["spec"]["clusters"]:
  if j["clusterName"] == "$wls_cluster_name":
    print((j["replicas"]))
INPUT
  num_ms=$(echo "${clusterJson}" | python cmds-$$.py 2>> ${log_file_name})
  fi

  if [ "${num_ms}" == "null" ] || [ "${num_ms}" == '' ] ; then
    num_ms=0
  fi

  echo "$num_ms"
}

# Gets the replicas value from the Cluster. Return -1 if no replicas configured in the Cluster.
# args:
# $1 Cluster Custom Resource
get_replicas_from_cluster() {
  local clusterJson="$1"
  local replicas
  if jq_available; then
    local numManagedServersCmd=".spec.replicas"
    replicas=$(echo "${clusterJson}" | jq "${numManagedServersCmd}" 2>> ${log_file_name} )
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

# Get the current replica count for the WLS cluster if defined in the CRD's Cluster
# configuration.  If WLS cluster is not defined in the CRD then return the Domain
# scoped replica value, if present.  Returns replica count = 0 if no replica count found.
# args:
# $1 "True" if WLS cluster configuration defined in CRD, "False" otherwise
# $2 Custom Resource Domain
get_replica_count() {
  local in_cluster_startup="$1"
  local DOMAIN="$2"
  local num_ms
  if [ "$in_cluster_startup" == "True" ]
  then
    trace "$wls_cluster_name defined in clusters"
    num_ms=$(get_num_ms_in_cluster "$DOMAIN")
  else
    trace "$wls_cluster_name is NOT defined in clusters"
    num_ms=$(get_num_ms_domain_scope "$DOMAIN")
  fi

  get_min_replicas "${DOMAIN}" "${wls_cluster_name}" minReplicas
  if [[ "${num_ms}" -lt "${minReplicas}" ]]; then
    # Reset managed server count to minimum replicas
    num_ms=${minReplicas}
  fi

  echo "$num_ms"
}

# Get the current replica count for the WLS cluster if defined in the Cluster
# resource. If not defined in the Cluster resource, return the default
# replica count from the Domain resource.
# Returns replica count = 0 if no replica count found.
# args:
# $1 Cluster Custom Resource
# $2 Domain Custom Resource
get_replica_count_from_cluster_or_domain() {
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

  get_min_replicas_from_cluster "${clusterJson}" minReplicas
  if [[ "${replicas}" -lt "${minReplicas}" ]]; then
    # Reset managed server count to minimum replicas
    replicas=${minReplicas}
  fi

  echo "$replicas"
}

# Determine the number of managed servers to scale
# args:
# $1 scaling action (scaleUp or scaleDown)
# $2 current replica count
# $3 scaling increment value
calculate_new_ms_count() {
  local scaling_action="$1"
  local current_replica_count="$2"
  local scaling_size="$3"
  local new_ms
  if [ "$scaling_action" == "scaleUp" ];
  then
    # Scale up by specified scaling size
    # shellcheck disable=SC2004
    new_ms=$((current_replica_count + scaling_size))
  else
    # Scale down by specified scaling size
    new_ms=$((current_replica_count - scaling_size))
  fi
  echo "$new_ms"
}

# Verify if requested managed server scaling count is less than the configured
# minimum replica count for the cluster.
# args:
# $1 Managed server count
# $2 Custom Resource Domain
# $3 Cluster name
verify_minimum_ms_count_for_cluster() {
  local new_ms="$1"
  local domainJson="$2"
  local clusterName="$3"
  # check if replica count is less than minimum replicas
  get_min_replicas "${domainJson}" "${clusterName}" minReplicas
  if [ "${new_ms}" -lt "${minReplicas}" ]; then
    trace "Scaling request to new managed server count $new_ms is less than configured minimum \
    replica count $minReplicas"
    exit 1
  fi
}

# Verify if requested managed server scaling count is less than the configured
# minimum replica count for the cluster.
# args:
# $1 Managed server count
# $2 Cluster Custom Resource
verify_minimum_replicas_for_cluster() {
  local new_ms="$1"
  local clusterJson="$2"
  # check if replica count is less than minimum replicas
  get_min_replicas_from_cluster "${clusterJson}" minReplicas
  if [ "${new_ms}" -lt "${minReplicas}" ]; then
    trace "Scaling request to new managed server count $new_ms is less than configured minimum \
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
  local new_ms="$1"
  local clusterJson="$2"
  # check if replica count is less than minimum replicas
  get_max_replicas_from_cluster "${clusterJson}" maxReplicas
  if [ -n "$maxReplicas" ] && [ "${new_ms}" -gt "${maxReplicas}" ]; then
    trace "Scaling request to new managed server count $new_ms is greater than configured maximum \
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
local new_ms="$1"
local request_body=$(cat <<EOF
{
    "managedServerCount": $new_ms
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
CLUSTER=$(get_custom_resource_cluster "$wls_cluster_name")

if [ -z "$CLUSTER" ]; then
  # Cluster resource not found. Should not be allowed to scale this cluster.

  # However, for the time being, we are keeping old code that use older domain
  # resource with clusters config to avoid breaking existing tests.
  # *** To be removed later. ***
  trace "Cluster resource not found. Assuming clusters still defined in Domain resource"

  # Determine if WLS cluster has configuration in CRD
  in_cluster_startup=$(is_defined_in_clusters "$DOMAIN")

  # Retrieve replica count, of WebLogic Cluster, from Domain Custom Resource
  # depending on whether the specified cluster is defined in clusters
  # or not.
  current_replica_count=$(get_replica_count "$in_cluster_startup" "$DOMAIN")
  trace "current number of managed servers is $current_replica_count"

  # Calculate new managed server count
  new_ms=$(calculate_new_ms_count "$scaling_action" "$current_replica_count" "$scaling_size")

  # Verify the requested new managed server count is not less than
  # configured minimum replica count for the cluster
  verify_minimum_ms_count_for_cluster "$new_ms" "$DOMAIN" "$wls_cluster_name"

else

  trace "Cluster resource found."

  # Retrieve replica count from Cluster or Domain Resource.
  current_replica_count=$(get_replica_count_from_cluster_or_domain "$CLUSTER" "$DOMAIN")
  trace "current number of managed servers is $current_replica_count"

  # Calculate new managed server count
  new_ms=$(calculate_new_ms_count "$scaling_action" "$current_replica_count" "$scaling_size")

  # Verify the requested new managed server count
  verify_minimum_replicas_for_cluster "$new_ms" "$CLUSTER"
  verify_maximum_replicas_for_cluster "$new_ms" "$CLUSTER"

fi

# Cleanup cmds-$$.py
[ -e cmds-$$.py ] && rm cmds-$$.py

# Create the scaling request body
request_body=$(get_request_body "$new_ms")

content_type="Content-Type: application/json"
requested_by="X-Requested-By: WLDF"
authorization="Authorization: Bearer $access_token"
pem_filename="weblogic_operator-$$.pem"

# Create PEM file for Opertor SSL Certificate
create_ssl_certificate_file "$pem_filename"

# Operator Service REST URL for scaling
operator_url="https://$operator_service_name.$operator_namespace.svc.cluster.local:$port/operator/v1/domains/$wls_domain_uid/clusters/$wls_cluster_name/scale"

trace "domainName: $wls_domain_uid | clusterName: $wls_cluster_name | action: $scaling_action | port: $port | apiVer: $domain_api_version | inClusterPresent: $in_cluster_startup | oldReplica: $num_ms | newReplica: $new_ms | operator_url: $operator_url "

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
