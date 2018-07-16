# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.kibanaService" }}
{{- if .elkIntegrationEnabled }}
---
apiVersion: v1
kind: Service
metadata: 
  name: kibana
  labels: 
    app: kibana
spec: 
  type: NodePort
  ports:
    - port: 5601
  selector: 
    app: kibana
{{- end }}
{{- end }}
