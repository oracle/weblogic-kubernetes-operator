# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorClusterRoleNonResource" }}
---
kind: "ClusterRole"
apiVersion: "rbac.authorization.k8s.io/v1beta1"
metadata:
  name: "weblogic-operator-cluster-role-nonresource"
  labels:
    weblogic.resourceVersion: "operator-v1"
    weblogic.operatorName: {{ .Release.Namespace | quote }}
rules:
- nonResourceURLs: ["/version/*"]
  verbs: ["get"]
{{- end }}
