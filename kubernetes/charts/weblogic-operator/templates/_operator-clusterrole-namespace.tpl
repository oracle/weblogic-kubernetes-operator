# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorClusterRoleNamespace" }}
---
{{- if .dedicated }}
kind: "Role"
{{- else }}
kind: "ClusterRole"
{{- end }}
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  {{- if .dedicated }}
  name: "weblogic-operator-role-namespace"
  namespace: {{ .Release.Namespace | quote }}
  {{- else }}
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-namespace" | join "-" | quote }}
  {{- end }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
rules:
- apiGroups: [""]
  resources: ["services", "configmaps", "pods", "events"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources: ["pods/log"]
  verbs: ["get", "list"]
- apiGroups: [""]
  resources: ["pods/exec"]
  verbs: ["get", "create"]
- apiGroups: ["batch"]
  resources: ["jobs"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
{{- end }}
