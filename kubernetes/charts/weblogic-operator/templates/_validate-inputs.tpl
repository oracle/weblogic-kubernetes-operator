# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.validateInputs" -}}
{{- $scope := . -}}
{{- if include "operator.verifyBooleanInput" (list $scope "setupKubernetesCluster") -}}
{{-   if $scope.setupKubernetesCluster }}
{{-     $ignore := include "operator.verifyBooleanInput" (list $scope "elkIntegrationEnabled") -}}
{{-   end }}
{{- end }}
{{- if include "operator.verifyBooleanInput" (list $scope "createOperator") -}}
{{-   if .createOperator }}
{{-     $ignore := include "operator.verifyBooleanInput" (list $scope "elkIntegrationEnabled") -}}
{{-     $ignore := include "operator.verifyBooleanInput" (list $scope "createOperatorNamespace") -}}
{{-     $ignore := include "operator.verifyStringInput"  (list $scope "operatorNamespace") -}}
{{-     $ignore := include "operator.verifyStringInput"  (list $scope "operatorServiceAccount") -}}
{{-     $ignore := include "operator.verifyStringInput"  (list $scope "operatorImage") -}}
{{-     $ignore := include "operator.verifyEnumInput"    (list $scope "operatorImagePullPolicy" (list "Always" "IfNotPresent" "Never")) -}}
{{-     $ignore := include "operator.verifyEnumInput"    (list $scope "javaLoggingLevel" (list "SEVERE" "WARNING" "INFO" "CONFIG" "FINE" "FINER" "FINEST")) -}}
{{-     $ignore := include "operator.verifyStringInput"  (list $scope "internalOperatorCert") -}}
{{-     $ignore := include "operator.verifyStringInput"  (list $scope "internalOperatorKey") -}}
{{-     if include "operator.verifyBooleanInput" (list $scope "externalRestEnabled") -}}
{{-       if $scope.externalRestEnabled -}}
{{-         $ignore := include "operator.verifyIntegerInput" (list $scope "externalRestHttpsPort") -}}
{{-         $ignore := include "operator.verifyStringInput"  (list $scope "externalOperatorCert") -}}
{{-         $ignore := include "operator.verifyStringInput"  (list $scope "externalOperatorKey") -}}
{{-       end -}}
{{-     end -}}
{{-     if include "operator.verifyBooleanInput" (list $scope "remoteDebugNodePortEnabled") -}}
{{-       if $scope.remoteDebugNodePortEnabled -}}
{{-         $ignore := include "operator.verifyIntegerInput" (list $scope "internalDebugHttpPort") -}}
{{-         $ignore := include "operator.verifyIntegerInput" (list $scope "externalDebugHttpPort") -}}
{{-       end -}}
{{-     end -}}
{{-     if include "operator.verifyObjectInput" (list $scope "domainsNamespaces") -}}
{{-       $domainsNamespaces := $scope.domainsNamespaces -}}
{{-       range $key, $element := $domainsNamespaces -}}
{{-         if include "operator.verifyObjectInput" (list $domainsNamespaces $key) -}}
{{-           $s := merge (dict) $element $scope -}}
{{-           if include "operator.verifyBooleanInput" (list $s "createDomainsNamespace") -}}
{{-             if eq $key "default" -}}
{{-               if $s.createDomainsNamespace -}}
{{-                 $errorMsg := cat "The effective createDomainsNamespace value for the 'default' domainsNamespace must be set to false." -}}
{{-                 $ignore := include "operator.recordValidationError" (list $scope $errorMsg) -}}
{{-               end -}}
{{-             end -}}
{{-           end -}}
{{-         end -}}
{{-       end -}}
{{-     end -}}
{{-   end -}}
{{- end -}}
{{- include "operator.reportValidationErrors" $scope -}}
{{- end -}}
