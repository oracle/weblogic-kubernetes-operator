---
title: "Restarting"
date: 2019-02-23T20:58:51-05:00
draft: false
weight: 2
description: "This document describes when WebLogic Server instances should and will be restarted in the Kubernetes environment."
---


This document describes when WebLogic Server instances should and will be restarted in the Kubernetes environment.

#### Overview

There are many situations where changes to the WebLogic or Kubernetes environment configuration require that all the servers in
a domain or cluster be restarted, for example, when applying a WebLogic Server patch or when upgrading an application.

One of the operator's most important jobs is to start and stop WebLogic Server instances by creating and deleting their corresponding Kubernetes pods. Sometimes, you need to make changes that make the pods obsolete, therefore the pods need to be deleted and recreated. Depending on the change, often the pods can be gradually recreated, without taking the domain or cluster out of service
(for example, "rolling restarts") and sometimes all the pods need to be deleted and then recreated as part of a downtime (for example, "full restarts").

The following types of server restarts are supported by the operator:

* Rolling restarts - a coordinated and controlled shut down of all of the servers in a domain or cluster while ensuring that service to the end user is not interrupted.

   * Operator initiated - where the WebLogic Server Kubernetes Operator can detect some types of changes and will automatically initiate rolling restarts of pods in a domain or cluster.

   * Manually initiated - required when certain changes in the Oracle WebLogic Server in Kubernetes environment cannot be detected by the operator, so a rolling restart must be manually initiated.

* Full domain restarts - the Administration Server and all the Managed Servers in a domain are shutdown, impacting service availability to the end user, and then restarted.  Unlike a rolling restart, the operator cannot detect and initiate a full domain restart; it must always be manually initiated.

