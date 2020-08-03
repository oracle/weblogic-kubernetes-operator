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
{{- /*
      This regular expression matches any comma not between parentheses
*/ -}}
{{-   $commaMatch := ",(?![^(]*\\)" }}
{{-   $terms := regexSplit $commaMatch $args.domainNamespaceLabelSelector -1 }}
{{-   $namespaces := (lookup "v1" "Namespace" "" "").items }}
{{-   range $t := $terms }}
{{-     $term := trim $t }}
{{-     $local := $namespaces }}
{{-     range $index, $namespace := $local }}
{{- /*
        Label selector patterns
        Equality-based: =, ==, !=
        Set-based: x in (a, b), x notin (a, b)
        Existence: x, !x
*/ -}}
{{-       if hasPrefix "!" $term }}
{{-         if hasKey $namespace.metadata.labels (trimPrefix "!" $term) }}
{{-           $namespaces := without $local $namespace }}
{{-         end }}
{{-       else if contains "!=" $term }}
{{-         $split := regexSplit "!=" $term 2 }}
{{-         $key := nospace (first $split) }}
{{-         if hasKey $namespace.metadata.labels $key }}
{{-           if eq (last $split | nospace) (get $namespace.metadata.labels $key) }}
{{-             $namespaces := without $local $namespace }}
{{-           end }}
{{-         end }}
{{-       else if contains "==" $term }}
{{-         $split := regexSplit "==" $term 2 }}
{{-         $key := nospace (first $split) }}
{{-         if or (not (hasKey $namespace.metadata.labels $key)) (not (eq (last $split | nospace) (get $namespace.metadata.labels $key))) }}
{{-           $namespaces := without $local $namespace }}
{{-         end }}
{{-       else if contains "=" $term }}
{{-         $split := regexSplit "=" $term 2 }}
{{-         $key := nospace (first $split) }}
{{-         if or (not (hasKey $namespace.metadata.labels $key)) (not (eq (last $split | nospace) (get $namespace.metadata.labels $key))) }}
{{-           $namespaces := without $local $namespace }}
{{-         end }}
{{-       else if contains " notin " $term }}
{{-         $key := regexFind "^.+(? notin )" $term }}
{{-         if hasKey $namespace.metadata.labels $key }}
{{-           $parenContents := regexFind "\\(([^)]+)\\)" $term }}
{{-           $values := regexSplit "," $parenContents -1 }}
{{-           range $value := $values }}
{{-             if eq ($value | nospace) (get $namespace.metadata.labels $key) }}
{{-               $namespaces := without $local $namespace }}
{{-             end }}
{{-           end }}
{{-         end }}
{{-       else if contains " in " $term }}
{{-         $key := regexFind "^.+(? in )" $term }}
{{-         if not (hasKey $namespace.metadata.labels $key) }}
{{-           $namespaces := without $local $namespace }}
{{-         else }}
{{-           $parenContents := regexFind "\\(([^)]+)\\)" $term }}
{{-           $values := regexSplit "," $parenContents -1 }}
{{-           $found := false }}
{{-           range $value := $values }}
{{-             if eq ($value | nospace) (get $namespace.metadata.labels $key) }}
{{-               $found := true }}
{{-             end }}
{{-           end }}
{{-           if not $found }}
{{-             $namespaces := without $local $namespace }}
{{-           end }}
{{-         end }}
{{-       else }}
{{-         if not (hasKey $namespace.metadata.labels $term) }}
{{-           $namespaces := without $local $namespace }}
{{-         end }}
{{-       end }}
{{-     end }}
{{-   end }}
{{-   range $index, $namespace := $namespaces }}
{{-     $key := $namespace.metadata.name -}}
{{-     $ignore := set $args "domainNamespace" $key -}}
{{-     include "operator.operatorRoleBindingNamespace" $args -}}
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
