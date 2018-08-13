# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "domain.voyagerIngress" }}
---
apiVersion: voyager.appscode.com/v1beta1
kind: Ingress
metadata:
  name: {{ .Release.Name }}-voyager
  namespace: {{ .Release.Namespace }}
  labels:
    weblogic.resourceVersion: voyager-load-balancer-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
  annotations:
    ingress.appscode.com/type: 'NodePort'
    ingress.appscode.com/stats: 'true'
    ingress.appscode.com/affinity: 'cookie'
spec:
  rules:
  - host: '*' 
    http:
      nodePort: '{{ .loadBalancerWebPort }}'
      paths:
      - backend:
          serviceName: {{ .Release.Name }}-cluster-{{ .clusterName | lower }}
          servicePort: '{{ .managedServerPort }}'

---
apiVersion: v1
kind: Service
metadata:
  name: {{ .Release.Name }}-voyager-stats
  namespace: {{ .Release.Namespace }}
  labels:
    app: {{ .Release.Name }}-voyager-stats
    weblogic.resourceVersion: voyager-load-balancer-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
spec:
  type: NodePort
  ports:
    - name: client
      protocol: TCP
      port: 56789
      targetPort: 56789
      nodePort: {{ .loadBalancerDashboardPort }}
  selector:
    origin: voyager
    origin-name: {{ .Release.Name }}-voyager
{{- end }}
