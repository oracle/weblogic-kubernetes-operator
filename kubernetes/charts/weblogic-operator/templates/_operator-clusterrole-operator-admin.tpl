# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorClusterRoleOperatorAdmin" }}
---
{{- if .dedicated }}
kind: "Role"
{{- else }}
kind: "ClusterRole"
{{- end }}
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  {{- if .dedicated }}
  name: "weblogic-operator-role-operator-admin"
  namespace: {{ .Release.Namespace | quote }}
  {{- else }}
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-operator-admin" | join "-" | quote }}
  {{- end }}
  labels:
    weblogic.resourceVersion: "operator-v2"
    weblogic.operatorName: {{ .Release.Namespace | quote }}
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: [""]
  resources: ["pods", "events"]
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources: ["pods/log"]
  verbs: ["get", "list"]
- apiGroups: [""]
  resources: ["pods/exec"]
  verbs: ["create"]
{{- end }}
