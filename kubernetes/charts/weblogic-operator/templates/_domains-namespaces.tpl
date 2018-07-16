# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.domainsNamespaces" }}
{{- $scope := . -}}
{{- $domainsNamespaces := merge (dict) .domainsNamespaces -}}
{{- $len := len $domainsNamespaces -}}
{{- if eq $len 0 -}}
{{-   $ignore := set $domainsNamespaces "default" (dict) -}}
{{- end -}}
{{- range $key, $element := $domainsNamespaces -}}
{{-   $args := merge (dict) $element $scope -}}
{{-   $ignore := set $args "domainsNamespace" $key -}}
{{-   include "operator.domainsNamespace" $args -}}
{{- /*   include "operator.domainConfigMap" $args currently the GA operator runtime does this */ -}}
{{-   include "operator.operatorRoleBinding" $args -}}
{{- end }}
{{- end }}
