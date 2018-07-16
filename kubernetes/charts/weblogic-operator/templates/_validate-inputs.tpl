# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.validateInputs" -}}
{{- $scope := . -}}
{{- include "operator.verifyBooleanInput" (list . "setupKubernetesCluster") -}}
{{- include "operator.verifyBooleanInput" (list . "createOperator") -}}
{{- if (or .setupKubernetesCluster .createOperator) }}
{{-   include "operator.verifyBooleanInput" (list . "elkIntegrationEnabled") -}}
{{- end }}
{{- if .createOperator }}
{{-   include "operator.verifyBooleanInput" (list . "createOperatorNamespace") -}}
{{-   include "operator.verifyStringInput"  (list . "operatorNamespace") -}}
{{-   include "operator.verifyStringInput"  (list . "operatorServiceAccount") -}}
{{-   include "operator.verifyStringInput"  (list . "operatorImage") -}}
{{-   include "operator.verifyEnumInput"    (list . "operatorImagePullPolicy" (list "Always" "IfNotPresent" "Never")) -}}
{{-   include "operator.verifyStringInput"  (list . "internalOperatorCert") -}}
{{-   include "operator.verifyStringInput"  (list . "internalOperatorKey") -}}
{{-   include "operator.verifyBooleanInput" (list . "externalRestEnabled") -}}
{{-   include "operator.verifyBooleanInput" (list . "remoteDebugNodePortEnabled") -}}
{{-   include "operator.verifyEnumInput"    (list . "javaLoggingLevel" (list "SEVERE" "WARNING" "INFO" "CONFIG" "FINE" "FINER" "FINEST")) -}}
{{-   include "operator.verifyObjectInput"  (list . "domainsNamespaces") -}}
{{-   if .externalRestEnabled -}}
{{-     include "operator.verifyIntegerInput" (list . "externalRestHttpsPort") -}}
{{-     include "operator.verifyStringInput"  (list . "externalOperatorCert") -}}
{{-     include "operator.verifyStringInput"  (list . "externalOperatorKey") -}}
{{-   end -}}
{{-   if .remoteDebugNodePortEnabled -}}
{{-     include "operator.verifyIntegerInput" (list . "internalDebugHttpPort") -}}
{{-     include "operator.verifyIntegerInput" (list . "externalDebugHttpPort") -}}
{{-   end -}}
{{-   $domainsNamespaces := .domainsNamespaces -}}
{{-   range $key, $element := .domainsNamespaces -}}
{{-     include "operator.verifyObjectInput" (list $domainsNamespaces $key) -}}
{{-     $s := merge (dict) $element $scope -}}
{{-     include "operator.verifyBooleanInput" (list $s "createDomainsNamespace") -}}
{{-   end -}}
{{- end -}}
{{- end -}}
