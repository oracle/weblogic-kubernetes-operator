# Copyright (c) 2018, 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorInternalService" }}
---
{{- if not .operatorOnly }}
apiVersion: "v1"
kind: "Service"
metadata:
  name: "weblogic-operator-webhook-svc"
  namespace: {{ .Release.Namespace | quote }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
spec:
  type: "ClusterIP"
  selector:
    app: "weblogic-operator-webhook"
  ports:
    - port: 8083
      name: "metrics"
      appProtocol: http
    - port: 8084
      name: "restwebhook"
      appProtocol: https
{{- end }}
{{- end }}
