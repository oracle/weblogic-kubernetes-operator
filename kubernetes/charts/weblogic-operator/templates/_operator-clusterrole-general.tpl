# Copyright (c) 2018, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorClusterRoleGeneral" }}
---
{{- if (eq .domainNamespaceSelectionStrategy "Dedicated") }}
kind: "Role"
{{- else }}
kind: "ClusterRole"
{{- end }}
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  {{- if (eq .domainNamespaceSelectionStrategy "Dedicated") }}
  name: "weblogic-operator-role-general"
  namespace: {{ .Release.Namespace | quote }}
  {{- else }}
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-general" | join "-" | quote }}
  {{- end }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
rules:
{{- if not (eq .domainNamespaceSelectionStrategy "Dedicated") }}
- apiGroups: [""]
  resources: ["namespaces"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["apiextensions.k8s.io"]
  resources: ["customresourcedefinitions"]
  verbs: ["get", "list", "watch", "create", "update", "patch"]
{{- end }}
- apiGroups: [""]
  resources: ["persistentvolumes"]
  verbs: ["get", "list", "create"]
- apiGroups: ["weblogic.oracle"]
  resources: ["domains", "clusters", "domains/status", "clusters/status"]
  verbs: ["get", "create", "list", "watch", "update", "patch"]
- apiGroups: ["authentication.k8s.io"]
  resources: ["tokenreviews"]
  verbs: ["create"]
- apiGroups: ["authorization.k8s.io"]
  resources: ["selfsubjectrulesreviews"]
  verbs: ["create"]
- apiGroups: ["admissionregistration.k8s.io"]
  resources: ["validatingwebhookconfigurations"]
  verbs: ["get", "create", "update", "patch", "delete"]
{{- end }}
