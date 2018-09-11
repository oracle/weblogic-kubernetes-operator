# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.elasticSearchService" }}
{{- if .elkIntegrationEnabled }}
---
kind: "Service"
apiVersion: "v1"
metadata:
  name: "elasticsearch"
spec:
  ports:
  - name: "http"
    protocol: "TCP"
    port: 9200
    targetPort: 9200
  - name: "https"
    protocol: "TCP"
    port: 9300
    targetPort: 9300
  selector:
    app: "elasticsearch"
{{- end }}
{{- end }}
