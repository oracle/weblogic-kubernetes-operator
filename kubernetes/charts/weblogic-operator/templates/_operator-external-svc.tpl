# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorExternalService" }}
{{- if or .externalRestEnabled .remoteDebugNodePortEnabled }}
---
apiVersion: "v1"
kind: "Service"
metadata:
  name: "external-weblogic-operator-svc"
  namespace: {{ .Release.Namespace | quote }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
spec:
  type: "NodePort"
  selector:
    app: "weblogic-operator"
  ports:
    {{- if .externalRestEnabled }}
    - name: "rest"
      port: 8081
      appProtocol: https
      nodePort: {{ .externalRestHttpsPort }}
    {{- end }}
    {{- if .remoteDebugNodePortEnabled }}
    - name: "debug"
      port: {{ .internalDebugHttpPort }}
      appProtocol: http
      nodePort: {{ .externalDebugHttpPort }}
    {{- end }}
{{- end }}
{{- end }}
