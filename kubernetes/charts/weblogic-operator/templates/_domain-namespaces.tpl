# Copyright (c) 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.domainNamespaces" }}
{{- if .dedicated }}
{{- $args := include "utils.cloneDictionary" . | fromYaml -}}
{{- range $key := $args.domainNamespaces -}}
{{-   $ignore := set $args "domainNamespace" $key -}}
{{-   include "operator.operatorRoleBindingNamespace" $args -}}
{{- end }}
{{- else }}
$key := {{ .Release.Namespace | quote }}
{{- $ignore := set $args "domainNamespace" $key -}}
{{- include "operator.operatorRoleBindingNamespace" $args -}}
{{- end }}
{{- end }}
