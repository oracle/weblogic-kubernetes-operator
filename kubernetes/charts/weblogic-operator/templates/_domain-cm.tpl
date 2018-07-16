# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.domainConfigMap" }}
---
apiVersion: v1
kind: ConfigMap
metadata:
  labels:
    weblogic.createdByOperator: "true"
    weblogic.operatorName: {{ .operatorNamespace }}
    weblogic.resourceVersion: domain-v1
  name: weblogic-domain-cm
  namespace: {{ .domainsNamespace }}
data:
{{ (.Files.Glob "scripts/domain/*").AsConfig | indent 2 }}
{{- end }}
