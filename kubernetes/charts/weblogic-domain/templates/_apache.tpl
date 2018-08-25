# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "domain.apache" }}
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ .Release.Name }}-apache-webtier
  namespace: {{ .Release.Namespace }}
  labels:
    weblogic.resourceVersion: apache-load-balancer-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
    app: apache-webtier

--- 
kind: Deployment
apiVersion: extensions/v1beta1
metadata:
  name: {{ .Release.Name }}-apache-webtier
  namespace: {{ .Release.Namespace }}
  labels:
    weblogic.resourceVersion: apache-load-balancer-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
    app: apache-webtier
spec: 
  replicas: 1
  selector:
    matchLabels:
      weblogic.domainUID: {{ .Release.Name }}
      weblogic.domainName: {{ .domainName }}
      app: apache-webtier
  template:
    metadata:
      labels:
        weblogic.resourceVersion: apache-load-balancer-v1
        weblogic.domainUID: {{ .Release.Name }}
        weblogic.domainName: {{ .domainName }}
        app: apache-webtier
    spec:
      serviceAccountName: {{ .Release.Name }}-apache-webtier
      terminationGracePeriodSeconds: 60
      {{- if .loadBalancerVolumePath }}
      volumes:
      - name: {{ .Release.Name }}-apache-webtier
        hostPath:
          path: {{ .loadBalancerVolumePath }}
      {{- end }}
      containers:
      - name: {{ .Release.Name }}-apache-webtier
        image: store/oracle/apache:12.2.1.3
        imagePullPolicy: Never
        {{- if .loadBalancerVolumePath }}
        volumeMounts:
        - name: {{ .Release.Name }}-apache-webtier
          mountPath: "/config"
        {{- end }}
        env:
          - name: WEBLOGIC_CLUSTER
            value: '{{ .Release.Name }}-cluster-{{ .clusterName | lower }}:{{ .managedServerPort }}'
          - name: LOCATION
            value: '{{ .loadBalancerAppPrepath }}'
          {{- if .loadBalancerExposeAdminPort }}
          - name: WEBLOGIC_HOST
            value: '{{ .Release.Name }}-{{ .adminServerName }}'
          - name: WEBLOGIC_PORT
            value: '{{ .adminPort }}'
          {{- end }}
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

---
apiVersion: v1
kind: Service
metadata:
  name: {{ .Release.Name }}-apache-webtier
  namespace: {{ .Release.Namespace }}
  labels:
    weblogic.resourceVersion: apache-load-balancer-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
spec:
  type: NodePort
  selector:
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
    app: apache-webtier
  ports:
    - port: 80
      nodePort: {{ .loadBalancerWebPort }}
      name: rest-https
{{- end }}
