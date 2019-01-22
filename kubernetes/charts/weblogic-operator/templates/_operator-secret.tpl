# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorSecrets" }}
---
apiVersion: "v1"
kind: "Secret"
data:
  {{- if (and .externalRestEnabled (hasKey . "externalOperatorKey")) }}
  externalOperatorKey: {{ .externalOperatorKey | quote }}
  {{- end }}
metadata:
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
    weblogic.resourceVersion: "operator-v2"
  name: "weblogic-operator-secrets"
  namespace:  {{ .Release.Namespace | quote }}
type: "Opaque"
{{- end }}
