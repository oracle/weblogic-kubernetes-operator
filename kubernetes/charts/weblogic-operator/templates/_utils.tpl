# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{/*
Start validation
*/}}
{{- define "utils.startValidation" -}}
{{- $scope := . -}}
{{- $context := dict "scope" $scope "path" list -}}
{{- $stack := list $context -}}
{{- $ignore := set $scope "validationContextStack" $stack -}}
{{- $ignore := include "utils.setCurrentValidationContext" $scope -}}
{{- end -}}

{{/*
End validation
If there were any validation errors, report them and kill the helm chart installation.
*/}}
{{- define "utils.endValidation" -}}
{{- $scope := . -}}
{{- if hasKey $scope "validationErrors" -}}
{{-   fail $scope.validationErrors -}}
{{- end -}}
{{- end -}}

{{/*
Push a new validation context
*/}}
{{- define "utils.pushValidationContext" -}}
{{- $scope := index . 0 }}
{{- $scopeName := index . 1 }}
{{- $newScope := index $scope.validationScope $scopeName -}}
{{- $newPath := append $scope.validationPath $scopeName -}}
{{- $newContext := dict "scope" $newScope "path" $newPath -}}
{{- $newStack := append $scope.validationContextStack $newContext -}}
{{- $ignore := set $scope "validationContextStack" $newStack -}}
{{- $ignore := include "utils.setCurrentValidationContext" $scope -}}
{{- end -}}

{{/*
Pop the validation context
*/}}
{{- define "utils.popValidationContext" -}}
{{- $scope := . }}
{{- $stack := $scope.validationContextStack -}}
{{- $ignore := set $scope "validationContextStack" (initial $stack) -}}
{{- $ignore := include "utils.setCurrentValidationContext" $scope -}}
{{- end -}}

{{/*
Set the current validation context from the stack
*/}}
{{- define "utils.setCurrentValidationContext" -}}
{{- $scope := . }}
{{- $context := $scope.validationContextStack | last -}}
{{- $ignore := set $scope "validationScope" (index $context "scope") -}}
{{- $ignore := set $scope "validationPath" (index $context "path") -}}
{{- end -}}

{{/*
Record a validation error (it will get reported later by utils.reportValidationErrors)
*/}}
{{- define "utils.recordValidationError" -}}
{{- $scope := index . 0 -}}
{{- $errorMsg := index . 1 -}}
{{- $path := $scope.validationPath -}}
{{- $pathStr := $path | join "." | trim -}}
{{- $scopedErrorMsg := (list "\n" $pathStr $errorMsg) | compact | join " " -}}
{{- if hasKey $scope "validationErrors" -}}
{{-   $newValidationErrors := cat $scope.validationErrors $scopedErrorMsg -}}
{{-   $ignore := set $scope "validationErrors" $newValidationErrors -}}
{{- else -}}
{{-   $newValidationErrors := $scopedErrorMsg -}}
{{-   $ignore := set $scope "validationErrors" $newValidationErrors -}}
{{- end -}}
{{- end -}}

{{/*
Returns whether any errors have been reported
*/}}
{{- define "utils.haveValidationErrors" -}}
{{- if hasKey . "validationErrors" -}}
      true
{{- end -}}
{{- end -}}

{{/*
Determine whether a dictionary has a non-null value for a key
*/}}
{{- define "utils.dictionaryHasNonNullValue" -}}
{{- $dict := index . 0 -}}
{{- $name := index . 1 -}}
{{- if and (hasKey $dict $name) (not ( eq (typeOf (index $dict $name)) "<nil>" )) -}}
      true
{{- end -}}
{{- end -}}

