# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorInternalService" }}
---
apiVersion: "v1"
kind: "Service"
metadata:
  name: "internal-weblogic-operator-svc"
  namespace: {{ .Release.Namespace | quote }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
spec:
  type: "ClusterIP"
  selector:
    app: "weblogic-operator"
  ports:
    - port: 8082
      name: "rest"
{{- end }}
