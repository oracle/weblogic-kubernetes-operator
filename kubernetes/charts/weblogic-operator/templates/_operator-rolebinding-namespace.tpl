# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorRoleBindingNamespace" }}
---
kind: "RoleBinding"
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  name: "weblogic-operator-rolebinding-namespace"
  namespace: {{ .domainNamespace | quote }}
  labels:
    weblogic.resourceVersion: "operator-v2"
    weblogic.operatorName: {{ .Release.Namespace | quote }}
subjects:
- kind: "ServiceAccount"
  name: {{ .serviceAccount | quote }}
  namespace: {{ .Release.Namespace | quote }}
  apiGroup: ""
roleRef:
  kind: "ClusterRole"
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-namespace" | join "-" | quote }}
  apiGroup: "rbac.authorization.k8s.io"
{{- end }}
