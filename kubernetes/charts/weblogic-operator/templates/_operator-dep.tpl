# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorDeployment" }}
---
apiVersion: "apps/v1"
kind: "Deployment"
metadata:
  name: "weblogic-operator"
  namespace: {{ .Release.Namespace | quote }}
  labels:
    weblogic.operatorName: {{ .Release.Namespace | quote }}
spec:
  strategy:
    type: Recreate
  selector:
    matchLabels:
      weblogic.operatorName: {{ .Release.Namespace | quote }}
  replicas: 1
  template:
    metadata:
      {{- with .annotations }}
      annotations:
      {{- end }}
      {{- range $key, $value := .annotations }}
        {{ $key }}: {{ $value | quote }}
      {{- end }}
      labels:
        weblogic.operatorName: {{ .Release.Namespace | quote }}
        app: "weblogic-operator"
      {{- range $key, $value := .labels }}
        {{ $key }}: {{ $value | quote }}
      {{- end }}
    spec:
      serviceAccountName: {{ .serviceAccount | quote }}
      {{- if .runAsUser }}
      securityContext:
        runAsUser: {{ .runAsUser }}
      {{- end }}
      {{- with .nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
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
        - name: "OPERATOR_POD_NAME"
          valueFrom:
            fieldRef:
              fieldPath: "metadata.name"
        - name: "OPERATOR_POD_UID"
          valueFrom:
            fieldRef:
              fieldPath: "metadata.uid"
        - name: "OPERATOR_VERBOSE"
          value: "false"
        - name: "JAVA_LOGGING_LEVEL"
          value: {{ .javaLoggingLevel | quote }}
        - name: "KUBERNETES_PLATFORM"
          value: {{ .kubernetesPlatform | quote }}
        - name: "JAVA_LOGGING_MAXSIZE"
          value: {{ .javaLoggingFileSizeLimit | default 20000000 | quote }}
        - name: "JAVA_LOGGING_COUNT"
          value: {{ .javaLoggingFileCount | default 10 | quote }}
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
            cpu: {{ .cpuRequests | default "250m" }}
            memory: {{ .memoryRequests | default "512Mi" }}
          limits:
            {{- if .cpuLimits}}
            cpu: {{ .cpuLimits }}
            {{- end }}
            {{- if .memoryLimits}}
            memory: {{ .memoryLimits }}
            {{- end }}
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
          initialDelaySeconds: 40
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
