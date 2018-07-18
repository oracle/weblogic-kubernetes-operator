# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.domainsNamespace" }}
{{- if (and (.createDomainsNamespace) (not (eq .domainsNamespace "default"))) }}
---
apiVersion: "v1"
kind: "Namespace"
metadata:
  labels:
    weblogic.operatorName: {{ .operatorNamespace | quote }}
    weblogic.resourceVersion: "operator-v1"
  name: {{ .domainsNamespace | quote }}
{{- end }}
{{- end }}
