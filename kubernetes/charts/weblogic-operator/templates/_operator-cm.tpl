# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
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
  serviceaccount: {{ .serviceAccount | quote }}
  targetNamespaces: {{ .domainNamespaces | uniq | sortAlpha | join "," | quote }}
  dedicated: {{ .dedicated | quote }}
  {{- if .dns1123Fields }}
  dns1123Fields: {{ .dns1123Fields | quote }}
  {{- end }}
kind: "ConfigMap"
metadata:
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
    weblogic.resourceVersion: "operator-v2"
  name: "weblogic-operator-cm"
  namespace: {{ .Release.Namespace | quote }}
{{- end }}
