# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorRoleBinding" }}
---
kind: "RoleBinding"
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  name: "weblogic-operator-rolebinding"
  namespace: {{ .Release.Namespace | quote }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
subjects:
- kind: "ServiceAccount"
  name: {{ .serviceAccount | quote }}
  namespace: {{ .Release.Namespace | quote }}
  apiGroup: ""
roleRef:
  kind: "Role"
  name: "weblogic-operator-role"
  apiGroup: "rbac.authorization.k8s.io"
{{- end }}
