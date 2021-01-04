# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.clusterRoleBindingAuthDelegator" }}
---
apiVersion: "rbac.authorization.k8s.io/v1"
{{- if (or (eq (default "List" .domainNamespaceSelectionStrategy) "Dedicated") (and .dedicated (eq (default "List" .domainNamespaceSelectionStrategy) "List"))) }}
kind: "RoleBinding"
{{- else }}
kind: "ClusterRoleBinding"
{{- end }}
metadata:
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote}}
  {{- if (or (eq (default "List" .domainNamespaceSelectionStrategy) "Dedicated") (and .dedicated (eq (default "List" .domainNamespaceSelectionStrategy) "List"))) }}
  name: "weblogic-operator-rolebinding-auth-delegator"
  namespace: {{ .Release.Namespace | quote }}
  {{- else }}
  name: {{ list .Release.Namespace "weblogic-operator-clusterrolebinding-auth-delegator" | join "-" | quote }}
  {{- end }}
roleRef:
  apiGroup: "rbac.authorization.k8s.io"
  kind: "ClusterRole"
  name: "system:auth-delegator"
subjects:
- kind: "ServiceAccount"
  apiGroup: ""
  name: {{ .serviceAccount | quote }}
  namespace: {{ .Release.Namespace | quote }}
{{- end }}
