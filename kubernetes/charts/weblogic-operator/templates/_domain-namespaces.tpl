# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.domainNamespaces" }}
{{- if eq .domainNamespaceSelectionStrategy "List" }}
{{-   $args := include "utils.cloneDictionary" . | fromYaml -}}
{{-   range $key := $args.domainNamespaces -}}
{{-     $ignore := set $args "domainNamespace" $key -}}
{{-     include "operator.operatorRoleBindingNamespace" $args -}}
{{-   end }}
{{- else if eq .domainNamespaceSelectionStrategy "LabelSelector" }}
{{-   $args := include "utils.cloneDictionary" . | fromYaml -}}
{{-   range $index, $namespace := (lookup "v1" "Namespace" "" "").items }}
{{-     range $lname, $lvalue := $namespace.metadata.labels }}
{{-       if or (eq $args.domainNamespaceLabelSelector (cat $lname "=" $lvalue)) (eq $args.domainNamespaceLabelSelector (cat $lname "==" $lvalue)) (eq $args.domainNamespaceLabelSelector $lname) }}
{{-         $key := $namespace.metadata.name -}}
{{-         $ignore := set $args "domainNamespace" $key -}}
{{-         include "operator.operatorRoleBindingNamespace" $args -}}
{{-       end }}
{{-     end }}
{{-   end }}
{{- else if eq .domainNamespaceSelectionStrategy "RegExp" }}
{{-   $args := include "utils.cloneDictionary" . | fromYaml -}}
{{-   range $index, $namespace := (lookup "v1" "Namespace" "" "").items }}
{{-     if regexMatch $args.domainNamespaceRegExp $namespace.metadata.name }}
{{-       $key := $namespace.metadata.name -}}
{{-       $ignore := set $args "domainNamespace" $key -}}
{{-       include "operator.operatorRoleBindingNamespace" $args -}}
{{-     end }}
{{-   end }}
{{- else if or .dedicated (eq .domainNamespaceSelectionStrategy "Dedicated") }}
{{-   $args := include "utils.cloneDictionary" . | fromYaml -}}
{{-   $key := .Release.Namespace -}}
{{-   $ignore := set $args "domainNamespace" $key -}}
{{-   include "operator.operatorRoleBindingNamespace" $args -}}
{{- end }}
{{- end }}
