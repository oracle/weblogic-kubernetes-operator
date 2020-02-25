# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorClusterRoleGeneral" }}
---
{{- if .dedicated }}
kind: "Role"
{{- else }}
kind: "ClusterRole"
{{- end }}
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  {{- if .dedicated }}
  name: "weblogic-operator-role-general"
  namespace: {{ .Release.Namespace | quote }}
  {{- else }}
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-general" | join "-" | quote }}
  {{- end }}
  labels:
    weblogic.resourceVersion: "operator-v2"
    weblogic.operatorName: {{ .Release.Namespace | quote }}
rules:
{{- if not .dedicated }}
- apiGroups: [""]
  resources: ["namespaces"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["apiextensions.k8s.io"]
  resources: ["customresourcedefinitions"]
  verbs: ["get", "list", "watch", "create", "update", "patch"]
{{- end }}
- apiGroups: ["weblogic.oracle"]
  resources: ["domains", "domains/status"]
  verbs: ["get", "list", "watch", "update", "patch"]
- apiGroups: ["authentication.k8s.io"]
  resources: ["tokenreviews"]
  verbs: ["create"]
- apiGroups: ["authorization.k8s.io"]
  resources: ["selfsubjectrulesreviews"]
  verbs: ["create"]
{{- end }}
