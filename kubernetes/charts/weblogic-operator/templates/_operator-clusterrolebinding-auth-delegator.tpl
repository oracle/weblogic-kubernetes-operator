# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.clusterRoleBindingAuthDelegator" }}
---
apiVersion: "rbac.authorization.k8s.io/v1"
{{- if .dedicated }}
kind: "RoleBinding"
{{- else }}
kind: "ClusterRoleBinding"
{{- end }}
metadata:
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote}}
    weblogic.resourceVersion: "operator-v2"
  {{- if .dedicated }}
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
