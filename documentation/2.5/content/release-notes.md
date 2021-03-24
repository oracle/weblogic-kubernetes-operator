---
title: "Release Notes"
date: 2019-03-15T11:25:28-04:00
draft: false
---

### Recent changes

| Date | Version | Introduces backward incompatibilities | Change |
| --- | --- | --- | --- |
| February 26, 2020 | v2.5.0 | no | Support for Helm 3.x and OpenShift 4.3.  Operator can be installed in a namespace-dedicated mode where operator requires no cluster-level Kubernetes privileges. This version is not supported on Kubernetes 1.16+, check the prerequisites.
| November 15, 2019 | v2.4.0 | no | Includes fixes for a variety of issues related to FMW infrastructure domains and pod variable substitution.  Operator now uses WebLogic Deploy Tooling 1.6.0 and the latest version of the Kubernetes Java Client. 
| August 27, 2019 | v2.3.0 | no  | Added support for Coherence cluster rolling, pod templating and additional pod content, and experimental support for running under an Istio service mesh.
| June 20, 2019 | v2.2.1 | no  | The operator now supports Kubernetes 1.14.0+.  This release is primarily a bug fix release and resolves the following issues:<br><ul><li>Servers in domains, where the domain home is on a persistent volume, would sometimes fail to start. These failures would be during the introspection phase following a full domain shutdown.  Now, the introspection script better handles the relevant error conditions.</li><li>The domain resource provides an option to <a href="https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md#server-service">pre-create Kubernetes services</a> for WebLogic Servers that are not yet running so that the DNS addresses of these services are resolvable.  These services are now created as non-headless so that they have an IP address.</li></ul>
| June 6, 2019 | v2.2.0 | no  | Added support for FMW Infrastructure domains. WebLogic Server instances are now gracefully shut down by default and shutdown options are configurable. Operator is now built and runs on JDK 11.
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
