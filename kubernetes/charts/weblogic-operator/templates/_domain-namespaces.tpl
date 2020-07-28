# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.domainNamespaces" }}
{{- if and (not .dedicated) (not .grantPrivilegeAllNamespaces) }}
{{-   if or .onlyInitializeDomainNamespaces (eq .domainNamespaceSelectionStrategy "List") }}
{{-     $args := include "utils.cloneDictionary" . | fromYaml -}}
{{-     range $key := $args.domainNamespaces -}}
{{-       $ignore := set $args "domainNamespace" $key -}}
{{-       include "operator.operatorRoleBindingNamespace" $args -}}
{{-     end }}
{{-   end }}
{{- else }}
{{-   $args := include "utils.cloneDictionary" . | fromYaml -}}
{{-   $key := .Release.Namespace -}}
{{-   $ignore := set $args "domainNamespace" $key -}}
{{-   include "operator.operatorRoleBindingNamespace" $args -}}
{{- end }}
{{- end }}
