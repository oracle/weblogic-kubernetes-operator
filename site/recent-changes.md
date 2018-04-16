# Recent Changes to the Oracle WebLogic Server Kubernetes Operator

This page track recent changes to the operator, especially ones that introduce backward incompatibilities.

| Date | Introduces Backward Incompatibilities | Change |
| --- | --- | --- |
| March 20, 2018 | yes | See [Name Changes](name-changes.md).  Several files and input parameters have been renamed.  This affects how operators and domains are created.  It also changes generated Kubernetes artifacts, therefore customers must recreate their operators and domains.
| April 4, 2018 | yes | See [Name Changes](name-changes.md).  Many Kubernetes artifact names and labels have changed. Also, the names of generated YAML files for creating a domain's PV and PVC have changed.  Because of these changes, customers must recreate their operators and domains.
