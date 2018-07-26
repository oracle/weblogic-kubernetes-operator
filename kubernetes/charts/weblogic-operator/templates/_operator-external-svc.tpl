# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorExternalService" }}
{{- if or (not (eq .externalRestOption "NONE")) .remoteDebugNodePortEnabled }}
---
apiVersion: "v1"
kind: "Service"
metadata:
  name: "external-weblogic-operator-svc"
  namespace: {{ .operatorNamespace | quote }}
  labels:
    weblogic.resourceVersion: "operator-v1"
    weblogic.operatorName: {{ .operatorNamespace | quote }}
spec:
  type: "NodePort"
  selector:
    app: "weblogic-operator"
  ports:
    {{- if not (eq .externalRestOption "NONE") }}
    - name: "rest"
      port: 8081
      nodePort: {{ .externalRestHttpsPort }}
    {{- end }}
    {{- if .remoteDebugNodePortEnabled }}
    - name: "debug"
      port: {{ .internalDebugHttpPort }}
      nodePort: {{ .externalDebugHttpPort }}
    {{- end }}
{{- end }}
{{- end }}