{{/*
Verify that a value of a specific kind has been specified.
*/}}
{{- define "utils.verifyValue" -}}
{{- $requiredKind := index . 0 -}}
{{- $scope := index . 1 -}}
{{- $name := index . 2 -}}
{{- $isRequired := index . 3 -}}
{{- if $scope.trace -}}
{{-   $errorMsg := cat "TRACE" $name $requiredKind $isRequired -}}
{{-   $ignore := include "utils.recordValidationError" (list $scope $errorMsg) -}}
{{- end -}}
{{- $parent := $scope.validationScope -}}
{{- if include "utils.dictionaryHasNonNullValue" (list $parent $name) -}}
{{-   $value := index $parent $name -}}
{{-   $actualKind := kindOf $value -}}
{{-   if eq $requiredKind $actualKind -}}
        true
{{-   else -}}
{{-     $errorMsg := cat $name "must be a" $requiredKind ":" $actualKind -}}
{{-     include "utils.recordValidationError" (list $scope $errorMsg) -}}
{{-   end -}}
{{- else -}}
{{-   if $isRequired -}}
{{-     $errorMsg := cat $requiredKind $name "must be specified" -}}
{{-     include "utils.recordValidationError" (list $scope $errorMsg) -}}
{{-   else -}}
        true
{{-   end -}}
{{- end -}}
{{- end -}}

{{/*
Verify that a list value has been specified
*/}}
{{- define "utils.verifyListValue" -}}
{{- $requiredKind := index . 0 -}}
{{- $scope := index . 1 -}}
{{- $name := index . 2 -}}
{{- $isRequired := index . 3 -}}
{{- $parent := $scope.validationScope -}}
{{- $args := . -}}
{{- if include "utils.verifyValue" (list "slice" $scope $name $isRequired) -}}
{{-   $status := dict -}}
{{-   if hasKey $parent $name -}}
{{-     $list := index $parent $name -}}
{{-     range $value := $list -}}
{{-       $actualKind := kindOf $value -}}
{{-       if not (eq $requiredKind $actualKind) -}}
{{-         $errorMsg := cat $name "must only contain" $requiredKind "elements:" $actualKind -}}
{{-         include "utils.recordValidationError" (list $scope $errorMsg) -}}
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
Verify a string value
*/}}
{{- define "utils.baseVerifyString" -}}
{{- include "utils.verifyValue" (prepend . "string") -}} 
{{- end -}}

{{/*
Verify a required string value
*/}}
{{- define "utils.verifyString" -}}
{{- include "utils.baseVerifyString" (append . true) -}} 
{{- end -}}

{{/*
Verify an optional string value
*/}}
{{- define "utils.verifyOptionalString" -}}
{{- include "utils.baseVerifyString" (append . false) -}} 
{{- end -}}

{{/*
Verify a boolean value
*/}}
{{- define "utils.baseVerifyBoolean" -}}
{{- include "utils.verifyValue" (prepend . "bool") -}} 
{{- end -}}

{{/*
Verify a required boolean value
*/}}
{{- define "utils.verifyBoolean" -}}
{{- include "utils.baseVerifyBoolean" (append . true) -}} 
{{- end -}}

{{/*
Verify an optional boolean value
*/}}
{{- define "utils.verifyOptionalBoolean" -}}
{{- include "utils.baseVerifyBoolean" (append . false) -}} 
{{- end -}}

{{/*
Verify an integer value
*/}}
{{- define "utils.baseVerifyInteger" -}}
{{- include "utils.verifyValue" (prepend . "float64") -}} 
{{- end -}}

{{/*
Verify a required integer value
*/}}
{{- define "utils.verifyInteger" -}}
{{- include "utils.baseVerifyInteger" (append . true) -}} 
{{- end -}}

{{/*
Verify an optional required integer value
*/}}
{{- define "utils.verifyOptionalInteger" -}}
{{- include "utils.baseVerifyInteger" (append . false) -}} 
{{- end -}}

{{/*
Verify a dictionary value
*/}}
{{- define "utils.baseVerifyDictionary" -}}
{{- include "utils.verifyValue" (prepend . "map") -}} 
{{- end -}}

