# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorSecrets" }}
---
apiVersion: v1
data:
  internalOperatorKey: {{ .internalOperatorKey }}
  {{- if .externalRestEnabled }}
  externalOperatorKey: {{ .externalOperatorKey }}
  {{- end }}
kind: Secret
metadata:
  labels:
    weblogic.operatorName: {{ .operatorNamespace }}
    weblogic.resourceVersion: operator-v1
  name: weblogic-operator-secrets
  namespace:  {{ .operatorNamespace }}
type: Opaque
{{- end }}
