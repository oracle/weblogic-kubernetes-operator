# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.validateInputs" -}}
{{- $scope := . -}}
{{- if include "operator.verifyBooleanInput" (list $scope $scope "setupKubernetesCluster") -}}
{{-   if $scope.setupKubernetesCluster }}
{{-     $ignore := include "operator.verifyBooleanInput" (list $scope $scope "elkIntegrationEnabled") -}}
{{-   end }}
{{- end }}
{{- if include "operator.verifyBooleanInput" (list $scope $scope "createOperator") -}}
{{-   if .createOperator }}
{{-     $ignore := include "operator.verifyBooleanInput" (list $scope $scope "elkIntegrationEnabled") -}}
{{-     $ignore := include "operator.verifyStringInput"  (list $scope $scope "operatorNamespace") -}}
{{-     $ignore := include "operator.verifyStringInput"  (list $scope $scope "operatorServiceAccount") -}}
{{-     $ignore := include "operator.verifyStringInput"  (list $scope $scope "operatorImage") -}}
{{-     $ignore := include "operator.verifyEnumInput"    (list $scope $scope "operatorImagePullPolicy" (list "Always" "IfNotPresent" "Never")) -}}
{{-     $ignore := include "operator.verifyEnumInput"    (list $scope $scope "javaLoggingLevel" (list "SEVERE" "WARNING" "INFO" "CONFIG" "FINE" "FINER" "FINEST")) -}}
{{-     $ignore := include "operator.verifyStringInput"  (list $scope $scope "internalOperatorCert") -}}
{{-     $ignore := include "operator.verifyStringInput"  (list $scope $scope "internalOperatorKey") -}}
{{-     if include "operator.verifyEnumInput" (list $scope $scope "externalRestOption" (list "NONE" "SELF_SIGNED_CERT" "CUSTOM_CERT")) -}}
{{-       if eq $scope.externalRestOption "SELF_SIGNED_CERT" -}}
{{-         $ignore := include "operator.verifyIntegerInput" (list $scope $scope "externalRestHttpsPort") -}}
{{-         $ignore := include "operator.verifyStringInput"  (list $scope $scope "externalOperatorCertSans") -}}
{{/*        TBD - temporarily require the cert and key too until the operator runtime is updated to generate them */}}
{{-         $ignore := include "operator.verifyStringInput"  (list $scope $scope "externalOperatorCert") -}}
{{-         $ignore := include "operator.verifyStringInput"  (list $scope $scope "externalOperatorKey") -}}
{{-       end -}}
{{-       if eq $scope.externalRestOption "CUSTOM_CERT" -}}
{{-         $ignore := include "operator.verifyIntegerInput" (list $scope $scope "externalRestHttpsPort") -}}
{{-         $ignore := include "operator.verifyStringInput"  (list $scope $scope "externalOperatorCert") -}}
{{-         $ignore := include "operator.verifyStringInput"  (list $scope $scope "externalOperatorKey") -}}
{{-       end -}}
{{-     end -}}
{{-     if include "operator.verifyEnumInput" (list $scope $scope "internalRestOption" (list "SELF_SIGNED_CERT" "CUSTOM_CERT")) -}}
{{-       if eq $scope.internalRestOption "SELF_SIGNED_CERT" -}}
{{/*        TBD - temporarily require the cert and key too until the operator runtime is updated to generate them */}}
{{-         $ignore := include "operator.verifyStringInput"  (list $scope $scope "internalOperatorCert") -}}
{{-         $ignore := include "operator.verifyStringInput"  (list $scope $scope "internalOperatorKey") -}}
{{-       end -}}
{{-       if eq $scope.internalRestOption "CUSTOM_CERT" -}}
{{-         $ignore := include "operator.verifyStringInput"  (list $scope $scope "internalOperatorCert") -}}
{{-         $ignore := include "operator.verifyStringInput"  (list $scope $scope "internalOperatorKey") -}}
{{-       end -}}
{{-     end -}}
{{-     if include "operator.verifyBooleanInput" (list $scope $scope "remoteDebugNodePortEnabled") -}}
{{-       if $scope.remoteDebugNodePortEnabled -}}
{{-         $ignore := include "operator.verifyIntegerInput" (list $scope $scope "internalDebugHttpPort") -}}
{{-         $ignore := include "operator.verifyIntegerInput" (list $scope $scope "externalDebugHttpPort") -}}
{{-       end -}}
{{-     end -}}
{{-     if include "operator.verifyObjectInput" (list $scope $scope "domainsNamespaces") -}}
{{-       $domainsNamespaces := $scope.domainsNamespaces -}}
{{-       range $key, $element := $domainsNamespaces -}}
{{-         if include "operator.verifyObjectInput" (list $scope $domainsNamespaces $key) -}}
{{-           $s := merge (dict) $element $scope -}}
{{-           if include "operator.verifyBooleanInput" (list $scope $s "createDomainsNamespace") -}}
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