{{/*
Verify a required dictionary value
*/}}
{{- define "utils.verifyDictionary" -}}
{{- include "utils.baseVerifyDictionary" (append . true) -}} 
{{- end -}}

{{/*
Verify an optional dictionary value
*/}}
{{- define "utils.verifyOptionalDictionary" -}}
{{- include "utils.baseVerifyDictionary" (append . false) -}} 
{{- end -}}

{{/*
Verify a enum string value
*/}}
{{- define "utils.baseVerifyEnum" -}}
{{- $scope := index . 0 -}}
{{- $name := index . 1 -}}
{{- $legalValues := index . 2 -}}
{{- $isRequired := index . 3 -}}
{{- if include "utils.baseVerifyString" (list $scope $name $isRequired) -}}
{{-   $parent := $scope.validationScope -}}
{{-   if include "utils.dictionaryHasNonNullValue" (list $parent $name) -}}
{{-     $value := index $parent $name -}}
{{-     if has $value $legalValues -}}
          true
{{-     else -}}
{{        $errorMsg := cat $name "must be one of the following values" $legalValues ":" $value -}}
{{-       include "utils.recordValidationError" (list $scope $errorMsg) -}}
{{-     end -}}
{{-   end -}}
{{- end -}}
{{- end -}}

{{/*
Verify a required enum string value
*/}}
{{- define "utils.verifyEnum" -}}
{{- include "utils.baseVerifyEnum" (append . true) -}} 
{{- end -}}

{{/*
Verify an optional enum string value
*/}}
{{- define "utils.verifyOptionalEnum" -}}
{{- include "utils.baseVerifyEnum" (append . false) -}} 
{{- end -}}

{{/*
Verify a kubernetes resource name string value
*/}}
{{- define "utils.baseVerifyResourceName" -}}
{{/* https://kubernetes.io/docs/concepts/overview/working-with-objects/names */}}
{{/*   names: only lower case, numbers, dot, dash, max 253 */}}
{{/* https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/#syntax-and-character-set  */}}
{{/*   labels/selectors - upper & lower case, numbers, dot, dash, underscore, max 63  */}}
{{- $scope := index . 0 -}}
{{- $name := index . 1 -}}
{{- $max := index . 2 -}}
{{- $isRequired := index . 3 -}}
{{- if include "utils.baseVerifyString" (list $scope $name $isRequired) -}}
{{-   $parent := $scope.validationScope -}}
{{-   if include "utils.dictionaryHasNonNullValue" (list $parent $name) -}}
{{-     $value := index $parent $name -}}
{{-     $len := len $value -}}
{{-     if and (le $len $max) (regexMatch "^[a-z0-9.-]+$" $value) -}}
          true
{{-     else -}}
{{-       $errorMsg := cat $name "must only contain lower case letters, numbers, dashes and dots, and must not contain more than" $max "characters: " $value -}}
{{-       include "utils.recordValidationError" (list $scope $errorMsg) -}}
{{-     end -}}
{{-   end -}}
{{- else -}}
{{- end -}}
{{- end -}}

{{/*
Verify a required kubernetes resource name string value
*/}}
{{- define "utils.verifyResourceName" -}}
{{- include "utils.baseVerifyResourceName" (append . true)  -}}
{{- end -}}

{{/*
Verify an optional kubernetes resource name string value
*/}}
{{- define "utils.verifyOptionalResourceName" -}}
{{- include "utils.baseVerifyResourceName" (append . false) -}}
{{- end -}}

{{/*
Verify external service name suffix string value
*/}}
{{- define "utils.verifyExternalServiceNameSuffix" -}}
{{- include "utils.baseVerifyResourceName" (append . false) -}}
{{- end -}}

{{/*
Verify introspector job name suffix string value
*/}}
{{- define "utils.verifyIntrospectorJobNameSuffix" -}}
{{- include "utils.baseVerifyResourceName" (append . false) -}}
{{- end -}}

