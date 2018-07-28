# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.sharedOperatorResources" -}}
{{- include "operator.elasticSearchDeployment" . }}
{{- include "operator.elasticSearchService" . }}
{{- include "operator.kibanaDeployment" . }}
{{- include "operator.kibanaService" . }}
{{- include "operator.operatorClusterRole" . }}
{{- include "operator.operatorClusterRoleNamespace" . }}
{{- include "operator.operatorClusterRoleNonResource" . }}
{{- end }}
