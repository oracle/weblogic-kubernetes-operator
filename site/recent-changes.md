# Recent Changes to the Oracle WebLogic Server Kubernetes Operator

This page track recent changes to the operator, especially ones that introduce backwards incompatibilities.

| Date | Introduces Backwards Incompatibilities | Change |
| --- | --- | --- |
| March 20, 2018 | yes | See [Name Changes](name-changes.md).  Several of files and input parameters have been renamed.  This affects how operators an domains are created.  It also changes generated Kubernetes artifacts, therefore customers must recreate their operators and domains.
| April 4, 2018 | yes | See [Name Changes](name-changes.md).  Many kubernetes artifact names and labels have changed. Also, the names of generated yaml files for creating a domain's pv and pvc have changed.  Because of these changes, customers must recreate their operators and domains.
