# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.clusterRoleBinding" }}
---
apiVersion: "rbac.authorization.k8s.io/v1beta1"
kind: "ClusterRoleBinding"
metadata:
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
    weblogic.resourceVersion: "operator-v1"
  name: {{ list .Release.Namespace "operator-rolebinding" | join "-" | quote }}
roleRef:
  apiGroup: "rbac.authorization.k8s.io"
  kind: "ClusterRole"
  name: {{ list .Release.Namespace "cluster-role" | join "-" | quote }}
subjects:
- kind: "ServiceAccount"
  apiGroup: ""
  name: {{ .operatorServiceAccount | quote }}
  namespace: {{ .Release.Namespace | quote }}
{{- end }}
