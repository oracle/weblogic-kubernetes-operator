# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorConfigMap" }}
---
apiVersion: "v1"
data:
  {{- if .externalRestEnabled }}
    {{- if (hasKey . "externalRestIdentitySecret") }}
  externalRestIdentitySecret: {{ .externalRestIdentitySecret | quote }}
    {{- else }}
  externalOperatorCert: {{ .externalOperatorCert | quote }}
    {{- end }}
  {{- end }}
  {{- $configmap := (lookup "v1" "ConfigMap" .Release.Namespace "weblogic-operator-cm") }}
  {{- if (and $configmap $configmap.data) }}
  {{- $internalOperatorCert := index $configmap.data "internalOperatorCert" }}
  {{- if $internalOperatorCert }}
  internalOperatorCert: {{ $internalOperatorCert }}
  {{- end }}
  {{- end }}
  serviceaccount: {{ .serviceAccount | quote }}
  domainNamespaceSelectionStrategy: {{ (default "List" .domainNamespaceSelectionStrategy) | quote }}
  domainNamespaces: {{ .domainNamespaces | uniq | sortAlpha | join "," | quote }}
  {{- if .dedicated }}
  dedicated: {{ .dedicated | quote }}
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
  {{- if .domainPresenceFailureRetryMaxCount }}
  domainPresenceFailureRetryMaxCount: {{ .domainPresenceFailureRetryMaxCount | quote }}
  {{- end }}
  {{- if .domainPresenceFailureRetrySeconds }}
  domainPresenceFailureRetrySeconds: {{ .domainPresenceFailureRetrySeconds | quote }}
  {{- end }}
kind: "ConfigMap"
metadata:
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
  name: "weblogic-operator-cm"
  namespace: {{ .Release.Namespace | quote }}
{{- end }}
