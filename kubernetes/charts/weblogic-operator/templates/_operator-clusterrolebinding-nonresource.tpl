# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.clusterRoleBindingNonResource" }}
---
apiVersion: "rbac.authorization.k8s.io/v1beta1"
kind: "ClusterRoleBinding"
metadata:
  labels:
    weblogic.operatorName: {{ .operatorNamespace | quote }}
    weblogic.resourceVersion: "operator-v1"
  name: {{ list .operatorNamespace "operator-rolebinding-nonresource" | join "-" | quote }}
roleRef:
  apiGroup: "rbac.authorization.k8s.io"
  kind: "ClusterRole"
  name: "weblogic-operator-cluster-role-nonresource"
subjects:
- kind: "ServiceAccount"
  apiGroup: ""
  name: {{ .operatorServiceAccount | quote }}
  namespace: {{ .operatorNamespace | quote }}
{{- end }}
