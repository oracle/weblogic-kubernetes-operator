# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "domain.domainPersistentVolume" }}
---
apiVersion: v1
kind: PersistentVolume
metadata:
  name: {{ .Release.Name }}-weblogic-domain-pv
  labels:
    weblogic.resourceVersion: domain-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
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
