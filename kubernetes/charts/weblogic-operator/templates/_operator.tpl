# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

{{- define "operator.operator" -}}
{{- include "operator.operatorClusterRoleGeneral" . }}
{{- include "operator.operatorClusterRoleNamespace" . }}
{{- if not .dedicated }}
{{- include "operator.operatorClusterRoleNonResource" . }}
{{- end }}
{{- include "operator.operatorClusterRoleOperatorAdmin" . }}
{{- include "operator.operatorClusterRoleDomainAdmin" . }}
{{- include "operator.clusterRoleBindingGeneral" . }}
{{- include "operator.clusterRoleBindingAuthDelegator" . }}
{{- include "operator.clusterRoleBindingDiscovery" . }}
{{- if not .dedicated }}
{{- include "operator.clusterRoleBindingNonResource" . }}
{{- end }}
{{- include "operator.operatorRole" . }}
{{- include "operator.operatorRoleBinding" . }}
{{- include "operator.operatorConfigMap" . }}
{{- include "operator.operatorSecrets" . }}
{{- include "operator.operatorDeployment" . }}
{{- include "operator.operatorInternalService" . }}
{{- include "operator.operatorExternalService" . }}
{{- include "operator.domainNamespaces" . }}
{{- end }}
