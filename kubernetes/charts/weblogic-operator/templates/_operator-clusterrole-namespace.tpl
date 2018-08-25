# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorClusterRoleNamespace" }}
---
kind: "ClusterRole"
apiVersion: "rbac.authorization.k8s.io/v1beta1"
metadata:
  name: "weblogic-operator-namespace-role"
  labels:
    weblogic.resourceVersion: "operator-v1"
    weblogic.operatorName: {{ .Release.Namespace | quote }}
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
{{- end }}
