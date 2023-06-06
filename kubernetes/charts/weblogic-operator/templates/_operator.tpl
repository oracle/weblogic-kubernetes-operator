# Copyright (c) 2018, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- if and (not (empty .Capabilities.APIVersions)) (not (.Capabilities.APIVersions.Has "policy/v1")) }}
{{- fail "Kubernetes version 1.21 or greater is required." }}
{{- end }}

{{- define "operator.operator" -}}
{{- include "operator.operatorClusterRoleGeneral" . }}
{{- include "operator.operatorClusterRoleNamespace" . }}
{{- if not (eq .domainNamespaceSelectionStrategy "Dedicated") }}
{{-   include "operator.operatorClusterRoleNonResource" . }}
{{- end }}
{{- include "operator.operatorClusterRoleOperatorAdmin" . }}
{{- include "operator.operatorClusterRoleDomainAdmin" . }}
{{- include "operator.clusterRoleBindingGeneral" . }}
{{- if not (eq .domainNamespaceSelectionStrategy "Dedicated") }}
{{-   include "operator.clusterRoleBindingNonResource" . }}
{{- end }}
{{- include "operator.operatorRole" . }}
{{- include "operator.operatorRoleBinding" . }}
{{- include "operator.operatorConfigMap" . }}
{{- if and .elkIntegrationEnabled .createLogStashConfigMap }}
{{-   include "operator.logStashConfigMap" . }}
{{- end }}
{{- include "operator.operatorSecrets" . }}
{{- include "operator.operatorDeployment" . }}
{{- include "operator.operatorInternalService" . }}
{{- include "operator.operatorExternalService" . }}
{{- include "operator.operatorWebhookExternalService" . }}
{{- if or (not .enableClusterRoleBinding) (eq .domainNamespaceSelectionStrategy "Dedicated") }}
{{-   include "operator.domainNamespaces" . }}
{{- else }}
{{-   include "operator.operatorRoleBindingNamespace" . }}
{{- end }}
{{- end }}
