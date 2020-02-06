# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorInternalService" }}
---
apiVersion: "v1"
kind: "Service"
metadata:
  name: "internal-weblogic-operator-svc"
  namespace: {{ .Release.Namespace | quote }}
  labels:
    weblogic.resourceVersion: "operator-v2"
    weblogic.operatorName: {{ .Release.Namespace | quote }}
spec:
  type: "ClusterIP"
  selector:
    app: "weblogic-operator"
  ports:
    - port: 8082
      name: "rest"
{{- end }}
