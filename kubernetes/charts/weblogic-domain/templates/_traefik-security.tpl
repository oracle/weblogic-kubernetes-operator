# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "domain.traefikSecurity" }}
---
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: {{ .Release.Name }}-{{ .clusterName | lower | replace "_" "-" }}-traefik
  labels:
    weblogic.resourceVersion: traefik-load-balancer-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
    weblogic.clusterName: {{ .clusterName }}
rules:
  - apiGroups:
      - ""
    resources:
      - pods
      - services
      - endpoints
      - secrets
    verbs:
      - get
      - list
      - watch
  - apiGroups:
      - extensions
    resources:
      - ingresses
    verbs:
      - get
      - list
      - watch

---
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: {{ .Release.Name }}-{{ .clusterName | lower | replace "_" "-" }}-traefik
  labels:
    weblogic.resourceVersion: traefik-load-balancer-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
    weblogic.clusterName: {{ .clusterName }}
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: {{ .Release.Name }}-{{ .clusterName | lower | replace "_" "-" }}-traefik
subjects:
- kind: ServiceAccount
  name: {{ .Release.Name }}-{{ .clusterName | lower | replace "_" "-" }}-traefik
  namespace: {{ .Release.Namespace }}
{{- end }}
