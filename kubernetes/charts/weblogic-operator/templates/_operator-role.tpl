# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorRole" }}
---
kind: "Role"
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  name: "weblogic-operator-role"
  namespace: {{ .Release.Namespace | quote }}
  labels:
    weblogic.resourceVersion: "operator-v2"
    weblogic.operatorName: {{ .Release.Namespace | quote }}
rules:
- apiGroups: [""]
  resources: ["secrets", "configmaps", "events"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
{{- end }}
