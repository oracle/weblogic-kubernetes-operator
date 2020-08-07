# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorSecrets" }}
---
apiVersion: "v1"
kind: "Secret"
data:
  {{- if (and .externalRestEnabled (hasKey . "externalOperatorKey")) }}
  externalOperatorKey: {{ .externalOperatorKey | quote }}
  {{- end }}
  {{- $secret := (lookup "v1" "Secret" .Release.Namespace "weblogic-operator-secrets") }}
  {{- if $secret }}
  {{- $internalOperatorKey := index $secret.data "internalOperatorKey" }}
  {{- if $internalOperatorKey }}
  internalOperatorKey: {{ $internalOperatorKey }}
  {{- end }}
  {{- end }}
metadata:
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
  name: "weblogic-operator-secrets"
  namespace:  {{ .Release.Namespace | quote }}
type: "Opaque"
{{- end }}
