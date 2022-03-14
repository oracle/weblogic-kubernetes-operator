# Copyright (c) 2018, 2022, Oracle and/or its affiliates.
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
  {{- if (and $secret $secret.data) }}
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
---
apiVersion: "v1"
kind: "Secret"
data:
  {{- $secret := (lookup "v1" "Secret" .Release.Namespace "weblogic-webhook-secrets") }}
  {{- if (and $secret $secret.data) }}
  {{- $webhookKey := index $secret.data "webhookKey" }}
  {{- if $webhookKey }}
  webhookKey: {{ $webhookKey }}
  {{- end }}
  {{- $webhookCert := index $secret.data "webhookCert" }}
  {{- if $webhookCert }}
  webhookCert: {{ $webhookCert }}
  {{- end }}
  {{- end }}
metadata:
  labels:
    weblogic.webhookName: {{ .Release.Namespace | quote }}
  name: "weblogic-webhook-secrets"
  namespace:  {{ .Release.Namespace | quote }}
type: "Opaque"
{{- end }}
