> **WARNING** This documentation is for version 1.0 of the operator.  To view documenation for the current release, [please click here](/site).

# Recent changes to the Oracle WebLogic Server Kubernetes Operator

This document tracks recent changes to the operator, especially ones that introduce backward incompatibilities.

| Date | Introduces backward incompatibilities | Change |
| --- | --- | --- |
| March 20, 2018 | yes | Several files and input parameters have been renamed.  This affects how operators and domains are created.  It also changes generated Kubernetes artifacts, therefore customers must recreate their operators and domains.
| April 4, 2018 | yes | Many Kubernetes artifact names and labels have changed. Also, the names of generated YAML files for creating a domain's PV and PVC have changed.  Because of these changes, customers must recreate their operators and domains.
| May 7, 2018 | no | Added support for dynamic clusters, the Apache HTTP Server, the Voyager Ingress Controller, and for PV in NFS storage for multi-node environments.
