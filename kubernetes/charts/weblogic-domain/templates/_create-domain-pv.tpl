# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "domain.createDomainPersistentVolume" }}
---
apiVersion: v1
kind: PersistentVolume
metadata:
  name: {{ .Release.Name }}-weblogic-domain-job-pv
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
  capacity:
    storage: {{ .weblogicDomainStorageSize }}
  accessModes:
    - ReadWriteMany
  # Valid values are Retain, Delete or Recycle
  persistentVolumeReclaimPolicy: {{ .weblogicDomainStorageReclaimPolicy }}
  {{- if ne .weblogicDomainStorageType "NFS" }}
  hostPath:
  {{- end }}
  {{- if eq .weblogicDomainStorageType "NFS" }}
  nfs:
    server: {{ .weblogicDomainStorageNFSServer }}
  {{- end }}
    path: "{{ .weblogicDomainStoragePath }}"
{{- end }}