{{/*
Verify a list of strings value
*/}}
{{- define "utils.baseVerifyStringList" -}}
{{- include "utils.verifyListValue" (prepend . "string") -}} 
{{- end -}}

{{/*
Verify a required list of strings value
*/}}
{{- define "utils.verifyStringList" -}}
{{- include "utils.baseVerifyStringList" (append . true) -}} 
{{- end -}}

{{/*
Verify an optional list of strings value
*/}}
{{- define "utils.verifyOptionalStringList" -}}
{{- include "utils.baseVerifyStringList" (append . false) -}} 
{{- end -}}

{{/*
Verify a list of dictionaries value
*/}}
{{- define "utils.baseVerifyDictionaryList" -}}
{{- include "utils.verifyListValue" (prepend . "map") -}} 
{{- end -}}

{{/*
Verify a required list of dictionaries value
*/}}
{{- define "utils.verifyDictionaryList" -}}
{{- include "utils.baseVerifyDictionaryList" (append . true) -}} 
{{- end -}}

{{/*
Verify an optional list of dictionaries value
*/}}
{{- define "utils.verifyOptionalDictionaryList" -}}
{{- include "utils.baseVerifyDictionaryList" (append . false) -}} 
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
{{- define "utils.mergeDictionaries" -}}
{{- $dest := dict -}}
{{- range $src := . -}}
{{-   if not (empty $src) -}}
{{-     range $key, $value := $src -}}
{{-       $ignore := include "utils.mergeDictionaryValue" (list $dest $key $value) -}}
{{-     end -}}
{{-   end -}}
{{- end -}}
{{- toYaml $dest -}}
{{- end -}}

{{/*
Merge a value into a dictionary.
This is like helm's 'merge' function, except that it handles null entries too.
*/}}
{{- define "utils.mergeDictionaryValue" -}}
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
{{-       $merged := include "utils.mergeDictionaries" (list $oldValue $newValue) | fromYaml -}}
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
{{- define "utils.cloneDictionary" -}}
{{- include "utils.mergeDictionaries" (list .) -}}
{{- end -}}

{{/*
Verify that a list of values (exclude) can not be defined if another value (key) is already defined 
*/}}
{{- define "utils.mutexValue" -}}
{{- $scope := index . 0 -}}
{{- $key := index . 1 -}}
{{- $exclude := index . 2 -}}
{{- $type := index . 3 -}}
{{- $parent := $scope.validationScope -}}
{{- $args := . -}}
{{- $status := dict -}}
{{- if hasKey $parent $key -}}
{{-   range $value := $exclude -}}
{{-     if hasKey $parent $value -}}
{{-       $errorMsg := cat $value "can not be present when" $key "is defined"  " " -}}
{{-       include "utils.recordValidationError" (list $scope $errorMsg) -}}
{{-       $ignore := set $status "error" true -}}
{{-     end -}}
{{-   end -}}
{{- end -}}
{{- if not (hasKey $status "error") -}}
      true
{{- end -}}
{{- end -}}

{{/*
Verify that a list of strings can not be defined if another string is already defined 
*/}}
{{- define "utils.mutexString" -}}
{{- include "utils.mutexValue" (append . "string") -}}
{{- end -}}

{{/*
Verify that a Kubernetes resource exists in a given namespace
*/}}
{{- define "utils.verifyK8SResource" -}}
{{- $scope := index . 0 -}}
{{- $name := index . 1 -}}
{{- $type := index . 2 -}}
{{- $namespace := index . 3 -}}
{{- $foundNS := (lookup "v1" "Namespace" "" $namespace) }}
{{- if $foundNS }}
{{-   $foundResource := (lookup "v1" $type $namespace $name) }}
{{-   if not $foundResource }}
{{-     $errorMsg := cat $type $name " not found in namespace " $namespace -}}
{{-     include "utils.recordValidationError" (list $scope $errorMsg) -}}
{{-   end -}}
{{- end -}}
{{- end -}}
