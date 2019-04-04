---
title: "Release Notes"
date: 2019-03-15T11:25:28-04:00
draft: false
---

### Recent changes

| Date | Version | Introduces backward incompatibilities | Change |
| --- | --- | --- | --- |
| April 4, 2019 | v2.1 | no  | Customers can add init and sidecar containers to generated pods.  
| March 4, 2019 | v2.0.1 | no  | OpenShift support is now certified.  Many bug fixes, including fixes for configuration overrides, cluster services, and domain status processing.  
| January 24, 2019 | v2.0 | yes; not compatible with 1.x releases, but is compatible with 2.0-rc2. | Final version numbers and documentation updates.  
| January 16, 2019 | v2.0-rc2 | yes | Schema updates are completed, and various bugs fixed.
| December 20, 2018 | v2.0-rc1 | yes | Operator is now installed via Helm charts, replacing the earlier scripts.  The operator now supports the domain home on persistent volume or in Docker image use cases, which required a redesign of the domain schema.  You can override the domain configuration using configuration override templates.  Now load balancers and Ingresses can be independently configured.  You can direct WebLogic logs to a persistent volume or to the pod's log.  Added life cycle support for servers and significantly enhanced configurability for generated pods.  The final v2.0 release will be the initial release where the operator team intends to provide backward compatibility as part of future releases.
| September 11, 2018 | v1.1  | no | Enhanced the documentation and fixed various bugs.
| May 7, 2018 | v1.0  | no | Added support for dynamic clusters, the Apache HTTP Server, the Voyager Ingress Controller, and for PV in NFS storage for multi-node environments.
| April 4, 2018 | 0.2 | yes | Many Kubernetes artifact names and labels have changed. Also, the names of generated YAML files for creating a domain's PV and PVC have changed.  Because of these changes, customers must recreate their operators and domains.
| March 20, 2018 |  | yes | Several files and input parameters have been renamed.  This affects how operators and domains are created.  It also changes generated Kubernetes artifacts, therefore customers must recreate their operators and domains.

### Known issues

| Issue | Description |
| --- | --- |
| None currently |  |
