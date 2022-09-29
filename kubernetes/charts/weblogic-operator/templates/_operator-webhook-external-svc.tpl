# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorWebhookExternalService" }}
{{- if and (.remoteDebugNodePortEnabled) (not .operatorOnly) }}
---
apiVersion: "v1"
kind: "Service"
metadata:
  name: "external-weblogic-operator-webhook-svc"
  namespace: {{ .Release.Namespace | quote }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
spec:
  type: "NodePort"
  selector:
    app: "weblogic-operator-webhook"
  ports:
    - name: "debug"
      port: {{ .webhookDebugHttpPort }}
      appProtocol: http
      nodePort: {{ .webhookDebugHttpPort }}
{{- end }}
{{- end }}
