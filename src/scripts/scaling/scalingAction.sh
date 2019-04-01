#!/bin/sh
# Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

echo "called scalingAction.sh" >> scalingAction.log

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
kubernetes_master="https://kubernetes"

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
    *)
          # unknown option
    ;;
esac
done

# Verify required parameters
if [ -z "$scaling_action" ] || [ -z "$wls_domain_uid" ] || [ -z "$wls_cluster_name" ]
then
    echo "Usage: scalingAction.sh --action=[scaleUp | scaleDown] --domain_uid=<domain uid> --cluster_name=<cluster name> [--kubernetes_master=https://kubernetes] [--access_token=<access_token>] [--wls_domain_namespace=default] [--operator_namespace=weblogic-operator] [--operator_service_name=weblogic-operator] [--scaling_size=1]"
    echo "  where"
    echo "    action - scaleUp or scaleDown"
    echo "    domain_uid - WebLogic Domain Unique Identifier"
    echo "    cluster_name - WebLogic Cluster Name"
    echo "    kubernetes_master - Kubernetes master URL, default=https://kubernetes"
    echo "    access_token - Service Account Bearer token for authentication and authorization for access to REST Resources"
    echo "    wls_domain_namespace - Kubernetes name space WebLogic Domain is defined in, default=default"
    echo "    operator_service_name - WebLogic Operator Service name, default=internal-weblogic-operator-svc"
    echo "    operator_service_account - Kubernetes Service Account for WebLogic Operator, default=weblogic-operator"
    echo "    operator_namespace - WebLogic Operator Namespace, default=weblogic-operator"
    echo "    scaling_size - number of WebLogic server instances by which to scale up or down, default=1"
    exit 1
fi

# Retrieve WebLogic Operator Service Account Token for Authorization
if [ -z "$access_token" ]
then
  access_token=`cat /var/run/secrets/kubernetes.io/serviceaccount/token`
fi

echo "scaling_action: $scaling_action" >> scalingAction.log
echo "wls_domain_uid: $wls_domain_uid" >> scalingAction.log
echo "wls_cluster_name: $wls_cluster_name" >> scalingAction.log
echo "wls_domain_namespace: $wls_domain_namespace" >> scalingAction.log
echo "operator_service_name: $operator_service_name" >> scalingAction.log
echo "operator_service_account: $operator_service_account" >> scalingAction.log
echo "operator_namespace: $operator_namespace" >> scalingAction.log
echo "scaling_size: $scaling_size" >> scalingAction.log

# Query WebLogic Operator Service Port
STATUS=`curl -v --cacert /var/run/secrets/kubernetes.io/serviceaccount/ca.crt -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" -X GET $kubernetes_master/api/v1/namespaces/$operator_namespace/services/$operator_service_name/status` 
if [ $? -ne 0 ]
  then
    echo "Failed to retrieve status of $operator_service_name in name space: $operator_namespace" >> scalingAction.log
    echo "STATUS: $STATUS" >> scalingAction.log
    exit 1
fi

cat > cmds.py << INPUT
import sys, json
for i in json.load(sys.stdin)["spec"]["ports"]:
  if i["name"] == "rest":
    print(i["port"])
INPUT
port=`echo ${STATUS} | python cmds.py`
echo "port: $port" >> scalingAction.log

# Retrieve Custom Resource Definition for WebLogic domain
CRD=`curl -v --cacert /var/run/secrets/kubernetes.io/serviceaccount/ca.crt -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" -X GET $kubernetes_master/apis/apiextensions.k8s.io/v1beta1/customresourcedefinitions/domains.weblogic.oracle`
if [ $? -ne 0 ]
  then
    echo "Failed to retrieve Custom Resource Definition for WebLogic domain" >> scalingAction.log
    echo "CRD: $CRD" >> scalingAction.log
    exit 1
fi

# Find domain version
cat > cmds.py << INPUT
import sys, json
print(json.load(sys.stdin)["spec"]["version"])
INPUT
domain_api_version=`echo ${CRD} | python cmds.py`
echo "domain_api_version: $domain_api_version" >> scalingAction.log

