# Copyright (c) 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

oc patch configmap weblogic-operator-cm -n sample-weblogic-operator-ns --type='json' -p='[
  {"op":"replace","path":"/data/domainNamespaceSelectionStrategy","value":"List"},
  {"op":"add","path":"/data/domainNamespaces","value":"sample-domain1-ns"}
]'
