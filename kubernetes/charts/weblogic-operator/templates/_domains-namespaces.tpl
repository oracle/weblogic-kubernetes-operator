# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.domainsNamespaces" }}
{{- $scope := . -}}
{{- $args := merge (dict) $scope -}}
{{- $domainsNamespaces := .domainsNamespaces -}}
{{- $len := len $domainsNamespaces -}}
{{- if eq $len 0 -}}
{{-   $ignore := set $args "domainsNamespacesList" (list "default") -}}
{{- else -}}
{{-   $ignore := set $args "domainsNamespacesList" $domainsNamespaces -}}
{{- end -}}
{{- range $key := $args.domainsNamespacesList -}}
{{-   $ignore := set $args "domainsNamespace" $key -}}
{{/*- include "operator.domainConfigMap" $args currently the GA operator runtime does this -*/}}
{{-   include "operator.operatorRoleBinding" $args -}}
{{- end }}
{{- end }}
