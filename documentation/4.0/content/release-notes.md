---
title: "Release Notes"
date: 2019-03-15T11:25:28-04:00
draft: false
---

{{< table_of_contents >}}

### Releases

| Date               | Version  | Change - See also, [Change log](#change-log).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
|--------------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| February 21, 2023  | v4.0.5   | Resolved.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| February 17, 2023  | v3.4.6   | Resolved several issues related to WDT models for Model in Image, resolved an issue related to an exception while correcting container port names, and updates including the WebLogic Monitoring Exporter version.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| January 24, 2023   | v3.4.5   | Resolved an issue related to decorating the name of the ConfigMap created for Fluentd integration and dependency updates including WebLogic Monitoring Exporter, Jackson Databind, and the Oracle Linux base image.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| December 20, 2022  | v4.0.4   | Resolved an issue related to the automatic schema conversion of the `logHomeLayout` field and an issue related to stranding a ValidatingWebhookConfiguration resource on uninstall.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| December 15, 2022  | v4.0.3   | Resolved a set of issues related to Istio strict mutual TLS mode and an issue related to the `Completed` status being set too early during cluster scale down.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| December 9, 2022   | v4.0.2   | Resolved a set of issues related to automatic schema conversion from Domain "v8" to "v9 and an issue related to Model in Image domains and secured production mode.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| November 4, 2022   | v4.0.1   | Resolved an issue where introspection would fail because the function `wlsVersionEarlierThan` was missing.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| November 2, 2022   | v4.0.0   | New Cluster resource for Horizontal Pod Autoscaling (HPA). Domain resource "v9" with auxiliary image simplification, improved status reporting, and improved failure retry predictability and transparency. Istio and other service mesh support enabled automatically. Kubernetes 1.24 and 1.25 support. Minimum Kubernetes version is now 1.21.                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| October 26, 2022   | v3.4.4   | Support added to specify resource requests and limits for Monitoring Exporter sidecar containers. This release of the operator is compatible with running in the same Kubernetes cluster as additional operators from the upcoming 4.0 release.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| August 25, 2022    | v3.4.3   | Resolved an issue related to introspector failure for non-English locales and improved concurrency for managing configuration override files.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| August 9, 2022     | v3.4.2   | Updated several dependencies, including the Oracle Linux base for the container image.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| June 13, 2022      | v3.4.1   | Resolved several issues related to Model in Image and introspection.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| April 22, 2022     | v3.4.0   | Kubernetes 1.22 and 1.23 support, Fluentd sidecar injection, and dependency updates including WebLogic Monitoring Exporter 2.0.5.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| February 18, 2022  | v3.3.8   | Resolved several issues related to WDT 2.0, Istio, and auxiliary images.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| December 21, 2021  | v3.3.7   | Resolved two issues related to auxiliary images.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| November 24, 2021  | v3.3.6   | Support added for a `hostAliases` field for WebLogic Server pod generation.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| November 23, 2021  | v3.3.5   | Resolved several issues, including an issue related to collecting logs from failed Model in Image domains that used auxiliary images and an issue related to reading PodDisruptionBudget resources not created by the operator.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| November 9, 2021   | v3.3.4   | Resolved an issue related to Model in Image domains and enabling WebLogic secure mode.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| November 1, 2021   | v3.3.3   | Resolved an issue related to WebLogic cluster replication with Istio 1.10 and resolved several issues related to introspector failure, retry, and status.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| September 24, 2021 | v3.3.2   | Istio 1.10 support, enhanced liveness and readiness probe customization to support customizing failure thresholds, and additional validations.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| August 23, 2021    | v3.3.1   | Resolved an issue related to managed Coherence cluster formation when using Istio and another issue related to Secret and ConfigMap validation.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| July 20, 2021      | v3.3.0   | Auxiliary image support.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| June 21, 2021      | v3.2.5   | Updated Oracle Linux libraries and resolved an issue related to repeated introspection.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| June 18, 2021      | v3.2.4   | Resolved several issues related to Istio, diagnostics, and recovery.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| May 21, 2021       | v3.2.3   | Resolved several issues, including an issue related to preserving the operator-generated internal certificate, corrected the monitoring exporter integration to include the Administration Server, enhanced the model-in-image support to not require the use of configuration overrides, and updated the domain-home-in-image samples to support the WebLogic Image Tool.                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| April 27, 2021     | v3.2.2   | Resolved a set of issues with many related to reducing the operator's network utilization.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| April 5, 2021      | v3.2.1   | Updated several dependencies, including the Oracle Linux base for the container image.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| March 31, 2021     | v3.2.0   | Online updates for dynamic changes for Model in Image, injection of the WebLogic Monitoring Exporter, other features, and a significant number of additional fixes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| March 1, 2021      | v3.1.4   | Resolved an issue where the operator would ignore live data that was older than cached data, such as following an etcd restore and updated Kubernetes Java Client and Bouncy Castle dependencies.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| February 12, 2021  | v3.1.3   | Resolved a pair of issues related to the operator running well in very large Kubernetes clusters.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| January 22, 2021   | v3.1.2   | Resolved an issue where the operator failed to start servers in which the pods were configured to have an annotation containing a forward slash.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| December 17, 2020  | v3.1.1   | Resolved an issue that caused unexpected server restarts when the domain had multiple WebLogic clusters.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| November 24, 2020  | v3.0.4   | This release contains a back-ported fix from 3.1.0 for Managed Server pods that do not properly restart following a rolling activity.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| November 13, 2020  | v3.1.0   | Enhanced options for specifying managed namespaces. Helm 3.1.3+ now required. Added support for Tanzu Kubernetes Service.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| November 9, 2020   | v3.0.3   | This release contains a fix for pods that are stuck in the Terminating state after an unexpected shut down of a worker node.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| September 15, 2020 | v3.0.2   | This release contains several fixes, including improvements to log rotation and a fix that avoids unnecessarily updating the domain status.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| August 13, 2020    | v3.0.1   | Fixed an issue preventing the REST interface from working after a Helm upgrade. Helm 3.1.3+ now required.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| July 17, 2020      | v3.0.0   | Adds Model in Image feature and support for applying topology and configuration override changes without downtime. Removal of support for Helm 2.x. Operator performance improvements to manage many domains in the same Kubernetes cluster.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| June 22, 2020      | v2.6.0   | Kubernetes 1.16, 1.17, and 1.18 support. Removal of support for Kubernetes 1.13 and earlier. This release can be run in the same cluster with operators of either 2.5.0 and earlier, or with 3.x providing an upgrade path. Certified support of Oracle Cloud Native Environment 1.1 with Kubernetes 1.17.0.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| February 26, 2020  | v2.5.0   | Support for Helm 3.x and OpenShift 4.3.  Operator can be installed in a namespace-dedicated mode where operator requires no cluster-level Kubernetes privileges. This version is not supported on Kubernetes 1.16+; check the [prerequisites]({{< relref "/introduction/prerequisites/introduction.md" >}}).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| November 15, 2019  | v2.4.0   | Includes fixes for a variety of issues related to FMW infrastructure domains and pod variable substitution.  Operator now uses WebLogic Deploy Tooling 1.6.0 and the latest version of the Kubernetes Java Client.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| August 27, 2019    | v2.3.0   | Added support for Coherence cluster rolling, pod templating and additional pod content, and experimental support for running under an Istio service mesh.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| June 20, 2019      | v2.2.1   | The operator now supports Kubernetes 1.14.0+.  This release is primarily a bug fix release and resolves the following issues: Servers in domains, where the domain home is on a persistent volume, would sometimes fail to start. These failures would be during the introspection phase following a full domain shutdown.  Now, the introspection script better handles the relevant error conditions. Also, now the Domain provides an option to [pre-create Kubernetes Services](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md#server-service) for WebLogic Servers that are not yet running so that the DNS addresses of these services are resolvable.  These services are now created as non-headless so that they have an IP address. |
| June 6, 2019       | v2.2.0   | Added support for FMW Infrastructure domains. WebLogic Server instances are now gracefully shut down by default and shutdown options are configurable. Operator is now built and runs on JDK 11.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| April 4, 2019      | v2.1     | Customers can add init and sidecar containers to generated pods.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| March 4, 2019      | v2.0.1   | OpenShift support is now certified.  Many bug fixes, including fixes for configuration overrides, cluster services, and domain status processing.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| January 24, 2019   | v2.0     | Final version numbers and documentation updates.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| January 16, 2019   | v2.0-rc2 | Schema updates are completed, and various bugs fixed.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| December 20, 2018  | v2.0-rc1 | Operator is now installed using Helm charts, replacing the earlier scripts.  The operator now supports the domain home on a persistent volume or in image use cases, which required a redesign of the domain schema.  You can override the domain configuration using configuration override templates.  Now load balancers and ingresses can be independently configured.  You can direct WebLogic logs to a persistent volume or to the pod's log.  Added life cycle support for servers and significantly enhanced configurability for generated pods.  The final v2.0 release will be the initial release where the operator team intends to provide backward compatibility as part of future releases.                                                                                                           |
| September 11, 2018 | v1.1     | Enhanced the documentation and fixed various bugs.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| May 7, 2018        | v1.0     | Added support for dynamic clusters, the Apache HTTP Server, the Voyager Ingress Controller, and for PV in NFS storage for multi-node environments.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| April 4, 2018      | v0.2     | Many Kubernetes artifact names and labels have changed. Also, the names of generated YAML files for creating a domain's PV and PVC have changed.  Because of these changes, customers must recreate their operators and domains.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| March 20, 2018     | v0.1     | Several files and input parameters have been renamed.  This affects how operators and domains are created.  It also changes generated Kubernetes artifacts, therefore customers must recreate their operators and domains.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |

### Change log

#### Operator 4.0.5

* Updated the Fluentd integration to make the Elasticsearch credentials optional and to allow the specification of the container command and arguments.
* Resolved an issue related to growth in the number of WebLogic sessions related to the monitoring exporter and health checks.
* Resolved [issue #3865](https://github.com/oracle/weblogic-kubernetes-operator/issues/3865) related to decorating the name of the ConfigMap for Fluentd integration ([#3883](https://github.com/oracle/weblogic-kubernetes-operator/pull/3883)).
* Updated the generation of pods and containers for [security best practices](https://oracle.github.io/weblogic-kubernetes-operator/security/pod-and-container/).
* Added support to specify the [container resources and Java options for the introspector job](https://oracle.github.io/weblogic-kubernetes-operator/faq/resource-settings/).
* Changed the Helm chart to disable the operator's REST endpoint by default. This endpoint is only needed for [WLDF action based scaling](https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-lifecycle/scaling/). All other forms of scaling are unaffected by this change.
* Resolved an issue related to schema webhook conversion of the `domainHomeInImage` field.
* Resolved an issue related to schema webhook conversion of status conditions only supported for "v9" schema.
* Resolved an issue related to the ordering of init containers generated for "v8" style auxiliary images.
* Resolved several issues related to the generation of Domain and Cluster deletion events.
* Resolved an issue related to using the `nodeSelector` and `affinity` values with the operator's Helm chart.
* Resolved several issues related to WebLogic Deploy Tooling (WDT) models for Model in Image.
  * Added support or provided clearer validation errors for cases where `AdminServerName` is set or missing and where there is or is not a matching entry under `Server`.
  * Improved support for dynamic clusters that do not specify `ServerTemplate` or `DynamicClusterSize`.
  * Provided clearer validation errors for dynamic clusters that have no member servers defined under `DynamicServers`.
* Resolved an issue related to a StringIndexOutOfBoundsException generated while ensuring container port names are not longer than the Kubernetes enforced maximum of 15 characters.
* Updated the default WebLogic Monitoring Exporter injection to version 2.1.2.

#### Operator 3.4.6

* Resolved several issues related to WebLogic Deploy Tooling (WDT) models for Model in Image.
  * Added support or provided clearer validation errors for cases where `AdminServerName` is set or missing and where there is or is not a matching entry under `Server`.
  * Improved support for dynamic clusters that do not specify `ServerTemplate` or `DynamicClusterSize`.
  * Provided clearer validation errors for dynamic clusters that have no member servers defined under `DynamicServers`.
* Resolved an issue related to a StringIndexOutOfBoundsException generated while ensuring container port names are not longer than the Kubernetes enforced maximum of 15 characters.
* Updated the default WebLogic Monitoring Exporter injection to version 2.1.2.

#### Operator 3.4.5

* Resolved [issue #3865](https://github.com/oracle/weblogic-kubernetes-operator/issues/3865) related to decorating the name of the ConfigMap for Fluentd integration ([#3883](https://github.com/oracle/weblogic-kubernetes-operator/pull/3883)).
* Updated the default WebLogic Monitoring Exporter injection to version 2.1.1.
* Updated several dependencies, including Jackson Databind to version 2.14.1 and the Oracle Linux base for the container image to version 9.

#### Operator 4.0.4

* Resolved an issue related to the `logHomeLayout` field during schema conversion from "v8" to "v9" Domains.
  * Operator 4.0 introduced the `logHomeLayout` field to [control the organization of WebLogic Server instance log files](https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/accessing-the-domain/logs/).
  * The default value for `logHomeLayout` is `ByServers`; however, the operator 3.4 behavior was consistent with the value of `Flat`.
  * Therefore, schema conversion from "v8" to "v9" Domains now explicitly selects a `logHomeLayout` value of `Flat`.
* Resolved an issue during uninstall where the validating webhook was leaving behind a ValidatingWebhookConfiguration resource. This resource is now deleted.

#### Operator 4.0.3

* The Helm chart now excludes the operator's webhook from running in an Istio mesh.
* Communication from the operator to WebLogic Server instances or to the WebLogic Monitoring Exporter sidecar use the service name and namespace rather than IP addresses to be more compatible with Istio strict mutual TLS mode.
* Resolved an issue where the `Completed` status condition would be set to true too early during the scale down of a cluster to 0 replicas.
* The [Model in Image sample](https://oracle.github.io/weblogic-kubernetes-operator/samples/domains/model-in-image/) now uses Domain "v9" style auxiliary images.
* The coordination between the periodic listing of Domain and Cluster resources and the processing of watch notifications has been improved to increase operator efficiency.

#### Operator 4.0.2

* The Helm chart now supports configuring [tolerations](https://kubernetes.io/docs/concepts/scheduling-eviction/taint-and-toleration/) for the operator's deployment.
* Domain resource schema now supports `.spec.maxClusterUnavailable`, which is the default value for the maximum number of cluster members that can be temporarily unavailable, such as during a roll.
* Resolved an issue that would prevent Model in Image domains from working correctly when [Secured Production Mode](https://docs.oracle.com/en/middleware/fusion-middleware/weblogic-server/12.2.1.4/lockd/secure.html#GUID-ADF914EF-0FB6-446E-B6BF-D230D8B0A5B0) is enabled.
* Pods created for WebLogic Server instances that are members of a WebLogic cluster will now include the label `weblogic.clusterObservedGeneration` specifying the `metadata.generation` of the Cluster resource. This is similar to the already existing `weblogic.domainObservedGeneration` label that specifies the observed generation of the Domain resource.
* Schema conversion from "v8" to "v9" Domains now uses shorter name prefixes when converting "v8" style auxiliary images.
* The Helm chart specifies a more compatible `securityContext` for the operator's deployment when the `kubernetesPlatform` value is "OpenShift".
* The Node Manager instance running within the Pod created for each WebLogic Server instance [now supports additional configuration for how often to restart WebLogic before the Pod's liveness probe fails and the Pod is recreated](https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-lifecycle/liveness-readiness-probe-customization/#automatic-restart-of-failed-server-instances-by-node-manager).
* Samples and utility scripts now support the environment variables `KUBERNETES_CLI`, which defaults to `kubectl`, and `WLSIMG_BUILDER`, which defaults to `docker`, to make it easier to use these samples and scripts on platforms that use binaries with different names.
* Resolved an issue where a "v8" Domain that used auxiliary images and also configured `logHome` could be converted to a "v9" Domain that was invalid.
* Resolved an issue where Domains that had a `.spec.domainUid` that was different from the value of `.metadata.name` would not start.

#### Operator 4.0.1

* Resolved an issue where introspection would fail because the function `wlsVersionEarlierThan` was missing.

#### Operator 4.0.0

##### Major Themes

* Auxiliary Image simplification:
  * The usage of Model in Image
    [auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}})
    has been substantially simplified.
  * In most cases, customers now need only specify the name of the auxiliary image.

* New Cluster resource:
  * The operator now provides a new custom resource, [Cluster]({{< relref "/managing-domains/domain-resource.md" >}}), which configures a specific WebLogic cluster and provides status information for that cluster.
  * This resource can be used with [Kubernetes Horizontal Pod Autoscaling (HPA)](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/) or similar technologies to scale WebLogic clusters.

* Status and Events updates:
  * The [Conditions]({{< relref "/managing-domains/accessing-the-domain/status-conditions.md" >}}) and [Events]({{< relref "/managing-domains/accessing-the-domain/domain-events.md" >}}) generated about Domain and Cluster resources have been significantly updated.
  * The operator more clearly communicates when the domain or individual clusters have reached the intended state or significant availability for your application workloads.
  * Failures are distinguished between those that require customer intervention and those that are potentially temporary and will be retried.

* Retry updates:
  * The operator handling of
    [retries after a failure]({{< relref "/managing-domains/domain-lifecycle/retry.md" >}})
    have been significantly updated.
  * Customers can now tune retry intervals and limits, or disable retries, on a per domain basis.
  * The time of the first failure, last retry, and the expected time of the next retry can now be easily inferred from Domain resource `.status`.

* Istio compatibility:
  * Domain configurations are always compatible with [Istio]({{< relref "/managing-domains/accessing-the-domain/istio/istio.md" >}}) and other service mesh products.
  * Customers are no longer required to enable this support.

##### Upgrade Notes

* This release introduces a new API version for the Domain resource, `weblogic.oracle/v9`.
  * The previous API version, `weblogic.oracle/v8` introduced with Operator 3.0.0 is deprecated, but is still supported.

* This release also introduces a new custom resource, the `weblogic.oracle/v1` Cluster resource.
  * This Cluster resource configures a specific WebLogic cluster and allows customers to scale that cluster using the Kubernetes Horizontal Pod Autoscaling (HPA) or similar technologies.
  * The Cluster resource `.spec` schema is similar to and replaces the former `weblogic.oracle/v8` Domain resource `.spec.clusters[*]` content.
  * The Cluster resource `.status` schema mirrors the content of the Domain resource `.status.clusters[*]` content.

* This release simplifies [upgrading the operator]({{< relref "/managing-operators/installation.md" >}}).
  * The operator added a [schema conversion webhook]({{< relref "/managing-operators/conversion-webhook.md" >}}).
    * This webhook automates the upgrade of `weblogic.oracle/v8` Domain resources to `weblogic.oracle/v9`.
    * Customers do not need to take any action to begin using the latest Domain schema; however, the team recommends starting with "v9" Domain resources for any new projects.
  * Customers who install a single WebLogic Kubernetes Operator per Kubernetes cluster (most common use case) can upgrade directly from any 3.x operator release to 4.0.0. The Helm chart for 4.0.0 will automatically install the schema conversion webhook.
  * For those customers who install more than one WebLogic Kubernetes Operator in a single Kubernetes cluster:
    * You must upgrade every operator to at least version 3.4.1 before upgrading any operator to 4.0.0.
    * As the 4.0.0 Helm chart now also installs a singleton schema conversion webhook that is shared by all 4.0.0 operators in the cluster,
      you will want to use the `webhookOnly` Helm chart option to install this webhook in its own namespace prior
      to installing any of the 4.0.0 operators,
      and also use the `preserveWebhook` Helm chart option with each operator to prevent an operator uninstall from uninstalling the shared webhook.
  * The operator provides a utility that can be used to convert existing "v8" Domain YAML files to "v9".
  * Several [Helm chart default values have been changed]({{< ref "#changes-to-operator-helm-chart-configuration-values" >}}). Customers who upgrade their 3.x installations using the `--reuse-values` option during the Helm upgrade will continue to use the values from their original installation.

##### Changes to domain schema

* Model in Image auxiliary image related.
  * Added `.spec.configuration.model.auxiliaryImages` for simplified configuration of auxiliary images.
  * Added several additional advanced settings related to auxiliary images including `.spec.configuration.model.auxiliaryImageVolumeMountPath`, `.spec.configuration.model.auxiliaryImageVolumeMedium`, and `.spec.configuration.model.auxiliaryImageVolumeSizeLimit`.
  * Removed `.spec.serverPod.auxiliaryImages` and `.spec.auxiliaryImageVolumes` as part of the simplification effort.
  * Default change for `domain.spec.configuration.model.wdtInstallHome` to `/aux/weblogic-deploy` if `spec.configuration.model.AuxiliaryImages` are specified, and to `/u01/wdt/weblogic-deploy` otherwise. It previously always defaulted to `/u01/wdt/weblogic-deploy`.
  * Default change for `domain.spec.configuration.model.wdtModelHome` to `/aux/models` if `spec.configuration.model.AuxiliaryImages` are specified, and to `/u01/wdt/models` otherwise. It previously always defaulted to `/u01/wdt/models`. 

* Cluster changes.
  * Moved content under `.spec.clusters[*]` to the new Cluster resource `.spec`.
  * Modified `.spec.clusters[*]` to be a list of Cluster resource names.
  * Removed `allowReplicasBelowMinDynClusterSize` field from `.spec.` and `.spec.clusters[*]` as cluster minimums must now be configured through autoscaling.

* Retry and failure tuning related.
  * Added `.spec.failureRetryIntervalSeconds` and `.spec.failureRetryLimitMinutes` for configuration of failure retry timing.
  * Added `.spec.serverPod.maxReadyWaitTimeSeconds` for configuring maximum time before considering servers as stalled during startup.

* Condition status updates.
  * Added or updated Conditions to clearly reflect the current state of the domain:
    * The `Completed` condition is set to `True` when no failures are detected, plus all expected pods are `ready`, at their target image(s), `restartVersion`, and `introspectVersion`.
    * The `Available` condition is set to `True` when a sufficient number of pods are `ready`. This condition can be `True` even when `Completed` is `False`, a `Failed` condition is reported, or a cluster has up to `cluster.spec.maxUnavailable` pods that are not `ready`.
    * The `Failed` condition is set to `True` and its `.message` field is updated when there is a failure.
  * Added `.status.conditions[*].severity` to describe the severity of conditions that represent failures or warnings.
  * Added fields `.status.initialFailureTime` and `.status.lastFailureTime` to enable measuring how long a `Failure` condition has been reported.
  * Removed condition `ServersReady`. Instead use `Available` or `Completed`.
  * Removed condition `Progressing`. Instead:
    * Check `Completed` is `True` to see when a Domain is fully up-to-date and running.
    * Check whether `Available` or `Completed` exist to verify that the operator has noticed the Domain resource.
    * If `Failed` is `True`, then check its `.message` and `.severity` to see if retries have stopped.

* General status updates.
  * Added `.status.observedGeneration` for comparison to `.metadata.generation`; if the two values match, then the status correctly reflects the status of the latest updates to the Domain resource.
  * Renamed `.status.lastIntrospectJobProcessedUid` to `.status.failedIntrospectionUid`.
  * Added `.status.serverStatus[*].podReady` and `.status.serverStatus[*].podPhase` with additional information about the pod associated with each WebLogic Server instance.

* Miscellaneous changes.
  * Added `.spec.logHomeLayout` for configuring the directory layout for WebLogic Server logs.
  * Added `.spec.serverPod.shutdown.waitForAllSessions` to enhance the configuration of server shutdown options.
  * Removed `.spec.configuration.istio` as this section is no longer required.
  * Removed previously deprecated fields `.spec.domainHomeInImage`, `.spec.configOverrides`, and `.spec.configOverrideSecrets`.
    These have been replaced with `.spec.domainHomeSourceType`, `.spec.configuration.overridesConfigMap`, and `.spec.configuration.secrets`.
  * Removed `serverStartState` field from `.spec`, `.spec.adminServer`, `.spec.managedServers[*]`, and `spec.clusters[*]` as this field is no longer needed.
  * Default change for `.spec.adminServer.adminChannelPortForwardingEnabled` to `true`. It previously defaulted to `false`.

##### Changes to operator Helm chart configuration values

* Changed defaults for configuration values.
  * The default for `domainNamespaceSelectionStrategy` is now `LabelSelector`. It was previously `List`.
  * The default for `domainNamespaceLabelSelector` is now `weblogic-operator=enabled`. It was previously unspecified.
  * The default for `enableClusterRoleBinding` is now `true`; however, this value is ignored if `domainNamespaceSelectionStrategy` is `Dedicated`. It was previously `false`.

* Removed configuration value.
  * The previously deprecated value `dedicated` has been removed. Set the `domainNamespaceSelectionStrategy` to `Dedicated` instead.

* New configuration values.
  * Added value `createLogStashConfigMap`, default `true`, that configures how an optional Logstash container for the operator is configured.
  * Added value `webhookOnly`, default `false`, that specifies if this Helm release will install only the schema conversion webhook.
  * Added value `operatorOnly`, default `false`, that specifies if this Helm release will install only the operator.
  * Added value `preserveWebhook`, default to `false`, that specifies if the schema conversion webhook should be orphaned when this Helm release is uninstalled.
  * Added value `webhookDebugHttpPort`, default `31999`, that configures an optional debugging port for the webhook.

##### Minor features and fixes

* The operator added a validating webhook that will help customers detect invalid configurations more quickly.
* The operator's container image is substantially smaller through the use of `jlink` to create a minimal Java JRE.

#### Operator 3.4.4

* Support added to specify resource requests and limits for Monitoring Exporter sidecar containers.
* Resolved an issue that prevented the generation of the `RollCompleted` event.
* Updated scaling and lifecycle scripts to work correctly when used in a cluster with the upcoming WebLogic Kubernetes Operator 4.0 release.
* Updated several dependencies, including SnakeYAML and the Oracle Linux base for the container image.

#### Operator 3.4.3

* Resolved an issue where introspection would fail due to the node manager for non-English locales ([#3328](https://github.com/oracle/weblogic-kubernetes-operator/pull/3328)).
* Resolved an issue related to configuration override files where servers may fail to start because another server was updating files on a shared volume ([#3359](https://github.com/oracle/weblogic-kubernetes-operator/pull/3359)).

#### Operator 3.4.2

* Updated several dependencies, including the Oracle Linux base for the container image.
* Updated the default WebLogic Monitoring Exporter injection to version 2.0.7.

#### Operator 3.4.1

* Better support for retrying introspector `DeadlineExceeded` failures ([#3109](https://github.com/oracle/weblogic-kubernetes-operator/pull/3109)).
* For Model in Image, expand the WDT custom folder from the archive in the domain directory before calling update or create domain ([#3110](https://github.com/oracle/weblogic-kubernetes-operator/pull/3110)).

#### Operator 3.4.0

* Certified support for Kubernetes 1.22.5+ and 1.23.4+.
* Support for injecting [Fluentd sidecar containers]({{< relref "/samples/elastic-stack/weblogic-domain/_index.md" >}}) for WebLogic Server pods and the introspector.
* Resolved an issue where operator Services caused warnings in the Kiali console because of missing protocol designations ([#2957](https://github.com/oracle/weblogic-kubernetes-operator/pull/2957)).
* Updated multiple dependencies, including updating the default WebLogic Monitoring Exporter injection to version 2.0.5.
* Resolved [issue #2794](https://github.com/oracle/weblogic-kubernetes-operator/issues/2794) related to restarting evicted pods ([#2979](https://github.com/oracle/weblogic-kubernetes-operator/pull/2979)).

#### Operator 3.3.8

* Resolved an issue where the WebLogic Server Administration Console is not accessible through port forwarding after upgrade to WebLogic Deploy Tooling (WDT) 2.0 ([#2776](https://github.com/oracle/weblogic-kubernetes-operator/pull/2776)).
* Resolved an issue where the operator would log a SEVERE message about failing to create the CRD even though the creation was successful ([#2772](https://github.com/oracle/weblogic-kubernetes-operator/pull/2772)).
* Domain resource status now correctly displays problems pulling auxiliary container images ([#2681](https://github.com/oracle/weblogic-kubernetes-operator/pull/2681)).
* Resolved an issue related to high CPU usage in the `startServer.sh` script ([#2684](https://github.com/oracle/weblogic-kubernetes-operator/pull/2684)).
* Resolved [issue #2685](https://github.com/oracle/weblogic-kubernetes-operator/issues/2685) related to an NPE while reading server health information ([#2692](https://github.com/oracle/weblogic-kubernetes-operator/pull/2692)).
* Resolved an issue related to the `create-domain.sh` sample script ([#2696](https://github.com/oracle/weblogic-kubernetes-operator/pull/2696)).
* Added validation to reject domain configurations that use the same `serverNamePrefix` for multiple clusters ([#2700](https://github.com/oracle/weblogic-kubernetes-operator/pull/2700)).
* Resolved an issue related to properly handling WDT archive `domainBin` directory updates ([#2704](https://github.com/oracle/weblogic-kubernetes-operator/pull/2704)).
* Restricted HTTP tunneling for Istio related replication channels ([#2754](https://github.com/oracle/weblogic-kubernetes-operator/pull/2754)).

#### Operator 3.3.7

* Resolved an issue related to the incorrect validation of the auxiliary image path when the default location has been overridden ([#2659](https://github.com/oracle/weblogic-kubernetes-operator/pull/2659)).
* Resolved an issue related to preserving auxiliary image logs when the main container crashes ([#2664](https://github.com/oracle/weblogic-kubernetes-operator/pull/2664)).

#### Operator 3.3.6

* Support added for a `hostAliases` field for WebLogic Server pod generation ([#2639](https://github.com/oracle/weblogic-kubernetes-operator/pull/2639)).

#### Operator 3.3.5

* Resolved an issue related to collecting logs for failed Model in Image domains that used auxiliary images ([#2627](https://github.com/oracle/weblogic-kubernetes-operator/pull/2627), [#2629](https://github.com/oracle/weblogic-kubernetes-operator/pull/2629)).
* Resolved an issue related to improperly reading PodDisruptionBudget resources not created by the operator ([#2633](https://github.com/oracle/weblogic-kubernetes-operator/pull/2633)).
* Resolved [issue #2636](https://github.com/oracle/weblogic-kubernetes-operator/issues/2636) related to sample scripts having invalid line endings for the Windows platform ([#2637](https://github.com/oracle/weblogic-kubernetes-operator/pull/2637)).

#### Operator 3.3.4

* Resolved an issue related to Model in Image domains and enabling WebLogic secure mode ([#2616](https://github.com/oracle/weblogic-kubernetes-operator/pull/2616)).

#### Operator 3.3.3

* Resolved an issue related to WebLogic cluster replication when using Istio 1.10 ([#2582](https://github.com/oracle/weblogic-kubernetes-operator/pull/2582)).
* Resolved several issues related to introspector failure, retry, and status ([#2564](https://github.com/oracle/weblogic-kubernetes-operator/pull/2564), [#2571](https://github.com/oracle/weblogic-kubernetes-operator/pull/2571), [#2578](https://github.com/oracle/weblogic-kubernetes-operator/pull/2578), [#2580](https://github.com/oracle/weblogic-kubernetes-operator/pull/2580)).

#### Operator 3.3.2

* Support for the networking changes included with Istio 1.10 ([#2538](https://github.com/oracle/weblogic-kubernetes-operator/pull/2538)).
* Support for accessing the WebLogic Server Administration Console through `kubectl port-forward` ([#2520](https://github.com/oracle/weblogic-kubernetes-operator/pull/2520)).
* Prevent insecure file system warnings related to the "umask 027" requirement ([#2533](https://github.com/oracle/weblogic-kubernetes-operator/pull/2533)).
* Enhanced [liveness and readiness probe customization](https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-lifecycle/liveness-readiness-probe-customization/) to support customizing failure thresholds ([#2521](https://github.com/oracle/weblogic-kubernetes-operator/pull/2521)).
* Additional validation for container port names and WebLogic Network Access Point (NAP) names that will be used as container ports ([#2542](https://github.com/oracle/weblogic-kubernetes-operator/pull/2542)).

#### Operator 3.3.1

* Resolved an issue related to managed Coherence cluster formation when using Istio ([#2499](https://github.com/oracle/weblogic-kubernetes-operator/pull/2499)).
* Resolved an issue related to generating the internal certificate when using Istio ([#2486](https://github.com/oracle/weblogic-kubernetes-operator/pull/2486)).
* Resolved an issue related to validating Secrets and ConfigMaps referenced by a Domain when the namespace has a larger number of such resources ([#2500](https://github.com/oracle/weblogic-kubernetes-operator/pull/2500)).

#### Operator 3.3.0

* [Auxiliary images support](https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/model-in-image/auxiliary-images/).
* Resolved an issue related to Event creation failure with the error: "StorageError: invalid object, Code: 4" ([#2443](https://github.com/oracle/weblogic-kubernetes-operator/pull/2443)).
* Improved the ability of the operator to use an existing introspection ([#2430](https://github.com/oracle/weblogic-kubernetes-operator/pull/2430)).
* Upgraded core dependency versions, including upgrading the Kubernetes Java Client to 13.0.0 ([#2466](https://github.com/oracle/weblogic-kubernetes-operator/pull/2466)).
* Corrected documentation of roles necessary for the scaling script ([#2464](https://github.com/oracle/weblogic-kubernetes-operator/pull/2464)).
* All fixes included in 3.2.1 through 3.2.5 are included in 3.3.0.

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
* [Online updates]({{<relref "/managing-domains/model-in-image/runtime-updates/_index.md#online-updates">}}) of dynamic configuration changes for Model in Image.
* Automatic injection of the [WebLogic Monitoring Exporter](https://github.com/oracle/weblogic-monitoring-exporter) as a sidecar container.
* [Events]({{< relref "/managing-domains/accessing-the-domain/domain-events.md" >}}) are generated at important moments in the life cycle of the operator or a domain.
* [PodDisruptionBudgets]({{<relref "/managing-domains/domain-lifecycle/startup.md#draining-a-node-and-poddisruptionbudget">}}) are generated for clusters improving the ability to maintain cluster availability during planned node shutdowns and Kubernetes upgrade.
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
* Sample [scripts to start and stop server instances]({{< relref "/managing-domains/domain-lifecycle/startup#domain-life-cycle-sample-scripts" >}}) ([#2002](https://github.com/oracle/weblogic-kubernetes-operator/pull/2002)).
* Support running with [OpenShift restrictive SCC]({{< relref "/security/openshift#create-a-custom-security-context-constraint" >}}) ([#2007](https://github.com/oracle/weblogic-kubernetes-operator/pull/2007)).
* Updated [default resource and Java options]({{< relref "/faq/resource-settings.md" >}}) ([#1775](https://github.com/oracle/weblogic-kubernetes-operator/pull/1775)).
* Introspection failures are logged to the operator's log ([#1787](https://github.com/oracle/weblogic-kubernetes-operator/pull/1787)).
* Mirror introspector log to a rotating file in the log home ([#1827](https://github.com/oracle/weblogic-kubernetes-operator/pull/1827)).
* Reflect introspector status to domain status ([#1832](https://github.com/oracle/weblogic-kubernetes-operator/pull/1832)).
* Ensure operator detects pod state changes even when watch events are not delivered ([#1811](https://github.com/oracle/weblogic-kubernetes-operator/pull/1811)).
* Support configurable WDT model home ([#1828](https://github.com/oracle/weblogic-kubernetes-operator/pull/1828)).
* [Namespace management enhancements]({{< relref "/managing-operators/namespace-management.md" >}}) ([#1860](https://github.com/oracle/weblogic-kubernetes-operator/pull/1860)).
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

* The operator now correctly completes restarting Managed Server pods to complete a rolling activity. This fix is already present in 3.1.0.

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

| Issue | Description                                                                                                                                                                                                                                         |
| --- |-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Deadlock on WebLogic Managed Coherence Server startup with Oracle Coherence 12.2.1.3.20. | Intermittently, a deadlock may occur during deployment of the `CoherenceModule`, which prevents a WebLogic Managed Coherence Server from reaching `RUNNING` state.  This issue has been resolved in Oracle Coherence versions 12.2.1.4.0 and later. |
