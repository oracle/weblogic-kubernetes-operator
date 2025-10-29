# Copyright (c) 2018, 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorRole" }}
---
kind: "Role"
apiVersion: "rbac.authorization.k8s.io/v1"
metadata:
  name: "weblogic-operator-role"
  namespace: {{ .Release.Namespace | quote }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
rules:
- apiGroups: [""]
  resources: ["secrets", "configmaps"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["events.k8s.io"]
  resources: ["events"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["admissionregistration.k8s.io"]
  resources: ["validatingwebhookconfigurations"]
  verbs: ["get", "create", "update", "patch", "delete"]
- apiGroups: ["weblogic.oracle"]
  resources: ["domains", "clusters"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete", "deletecollection"]
- apiGroups: ["weblogic.oracle"]
  resources: ["domains/status", "clusters/status"]
  verbs: ["get", "watch"]
- apiGroups: ["weblogic.oracle"]
  resources: ["clusters/scale"]
  verbs: ["update", "patch"]
{{- end }}
