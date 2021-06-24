---
title: "Release Notes"
date: 2019-03-15T11:25:28-04:00
draft: false
---

### Releases

| Date | Version | Introduces backward incompatibilities? | Change |
| --- | --- | --- | --- |
| June 21, 2021 | v3.2.5 | no | Updated Oracle Linux libraries and resolved an issue related to repeated introspection. |
| June 18, 2021 | v3.2.4 | no | Resolved several issues related to Istio, diagnostics, and recovery. |
| May 21, 2021 | v3.2.3 | no | Resolved several issues, including an issue related to preserving the operator-generated internal certificate, corrected the monitoring exporter integration to include the Administration Server, enhanced the model-in-image support to not require the use of configuration overrides, and updated the domain-home-in-image samples to support the WebLogic Image Tool. |
| April 27, 2021 | v3.2.2 | no | Resolved a set of issues with many related to reducing the operator's network utilization. |
| April 5, 2021 | v3.2.1 | no | Updated several dependencies, including the Oracle Linux base for the container image. |
| March 31, 2021 | v3.2.0 | no | Online updates for dynamic changes for Model in Image, injection of the WebLogic Monitoring Exporter, other features, and a significant number of additional fixes. |
| March 1, 2021 | v3.1.4 | no | Resolved an issue where the operator would ignore live data that was older than cached data, such as following an etcd restore and updated Kubernetes Java Client and Bouncy Castle dependencies. |
| February 12, 2021 | v3.1.3 | no | Resolved a pair of issues related to the operator running well in very large Kubernetes clusters. |
| January 22, 2021 | v3.1.2 | no | Resolved an issue where the operator failed to start servers in which the pods were configured to have an annotation containing a forward slash. |
| December 17, 2020 | v3.1.1 | no | Resolved an issue that caused unexpected server restarts when the domain had multiple WebLogic clusters. |
| November 24, 2020 | v3.0.4 | no | This release contains a back-ported fix from 3.1.0 for Managed Server pods that do not properly restart following a rolling activity. |
| November 13, 2020 | v3.1.0 | no | Enhanced options for specifying managed namespaces. Helm 3.1.3+ now required. Added support for Tanzu Kubernetes Service. |
| November 9, 2020 | v3.0.3 | no | This release contains a fix for pods that are stuck in the Terminating state after an unexpected shut down of a worker node. |
| September 15, 2020 | v3.0.2 | no | This release contains several fixes, including improvements to log rotation and a fix that avoids unnecessarily updating the domain status. |
| August 13, 2020 | v3.0.1 | no | Fixed an issue preventing the REST interface from working after a Helm upgrade. Helm 3.1.3+ now required. |
| July 17, 2020 | v3.0.0 | yes; for more information, see [Upgrade the operator]({{< relref "/userguide/managing-operators/installation.md#upgrade-the-operator" >}}). | Adds Model in Image feature and support for applying topology and configuration override changes without downtime. Removal of support for Helm 2.x. Operator performance improvements to manage many domains in the same Kubernetes cluster. |
| June 22, 2020 | v2.6.0 | no | Kubernetes 1.16, 1.17, and 1.18 support. Removal of support for Kubernetes 1.13 and earlier. This release can be run in the same cluster with operators of either 2.5.0 and below, or with 3.x providing an upgrade path. Certified support of Oracle Linux Cloud Native Environment (OLCNE) 1.1 with Kubernetes 1.17.0.
| February 26, 2020 | v2.5.0 | no | Support for Helm 3.x and OpenShift 4.3.  Operator can be installed in a namespace-dedicated mode where operator requires no cluster-level Kubernetes privileges. This version is not supported on Kubernetes 1.16+; check the [prerequisites]({{< relref "/userguide/prerequisites/introduction.md" >}}).
| November 15, 2019 | v2.4.0 | no | Includes fixes for a variety of issues related to FMW infrastructure domains and pod variable substitution.  Operator now uses WebLogic Deploy Tooling 1.6.0 and the latest version of the Kubernetes Java Client.
| August 27, 2019 | v2.3.0 | no  | Added support for Coherence cluster rolling, pod templating and additional pod content, and experimental support for running under an Istio service mesh.
| June 20, 2019 | v2.2.1 | no  | The operator now supports Kubernetes 1.14.0+.  This release is primarily a bug fix release and resolves the following issues: Servers in domains, where the domain home is on a persistent volume, would sometimes fail to start. These failures would be during the introspection phase following a full domain shutdown.  Now, the introspection script better handles the relevant error conditions. Also, now the Domain provides an option to [pre-create Kubernetes Services](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md#server-service) for WebLogic Servers that are not yet running so that the DNS addresses of these services are resolvable.  These services are now created as non-headless so that they have an IP address.
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

#### Operator 3.2.5

* Updated the Dockerfile to ensure Oracle Linux libraries are at the latest versions.
* Resolved an issue related to unnecessary repeated introspection ([#2418](https://github.com/oracle/weblogic-kubernetes-operator/pull/2418)).
* Updated the default monitoring exporter sidecar container image to use the 2.0.3 version.

#### Operator 3.2.4

* Added support for the sessionAffinity field for the clusterService ([#2383](https://github.com/oracle/weblogic-kubernetes-operator/pull/2383)).
* Moved several logging messages to the CONFIG level ([#2387](https://github.com/oracle/weblogic-kubernetes-operator/pull/2387)).
* Resolved an issue related to scalingAction.sh when there were multiple domains in the same namespace ([#2388](https://github.com/oracle/weblogic-kubernetes-operator/pull/2388)).
* Updated operator logging and related scripts to consistently use ISO-8601 timestamp formatting ([#2386](https://github.com/oracle/weblogic-kubernetes-operator/pull/2386)).
* Resolved an issue related to monitoring exporter integration and Istio ([#2390](https://github.com/oracle/weblogic-kubernetes-operator/pull/2390)).
* Additional diagnostics when container start scripts fail to start the WebLogic Server instance ([#2393](https://github.com/oracle/weblogic-kubernetes-operator/pull/2393)).
* Ensure Kubernetes API failures are logged after the final retry ([#2406](https://github.com/oracle/weblogic-kubernetes-operator/pull/2406)).
* Resolved an issue related to failing to recover when a node drain or repaving occurred while waiting for the Administration Server to start ([#2398](https://github.com/oracle/weblogic-kubernetes-operator/pull/2398)).
* Resolved an issue related to Istio and WDT models that use default listen and SSL ports ([#2379](https://github.com/oracle/weblogic-kubernetes-operator/pull/2379)).

#### Operator 3.2.3

* Resolved an issue related to preserving the operator-generated internal certificate ([#2374](https://github.com/oracle/weblogic-kubernetes-operator/pull/2374)).
* Corrected the monitoring exporter integration to include the Administration Server ([#2365](https://github.com/oracle/weblogic-kubernetes-operator/pull/2365)).
* Enhanced the model-in-image support to not require the use of configuration overrides ([#2344](https://github.com/oracle/weblogic-kubernetes-operator/pull/2344)).
* Resolved an issue related to model-in-image domains performing a rolling restart when there had been no change to the model ([#2348](https://github.com/oracle/weblogic-kubernetes-operator/pull/2348)).
* Resolved an issue related to RCU schema password updates ([#2357](https://github.com/oracle/weblogic-kubernetes-operator/pull/2357)).
* Resolved an issue related to namespace starting and stopping events ([#2345](https://github.com/oracle/weblogic-kubernetes-operator/pull/2345)).
* Added support for several new events related to rolling restarts ([#2364](https://github.com/oracle/weblogic-kubernetes-operator/pull/2364)).
* Added support for customer-defined labels and annotations on the operator's pod ([#2370](https://github.com/oracle/weblogic-kubernetes-operator/pull/2370)).

#### Operator 3.2.2

* Resolved an issue where the operator would retry Kubernetes API requests that timed out without a backoff causing increased network utilization ([#2300](https://github.com/oracle/weblogic-kubernetes-operator/pull/2300)).
* Resolved an issue where the operator would select the incorrect WebLogic Server port for administrative traffic ([#2301](https://github.com/oracle/weblogic-kubernetes-operator/pull/2301)).
* Resolved an issue where the operator, when running in a large and heavily loaded Kubernetes cluster, would not properly detect when a domain had been deleted and recreated ([#2305](https://github.com/oracle/weblogic-kubernetes-operator/pull/2305) and [#2314](https://github.com/oracle/weblogic-kubernetes-operator/pull/2314)).
* Resolved an issue where the operator would fail to recover and begin processing in a namespace if the operator did not immediately have privileges in that namespace when it was first detected ([#2315](https://github.com/oracle/weblogic-kubernetes-operator/pull/2315)).
* The operator logs a message when it cannot generate a `NamespaceWatchingStopped` Event in a namespace because the operator no longer has privileges in that namespace ([#2323](https://github.com/oracle/weblogic-kubernetes-operator/pull/2323)).
* Resolved an issue where the operator would repeatedly replace a ConfigMap causing increased network utilization ([#2321](https://github.com/oracle/weblogic-kubernetes-operator/pull/2321)).
* Resolved an issue where the operator would repeatedly read a Secret causing increased network utilization ([#2326](https://github.com/oracle/weblogic-kubernetes-operator/pull/2326)).
* Resolved an issue where the operator was not honoring `domain.spec.adminServer.serverService` ([#2334](https://github.com/oracle/weblogic-kubernetes-operator/pull/2334)).

#### Operator 3.2.1

Updated several dependencies, including the Oracle Linux base for the container image.

#### Operator 3.2.0

##### Features

* The operator's container image is based on Oracle Linux 8.
* WebLogic Server container images based on Oracle Linux 8 are supported.  
* [Online updates]({{<relref "/userguide/managing-domains/model-in-image/runtime-updates/_index.md#online-updates">}}) of dynamic configuration changes for Model in Image.
* Automatic injection of the [WebLogic Monitoring Exporter](https://github.com/oracle/weblogic-monitoring-exporter) as a sidecar container.  
* [Events]({{< relref "/userguide/managing-domains/domain-events.md" >}}) are generated at important moments in the life cycle of the operator or a domain.
* [PodDisruptionBudgets]({{<relref "/userguide/managing-domains/domain-lifecycle/startup.md#draining-a-node-and-poddisruptionbudget">}}) are generated for clusters improving the ability to maintain cluster availability during planned node shutdowns and Kubernetes upgrade.
* Additional scripts to assist with common tasks, such as the `scaleCluster.sh` script.
* Support for TCP and UDP on the same channel when the SIP protocol is used.

##### Fixes for Bugs or Regressions

* All fixes included in 3.1.1 through 3.1.4 are included in 3.2.0.
* Resolved an issue where clustered Managed Servers would not start when the Administration Server was not running ([#2093](https://github.com/oracle/weblogic-kubernetes-operator/pull/2093)).
* Model in Image generated domain home file systems that exceed 1 MB are supported ([#2095](https://github.com/oracle/weblogic-kubernetes-operator/pull/2095)).
* An event and status update are generated when a cluster can't be scaled past the cluster's maximum size ([#2097](https://github.com/oracle/weblogic-kubernetes-operator/pull/2097)).
* Improved the operator's ability to recover from failures during its initialization ([#2118](https://github.com/oracle/weblogic-kubernetes-operator/pull/2118)).
* Improved the ability for `scalingAction.sh` to discover the latest API version ([#2130](https://github.com/oracle/weblogic-kubernetes-operator/pull/2130)).
* Resolved an issue where the operator's log would show incorrect warnings related to missing RBAC permissions ([#2138](https://github.com/oracle/weblogic-kubernetes-operator/pull/2138)).
* Captured WDT errors related to validating the model ([#2140](https://github.com/oracle/weblogic-kubernetes-operator/pull/2140)).
* Resolved an issue where the operator incorrectly missed Secrets or ConfigMaps in namespaces with a large number of either resource ([#2199](https://github.com/oracle/weblogic-kubernetes-operator/pull/2199)).
* Resolved an issue where the operator could report incorrect information about an introspection job that failed ([#2201](https://github.com/oracle/weblogic-kubernetes-operator/pull/2201)).
* Resolved an issue where a Service with the older naming pattern from operator 3.0.x could be stranded ([#2208](https://github.com/oracle/weblogic-kubernetes-operator/pull/2208)).
* Resolved an issue in the domain and cluster start scripts related to overrides at specific Managed Servers ([#2222](https://github.com/oracle/weblogic-kubernetes-operator/pull/2222)).
* The operator supports logging rotation and maximum file size configurations through Helm chart values ([#2229](https://github.com/oracle/weblogic-kubernetes-operator/pull/2229)).
* Resolved an issue supporting session replication when Istio is in use ([#2242](https://github.com/oracle/weblogic-kubernetes-operator/pull/2242)).
* Resolved an issue where the operator could swallow exceptions related to SSL negotiation failure ([#2251](https://github.com/oracle/weblogic-kubernetes-operator/pull/2251)).
* Resolved an issue where introspection would detect the wrong SSL port ([#2256](https://github.com/oracle/weblogic-kubernetes-operator/pull/2256)).
* Resolved an issue where introspection would fail if a referenced Secret or ConfigMap name was too long ([#2257](https://github.com/oracle/weblogic-kubernetes-operator/pull/2257)).

#### Operator 3.1.4

* Resolved an issue where the operator would ignore live data that was older than cached data, such as following an etcd restore ([#2196](https://github.com/oracle/weblogic-kubernetes-operator/pull/2196)).
* Updated Kubernetes Java Client and Bouncy Castle dependencies.

#### Operator 3.1.3

* Resolved an issue that caused some WebLogic Servers to fail to start in large Kubernetes clusters where Kubernetes watch notifications were not reliably delivered ([#2188](https://github.com/oracle/weblogic-kubernetes-operator/pull/2188)).
* Resolved an issue that caused the operator to ignore some namespaces it was configured to manage in Kubernetes clusters that had more than 500 namespaces ([#2189](https://github.com/oracle/weblogic-kubernetes-operator/pull/2189)).

#### Operator 3.1.2

* Resolved an issue where the operator failed to start servers in which the pods were configured to have an annotation containing a forward slash ([#2089](https://github.com/oracle/weblogic-kubernetes-operator/pull/2089)).

#### Operator 3.1.1

* Resolved an issue that caused unexpected server restarts when the domain had multiple WebLogic clusters ([#2109](https://github.com/oracle/weblogic-kubernetes-operator/pull/2109)).

#### Operator 3.1.0

* All fixes included in 3.0.1 through 3.0.4 are included in 3.1.0.
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
