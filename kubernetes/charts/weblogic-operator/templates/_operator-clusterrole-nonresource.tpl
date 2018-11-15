# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorClusterRoleNonResource" }}
---
kind: "ClusterRole"
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  name: {{ list .Release.Namespace "weblogic-operator-clusterrole-nonresource" | join "-" | quote }}
  labels:
    weblogic.resourceVersion: "operator-v2"
    weblogic.operatorName: {{ .Release.Namespace | quote }}
rules:
- nonResourceURLs: ["/version/*"]
  verbs: ["get"]
{{- end }}
