# Copyright (c) 2018, 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorDeployment" }}

---
{{- if not .webhookOnly }}
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
      annotations:
        prometheus.io/port: '8083'
        prometheus.io/scrape: 'true'
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
      {{- if (ne ( .kubernetesPlatform | default "Generic" ) "OpenShift") }}
      securityContext:
        seccompProfile:
          type: RuntimeDefault
      {{- end }}
      {{- with .nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      initContainers:
      - name:  "copy-container"
        image: {{ .image | quote }}
        imagePullPolicy: "IfNotPresent"
        command: ["/bin/sh", "-c", "cp /deployment/* /deployment_copy && cp /probes/* /probes_copy"]
        volumeMounts:
        - name: "deployment-volume"
          mountPath: "/deployment_copy"
        - name: "probes-volume"
          mountPath: "/probes_copy"
      containers:
      - name: "weblogic-operator"
        image: {{ .image | quote }}
        imagePullPolicy: {{ .imagePullPolicy | quote }}
        command: ["/deployment/operator.sh"]
        lifecycle:
          preStop:
            exec:
              command: ["/deployment/stop.sh"]
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
        {{- if .kubernetesPlatform }}
        - name: "KUBERNETES_PLATFORM"
          value: {{ .kubernetesPlatform | quote }}
        {{- end }}
        {{- if and (hasKey . "enableRest") .enableRest }}
        - name: "ENABLE_REST_ENDPOINT"
          value: "true"
        {{- end }}
        - name: "JAVA_LOGGING_LEVEL"
          value: {{ .javaLoggingLevel | quote }}
        - name: "JAVA_LOGGING_MAXSIZE"
          value: {{ int64 .javaLoggingFileSizeLimit | default 20000000 | quote }}
        - name: "JAVA_LOGGING_COUNT"
          value: {{ .javaLoggingFileCount | default 10 | quote }}
        - name: "JVM_OPTIONS"
          value: {{ .jvmOptions | default "-XshowSettings:vm -XX:MaxRAMPercentage=70" | quote }}
        {{- if and .operatorLogDir .operatorLogMount }}
           {{- if not (hasPrefix (toString .operatorLogMount) (toString .operatorLogDir)) }}
            {{- fail (printf "Error: Invalid Configuration: operatorLogDir %s must start with operatorlogMount %s" .operatorLogDir .operatorLogMount) }}
          {{- end }}
        - name: "OPERATOR_LOGDIR"
          value: {{ .operatorLogDir | quote }}
        {{- end }}
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
        securityContext:
          {{- if or (hasKey . "runAsUser") (ne ( .kubernetesPlatform | default "Generic" ) "OpenShift") }}
          runAsUser: {{ .runAsUser | default 1000 }}
          {{- end }}
          runAsNonRoot: true
          readOnlyRootFilesystem: true
          privileged: false
          allowPrivilegeEscalation: false
          capabilities:
            drop: ["ALL"]
        volumeMounts:
        - name: "weblogic-operator-cm-volume"
          mountPath: "/deployment/config"
        - name: "weblogic-operator-debug-cm-volume"
          mountPath: "/deployment/debug-config"
        - name: "weblogic-operator-secrets-volume"
          mountPath: "/deployment/secrets"
          readOnly: true
        - name: "deployment-volume"
          mountPath: "/deployment"
        {{- if not .elkIntegrationEnabled }}
        - name: "log-volume"
          mountPath: "/logs"
        {{- end }}
        - name: "probes-volume"
          mountPath: "/probes"
        {{- if and .elkIntegrationEnabled .operatorLogPVC }}
            {{- fail "Error: elkIntegrationEnabled and opeatorLogPVC cannot be set at the same time."}}
        {{- else if .elkIntegrationEnabled }}
        - mountPath: "/logs"
          name: "log-dir"
          readOnly: false
        {{- else if .operatorLogPVC }}
        - mountPath: {{ .operatorLogMount | quote }}
          name: "log-dir"
          readOnly: false
        {{- end }}
        {{- if not .remoteDebugNodePortEnabled }}
        livenessProbe:
          exec:
            command: ["/probes/livenessProbe.sh"]
          initialDelaySeconds: 40
          periodSeconds: 10
          failureThreshold: 5
        readinessProbe:
          exec:
            command: ["/probes/readinessProbe.sh"]
          initialDelaySeconds: 2
          periodSeconds: 10
        {{- end }}
      {{- if .elkIntegrationEnabled }}
      - name: "logstash"
        image: {{ .logStashImage | quote }}
        volumeMounts:
        - name: "log-dir"
          mountPath: "/logs"
        - name: "logstash-pipeline-volume"
          mountPath: "/usr/share/logstash/pipeline"
        - name: "logstash-config-volume"
          mountPath: "/usr/share/logstash/config/logstash.yml"
          subPath: "logstash.yml"
        - name: "logstash-certs-secret-volume"
          mountPath: "/usr/share/logstash/config/certs"
        env:
        - name: "ELASTICSEARCH_HOST"
          value: {{ .elasticSearchHost | quote }}
        - name: "ELASTICSEARCH_PORT"
          value: {{ .elasticSearchPort | quote }}
        - name: "ELASTICSEARCH_PROTOCOL"
          value: {{ .elasticSearchProtocol | quote }}
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
      - name: "deployment-volume"
        emptyDir: {}
      {{- if not .elkIntegrationEnabled }}
      - name: "log-volume"
        emptyDir: {}
      {{- end }}
      - name: "probes-volume"
        emptyDir: {}
      {{- if .elkIntegrationEnabled }}
      - name: "log-dir"
        emptyDir:
          medium: "Memory"
      - name: "logstash-pipeline-volume"
        configMap:
          name: "weblogic-operator-logstash-cm"
          items:
          - key: logstash.conf
            path: logstash.conf
      - name: "logstash-config-volume"
        configMap:
          name: "weblogic-operator-logstash-cm"
          items:
          - key: logstash.yml
            path: logstash.yml
      - name: "logstash-certs-secret-volume"
        secret:
          secretName: "logstash-certs-secret"
          optional: true
      {{- else if .operatorLogPVC }}
      {{- if not (and .operatorLogMount .operatorLogDir) }}
        {{- fail "Must provide operatorLogMount and operatorLogDir when using operatorLogPVC" }}
      {{- end }}
      - name: "log-dir"
        persistentVolumeClaim:
         claimName: {{ .operatorLogPVC }}
      {{- end }}

{{- end }}
---
  {{ $chartVersion := .Chart.Version }}
  {{ $releaseNamespace := .Release.Namespace }}
  {{- if not .operatorOnly }}
  {{ $webhookExists := include "utils.verifyExistingWebhookDeployment" (list $chartVersion $releaseNamespace) | trim }}
  {{- if ne $webhookExists "true" }}
    # webhook does not exist or chart version is newer, create a new webhook
    apiVersion: "v1"
    kind: "ConfigMap"
    metadata:
      labels:
        weblogic.webhookName: {{ .Release.Namespace | quote }}
      name: "weblogic-webhook-cm"
      namespace: {{ .Release.Namespace | quote }}
    data:
      serviceaccount: {{ .serviceAccount | quote }}
      {{- if .featureGates }}
      featureGates: {{ .featureGates | quote }}
      {{- end }}
      {{- if .domainNamespaceSelectionStrategy }}
      domainNamespaceSelectionStrategy: {{ .domainNamespaceSelectionStrategy | quote }}
      {{- end }}
---
    # webhook does not exist or chart version is newer, create a new webhook
    apiVersion: "apps/v1"
    kind: "Deployment"
    metadata:
      name: "weblogic-operator-webhook"
      namespace: {{ .Release.Namespace | quote }}
      labels:
        weblogic.webhookName: {{ .Release.Namespace | quote }}
        weblogic.webhookVersion: {{ .Chart.Version }}
      {{- if and (.preserveWebhook) (not .webhookOnly) }}
      annotations:
        "helm.sh/hook": pre-install
        "helm.sh/resource-policy": keep
        "helm.sh/hook-delete-policy": "before-hook-creation"
      {{- end }}
    spec:
      strategy:
        type: Recreate
      selector:
        matchLabels:
          weblogic.webhookName: {{ .Release.Namespace | quote }}
      replicas: 1
      template:
        metadata:
          annotations:
            prometheus.io/port: '8083'
            prometheus.io/scrape: 'true'
            sidecar.istio.io/inject: 'false'
          {{- range $key, $value := .annotations }}
            {{- if ne $key "sidecar.istio.io/inject" }}
            {{ $key }}: {{ $value | quote }}
            {{- end }}          
          {{- end }}
          labels:
            weblogic.webhookName: {{ .Release.Namespace | quote }}
            app: "weblogic-operator-webhook"
          {{- range $key, $value := .labels }}
            {{ $key }}: {{ $value | quote }}
          {{- end }}
        spec:
          serviceAccountName: {{ .serviceAccount | quote }}
          {{- if (ne ( .kubernetesPlatform | default "Generic" ) "OpenShift") }}
          securityContext:
            seccompProfile:
              type: RuntimeDefault
          {{- end }}
          {{- with .nodeSelector }}
          nodeSelector:
            {{- toYaml . | nindent 12 }}
          {{- end }}
          {{- with .affinity }}
          affinity:
            {{- toYaml . | nindent 12 }}
          {{- end }}
          {{- with .tolerations }}
          tolerations:
            {{- toYaml . | nindent 12 }}
          {{- end }}
          initContainers:
          - name:  "copy-container"
            image: {{ .image | quote }}
            imagePullPolicy: "IfNotPresent"
            command: ["/bin/sh", "-c", "cp /deployment/* /deployment_copy && cp /probes/* /probes_copy"]
            volumeMounts:
            - name: "deployment-volume"
              mountPath: "/deployment_copy"
            - name: "probes-volume"
              mountPath: "/probes_copy"
          containers:
          - name: "weblogic-operator-webhook"
            image: {{ .image | quote }}
            imagePullPolicy: {{ .imagePullPolicy | quote }}
            command: ["/deployment/webhook.sh"]
            lifecycle:
              preStop:
                exec:
                  command: ["/deployment/stop.sh"]
            env:
            - name: "WEBHOOK_NAMESPACE"
              valueFrom:
                fieldRef:
                  fieldPath: "metadata.namespace"
            - name: "WEBHOOK_POD_NAME"
              valueFrom:
                fieldRef:
                  fieldPath: "metadata.name"
            - name: "WEBHOOK_POD_UID"
              valueFrom:
                fieldRef:
                  fieldPath: "metadata.uid"
            - name: "JAVA_LOGGING_LEVEL"
              value: {{ .javaLoggingLevel | quote }}
            - name: "JAVA_LOGGING_MAXSIZE"
              value: {{ int64 .javaLoggingFileSizeLimit | default 20000000 | quote }}
            - name: "JAVA_LOGGING_COUNT"
              value: {{ .javaLoggingFileCount | default 10 | quote }}
            {{- if and .operatorLogDir .operatorLogMount }}
              {{- if not (hasPrefix (toString .operatorLogMount) (toString .operatorLogDir)) }}
                {{- fail (printf "Error: Invalid Configuration: operatorLogDir %s must start with operatorlogMount %s" .operatorLogDir .operatorLogMount) }}
              {{- end }}
            - name: "OPERATOR_LOGDIR"
              value: {{ .operatorLogDir | quote }}
            {{- end }}
            {{- if .remoteDebugNodePortEnabled }}
            - name: "REMOTE_DEBUG_PORT"
              value: {{ .webhookDebugHttpPort | quote }}
            - name: "DEBUG_SUSPEND"
              {{- if .suspendOnDebugStartup }}
              value: "y"
              {{- else }}
              value: "n"
              {{- end }}
            {{- end }}
            resources:
              requests:
                cpu: {{ .cpuRequests | default "100m" }}
                memory: {{ .memoryRequests | default "100Mi" }}
              limits:
                {{- if .cpuLimits}}
                cpu: {{ .cpuLimits }}
                {{- end }}
                {{- if .memoryLimits}}
                memory: {{ .memoryLimits }}
                {{- end }}
            securityContext:
              {{- if or (hasKey . "runAsUser") (ne ( .kubernetesPlatform | default "Generic" ) "OpenShift") }}
              runAsUser: {{ .runAsUser | default 1000 }}
              {{- end }}
              runAsNonRoot: true
              privileged: false
              allowPrivilegeEscalation: false
              readOnlyRootFilesystem: true
              capabilities:
                drop: ["ALL"]
            volumeMounts:
            - name: "weblogic-webhook-cm-volume"
              mountPath: "/deployment/config"
            - name: "weblogic-webhook-secrets-volume"
              mountPath: "/deployment/secrets"
              readOnly: true
            - name: "deployment-volume"
              mountPath: "/deployment"
            {{- if not .elkIntegrationEnabled }}
            - name: "log-volume"
              mountPath: "/logs"
            {{- end }}
            - name: "probes-volume"
              mountPath: "/probes"
            {{- if and .elkIntegrationEnabled .operatorLogPVC }}
                {{- fail "Error: elkIntegrationEnabled and opeatorLogPVC cannot be set at the same time."}}
            {{- else if .elkIntegrationEnabled }}
            - mountPath: "/logs"
              name: "log-dir"
              readOnly: false
            {{- else if .operatorLogPVC }}
            - mountPath: {{ .operatorLogMount | quote }}
              name: "log-dir"
              readOnly: false
            {{- end }}
            {{- if not .remoteDebugNodePortEnabled }}
            livenessProbe:
              exec:
                command: ["/probes/livenessProbe.sh"]
              initialDelaySeconds: 40
              periodSeconds: 5
            readinessProbe:
              exec:
                command: ["/probes/readinessProbe.sh"]
              initialDelaySeconds: 2
              periodSeconds: 10
            {{- end }}
          {{- if .elkIntegrationEnabled }}
          - name: "logstash"
            image: {{ .logStashImage | quote }}
            volumeMounts:
            - name: "log-dir"
              mountPath: "/logs"
            - name: "logstash-pipeline-volume"
              mountPath: "/usr/share/logstash/pipeline"
            - name: "logstash-config-volume"
              mountPath: "/usr/share/logstash/config/logstash.yml"
              subPath: "logstash.yml"
            - name: "logstash-certs-secret-volume"
              mountPath: "/usr/share/logstash/config/certs"
            env:
            - name: "ELASTICSEARCH_HOST"
              value: {{ .elasticSearchHost | quote }}
            - name: "ELASTICSEARCH_PORT"
              value: {{ .elasticSearchPort | quote }}
            - name: "ELASTICSEARCH_PROTOCOL"
              value: {{ .elasticSearchProtocol | quote }}
          {{- end }}
          {{- if .imagePullSecrets }}
          imagePullSecrets:
          {{ .imagePullSecrets | toYaml }}
          {{- end }}
          volumes:
          - name: "weblogic-webhook-cm-volume"
            configMap:
              name: "weblogic-webhook-cm"
          - name: "weblogic-webhook-secrets-volume"
            secret:
              secretName: "weblogic-webhook-secrets"
          - name: "deployment-volume"
            emptyDir: {}
          {{- if not .elkIntegrationEnabled }}
          - name: "log-volume"
            emptyDir: {}
          {{- end }}
          - name: "probes-volume"
            emptyDir: {}
          {{- if .elkIntegrationEnabled }}
          - name: "log-dir"
            emptyDir:
              medium: "Memory"
          - name: "logstash-pipeline-volume"
            configMap:
              name: "weblogic-operator-logstash-cm"
              items:
              - key: logstash.conf
                path: logstash.conf
          - name: "logstash-config-volume"
            configMap:
              name: "weblogic-operator-logstash-cm"
              items:
              - key: logstash.yml
                path: logstash.yml
          - name: "logstash-certs-secret-volume"
            secret:
              secretName: "logstash-certs-secret"
              optional: true
          {{- else if .operatorLogPVC }}
          {{- if not (and .operatorLogMount .operatorLogDir) }}
            {{- fail "Must provide operatorLogMount and operatorLogDir when using operatorLogPVC" }}
          {{- end }}
          - name: "log-dir"
            persistentVolumeClaim:
             claimName: {{ .operatorLogPVC }}
          {{- end }}
  {{- end }}
  {{- end }}
{{- end }}
