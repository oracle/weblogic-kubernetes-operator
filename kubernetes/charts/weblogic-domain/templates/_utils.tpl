# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{/*
Record a validation error (it will get reported later by operator reportValidationErrors)
*/}}
{{- define "domain.recordValidationError" -}}
{{- $scope := index . 0 -}}
{{- $errorMsg := index . 1 -}}
{{- if hasKey $scope "validationErrors" -}}
{{-   $newValidationErrors := cat $scope.validationErrors "\n" $errorMsg -}}
{{-   $ignore := set $scope "validationErrors" $newValidationErrors -}}
{{- else -}}
{{-   $newValidationErrors := cat "\n" $errorMsg -}}
{{-   $ignore := set $scope "validationErrors" $newValidationErrors -}}
{{- end -}}
{{- end -}}

{{/*
Verify that an input value of a specific kind has been specified.
*/}}
{{- define "domain.verifyInputKind" -}}
{{- $requiredKind := index . 0 -}}
{{- $scope := index . 1 -}}
{{- $parent := index . 2 -}}
{{- $name := index . 3 -}}
{{- if hasKey $parent $name -}}
{{-   $value := index $parent $name -}}
{{-   $actualKind := kindOf $value -}}
{{-   if eq $requiredKind $actualKind -}}
        true
{{-   else -}}
{{-     $errorMsg := cat "The" $actualKind "property" $name "must be a" $requiredKind "instead." -}}
{{-     include "domain.recordValidationError" (list $scope $errorMsg) -}}
{{-   end -}}
{{- else -}}
{{-   $errorMsg := cat "The" $requiredKind "property" $name "must be specified." -}}
{{-   include "domain.recordValidationError" (list $scope $errorMsg) -}}
{{- end -}}
{{- end -}}

{{/*
Verify that a list input value has been specified
*/}}
{{- define "domain.verifyListInput" -}}
{{- $requiredKind := index . 0 -}}
{{- $scope := index . 1 -}}
{{- $parent := index . 2 -}}
{{- $name := index . 3 -}}
{{- $args := . -}}
{{- if include "domain.verifyInputKind" (list "slice" $scope $parent $name) -}}
{{-   $status := dict -}}
{{-   if hasKey $parent $name -}}
{{-     $list := index $parent $name -}}
{{-     range $value := $list -}}
{{-       $actualKind := kindOf $value -}}
{{-       if not (eq $requiredKind $actualKind) -}}
{{-         $errorMsg := cat "The list property" $name "has a" $actualKind "element.  It must only contain" $requiredKind "elements." -}}
{{-         include "domain.recordValidationError" (list $scope $errorMsg) -}}
{{-         $ignore := set $status "error" true -}}
{{-       end -}}
{{-     end -}}
{{-   end -}}
{{-   if not (hasKey $status "error") -}}
        true
{{-   end -}}
{{- end -}}
{{- end -}}

{{/*
Verify that a string input value has been specified
*/}}
{{- define "domain.verifyStringInput" -}}
{{- $args := . -}}
{{- include "domain.verifyInputKind" (prepend $args "string") -}} 
{{- end -}}

{{/*
Verify that a boolean input value has been specified
*/}}
{{- define "domain.verifyBooleanInput" -}}
{{- include "domain.verifyInputKind" (prepend . "bool") -}} 
{{- end -}}

{{/*
Verify that an integer input value has been specified
*/}}
{{- define "domain.verifyIntegerInput" -}}
{{- include "domain.verifyInputKind" (prepend . "float64") -}} 
{{- end -}}

{{/*
Verify that a dictionary input value has been specified
*/}}
{{- define "domain.verifyDictInput" -}}
{{- include "domain.verifyInputKind" (prepend . "map") -}} 
{{- end -}}

{{/*
Verify that an enum string input value has been specified
*/}}
{{- define "domain.verifyEnumInput" -}}
{{- $scope := index . 0 -}}
{{- $parent := index . 1 -}}
{{- $name := index . 2 -}}
{{- $legalValues := index . 3 -}}
{{- if include "domain.verifyStringInput" (list $scope $parent $name) -}}
{{-   $value := index $parent $name -}}
{{-   if has $value $legalValues -}}
      true
{{-   else -}}
{{      $errorMsg := cat "The property" $name "must be one of the following values" $legalValues "instead of" $value -}}
{{-     include "domain.recordValidationError" (list $scope $errorMsg) -}}
{{-   end -}}
{{- end -}}
{{- end -}}

{{/*
Verify that a list of strings input value has been specified
*/}}
{{- define "domain.verifyStringListInput" -}}
{{- include "domain.verifyListInput" (prepend . "string") -}} 
{{- end -}}

{{/*
Verify that a list of dictionaries input value has been specified
*/}}
{{- define "domain.verifyDictListInput" -}}
{{- include "domain.verifyListInput" (prepend . "map") -}} 
{{- end -}}

{{/*
Report the validation errors that have been found then kill the helm chart install
*/}}
{{- define "domain.reportValidationErrors" -}}
{{- if .validationErrors -}}
{{-   fail .validationErrors -}}
{{- end -}}
{{- end -}}

{{/*
Merge a set of dictionaries into a single dictionary.

The scope must be a list of dictionaries, starting with the least specific
and ending with the most specific.

First it makes an empty destinaction dictionary, then iterates over the dictionaries,
overlaying their values on the destination dictionary.

If a value is null, then it removes that key from the destination dictionary.

If the value is already present in the destination dictionary, and the old and
new values are both dictionaries, it merges them into the destination.
*/}}
{{- define "domain.mergeDictionaries" -}}
{{- $dest := dict -}}
{{- range $src := . -}}
{{-   if not (empty $src) -}}
{{-     range $key, $value := $src -}}
{{-       $ignore := include "domain.mergeDictionaryValue" (list $dest $key $value) -}}
{{-     end -}}
{{-   end -}}
{{- end -}}
{{- toYaml $dest -}}
{{- end -}}

{{/*
Merge a value into a dictionary.
This is like helm's 'merge' function, except that it handles null entries too.
*/}}
{{- define "domain.mergeDictionaryValue" -}}
{{- $dest := index . 0 -}}
{{- $key := index . 1 -}}
{{- $newValue := index . 2 -}}
{{- $newType := typeOf $newValue -}}
{{- if hasKey $dest $key -}}
{{-   if eq $newType "<nil>" -}}
{{/*    # if the value already existed, and the new value is null, remove the old value */}}
{{-     $ignore := unset $dest $key -}}
{{-   else -}}
{{-     $oldValue := index $dest $key -}}
{{-     $oldKind := kindOf $oldValue -}}
{{-     $newKind := kindOf $newValue -}}
{{-     if (and (eq $oldKind "map") (eq $newKind "map")) -}}
{{/*       # if both values are maps, merge them */}}
{{-       $merged := include "domain.mergeDictionaries" (list $oldValue $newValue) | fromYaml -}}
{{-       $ignore := set $dest $key $merged -}}
{{-     else -}}
{{/*       # replace the old value with the new one */}}
{{-       $ignore := set $dest $key $newValue -}}
{{-     end -}}
{{-   end -}}
{{- else -}}
{{-   if not (eq $newType "<nil>") -}}
{{/*     #if there was no old value, and the new value isn't null, use the new value */}}
{{-     $ignore := set $dest $key $newValue -}}
{{-   end -}}
{{- end -}}
{{- end -}}

{{/*
Make a writable copy of a dictionary.
TBD - does helm provide a clone method we can use instead?
*/}}
{{- define "domain.cloneDictionary" -}}
{{- include "domain.mergeDictionaries" (list .) -}}
{{- end -}}
