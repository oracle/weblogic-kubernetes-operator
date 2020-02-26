# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorDeployment" }}
---
apiVersion: "apps/v1"
kind: "Deployment"
metadata:
  name: "weblogic-operator"
  namespace: {{ .Release.Namespace | quote }}
  labels:
    weblogic.resourceVersion: "operator-v2"
    weblogic.operatorName: {{ .Release.Namespace | quote }}
spec:
  selector:
    matchLabels:
      weblogic.resourceVersion: "operator-v2"
      weblogic.operatorName: {{ .Release.Namespace | quote }}
  replicas: 1
  template:
    metadata:
     labels:
        weblogic.resourceVersion: "operator-v2"
        weblogic.operatorName: {{ .Release.Namespace | quote }}
        app: "weblogic-operator"
    spec:
      serviceAccountName: {{ .serviceAccount | quote }}
      containers:
      - name: "weblogic-operator"
        image: {{ .image | quote }}
        imagePullPolicy: {{ .imagePullPolicy | quote }}
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
        - name: ISTIO_ENABLED
          value: {{ .istioEnabled | quote }}
        {{- if .remoteDebugNodePortEnabled }}
        - name: "REMOTE_DEBUG_PORT"
          value: {{ .internalDebugHttpPort | quote }}
        - name: "DEBUG_SUSPEND"
          {{- if .suspendOnDebugStartup }}
          value: "y"
          {{- else }}
          value: "n"
          {{- end }}
        {{- end }}
        {{- if .mockWLS }}
        - name: "MOCK_WLS"
          value: "true"
        {{- end }}
        resources:
          requests:
            cpu: "100m"
            memory: "512Mi"
        volumeMounts:
        - name: "weblogic-operator-cm-volume"
          mountPath: "/operator/config"
        - name: "weblogic-operator-debug-cm-volume"
          mountPath: "/operator/debug-config"
        - name: "weblogic-operator-secrets-volume"
          mountPath: "/operator/secrets"
          readOnly: true
        {{- if .elkIntegrationEnabled }}
        - mountPath: "/logs"
          name: "log-dir"
          readOnly: false
        {{- end }}
        {{- if not .remoteDebugNodePortEnabled }}
        livenessProbe:
          exec:
            command:
              - "bash"
              - "/operator/livenessProbe.sh"
          initialDelaySeconds: 20
          periodSeconds: 5
        readinessProbe:
          exec:
            command:
              - "bash"
              - "/operator/readinessProbe.sh"
          initialDelaySeconds: 2
          periodSeconds: 10
        {{- end }}
      {{- if .elkIntegrationEnabled }}
      - name: "logstash"
        image: {{ .logStashImage | quote }}
        args: [ "-f", "/logs/logstash.conf" ]
        volumeMounts:
        - name: "log-dir"
          mountPath: "/logs"
        env:
        - name: "ELASTICSEARCH_HOST"
          value: {{ .elasticSearchHost | quote }}
        - name: "ELASTICSEARCH_PORT"
          value: {{ .elasticSearchPort | quote }}
      {{- end }}
      {{- if .imagePullSecrets }}
      imagePullSecrets:
      {{ .imagePullSecrets | toYaml }}
      {{- end }}
      volumes:
      - name: "weblogic-operator-cm-volume"
        configMap:
          name: "weblogic-operator-cm"
      - name: "weblogic-operator-debug-cm-volume"
        configMap:
          name: "weblogic-operator-debug-cm"
          optional: true
      - name: "weblogic-operator-secrets-volume"
        secret:
          secretName: "weblogic-operator-secrets"
      {{- if .elkIntegrationEnabled }}
      - name: "log-dir"
        emptyDir:
          medium: "Memory"
      {{- end }}
{{- end }}