For detailed information on how to restart servers using the operator, see [Starting, stopping, and restarting servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md" >}}).

#### Common restart scenarios

This document describes what actions you need to take to properly restart your servers for a number of common scenarios:

* Modifying the WebLogic domain configuration
* Changing the domain configuration overrides (also called situational configuration) for Domain in PV and Domain in Image domains
* Changing the model files for Model in Image domains
* Changing the WebLogic Server credentials (the user name and password)
* Changing fields on the Domain that [affect WebLogic Server instance Pod generation]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#fields-that-cause-servers-to-be-restarted" >}}) (such as `image`, `volumes`, and `env`)
* Applying WebLogic Server patches
* Updating deployed applications for Domain in Image or Model in Image

### Use cases

#### Modifying the WebLogic domain configuration

Changes to the WebLogic domain configuration may require either a rolling or full domain restart depending on the domain home location and the type of configuration change.

##### Domain in Image

For Domain in Image, you may only perform a rolling restart if both the WebLogic configuration changes between the present image and a new image are dynamic and you have [followed the CI/CD guidelines]({{< relref "/userguide/cicd/mutate-the-domain-layer">}}) to create an image with compatible encryption keys.

Otherwise, use of a new image that does not have compatible encryption keys or any non-dynamic configuration changes require a full domain restart. 
 
* If you create a new image with a new name, then you must avoid a rolling restart, which can cause unexpected behavior for the running domain due to configuration inconsistencies as seen by the various servers, by following the steps in [Avoiding a rolling restart when changing image field on a Domain](#avoiding-a-rolling-restart-when-changing-image-field-on-a-domain).
* If you create a new image with the same name, then you must manually initiate a full domain restart. See [Full domain restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts">}}).

##### Model in Image

* Any image that supplies configuration changes that are incompatible with the current running domain require a full shut down before changing the Domain `image` field, instead of a rolling restart. For changes that support a rolling restart, see [Supported and unsupported updates]({{< relref "/userguide/managing-domains/model-in-image/runtime-updates/_index.md#supported-and-unsupported-updates" >}}).

* If you create a new image with a new name, and you want to avoid a rolling restart, see [Avoiding a rolling restart when changing the image field on a Domain](#avoiding-a-rolling-restart-when-changing-the-image-field-on-a-domain).

* If you create a new image with the same name, then you must manually initiate either a full domain restart or rolling restart for pods to run with the new image. To initiate a full restart, see [Full domain restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts">}}). To initiate a rolling restart, change the value of your Domain `restartVersion` field.  See [Restarting servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#restarting-servers" >}}) and [Rolling restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#rolling-restarts" >}}).

* If you are supplying updated models or Secrets for a running domain, and you want the configuration updates to take effect using a rolling restart, then do one of the following:
  * Supply a new value for the `image` field in the Domain or any of the other [fields affecting WebLogic Server instance Pod generation]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#fields-that-cause-servers-to-be-restarted" >}}).
  * Change the Domain `restartVersion` field. This will cause the operator to restart all running servers and, prior to the restarts, the operator will introspect any new configuration.
  * Change the Domain `introspectVersion` field. This will cause the operator to introspect any new configuration and, if needed, restart servers to use that new configuration.

##### Domain in PV

For Domain in PV, the type of restart needed depends on the nature of the WebLogic domain configuration change:
* Domain configuration changes that add new clusters (either configured or dynamic), member servers for these new clusters, or non-clustered servers can now be performed dynamically. This support requires that the new clusters or servers are added to the domain configuration and then that you [initiate the operator's introspection]({{< relref "/userguide/managing-domains/domain-lifecycle/introspection.md" >}}) of that new configuration.
* Other changes to parts of the domain configuration that the operator introspects, require a full shutdown and restart, even if the changes are dynamic for WebLogic Server, such as:
  * Adding or removing a network access point
  * Adding a server to an existing cluster
  * Changing a cluster, server, dynamic server, or network access point name
  * Enabling or disabling the listen port, SSL port, or admin port
  * Changing any port numbers
  * Changing a network access point's public address
* Other dynamic WebLogic configuration changes do not require a restart. For example, a change to a server's connection timeout property is dynamic and does not require a restart.
* Other non-dynamic domain configuration changes require either a manually initiated rolling restart or a full domain shut down and restart, depending on the nature of the change.
  * For example, a rolling restart is applicable when changing a WebLogic Server `stuck thread timer interval` property. See [Restart all the servers in the domain]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#restart-all-the-servers-in-the-domain" >}}).

{{% notice note %}} The preceding description of the operator's life cycle of responding to WebLogic domain configuration changes applies to version 3.0.0 and later. Prior to operator version 3.0.0, while you could make changes to WebLogic domain configuration using the Administration Console or WLST, the operator would only detect and respond to those changes following a full domain shut down and restart.
{{% /notice %}}

#### Changing the domain configuration overrides

Beginning with operator version 3.0.0, many changes to domain configuration overrides can be applied dynamically or as part of a rolling restart. Previously, any changes to the configuration overrides required a full domain shutdown and restart.
Changes to configuration overrides include:

* Changing the Domain YAML file's `configuration.overridesConfigMap` to point to a different ConfigMap
* Changing the Domain YAML file's `configuration.secrets` to point to a different list of Secrets
* Changing the contents of the ConfigMap referenced by `configuration.overridesConfigMap`
* Changing the contents to any of the Secrets referenced by `configuration.secrets`
  
The changes to the above fields or contents of related resources are not processed automatically. Instead, these fields are processed only when you [initiate operator introspection]({{< relref "/userguide/managing-domains/domain-lifecycle/introspection.md" >}}). The operator then will apply the new configuration overrides dynamically or only apply the overrides when WebLogic Server instances restart, depending on the strategy that you select.

{{% notice note %}} Changes to configuration overrides distributed to running WebLogic Server instances can only take effect if the corresponding WebLogic configuration MBean attribute is "dynamic". For instance, the Data Source "passwordEncrypted" attribute is dynamic while the "Url" attribute is non-dynamic.
{{% /notice %}}

#### Changing the WebLogic Server credentials

A change to the WebLogic Server credentials (the user name and password), contained in the Kubernetes Secret for the domain, requires a
_full domain restart_.  The Kubernetes Secret can be updated directly or a new Secret can be created and then referenced by the `webLogicCredentialsSecret`
field in the Domain YAML file.

#### Changing fields on the Domain that affect WebLogic Server instance Pods

The operator will initiate a rolling restart of the domain when you modify any of the Domain YAML file fields that affect the WebLogic Server instance Pod generation,
such as `image`, `volumes`, and `env`.  For a complete list, see [Fields that cause servers to be restarted]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#fields-that-cause-servers-to-be-restarted" >}}).

You can modify these fields using the `kubectl` command-line tool's `edit` and `patch` commands or through the Kubernetes REST API.

For example, to edit the Domain YAML file directly using the `kubectl` command-line tool:

```
kubectl edit domain <domain name> -n <domain namespace>
```

The `edit` command opens a text editor which lets you edit the Domain in place.

{{% notice note %}}
Typically, it's better to edit the Domain YAML file directly; otherwise, if you scaled the domain, and you edit only the original `domain.yaml` file and reapply it, you could go back to your old replicas count.
{{% /notice %}}

#### Applying WebLogic Server patches

Oracle provides different types of patches for WebLogic Server, such as Patch Set Updates, Security Patch Updates, and One-Off patches.
Information on whether a patch is rolling-compatible or requires a manual full domain restart usually can be found in the patch's documentation, such as the README file.

WebLogic Server patches can be applied to either a domain home in image or a domain home on PV.

With rolling-compatible patches:

* If you update the `image` property with a new image name, then the operator will initiate a rolling restart.
* If you keep the same image name, then you must manually initiate a rolling restart. See [Restart all the servers in the domain]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#restart-all-the-servers-in-the-domain" >}}).

With patches that are not rolling-compatible:

* If you keep the same image name, then you must manually initiate a full domain restart. See [Full domain restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts" >}}).
* If you update the `image` property with a new image name, then you must avoid the rolling restart by following the steps in [Avoiding a rolling restart when changing the image field on a Domain](#avoiding-a-rolling-restart-when-changing-the-image-field-on-a-domain).

#### Updating deployed applications

Frequent updates of deployed applications using a [continuous integration/continuous delivery]({{< relref "/userguide/cicd/mutate-the-domain-layer">}}) (CI/CD) process is a very common use case.
The process for applying an updated application is different for domain home in image and model in image than it is for domain home on PV.
A rolling-compatible application update is where some servers are running the old version and some are running the new version
of the application during the rolling restart process. On the other hand, an application update that is not rolling-compatible requires that all the servers
in the domain be shut down and restarted.

If the application update is rolling-compatible:

* If you update the `image` property with a new image name, then the operator will initiate a rolling restart.
* If you keep the same image name, then you must manually initiate a rolling restart. See [Restart all the servers in the domain]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#restart-all-the-servers-in-the-domain">}}).

If the application update is not rolling-compatible:

* If you keep the same image name, then you must manually initiate a full domain restart. See [Full domain restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts">}}).
* If you update the `image` property with a new image name, then you must avoid the rolling restart by following the steps in [Avoiding a rolling restart when changing the image field on a Domain](#avoiding-a-rolling-restart-when-changing-the-image-field-on-a-domain).

#### Rolling out an updated domain home in image or model in image

Follow these steps to create new rolling-compatible image if you only need to patch your WebLogic Server domain or update application deployment files:

a. Select a different name for the new image.

b. For Domain in Image, it is important to keep your original domain home in your new image.

Using the same domain home-in-image image as a base, create a new image by copying (`COPY`
command in a Dockerfile) the updated application deployment files or WebLogic Server patches into the image during the image build.

{{% notice note %}}
The key here is to make sure that you do not re-run WLST or WDT to create a new domain home even though it will
    have the same configuration. Creating a new domain will change the domain encryption secret and you won't be able to do a
    rolling restart.
{{% /notice %}}

c. Deploy the new image to your container registry with the new name.

d. Update the `image` field of the Domain YAML file, specifying the new image name.

   For example:

     ```
     domain:
       spec:
         image: ghcr.io/oracle/weblogic-updated:3.2.0
     ```
e. The operator will now initiate a rolling restart, which will apply the updated image, for all the servers in the domain.

#### Avoiding a rolling restart when changing the image field on a Domain
If you've created a new image that is not rolling-compatible, and you've changed the image name, then:

1. Bring the domain down (stopping all the server pods) by setting the `serverStartPolicy` to `NEVER`. See [Shut down all the servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#shut-down-all-the-servers">}}).

2. Update the `image` property with a new image name.

3. Start up the domain (starting all the server pods) by setting the `serverStartPolicy` to `IF_NEEDED`.

#### Other considerations for restarting a domain

* **Consider the order of changes**:

    If you need to make multiple changes to your domain at the same time, you'll want to be careful
    about the order in which you do your changes, so that servers aren't restarted prematurely or restarted needlessly.
    For example, if you want to change the readiness probe's tuning parameters and the Java options (both of which are rolling-compatible), then you should update the Domain YAML file once, changing both values,
    so that the operator rolling restarts the servers once.  Or, if you want to change the readiness probe's tuning parameters (which is rolling-compatible)
    and change the domain customizations (which require a full restart), then you should do a full shutdown first,
    then make the changes, and then restart the servers.  

    Alternatively, if you know that your set of changes are not rolling-compatible, then you must avoiding a rolling restart by:

     1. Bringing the domain down (stopping all the server pods) by setting the `serverStartPolicy` to `NEVER`. See [Shut down all the servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#shut-down-all-the-servers">}}).

     2. Make all your changes to the Oracle WebLogic Server in Kubernetes environment.

     3. Starting up the domain (starting all the server pods) by setting the `serverStartPolicy` to `IF_NEEDED`.

* **Changes that require domain knowledge**.

    Sometimes you need to make changes that require server restarts, yet the changes are not to the domain configuration,
    the image, or the Kubernetes resources that register your domain with the operator.  For example, your servers are caching information from an external database and you've modified the contents of the database.

    In these cases, you must manually initiate a restart.

* **Managed Coherence Servers safe shut down**.

    If the domain is configured to use a Coherence cluster, then you will need to increase the Kubernetes graceful timeout value.
    When a server is shut down, Coherence needs time to recover partitions and rebalance the cluster before it is safe to shut down a second server.
    Using the Kubernetes graceful termination feature, the operator will automatically wait until the Coherence `HAStatus` MBean attribute
    indicates that it is safe to shut down the server.  However, after the graceful termination timeout expires, the pod will be deleted regardless.
    Therefore, it is important to set the domain YAML `timeoutSeconds` to a large enough value to prevent the server from shutting down before
    Coherence is safe. Furthermore, if the operator is not able to access the Coherence MBean, then the server will not be shut down
    until the domain `timeoutSeconds` expires.  To minimize any possibility of cache data loss, you should increase the `timeoutSeconds`
    value to a large number, for example, 15 minutes.
