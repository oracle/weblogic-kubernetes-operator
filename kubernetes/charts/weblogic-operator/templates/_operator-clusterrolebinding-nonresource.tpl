# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.clusterRoleBindingNonResource" }}
---
apiVersion: "rbac.authorization.k8s.io/v1"
kind: "ClusterRoleBinding"
metadata:
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
    weblogic.resourceVersion: "operator-v2"
  name: {{ list .Release.Namespace "weblogic-operator-clusterrolebinding-nonresource" | join "-" | quote }}
roleRef:
  apiGroup: "rbac.authorization.k8s.io"
  kind: "ClusterRole"
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-nonresource" | join "-" | quote }}
subjects:
- kind: "ServiceAccount"
  apiGroup: ""
  name: {{ .serviceAccount | quote }}
  namespace: {{ .Release.Namespace | quote }}
{{- end }}
