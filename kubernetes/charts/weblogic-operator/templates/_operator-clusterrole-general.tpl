# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorClusterRoleGeneral" }}
---
{{- if (or (eq (default "List" .domainNamespaceSelectionStrategy) "Dedicated") (and .dedicated (eq (default "List" .domainNamespaceSelectionStrategy) "List"))) }}
kind: "Role"
{{- else }}
kind: "ClusterRole"
{{- end }}
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  {{- if (or (eq (default "List" .domainNamespaceSelectionStrategy) "Dedicated") (and .dedicated (eq (default "List" .domainNamespaceSelectionStrategy) "List"))) }}
  name: "weblogic-operator-role-general"
  namespace: {{ .Release.Namespace | quote }}
  {{- else }}
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-general" | join "-" | quote }}
  {{- end }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
rules:
{{- if not (or (eq (default "List" .domainNamespaceSelectionStrategy) "Dedicated") (and .dedicated (eq (default "List" .domainNamespaceSelectionStrategy) "List"))) }}
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
