# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.validateInputs" -}}
{{- $scope := include "utils.cloneDictionary" . | fromYaml -}}
{{- $ignore:= include "utils.startValidation" $scope -}}
{{- $ignore := include "utils.pushValidationContext" (list $scope "Release") -}}
{{- $ignore := include "utils.verifyResourceName" (list $scope "Namespace") -}}
{{- $ignore := include "utils.popValidationContext" $scope -}}
{{- $ignore := include "utils.verifyString" (list $scope "serviceAccount") -}}
{{- $ignore := include "utils.verifyString" (list $scope "image") -}}
{{- $ignore := include "utils.verifyEnum" (list $scope "imagePullPolicy" (list "Always" "IfNotPresent" "Never")) -}}
{{- $ignore := include "utils.verifyOptionalDictionaryList" (list $scope "imagePullSecrets") -}}
{{- $ignore := include "utils.verifyEnum" (list $scope "javaLoggingLevel" (list "SEVERE" "WARNING" "INFO" "CONFIG" "FINE" "FINER" "FINEST")) -}}
{{- if include "utils.verifyBoolean" (list $scope "externalRestEnabled") -}}
{{-   if $scope.externalRestEnabled -}}
{{-     $ignore := include "utils.verifyInteger" (list $scope "externalRestHttpsPort") -}}
{{-     $ignore := include "utils.mutexString" (list $scope "externalRestIdentitySecret" (list "externalOperatorKey" "externalOperatorCert")) -}}
{{-     if (or (hasKey $scope "externalOperatorCert") (hasKey $scope "externalOperatorKey")) -}}
{{-       $ignore := include "utils.verifyString"  (list $scope "externalOperatorCert") -}}
{{-       $ignore := include "utils.verifyString"  (list $scope "externalOperatorKey") -}}
{{-     else }}
{{-       $ignore := include "utils.verifyString"  (list $scope "externalRestIdentitySecret") -}}
{{-     end -}}
{{-   end -}}
{{- end -}}
{{- if include "utils.verifyBoolean" (list $scope "remoteDebugNodePortEnabled") -}}
{{-   if $scope.remoteDebugNodePortEnabled -}}
{{-     $ignore := include "utils.verifyBoolean" (list $scope "suspendOnDebugStartup") -}}
{{-     $ignore := include "utils.verifyInteger" (list $scope "internalDebugHttpPort") -}}
{{-     $ignore := include "utils.verifyInteger" (list $scope "externalDebugHttpPort") -}}
{{-   end -}}
{{- end -}}
{{- $ignore := include "utils.verifyOptionalBoolean" (list $scope "enableClusterRoleBinding") -}}
{{- if and .enableClusterRoleBinding (or .dedicated (eq .domainNamespaceSelectionStrategy "Dedicated")) }}
{{-   $errorMsg := "The enableClusterRoleBinding value may not be true when either dedicated is true or domainNamespaceSelectionStrategy is Dedicated" -}}
{{-   include "utils.recordValidationError" (list $scope $errorMsg) -}}
{{- end -}}
{{- if eq $scope.domainNamespaceSelectionStrategy "List" -}}
{{-     $ignore := include "utils.verifyStringList" (list $scope "domainNamespaces") -}}
{{- end -}}
{{- if include "utils.verifyBoolean" (list $scope "elkIntegrationEnabled") -}}
{{-   if $scope.elkIntegrationEnabled -}}
{{-     $ignore := include "utils.verifyString" (list $scope "logStashImage") -}}
{{-     $ignore := include "utils.verifyString" (list $scope "elasticSearchHost") -}}
{{-     $ignore := include "utils.verifyInteger" (list $scope "elasticSearchPort") -}}
{{-   end -}}
{{- end -}}
{{- $ignore := include "utils.verifyOptionalBoolean" (list $scope "dedicated") -}}
{{- $ignore := include "utils.verifyEnum" (list $scope "domainNamespaceSelectionStrategy" (list "List" "LabelSelector" "RegExp" "Dedicated")) -}}
{{- if eq $scope.domainNamespaceSelectionStrategy "LabelSelector" -}}
{{-   $ignore := include "utils.verifyString" (list $scope "domainNamespaceLabelSelector") -}}
{{- end -}}
{{- if eq $scope.domainNamespaceSelectionStrategy "RegExp" -}}
{{-   $ignore := include "utils.verifyString" (list $scope "domainNamespaceRegExp") -}}
{{- end -}}
{{- $ignore := include "utils.verifyOptionalBoolean" (list $scope "mockWLS") -}}
{{- $ignore := include "utils.endValidation" $scope -}}
{{- end -}}
