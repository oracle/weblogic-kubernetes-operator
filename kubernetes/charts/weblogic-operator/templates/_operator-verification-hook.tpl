# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorVerificationHook" }}
{{- $scope := index . 0 }}
{{- $hookType := index . 1 }}
---
apiVersion: "batch/v1"
kind: "Job"
metadata:
  name: {{ "weblogic-operator-HOOK_TYPE-hook" | replace "HOOK_TYPE" $hookType | quote }}
  namespace: {{ $scope.Release.Namespace | quote }}
  labels:
    weblogic.resourceVersion: "operator-v1"
    weblogic.operatorName: {{ $scope.Release.Namespace | quote }}
  annotations:
    "helm.sh/hook": {{ $hookType | quote }}
    "helm.sh/hook-delete-policy": "before-hook-creation"
spec:
  backoffLimit: 0
  template:
    metadata:
      labels:
        weblogic.resourceVersion: "operator-v1"
        weblogic.operatorName: {{ $scope.Release.Namespace | quote }}
    spec:
      restartPolicy: Never
      containers:
      - name: "weblogic-operator"
        command:
        - "/operator/operator-helm-verification-hook.sh"
        - {{ $hookType | quote }}
        - {{ $scope.Release.Namespace | quote }}
        - {{ $scope.serviceAccount | quote }}
        {{- range $key := $scope.domainNamespaces }}
        - {{ $key | quote }}
        {{- end }}
        image: {{ $scope.image | quote }}
        imagePullPolicy: {{ $scope.imagePullPolicy | quote }}
        {{- if $scope.imagePullSecrets }}
        imagePullSecrets:
        {{ $scope.imagePullSecrets | toYaml }}
        {{- end }}
{{- end }}
