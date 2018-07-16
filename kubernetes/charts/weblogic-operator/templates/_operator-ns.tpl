# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorNamespace" }}
{{- if (and (.createOperatorNamespace) (not (eq .operatorNamespace "default"))) }}
---
apiVersion: v1
kind: Namespace
metadata:
  labels:
    weblogic.resourceVersion: operator-v1
  name: {{ .operatorNamespace }}
{{- end }}
{{- end }}
