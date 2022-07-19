# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.logStashConfigMap" }}
---
apiVersion: "v1"
data:
  logstash.conf: |
{{ .Files.Get "logstash.conf" | indent 4 }}
  logstash.yml: |
{{ .Files.Get "logstash.yml" | indent 4 }}
kind: "ConfigMap"
metadata:
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
  name: "weblogic-operator-logstash-cm"
  namespace: {{ .Release.Namespace | quote }}
{{- end }}
