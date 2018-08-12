# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.elasticSearchDeployment" }}
{{- if .elkIntegrationEnabled }}
---
apiVersion: apps/v1beta1
kind: Deployment
metadata:
  name: "elasticsearch"
  labels:
    app: "elasticsearch"
spec:
  replicas: 1
  selector:
    matchLabels:
      app: "elasticsearch"
  template:
    metadata:
      labels:
        app: "elasticsearch"
    spec:
      containers:
      - name: "elasticsearch"
        image: "elasticsearch:5"
        ports:
        - containerPort: 9200
        - containerPort: 9300
{{- end }}
{{- end }}
