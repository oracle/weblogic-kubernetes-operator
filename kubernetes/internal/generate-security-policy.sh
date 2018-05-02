#!/bin/sh
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# --- Start Functions ---

usage()
{
        echo "Usage: $1 {account-name} {namespace} {targetNamespaces} [OPTIONS]"
        echo "OPTIONS: -o <output_file> | --output=<output_file>"
        echo "for example:"
        echo "$1 weblogic-operator-account weblogic-operator-namespace default -o /home/kubernetes/security_example.yaml"
}


# *************************************************************************
# This script is used to generate YAML files to setup the Kubernetes security
# service account and authorization policies for the WebLogic Operator.
# *************************************************************************

SCRIPT_DEFAULT="/tmp/example-rbac.yaml"

if [ "$1" = "" ] ; then
    usage $0
    exit
else
    ACCOUNT_NAME="$1"
    shift
fi

if [ "$1" = "" ] ; then
    usage $0
    exit
else
    NAMESPACE="$1"
    shift
fi

if [ "$1" = "" ] ; then
    usage $0
    exit
else
    TARGET_NAMESPACES="$1"
    shift
fi

if [ "$1" = "-o" ] ; then
        shift
        SCRIPT="$1"
elif [[ "$1" = "--output="* ]] ; then
        SCRIPT=`echo "$1" | cut -d \= -f 2`
else
        SCRIPT=$SCRIPT_DEFAULT
fi

#
# Create namespace and service account
#
echo "..."
echo "Generating YAML script ${SCRIPT} to create WebLogic Operator security configuration..."
cat > ${SCRIPT}  <<EOF
#
# Namespace for WebLogic Operator
#
apiVersion: v1
kind: Namespace
metadata:
  name: ${NAMESPACE}
  labels:
    weblogic.resourceVersion: operator-v1
    weblogic.operatorName: ${NAMESPACE}
---
#
# Service Account for WebLogic Operator
#
apiVersion: v1
kind: ServiceAccount
metadata:
  namespace: ${NAMESPACE}
  name: ${ACCOUNT_NAME}
  labels:
    weblogic.resourceVersion: operator-v1
    weblogic.operatorName: ${NAMESPACE}
---
EOF

cat >> ${SCRIPT}  <<EOF
#
# creating cluster roles
#
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: weblogic-operator-cluster-role
  labels:
    weblogic.resourceVersion: operator-v1
    weblogic.operatorName: ${NAMESPACE}
rules:
- apiGroups: [""]
  resources: ["namespaces"]
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources: ["persistentvolumes"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["apiextensions.k8s.io"]
  resources: ["customresourcedefinitions"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["weblogic.oracle"]
  resources: ["domains"]
  verbs: ["get", "list", "watch", "update", "patch"]
- apiGroups: ["weblogic.oracle"]
  resources: ["domains/status"]
  verbs: ["update"]
- apiGroups: ["extensions"]
  resources: ["ingresses"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["authentication.k8s.io"]
  resources: ["tokenreviews"]
  verbs: ["create"]
- apiGroups: ["authorization.k8s.io"]
  resources: ["selfsubjectaccessreviews", "localsubjectaccessreviews", "subjectaccessreviews", "selfsubjectrulesreviews"]
  verbs: ["create"]
---
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: weblogic-operator-cluster-role-nonresource
  labels:
    weblogic.resourceVersion: operator-v1
    weblogic.operatorName: ${NAMESPACE}
rules:
- nonResourceURLs: ["/version/*"]
  verbs: ["get"]
---
#
# creating cluster role-bindings
#
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: ${NAMESPACE}-operator-rolebinding
  labels:
    weblogic.resourceVersion: operator-v1
    weblogic.operatorName: ${NAMESPACE}
subjects:
- kind: ServiceAccount
  name: ${ACCOUNT_NAME}
  namespace: ${NAMESPACE}
  apiGroup: ""
roleRef:
  kind: ClusterRole
  name: weblogic-operator-cluster-role
  apiGroup: rbac.authorization.k8s.io
---
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: ${NAMESPACE}-operator-rolebinding-nonresource
  labels:
    weblogic.resourceVersion: operator-v1
    weblogic.operatorName: ${NAMESPACE}
subjects:
- kind: ServiceAccount
  name: ${ACCOUNT_NAME}
  namespace: ${NAMESPACE}
  apiGroup: ""
roleRef:
  kind: ClusterRole
  name: weblogic-operator-cluster-role-nonresource
  apiGroup: rbac.authorization.k8s.io
---
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: ${NAMESPACE}-operator-rolebinding-discovery
  labels:
    weblogic.resourceVersion: operator-v1
    weblogic.operatorName: ${NAMESPACE}
subjects:
- kind: ServiceAccount
  name: ${ACCOUNT_NAME}
  namespace: ${NAMESPACE}
  apiGroup: ""
roleRef:
  kind: ClusterRole
  name: system:discovery
  apiGroup: rbac.authorization.k8s.io
---
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: ${NAMESPACE}-operator-rolebinding-auth-delegator
  labels:
    weblogic.resourceVersion: operator-v1
    weblogic.operatorName: ${NAMESPACE}
subjects:
- kind: ServiceAccount
  name: ${ACCOUNT_NAME}
  namespace: ${NAMESPACE}
  apiGroup: ""
roleRef:
  kind: ClusterRole
  name: system:auth-delegator
  apiGroup: rbac.authorization.k8s.io
---
#
# creating roles
#
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: weblogic-operator-namespace-role
  labels:
    weblogic.resourceVersion: operator-v1
    weblogic.operatorName: ${NAMESPACE}
rules:
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["storage.k8s.io"]
  resources: ["storageclasses"]
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources: ["services", "configmaps", "pods", "podtemplates", "events", "persistentvolumeclaims"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: [""]
  resources: ["pods/logs"]
  verbs: ["get", "list"]
- apiGroups: [""]
  resources: ["pods/exec"]
  verbs: ["create"]
- apiGroups: ["batch"]
  resources: ["jobs", "cronjobs"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["settings.k8s.io"]
  resources: ["podpresets"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["extensions"]
  resources: ["podsecuritypolicies", "networkpolicies"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
---
EOF

  # Generate a RoleBinding for each target namespace
  for i in ${TARGET_NAMESPACES//,/ }
  do
    cat >> ${SCRIPT}  <<EOF
#
# creating role-bindings
#
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: weblogic-operator-rolebinding
  namespace: ${i}
  labels:
    weblogic.resourceVersion: operator-v1
    weblogic.operatorName: ${NAMESPACE}
subjects:
- kind: ServiceAccount
  name: ${ACCOUNT_NAME}
  namespace: ${NAMESPACE}
  apiGroup: ""
roleRef:
  kind: ClusterRole
  name: weblogic-operator-namespace-role
  apiGroup: ""
---
EOF
  done


#
# Output instructions to use kubectl
#
echo "Create the WebLogic Operator Security configuration using kubectl as follows: kubectl create -f ${SCRIPT}"
#
echo "Ensure you start the API server with the --authorization-mode=RBAC option."