# Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operatorCustomResourceDefinition" }}
{{- $versions := (dict "current" "v7" "alternates" (list "v5" "v4" "v3" "v2")) }}
{{- $crdVersions := (regexFindAll "weblogic.oracle/v[0-9]+," (cat (.APIVersions | keys | join ",") ",") -1) }}
{{- $maxInstalledVersion := (dict "max" (int 0)) }}
{{- range $crdVersions }}
  {{- $someInstalledVersion := substr 16 (int (sub (len .) 1)) . }}
  {{- if not (has $someInstalledVersion $versions.alternates) }}
    {{- $ignore := set $versions "alternates" (append $versions.alternates $someInstalledVersion) }}
  {{- end }}
  {{- $ignore := set $maxInstalledVersion "max" (int (max $maxInstalledVersion.max (int (substr 1 -1 $someInstalledVersion)))) }}
{{- end }}
{{- if (ge (int (substr 1 -1 $versions.current)) $maxInstalledVersion.max) }}
---
apiVersion: apiextensions.k8s.io/v1beta1
kind: CustomResourceDefinition
metadata:
  name: domains.weblogic.oracle
  annotations:
    "helm.sh/resource-policy": keep
    "helm.sh/hook": pre-install
spec:
  group: weblogic.oracle
  names:
    kind: Domain
    plural: domains
    shortNames:
      - dom
    singular: domain
  scope: Namespaced
  subresources:
    scale:
      specReplicasPath: .spec.replicas
      statusReplicasPath: .status.replicas
  validation:
    {{- include "operator.operatorCustomResourceDefinitionValidation" . | nindent 4 }}
  version: {{ $versions.current }}
  versions:
  - name: {{ $versions.current }}
    served: true
    storage: true
{{- range $versions.alternates }}
  {{- if ne $versions.current . }}
  - name: {{ . }}
    served: true
  {{- end }}
{{- end }}
{{- end}}
{{- end}}