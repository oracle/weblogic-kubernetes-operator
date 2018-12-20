# Recent changes to the Oracle WebLogic Server Kubernetes Operator

This document tracks recent changes to the operator, especially ones that introduce backward incompatibilities.

| Date | Version | Introduces backward incompatibilities | Change |
| --- | --- | --- | --- |
| December 20, 2018 | v2.0-rc1 | yes | Operator is now installed via Helm charts, replacing the earlier scripts.  The operator now support domain home on Persistent Volume or in Docker image use cases, which required a redesign of the Domain schema.  Customers can override domain configuration using configuration override templates.  Load balancers and Ingress can now be independently configured.  WebLogic logs can be directed to a Persistent Volume or choice or WebLogic server out can be directed to the Pod log.  Added lifecycle support for servers and significantly more configurability for generated Pods.  The final v2.0 release will be initial release where the operator team intends to provide backwards compatability as part of future releases.
| March 20, 2018 | v1.1 | yes | Several files and input parameters have been renamed.  This affects how operators and domains are created.  It also changes generated Kubernetes artifacts, therefore customers must recreate their operators and domains.
| April 4, 2018 | v1.0 | yes | Many Kubernetes artifact names and labels have changed. Also, the names of generated YAML files for creating a domain's PV and PVC have changed.  Because of these changes, customers must recreate their operators and domains.
| May 7, 2018 |   | no | Added support for dynamic clusters, the Apache HTTP Server, the Voyager Ingress Controller, and for PV in NFS storage for multi-node environments.
