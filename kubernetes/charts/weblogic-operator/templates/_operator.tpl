# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operator" -}}
{{- include "operator.operatorClusterRoleGeneral" . }}
{{- include "operator.operatorClusterRoleNamespace" . }}
{{- if not (or (eq (default "List" .domainNamespaceSelectionStrategy) "Dedicated") (and .dedicated (eq (default "List" .domainNamespaceSelectionStrategy) "List"))) }}
{{-   include "operator.operatorClusterRoleNonResource" . }}
{{- end }}
{{- include "operator.operatorClusterRoleOperatorAdmin" . }}
{{- include "operator.operatorClusterRoleDomainAdmin" . }}
{{- include "operator.clusterRoleBindingGeneral" . }}
{{- include "operator.clusterRoleBindingAuthDelegator" . }}
{{- include "operator.clusterRoleBindingDiscovery" . }}
{{- if not (or (eq (default "List" .domainNamespaceSelectionStrategy) "Dedicated") (and .dedicated (eq (default "List" .domainNamespaceSelectionStrategy) "List"))) }}
{{-   include "operator.clusterRoleBindingNonResource" . }}
{{- end }}
{{- include "operator.operatorRole" . }}
{{- include "operator.operatorRoleBinding" . }}
{{- include "operator.operatorConfigMap" . }}
{{- include "operator.operatorSecrets" . }}
{{- include "operator.operatorDeployment" . }}
{{- include "operator.operatorInternalService" . }}
{{- include "operator.operatorExternalService" . }}
{{- if .enableClusterRoleBinding }}
{{-   include "operator.operatorRoleBindingNamespace" . }}
{{- else }}
{{-   include "operator.domainNamespaces" . }}
{{- end }}
{{- end }}
