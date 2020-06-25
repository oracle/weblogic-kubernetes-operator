#!/bin/bash
# Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

echo "called introspection.sh" >> introspection.log

# script parameters
wls_domain_uid=""
wls_domain_namespace="default"
operator_service_name="internal-weblogic-operator-svc"
operator_namespace="weblogic-operator"
operator_service_account="weblogic-operator"
access_token=""
kubernetes_master="https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}"

# Parse arguments/parameters
for arg in "$@"
do
case $arg in
    --domain_uid=*)
    wls_domain_uid="${arg#*=}"
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
if [ -z "$wls_domain_uid" ]
then
    echo "Usage: introspection.sh --domain_uid=<domain uid> [--kubernetes_master=https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}] [--access_token=<access_token>] [--wls_domain_namespace=default] [--operator_namespace=weblogic-operator] [--operator_service_name=weblogic-operator]"
    echo "  where"
    echo "    domain_uid - WebLogic Domain Unique Identifier"
    echo "    kubernetes_master - Kubernetes master URL, default=https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}"
    echo "    access_token - Service Account Bearer token for authentication and authorization for access to REST Resources"
    echo "    wls_domain_namespace - Kubernetes name space WebLogic Domain is defined in, default=default"
    echo "    operator_service_name - WebLogic Operator Service name, default=internal-weblogic-operator-svc"
    echo "    operator_service_account - Kubernetes Service Account for WebLogic Operator, default=weblogic-operator"
    echo "    operator_namespace - WebLogic Operator Namespace, default=weblogic-operator"
    exit 1
fi

# Retrieve WebLogic Operator Service Account Token for Authorization
if [ -z "$access_token" ]
then
  access_token=`cat /var/run/secrets/kubernetes.io/serviceaccount/token`
fi

echo "wls_domain_uid: $wls_domain_uid" >> introspection.log
echo "wls_domain_namespace: $wls_domain_namespace" >> introspection.log
echo "operator_service_name: $operator_service_name" >> introspection.log
echo "operator_service_account: $operator_service_account" >> introspection.log
echo "operator_namespace: $operator_namespace" >> introspection.log

# Query WebLogic Operator Service Port
STATUS=`curl -v --cacert /var/run/secrets/kubernetes.io/serviceaccount/ca.crt -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" -X GET $kubernetes_master/api/v1/namespaces/$operator_namespace/services/$operator_service_name/status` 
if [ $? -ne 0 ]
  then
    echo "Failed to retrieve status of $operator_service_name in name space: $operator_namespace" >> introspection.log
    echo "STATUS: $STATUS" >> introspection.log
    exit 1
fi

cat > cmds.py << INPUT
import sys, json
for i in json.load(sys.stdin)["spec"]["ports"]:
  if i["name"] == "rest":
    print(i["port"])
INPUT
port=`echo ${STATUS} | python cmds.py`
echo "port: $port" >> introspection.log

# Cleanup cmds.py
[ -e cmds.py ] && rm cmds.py

request_body=$(cat <<EOF
{
    "action": "INTROSPECT"
}
EOF
)

echo "request_body: $request_body" >> introspection.log

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
  echo "Operator Cert File not found" >> introspection.log
  exit 1
fi

# Operator Service REST URL for introspection
operator_url="https://$operator_service_name.$operator_namespace.svc.cluster.local:$port/operator/v1/domains/$wls_domain_uid"
echo "operator_url: $operator_url" >> introspection.log

# send REST request to Operator
if [ -e $pem_filename ]
then
  result=`curl --cacert $pem_filename -X POST -H "$content_type" -H "$requested_by" -H "$authorization" -d "$request_body" $operator_url`
else
  echo "Operator PEM formatted file not found" >> introspection.log
  exit 1
fi

if [ $? -ne 0 ]
then
  echo "Failed introspection request to WebLogic Operator" >> introspection.log
  echo $result >> introspection.log
  exit 1
fi
echo $result >> introspection.log

# Cleanup generated operator PEM file
[ -e $pem_filename ] && rm $pem_filename 
