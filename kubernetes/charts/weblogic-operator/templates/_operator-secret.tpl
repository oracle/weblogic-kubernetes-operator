# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorSecrets" }}
---
apiVersion: "v1"
kind: "Secret"
data:
  internalOperatorKey: {{ .internalOperatorKey | quote }}
  {{- if not (eq .externalRestOption "NONE") }}
  externalOperatorKey: {{ .externalOperatorKey | quote }}
  {{- end }}
metadata:
  labels:
    weblogic.operatorName: {{ .operatorNamespace | quote }}
    weblogic.resourceVersion: "operator-v1"
  name: "weblogic-operator-secrets"
  namespace:  {{ .operatorNamespace | quote }}
type: "Opaque"
{{- end }}
