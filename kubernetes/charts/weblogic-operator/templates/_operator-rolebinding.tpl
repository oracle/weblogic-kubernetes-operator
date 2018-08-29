# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorRoleBinding" }}
---
kind: "RoleBinding"
apiVersion: "rbac.authorization.k8s.io/v1beta1"
metadata:
  name: "weblogic-operator-rolebinding"
  namespace: {{ .domainsNamespace | quote }}
  labels:
    weblogic.resourceVersion: "operator-v1"
    weblogic.operatorName: {{ .Release.Namespace | quote }}
subjects:
- kind: "ServiceAccount"
  name: {{ .operatorServiceAccount | quote }}
  namespace: {{ .Release.Namespace | quote }}
  apiGroup: ""
roleRef:
  kind: "ClusterRole"
  name: {{ list .Release.Namespace "namespace-role" | join "-" | quote }}
  apiGroup: ""
{{- end }}
