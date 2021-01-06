# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorClusterRoleNonResource" }}
---
kind: "ClusterRole"
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-nonresource" | join "-" | quote }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
rules:
- nonResourceURLs: ["/version/*"]
  verbs: ["get"]
{{- end }}
