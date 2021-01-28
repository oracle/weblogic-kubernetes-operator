---
title: "Release Notes"
date: 2019-03-15T11:25:28-04:00
draft: false
---

### Releases

| Date | Version | Introduces backward incompatibilities? | Change |
| --- | --- | --- | --- |
| December ??, 2020 | v3.2.0 | no | Placeholder |
| January 22, 2021 | v3.1.2 | no | Resolved an issue where the operator failed to start servers in which the pods were configured to have an annotation containing a forward slash. |
| December 17, 2020 | v3.1.1 | no | Resolved an issue that caused unexpected server restarts when the domain had multiple WebLogic clusters. |
| November 24, 2020 | v3.0.4 | no | This release contains a back-ported fix from 3.1.0 for Managed Server pods that do not properly restart following a rolling activity. |
| November 13, 2020 | v3.1.0 | no | Enhanced options for specifying managed namespaces. Helm 3.1.3+ now required. Added support for Tanzu Kubernetes Service. |
| November 9, 2020 | v3.0.3 | no | This release contains a fix for pods that are stuck in the Terminating state after an unexpected shut down of a worker node. |
| September 15, 2020 | v3.0.2 | no | This release contains several fixes, including improvements to log rotation and a fix that avoids unnecessarily updating the domain status. |
| August 13, 2020 | v3.0.1 | no | Fixed an issue preventing the REST interface from working after a Helm upgrade. Helm 3.1.3+ now required. |
| July 17, 2020 | v3.0.0 | yes; for more information, see [Upgrade the operator]({{< relref "/userguide/managing-operators/installation/_index.md#upgrade-the-operator" >}}). | Adds Model in Image feature and support for applying topology and configuration override changes without downtime. Removal of support for Helm 2.x. Operator performance improvements to manage many domains in the same Kubernetes cluster. |
| June 22, 2020 | v2.6.0 | no | Kubernetes 1.16, 1.17, and 1.18 support. Removal of support for Kubernetes 1.13 and earlier. This release can be run in the same cluster with operators of either 2.5.0 and below, or with 3.x providing an upgrade path. Certified support of Oracle Linux Cloud Native Environment (OLCNE) 1.1 with Kubernetes 1.17.0.
| February 26, 2020 | v2.5.0 | no | Support for Helm 3.x and OpenShift 4.3.  Operator can be installed in a namespace-dedicated mode where operator requires no cluster-level Kubernetes privileges. This version is not supported on Kubernetes 1.16+; check the [prerequisites]({{< relref "/userguide/introduction/introduction#operator-prerequisites" >}}).
| November 15, 2019 | v2.4.0 | no | Includes fixes for a variety of issues related to FMW infrastructure domains and pod variable substitution.  Operator now uses WebLogic Deploy Tooling 1.6.0 and the latest version of the Kubernetes Java Client.
| August 27, 2019 | v2.3.0 | no  | Added support for Coherence cluster rolling, pod templating and additional pod content, and experimental support for running under an Istio service mesh.
| June 20, 2019 | v2.2.1 | no  | The operator now supports Kubernetes 1.14.0+.  This release is primarily a bug fix release and resolves the following issues: Servers in domains, where the domain home is on a persistent volume, would sometimes fail to start. These failures would be during the introspection phase following a full domain shutdown.  Now, the introspection script better handles the relevant error conditions. Also, now the Domain provides an option to [pre-create Kubernetes Services](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/docs/domains/Domain.md#server-service) for WebLogic Servers that are not yet running so that the DNS addresses of these services are resolvable.  These services are now created as non-headless so that they have an IP address.
| June 6, 2019 | v2.2.0 | no  | Added support for FMW Infrastructure domains. WebLogic Server instances are now gracefully shut down by default and shutdown options are configurable. Operator is now built and runs on JDK 11.
| April 4, 2019 | v2.1 | no  | Customers can add init and sidecar containers to generated pods.  
| March 4, 2019 | v2.0.1 | no  | OpenShift support is now certified.  Many bug fixes, including fixes for configuration overrides, cluster services, and domain status processing.  
| January 24, 2019 | v2.0 | yes; not compatible with 1.x releases, but is compatible with 2.0-rc2. | Final version numbers and documentation updates.  
| January 16, 2019 | v2.0-rc2 | yes | Schema updates are completed, and various bugs fixed.
| December 20, 2018 | v2.0-rc1 | yes | Operator is now installed using Helm charts, replacing the earlier scripts.  The operator now supports the domain home on a persistent volume or in image use cases, which required a redesign of the domain schema.  You can override the domain configuration using configuration override templates.  Now load balancers and ingresses can be independently configured.  You can direct WebLogic logs to a persistent volume or to the pod's log.  Added life cycle support for servers and significantly enhanced configurability for generated pods.  The final v2.0 release will be the initial release where the operator team intends to provide backward compatibility as part of future releases.
| September 11, 2018 | v1.1  | no | Enhanced the documentation and fixed various bugs.
| May 7, 2018 | v1.0  | no | Added support for dynamic clusters, the Apache HTTP Server, the Voyager Ingress Controller, and for PV in NFS storage for multi-node environments.
| April 4, 2018 | 0.2 | yes | Many Kubernetes artifact names and labels have changed. Also, the names of generated YAML files for creating a domain's PV and PVC have changed.  Because of these changes, customers must recreate their operators and domains.
| March 20, 2018 |  | yes | Several files and input parameters have been renamed.  This affects how operators and domains are created.  It also changes generated Kubernetes artifacts, therefore customers must recreate their operators and domains.

### Change log

#### Operator 3.2.0

#### Operator 3.1.2

* Resolved an issue where the operator failed to start servers in which the pods were configured to have an annotation containing a forward slash ([#2089](https://github.com/oracle/weblogic-kubernetes-operator/pull/2089)).

#### Operator 3.1.1

* Resolved an issue that caused unexpected server restarts when the domain had multiple WebLogic clusters ([#2109](https://github.com/oracle/weblogic-kubernetes-operator/pull/2109)).

#### Operator 3.1.0

* All fixes included in 3.0.1, 3.0.2, and 3.0.3 are included in 3.1.0.
* Sample [scripts to start and stop server instances]({{< relref "/userguide/managing-domains/domain-lifecycle/startup#domain-lifecycle-sample-scripts" >}}) ([#2002](https://github.com/oracle/weblogic-kubernetes-operator/pull/2002)).
* Support running with [OpenShift restrictive SCC]({{< relref "/security/openshift#create-a-custom-security-context-constraint" >}}) ([#2007](https://github.com/oracle/weblogic-kubernetes-operator/pull/2007)).
* Updated [default resource and Java options]({{< relref "/faq/resource-settings.md" >}}) ([#1775](https://github.com/oracle/weblogic-kubernetes-operator/pull/1775)).
* Introspection failures are logged to the operator's log ([#1787](https://github.com/oracle/weblogic-kubernetes-operator/pull/1787)).
* Mirror introspector log to a rotating file in the log home ([#1827](https://github.com/oracle/weblogic-kubernetes-operator/pull/1827)).
* Reflect introspector status to domain status ([#1832](https://github.com/oracle/weblogic-kubernetes-operator/pull/1832)).
* Ensure operator detects pod state changes even when watch events are not delivered ([#1811](https://github.com/oracle/weblogic-kubernetes-operator/pull/1811)).
* Support configurable WDT model home ([#1828](https://github.com/oracle/weblogic-kubernetes-operator/pull/1828)).
* [Namespace management enhancements]({{< relref "/faq/namespace-management.md" >}}) ([#1860](https://github.com/oracle/weblogic-kubernetes-operator/pull/1860)).
* Limit concurrent pod shut down while scaling down a cluster ([#1892](https://github.com/oracle/weblogic-kubernetes-operator/pull/1892)).
* List continuation and watch bookmark support ([#1881](https://github.com/oracle/weblogic-kubernetes-operator/pull/1881)).
* Fix scaling script when used with dedicated namespace mode ([#1921](https://github.com/oracle/weblogic-kubernetes-operator/pull/1921)).
* Fix token substitution for mount paths ([#1911](https://github.com/oracle/weblogic-kubernetes-operator/pull/1911)).
* Validate existence of service accounts during Helm chart processing ([#1939](https://github.com/oracle/weblogic-kubernetes-operator/pull/1939)).
* Use Kubernetes Java Client 10.0.0 ([#1937](https://github.com/oracle/weblogic-kubernetes-operator/pull/1937)).
* Better validation and guidance when using longer domainUID values ([#1979](https://github.com/oracle/weblogic-kubernetes-operator/pull/1979)).
* Update pods with label for introspection version ([#2012](https://github.com/oracle/weblogic-kubernetes-operator/pull/2012)).
* Fix validation error during inrtrospector for certain static clusters ([#2014](https://github.com/oracle/weblogic-kubernetes-operator/pull/2014)).
* Correct issue in wl-pod-wait.sh sample script ([#2018](https://github.com/oracle/weblogic-kubernetes-operator/pull/2018)).
* Correct processing of ALWAYS serverStartPolicy ([#2020](https://github.com/oracle/weblogic-kubernetes-operator/pull/2020)).

#### Operator 3.0.4

* The operator now correctly completes restarting Managed Server pods in order to complete a rolling activity. This fix is already present in 3.1.0.

#### Operator 3.0.3

* The operator now responds to WebLogic Server instance pods that are stuck in the Terminating state when those pods are evicted from a node that has unexpectedly shut down and where Kubernetes has not removed the pod.

#### Operator 3.0.2

* Removed unnecessary duplicated parameter in initialize-internal-operator-identity.sh script ([#1867](https://github.com/oracle/weblogic-kubernetes-operator/pull/1867)).
* Support nodeAffinity and nodeSelector for the operator in its Helm chart ([#1869](https://github.com/oracle/weblogic-kubernetes-operator/pull/1869)).
* Log file rotation enhancements and documentation ([#1872](https://github.com/oracle/weblogic-kubernetes-operator/pull/1872), [#1827](https://github.com/oracle/weblogic-kubernetes-operator/pull/1827)).
* Production support for the NGINX ingress controller ([#1878](https://github.com/oracle/weblogic-kubernetes-operator/pull/1878)).
* Prevent unnecessary changes to Domain status that were causing churn to the resourceVersion ([#1879](https://github.com/oracle/weblogic-kubernetes-operator/pull/1879)).
* Better reflect introspector status in the Domain status ([#1832](https://github.com/oracle/weblogic-kubernetes-operator/pull/1832)).
* Create each pod after any previous pods have been scheduled to allow for correct anti-affinity behavior ([#1855](https://github.com/oracle/weblogic-kubernetes-operator/pull/1855)).

#### Operator 3.0.1

* Resolved an issue where a Helm upgrade was incorrectly removing the operator's private key thereby disabling the operator's REST interface ([#1846](https://github.com/oracle/weblogic-kubernetes-operator/pull/1846)).

### Known issues

| Issue | Description |
| --- | --- |
| None currently |  |
