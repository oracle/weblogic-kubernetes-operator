# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.validateInputs" -}}
{{- $scope := include "utils.cloneDictionary" . | fromYaml -}}
{{- $ignore:= include "utils.startValidation" $scope -}}
{{- if include "utils.verifyBoolean" (list $scope "createSharedOperatorResources") -}}
{{-   if $scope.createSharedOperatorResources }}
{{-     $ignore := include "utils.verifyBoolean" (list $scope "elkIntegrationEnabled") -}}
{{-   end }}
{{- end }}
{{- if include "utils.verifyBoolean" (list $scope "createOperator") -}}
{{-   if $scope.createOperator }}
{{-     $ignore := include "utils.pushValidationContext" (list $scope "Release") -}}
{{-     $ignore := include "utils.verifyResourceName" (list $scope "Namespace") -}}
{{-     $ignore := include "utils.popValidationContext" $scope -}}
{{-     $ignore := include "utils.verifyBoolean" (list $scope "elkIntegrationEnabled") -}}
{{-     $ignore := include "utils.verifyString"  (list $scope "operatorServiceAccount") -}}
{{-     $ignore := include "utils.verifyString"  (list $scope "image") -}}
{{-     $ignore := include "utils.verifyEnum"    (list $scope "imagePullPolicy" (list "Always" "IfNotPresent" "Never")) -}}
{{-     $ignore := include "utils.verifyOptionalDictionaryList" (list $scope "imagePullSecrets") -}}
{{-     $ignore := include "utils.verifyEnum"    (list $scope "javaLoggingLevel" (list "SEVERE" "WARNING" "INFO" "CONFIG" "FINE" "FINER" "FINEST")) -}}
{{-     if include "utils.verifyEnum" (list $scope "externalRestOption" (list "NONE" "SELF_SIGNED_CERT" "CUSTOM_CERT")) -}}
{{-       if eq $scope.externalRestOption "SELF_SIGNED_CERT" -}}
{{-         $ignore := include "utils.verifyInteger" (list $scope "externalRestHttpsPort") -}}
{{-         $ignore := include "utils.verifyString"  (list $scope "externalOperatorCertSans") -}}
{{/*        TBD - temporarily require the cert and key too until the operator runtime is updated to generate them */}}
{{-         $ignore := include "utils.verifyString"  (list $scope "externalOperatorCert") -}}
{{-         $ignore := include "utils.verifyString"  (list $scope "externalOperatorKey") -}}
{{-       end -}}
{{-       if eq $scope.externalRestOption "CUSTOM_CERT" -}}
{{-         $ignore := include "utils.verifyInteger" (list $scope "externalRestHttpsPort") -}}
{{-         $ignore := include "utils.verifyString"  (list $scope "externalOperatorCert") -}}
{{-         $ignore := include "utils.verifyString"  (list $scope "externalOperatorKey") -}}
{{-       end -}}
{{-     end -}}
{{-     if include "utils.verifyEnum" (list $scope "internalRestOption" (list "SELF_SIGNED_CERT" "CUSTOM_CERT")) -}}
{{-       if eq $scope.internalRestOption "SELF_SIGNED_CERT" -}}
{{/*        TBD - temporarily require the cert and key too until the operator runtime is updated to generate them */}}
{{-         $ignore := include "utils.verifyString"  (list $scope "internalOperatorCert") -}}
{{-         $ignore := include "utils.verifyString"  (list $scope "internalOperatorKey") -}}
{{-       end -}}
{{-       if eq $scope.internalRestOption "CUSTOM_CERT" -}}
{{-         $ignore := include "utils.verifyString"  (list $scope "internalOperatorCert") -}}
{{-         $ignore := include "utils.verifyString"  (list $scope "internalOperatorKey") -}}
{{-       end -}}
{{-     end -}}
{{-     if include "utils.verifyBoolean" (list $scope "remoteDebugNodePortEnabled") -}}
{{-       if $scope.remoteDebugNodePortEnabled -}}
{{-         $ignore := include "utils.verifyInteger" (list $scope "internalDebugHttpPort") -}}
{{-         $ignore := include "utils.verifyInteger" (list $scope "externalDebugHttpPort") -}}
{{-       end -}}
{{-     end -}}
{{-     $ignore := include "utils.verifyStringList" (list $scope "domainNamespaces") -}}
{{-   end -}}
{{- end -}}
{{- $ignore:= include "utils.endValidation" $scope -}}
{{- end -}}
