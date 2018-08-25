# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "domain.domainPersistentVolumeClaim" }}
---
kind: PersistentVolumeClaim
apiVersion: v1
metadata:
  name: {{ .Release.Name }}-weblogic-domain-pvc
  namespace: {{ .Release.Namespace }}
  labels:
    weblogic.resourceVersion: domain-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
spec:
  storageClassName: {{ .Release.Name }}-weblogic-domain-storage-class
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: {{ .weblogicDomainStorageSize }}
{{- end }}
