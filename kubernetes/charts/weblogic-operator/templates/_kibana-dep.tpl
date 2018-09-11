# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.kibanaDeployment" }}
{{- if .elkIntegrationEnabled }}
---
apiVersion: "apps/v1beta1"
kind: "Deployment"
metadata:
  name: "kibana"
  labels:
    app: "kibana"
spec:
  replicas: 1
  selector:
    matchLabels:
      app: "kibana"
  template:
    metadata:
      labels:
        app: "kibana"
    spec:
      containers:
      - name: "kibana"
        image: "kibana:5"
        ports:
        - containerPort: 5601
{{- end }}
{{- end }}
