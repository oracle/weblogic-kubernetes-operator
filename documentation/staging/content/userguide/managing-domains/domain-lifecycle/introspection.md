---
title: "Domain introspection"
date: 2020-07-07T08:14:51-05:00
draft: false
weight: 5
description: "This document describes domain introspection in the Oracle WebLogic Server in Kubernetes environment."
---

#### Contents

- [Overview](#overview)
- [When introspection occurs automatically](#when-introspection-occurs-automatically)
- [Initiating introspection](#initiating-introspection)
- [Failed introspection](#failed-introspection)
- [Introspection use cases](#introspection-use-cases)

#### Overview

This document describes domain introspection, when it occurs automatically, and how and when to initiate additional introspections of the domain configuration in the Oracle WebLogic Server in Kubernetes environment.

In order to manage the operation of WebLogic domains in Kubernetes, the Oracle WebLogic Kubernetes Operator analyzes the WebLogic
domain configuration using an "introspection" job. This Job will be named `DOMAIN_UID-introspector`, will be run in the same namespace as the Domain, and must successfully complete before the operator will begin to start WebLogic Server instances. Because each of the
[domain home source types]({{< relref "/userguide/managing-domains/choosing-a-model/_index.md" >}}) are different (for instance, Domain in PV uses a domain home on a PersistentVolume while Model in Image generates the domain home dynamically from a WDT model), the Pod created by this Job will be
as similar as possible to the Pod that will later be generated for the Administration Server. This guarantees that the operator is
analyzing the same WebLogic domain configuration that WebLogic Server instances will use.

Introspection ensures that:
1. The operator is aware of domain topology from the WebLogic domain configuration, including servers, clusters, network access points, listen addresses, and other configurations.
2. The operator can generate configuration overrides to adjust the WebLogic domain configuration to match the Kubernetes environment, such as modifying listen addresses.
3. For Model in Image, the operator can generate the WebLogic domain home, including the final domain configuration.
4. For Domain in PV and Domain in Image, the operator can use any customer-provided [configuration overrides]({{<relref "/userguide/managing-domains/configoverrides/_index.md">}}) along with the operator-generated overrides to generate the final configuration overrides.

#### When introspection occurs automatically

Introspection automatically occurs when:
1. The operator is starting a WebLogic Server instance when there are currently no other servers running. This occurs when the operator first starts servers for a domain or when starting servers following a full domain shutdown.
2. For Model in Image, the operator determines that at least one WebLogic Server instance that is currently running must be shut down and restarted. This could be a rolling of one or more clusters, the shut down and restart of one or more WebLogic Server instances, or a combination.

#### Initiating introspection

Sometimes, such as for the [use cases](#introspection-use-cases) described below, it is desirable to explicitly initiate introspection. To initiate introspection, change the value of your Domain `introspectVersion` field.

Set `introspectVersion` to a new value.

```yaml
  kind: Domain
  metadata:
    name: domain1
  spec:
    introspectVersion: "2"
    ...
```

As with `restartVersion`, the `introspectVersion` field has no required format; however, we recommend using a value likely to be unique such as a continually increasing number or a timestamp.

Beginning with operator 3.1.0, if a domain resource's `spec.introspectVersion` is set, each of the domain's WebLogic Server pods will have a label with the key `weblogic.introspectVersion` to indicate the `introspectVersion` at which the pod is running.

```
Name:           domain1-admin-server
Namespace:      domain1-ns
Labels:         weblogic.createdByOperator=true
                weblogic.domainName=domain1
                weblogic.domainRestartVersion=abcdef
                weblogic.domainUID=domain1
                weblogic.introspectVersion=12345
                weblogic.serverName=admin-server
```

When a domain's `spec.introspectVersion` is changed, the `weblogic.introspectVersion` label of each WebLogic Server pod is updated to the new `introspectVersion` value, either when the operator restarts the pod or when the operator determines that the pod does not need to be restarted.

#### Failed introspection

Sometimes the Kubernetes Job, named `DOMAIN_UID-introspector`, created for the introspection will fail.

When introspection fails, the operator will not start any WebLogic Server instances. If this is not the initial introspection and there are already WebLogic Server instances running, then a failed introspection will leave the existing WebLogic Server instances running without making any changes to the operational state of the domain.

The introspection will be periodically retried and then will eventually timeout with the Domain `status` indicating the processing failed. To recover from a failed state, correct the underlying problem and update the `introspectVersion`.

Please review the details for diagnosing introspection failures related to [configuration overrides]({{<relref "/userguide/managing-domains/configoverrides/_index.md#debugging">}}) or [Model in Image domain home generation]({{<relref "/userguide/managing-domains/model-in-image/debugging.md">}}).

{{% notice tip %}}
The introspector log is mirrored to the Domain resource `spec.logHome` directory
when `spec.logHome` is configured and `spec.logHomeEnabled` is true.
{{% /notice %}}

### Introspection use cases

The following sections describe typical use cases for rerunning the introspector.

#### Adding clusters or Managed Servers to a Domain in PV configuration

When you have an existing WebLogic domain home on a persistent volume (Domain in PV) and you currently have WebLogic Server instances running, it is now possible to define new WebLogic clusters or Managed Servers in the domain configuration and start these new instances without affecting the life cycle of any WebLogic Server instances that are already running.

Prior to operator 3.0.0, this was not possible because there was no mechanism to initiate introspection other than a full domain shut down and restart and so the operator was unaware of the new clusters or Managed Servers. Now, after updating the domain configuration, you can initiate introspection by changing the `introspectVersion`.

For instance, if you had a domain configuration with a single cluster named "cluster-1" then your Domain YAML file may have content like this:

```yaml
spec:
  ...
  clusters:
    - clusterName: cluster-1
      replicas: 3
  ...
```

If you modified your WebLogic domain configuration (using the console or WLST) to add a new dynamic cluster named "cluster-2", then you could immediately start cluster members of this new cluster by updating your Domain YAML file like this:

```yaml
spec:
  ...
  clusters:
    - clusterName: cluster-1
      replicas: 3
    - clusterName: cluster-2
      replicas: 2
  introspectVersion: "2"
  ...
```

When this updated Domain YAML file is applied, the operator will initiate a new introspection of the domain configuration during which it will learn about the additional WebLogic cluster and then the operator will continue to start WebLogic Server instances that are members of this new cluster. In this case, the operator will start two Managed Servers that are members of the cluster named "cluster-2".

#### Distributing changes to configuration overrides

The operator supports customer-provided [configuration overrides]({{<relref "/userguide/managing-domains/configoverrides/_index.md">}}). These configuration overrides, which are supported with Domain in PV or Domain in Image, allow you to override elements of the domain configuration, such as data source URL's or credentials.

With operator 3.0.0, you can now change the configuration overrides and distribute these new configuration overrides to already running WebLogic Server instances. To do this, update the ConfigMap that contains the configuration overrides or update one or more of the Secrets referenced by those configuration overrides and then initiate introspection by changing the `introspectVersion` field.

We have introduced a new field, called `overrideDistributionStrategy` and located under `configuration`, that controls whether updated configuration overrides are distributed dynamically to already running WebLogic Server instances or if the new configuration overrides are only applied when servers are started or restarted.

The default value for `overrideDistributionStrategy` is DYNAMIC, which means that new configuration overrides are distributed dynamically to already running WebLogic Server instances.

Alternately, you can set `overrideDistributionStrategy` to ON_RESTART, which means that the new configuration overrides will not be distributed to already running WebLogic Server instances, but will instead be applied only to servers as they start or restart. Use of this value will *not* cause WebLogic Server instances to restart absent changes to other fields, such as `restartVersion`.

{{% notice note %}} Changes to configuration overrides distributed to running WebLogic Server instances can only take effect if the corresponding WebLogic configuration MBean attribute is "dynamic". For instance, the Data Source "passwordEncrypted" attribute is dynamic while the "Url" attribute is non-dynamic.
{{% /notice %}}

#### Distributing changes to running Model in Image domains

The operator supports rerunning the introspector in order to propagate [model updates]({{<relref "/userguide/managing-domains/model-in-image/runtime-updates.md">}}) to a running Model in Image domain.
