# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "domain.createDomainPersistentVolumeClaim" }}
---
kind: PersistentVolumeClaim
apiVersion: v1
metadata:
  name: {{ .Release.Name }}-weblogic-domain-job-pvc
  namespace: {{ .Release.Namespace }}
  labels:
    weblogic.resourceVersion: domain-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
  annotations:
    "helm.sh/hook": pre-install
    "helm.sh/hook-weight": "-5"
    "helm.sh/hook-delete-policy": hook-succeeded,hook-failed
spec:
  storageClassName: {{ .Release.Name }}-weblogic-domain-storage-class
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: {{ .weblogicDomainStorageSize }}
{{- end }}
