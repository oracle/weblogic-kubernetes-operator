# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

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
kind: "ConfigMap"
metadata:
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
    weblogic.resourceVersion: "operator-v2"
  name: "weblogic-operator-cm"
  namespace: {{ .Release.Namespace | quote }}
{{- end }}
