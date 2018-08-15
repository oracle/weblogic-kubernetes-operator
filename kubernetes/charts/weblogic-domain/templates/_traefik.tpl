# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "domain.traefik" }}
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ .Release.Name }}-{{ .clusterName | lower }}-traefik
  namespace: {{ .Release.Namespace }}
  labels:
    weblogic.resourceVersion: traefik-load-balancer-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
    weblogic.clusterName: {{ .clusterName }}

---
kind: Deployment
apiVersion: extensions/v1beta1
metadata:
  name: {{ .Release.Name }}-{{ .clusterName | lower }}-traefik
  namespace: {{ .Release.Namespace }}
  labels:
    weblogic.resourceVersion: traefik-load-balancer-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
    weblogic.clusterName: {{ .clusterName }}
spec:
  replicas: 1
  selector:
    matchLabels:
      weblogic.domainUID: {{ .Release.Name }}
      weblogic.clusterName: {{ .clusterName }}
  template:
    metadata:
      labels:
        weblogic.resourceVersion: traefik-load-balancer-v1
        weblogic.domainUID: {{ .Release.Name }}
        weblogic.domainName: {{ .domainName }}
        weblogic.clusterName: {{ .clusterName }}
    spec:
      serviceAccountName: {{ .Release.Name }}-{{ .clusterName | lower }}-traefik
      terminationGracePeriodSeconds: 60
      containers:
      - image: traefik:1.4.5
        name: traefik
        resources:
          requests:
            cpu: "100m"
            memory: "20Mi"
          limits:
            cpu: "100m"
            memory: "30Mi"
        readinessProbe:
          tcpSocket:
            port: 80
          failureThreshold: 1
          initialDelaySeconds: 10
          periodSeconds: 10
          successThreshold: 1
          timeoutSeconds: 2
        livenessProbe:
          tcpSocket:
            port: 80
          failureThreshold: 3
          initialDelaySeconds: 10
          periodSeconds: 10
          successThreshold: 1
          timeoutSeconds: 2
        volumeMounts:
        - mountPath: /config
          name: config
        ports:
        - name: http
          containerPort: 80
          protocol: TCP
        - name: dash
          containerPort: 8080
          protocol: TCP
        args:
        - --configfile=/config/traefik.toml
      volumes:
      - name: config
        configMap:
          name: {{ .Release.Name }}-{{ .clusterName | lower }}-traefik-cm

---
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .Release.Name }}-{{ .clusterName | lower }}-traefik-cm
  namespace: {{ .Release.Namespace }}
  labels:
    weblogic.resourceVersion: traefik-load-balancer-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
    weblogic.clusterName: {{ .clusterName }}
data:
  traefik.toml: |
    # traefik.toml
    logLevel = "INFO"
    defaultEntryPoints = ["http"]
    [entryPoints]
      [entryPoints.http]
      address = ":80"
      compress = true
    [kubernetes]
    labelselector = "weblogic.domainUID={{ .Release.Name }},weblogic.clusterName={{ .clusterName }}"
    [web]
    address = ":8080"

---
kind: Service
apiVersion: v1
metadata:
  name: {{ .Release.Name }}-{{ .clusterName | lower }}-traefik
  namespace: {{ .Release.Namespace }}
  labels:
    weblogic.resourceVersion: traefik-load-balancer-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
    weblogic.clusterName: {{ .clusterName }}
spec:
  selector:
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.clusterName: {{ .clusterName }}
  ports:
  - port: 80
    name: http
    targetPort: http
    nodePort: {{ .loadBalancerWebPort }}
  type: NodePort

---
apiVersion: v1
kind: Service
metadata:
  name: {{ .Release.Name }}-{{ .clusterName | lower }}-traefik-dashboard
  namespace: {{ .Release.Namespace }}
  labels:
    weblogic.resourceVersion: traefik-load-balancer-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
    weblogic.clusterName: {{ .clusterName }}
spec:
  selector:
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.clusterName: {{ .clusterName }}
  ports:
  - port: 8080
    name: dash
    targetPort: dash
    nodePort: {{ .loadBalancerDashboardPort }}
  type: NodePort
{{- end }}
