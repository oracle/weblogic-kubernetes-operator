# Copyright (c) 2018, 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorConfigMap" }}
---
apiVersion: "v1"
data:
  helmChartVersion: {{ .Chart.Version }}
  serviceaccount: {{ .serviceAccount | quote }}
  {{- if .domainNamespaceSelectionStrategy }}
  domainNamespaceSelectionStrategy: {{ .domainNamespaceSelectionStrategy | quote }}
  {{- end }}
  {{- if .domainNamespaces }}
  domainNamespaces: {{ .domainNamespaces | uniq | sortAlpha | join "," | quote }}
  {{- end }}
  {{- if .domainNamespaceLabelSelector }}
  domainNamespaceLabelSelector: {{ .domainNamespaceLabelSelector | quote }}
  {{- end }}
  {{- if .domainNamespaceRegExp }}
  domainNamespaceRegExp: {{ .domainNamespaceRegExp | quote }}
  {{- end }}
  {{- if .dns1123Fields }}
  dns1123Fields: {{ .dns1123Fields | quote }}
  {{- end }}
  {{- if .featureGates }}
  featureGates: {{ .featureGates | quote }}
  {{- end }}
  {{- if .introspectorJobNameSuffix }}
  introspectorJobNameSuffix: {{ .introspectorJobNameSuffix | quote }}
  {{- end }}
  {{- if .externalServiceNameSuffix }}
  externalServiceNameSuffix: {{ .externalServiceNameSuffix | quote }}
  {{- end }}
  {{- if .clusterSizePaddingValidationEnabled }}
  clusterSizePaddingValidationEnabled: {{ .clusterSizePaddingValidationEnabled | quote }}
  {{- end }}
  {{- if .tokenReviewAuthentication }}
  tokenReviewAuthentication: {{ .tokenReviewAuthentication | quote }}
  {{- end }}
  {{- if (hasKey . "istioLocalhostBindingsEnabled") }}
  istioLocalhostBindingsEnabled: {{ .istioLocalhostBindingsEnabled | quote }}
  {{- end }}
  {{- if .kubernetesPlatform }}
  kubernetesPlatform: {{ .kubernetesPlatform | quote }}
  {{- end }}
kind: "ConfigMap"
metadata:
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
  name: "weblogic-operator-cm"
  namespace: {{ .Release.Namespace | quote }}
{{- end }}