# Reteive Custom Resource Domain 
DOMAIN=`curl -v --cacert /var/run/secrets/kubernetes.io/serviceaccount/ca.crt -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" $kubernetes_master/apis/weblogic.oracle/$domain_api_version/namespaces/$wls_domain_namespace/domains/$domain_uid`
if [ $? -ne 0 ]
  then
    echo "Failed to retrieve WebLogic Domain Custom Resource Definition" >> scalingAction.log
    echo "DOMAIN: $DOMAIN" >> scalingAction.log
    exit 1
fi
echo "DOMAIN: $DOMAIN" >> scalingAction.log

# Verify if cluster is defined in clusters
cat > cmds.py << INPUT
import sys, json
outer_loop_must_break = False
for i in json.load(sys.stdin)["items"]:
  j = i["spec"]["clusters"]
  for index, cs in enumerate(j):
    if j[index]["clusterName"] == "$wls_cluster_name":
      outer_loop_must_break = True
      print True
      break
if outer_loop_must_break == False:
  print False
INPUT
in_cluster_startup=`echo ${DOMAIN} | python cmds.py`

# Retrieve replica count, of WebLogic Cluster, from Domain Custom Resource
# depending on whether the specified cluster is defined in clusters
# or not.
if [ $in_cluster_startup == "True" ]
then
  echo "$wls_cluster_name defined in clusters" >> scalingAction.log

cat > cmds.py << INPUT
import sys, json
for i in json.load(sys.stdin)["items"]:
  j = i["spec"]["clusters"]
  for index, cs in enumerate(j):
    if j[index]["clusterName"] == "$wls_cluster_name":
      print j[index]["replicas"]
INPUT
  num_ms=`echo ${DOMAIN} | python cmds.py`
else
  echo "$wls_cluster_name NOT defined in clusters" >> scalingAction.log
cat > cmds.py << INPUT
import sys, json
for i in json.load(sys.stdin)["items"]:
  print i["spec"]["replicas"]
INPUT
  num_ms=`echo ${DOMAIN} | python cmds.py`
fi
echo "current number of managed servers is $num_ms" >> scalingAction.log

# Cleanup cmds.py
[ -e cmds.py ] && rm cmds.py

# Calculate new managed server count
if [ "$scaling_action" == "scaleUp" ]
then
  # Scale up by specified scaling size 
  new_ms=$(($num_ms + $scaling_size))
else
  # Scale down by specified scaling size 
  new_ms=$(($num_ms - $scaling_size))
fi

echo "new_ms is $new_ms" >> scalingAction.log

request_body=$(cat <<EOF
{
    "managedServerCount": $new_ms 
}
EOF
)

echo "request_body: $request_body" >> scalingAction.log

content_type="Content-Type: application/json"
accept_resp_type="Accept: application/json"
requested_by="X-Requested-By: WLDF"
authorization="Authorization: Bearer $access_token"
pem_filename="weblogic_operator.pem"

# Create PEM file for Opertor SSL Certificate
if [ ${INTERNAL_OPERATOR_CERT} ]
then
  echo ${INTERNAL_OPERATOR_CERT} | base64 --decode >  $pem_filename
else
  echo "Operator Cert File not found" >> scalingAction.log
  exit 1
fi

# Operator Service REST URL for scaling
operator_url="https://$operator_service_name.$operator_namespace.svc.cluster.local:$port/operator/v1/domains/$wls_domain_uid/clusters/$wls_cluster_name/scale"
echo "operator_url: $operator_url" >> scalingAction.log

# send REST request to Operator
if [ -e $pem_filename ]
then
  result=`curl --cacert $pem_filename -X POST -H "$content_type" -H "$requested_by" -H "$authorization" -d "$request_body" $operator_url`
else
  echo "Operator PEM formatted file not found" >> scalingAction.log
  exit 1
fi

if [ $? -ne 0 ]
then
  echo "Failed scaling request to WebLogic Operator" >> scalingAction.log
  echo $result >> scalingAction.log
  exit 1
fi
echo $result >> scalingAction.log

# Cleanup generated operator PEM file
[ -e $pem_filename ] && rm $pem_filename 
