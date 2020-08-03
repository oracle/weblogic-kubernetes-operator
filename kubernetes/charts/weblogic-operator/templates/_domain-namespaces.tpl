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
      Split terms on commas not contained in parentheses. Unfortunately, the regular expression
      support included with Helm tempalates does not include lookarounds.
*/ -}}
{{-   $working := dict "rejected" (list) "terms" (list $args.domainNamespaceLabelSelector) }}
{{-   if contains "," $args.domainNamespaceLabelSelector }}
{{-     $cs := regexSplit "," $args.domainNamespaceLabelSelector -1 }}
{{-     $ignore := set $working "st" (list) }}
{{-     $ignore := set $working "item" "" }}
{{-     range $c := $cs }}
{{-       if contains "(" $c }}
{{-         $ignore := set $working "item" (print $working.item $c) }}
{{-       else if and (not (eq $working.item "")) (contains ")" $c) }}
{{-         $ignore := set $working "st" (append $working.st (print $working.item $c)) }}
{{-         $ignore := set $working "item" "" }}
{{-       else }}
{{-         $ignore := set $working "st" (append $working.st $c) }}
{{-       end }}
{{-     end }}
{{-     $ignore := set $working "terms" $working.st }}
{{-   end }}
{{-   $namespaces := (lookup "v1" "Namespace" "" "").items }}
{{-   range $t := $working.terms }}
{{-     $term := trim $t }}
{{-     range $index, $namespace := $namespaces }}
{{- /*
        Label selector patterns
        Equality-based: =, ==, !=
        Set-based: x in (a, b), x notin (a, b)
        Existence: x, !x
*/ -}}
{{-       if not $namespace.metadata.labels }}
{{-         $ignore := set $namespace.metadata "labels" (dict) }}
{{-       end }}
{{-       if hasPrefix "!" $term }}
{{-         if hasKey $namespace.metadata.labels (trimPrefix "!" $term) }}
{{-           $ignore := set $working "rejected" (append $working.rejected $namespace.metadata.name) }}
{{-         end }}
{{-       else if contains "!=" $term }}
{{-         $split := regexSplit "!=" $term 2 }}
{{-         $key := nospace (first $split) }}
{{-         if hasKey $namespace.metadata.labels $key }}
{{-           if eq (last $split | nospace) (get $namespace.metadata.labels $key) }}
{{-             $ignore := set $working "rejected" (append $working.rejected $namespace.metadata.name) }}
{{-           end }}
{{-         end }}
{{-       else if contains "==" $term }}
{{-         $split := regexSplit "==" $term 2 }}
{{-         $key := nospace (first $split) }}
{{-         if or (not (hasKey $namespace.metadata.labels $key)) (not (eq (last $split | nospace) (get $namespace.metadata.labels $key))) }}
{{-           $ignore := set $working "rejected" (append $working.rejected $namespace.metadata.name) }}
{{-         end }}
{{-       else if contains "=" $term }}
{{-         $split := regexSplit "=" $term 2 }}
{{-         $key := nospace (first $split) }}
{{-         if or (not (hasKey $namespace.metadata.labels $key)) (not (eq (last $split | nospace) (get $namespace.metadata.labels $key))) }}
{{-           $ignore := set $working "rejected" (append $working.rejected $namespace.metadata.name) }}
{{-         end }}
{{-       else if contains " notin " $term }}
{{-         $key := regexFind "^.+(? notin )" $term }}
{{-         if hasKey $namespace.metadata.labels $key }}
{{-           $parenContents := regexFind "\\(([^)]+)\\)" $term }}
{{-           $values := regexSplit "," $parenContents -1 }}
{{-           range $value := $values }}
{{-             if eq ($value | nospace) (get $namespace.metadata.labels $key) }}
{{-               $ignore := set $working "rejected" (append $working.rejected $namespace.metadata.name) }}
{{-             end }}
{{-           end }}
{{-         end }}
{{-       else if contains " in " $term }}
{{-         $key := regexFind "^.+(? in )" $term }}
{{-         if not (hasKey $namespace.metadata.labels $key) }}
{{-           $ignore := set $working "rejected" (append $working.rejected $namespace.metadata.name) }}
{{-         else }}
{{-           $parenContents := regexFind "\\(([^)]+)\\)" $term }}
{{-           $values := regexSplit "," $parenContents -1 }}
{{-           $ignore := set $working "found" false }}
{{-           range $value := $values }}
{{-             if eq ($value | nospace) (get $namespace.metadata.labels $key) }}
{{-               $ignore := set $working "found" true }}
{{-             end }}
{{-           end }}
{{-           if not $working.found }}
{{-             $ignore := set $working "rejected" (append $working.rejected $namespace.metadata.name) }}
{{-           end }}
{{-         end }}
{{-       else }}
{{-         if not (hasKey $namespace.metadata.labels $term) }}
{{-           $ignore := set $working "rejected" (append $working.rejected $namespace.metadata.name) }}
{{-         end }}
{{-       end }}
{{-     end }}
{{-   end }}
{{-   range $index, $namespace := $namespaces }}
{{-     $key := $namespace.metadata.name -}}
{{-     if not (has $key $working.rejected) }}
{{-       $ignore := set $args "domainNamespace" $key -}}
{{-       include "operator.operatorRoleBindingNamespace" $args -}}
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
