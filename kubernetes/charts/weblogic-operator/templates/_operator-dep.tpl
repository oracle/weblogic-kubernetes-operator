# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorDeployment" }}
---
apiVersion: "apps/v1beta1" # for versions before 1.6.0 use extensions/v1beta1
kind: "Deployment"
metadata:
  name: "weblogic-operator"
  namespace: {{ .operatorNamespace | quote }}
  labels:
    weblogic.resourceVersion: "operator-v1"
    weblogic.operatorName: {{ .operatorNamespace | quote }}
spec:
  replicas: 1
  template:
    metadata:
     labels:
        weblogic.resourceVersion: "operator-v1"
        weblogic.operatorName: {{ .operatorNamespace | quote }}
        app: "weblogic-operator"
    spec:
      serviceAccountName: {{ .operatorServiceAccount | quote }}
      containers:
      - name: "weblogic-operator"
        image: {{ .operatorImage | quote }}
        imagePullPolicy: {{ .operatorImagePullPolicy | quote }}
        command: ["bash"]
        args: ["/operator/operator.sh"]
        env:
        - name: "OPERATOR_NAMESPACE"
          valueFrom:
            fieldRef:
              fieldPath: "metadata.namespace"
        - name: "OPERATOR_VERBOSE"
          value: "false"
        - name: "JAVA_LOGGING_LEVEL"
          value: {{ .javaLoggingLevel | quote }}
        {{- if .remoteDebugNodePortEnabled }}
        - name: "REMOTE_DEBUG_PORT"
          value: {{ .internalDebugHttpPort | quote }}
        {{- end }}
        volumeMounts:
        - name: "weblogic-operator-cm-volume"
          mountPath: "/operator/config"
        - name: "weblogic-operator-secrets-volume"
          mountPath: "/operator/secrets"
          readOnly: true
        {{- if .elkIntegrationEnabled }}
        - mountPath: "/logs"
          name: "log-dir"
          readOnly: false
        {{- end }}
        livenessProbe:
          exec:
            command:
              - "bash"
              - "/operator/livenessProbe.sh"
          initialDelaySeconds: 120
          periodSeconds: 5
      {{- if .elkIntegrationEnabled }}
      - name: "logstash"
        image: "logstash:5"
        args: [ "-f", "/logs/logstash.conf" ]
        volumeMounts:
        - name: "log-dir"
          mountPath: "/logs"
        env:
        - name: "ELASTICSEARCH_HOST"
          value: "elasticsearch.default.svc.cluster.local"
        - name: "ELASTICSEARCH_PORT"
          value: "9200"
      {{- end }}
      {{- if .operatorImagePullSecrets }}
      imagePullSecrets:
      {{ .operatorImagePullSecrets | toYaml }}
      {{- end }}
      volumes:
      - name: "weblogic-operator-cm-volume"
        configMap:
          name: "weblogic-operator-cm"
      - name: "weblogic-operator-secrets-volume"
        secret:
          secretName: "weblogic-operator-secrets"
      {{- if .elkIntegrationEnabled }}
      - name: "log-dir"
        emptyDir:
          medium: "Memory"
      {{- end }}
{{- end }}
