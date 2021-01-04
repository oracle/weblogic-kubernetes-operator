# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorRoleBindingNamespace" }}
---
{{- if .enableClusterRoleBinding }}
kind: "ClusterRoleBinding"
{{- else }}
kind: "RoleBinding"
{{- end }}
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  {{- if .enableClusterRoleBinding }}
  name: {{ list .Release.Namespace "weblogic-operator-clusterrolebinding-namespace" | join "-" | quote }}
  {{- else }}
  name: "weblogic-operator-rolebinding-namespace"
  namespace: {{ .domainNamespace | quote }}
  {{- end }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
subjects:
- kind: "ServiceAccount"
  name: {{ .serviceAccount | quote }}
  namespace: {{ .Release.Namespace | quote }}
  apiGroup: ""
roleRef:
  {{- if (or (eq (default "List" .domainNamespaceSelectionStrategy) "Dedicated") (and .dedicated (eq (default "List" .domainNamespaceSelectionStrategy) "List"))) }}
  kind: "Role"
  name: "weblogic-operator-role-namespace"
  {{- else }}
  kind: "ClusterRole"
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-namespace" | join "-" | quote }}
  {{- end }}
  apiGroup: "rbac.authorization.k8s.io"
{{- end }}
