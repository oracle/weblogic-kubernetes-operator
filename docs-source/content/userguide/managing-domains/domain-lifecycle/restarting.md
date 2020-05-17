---
title: "Restarting"
date: 2019-02-23T20:58:51-05:00
draft: false
weight: 2
description: "This document describes when to restart servers in the Oracle WebLogic Server in Kubernetes environment."
---


This document describes _when_ to restart servers in the Oracle WebLogic Server in Kubernetes environment.

#### Overview

There are many situations where changes to the Oracle WebLogic Server in Kubernetes environment require that all the servers in
a domain or cluster be restarted, for example, when applying a WebLogic Server patch or when upgrading an application.

One of the operator's most important jobs is to start and stop WebLogic Servers by creating and deleting their corresponding Kubernetes Pods. Sometimes, you need to make changes that make the pods obsolete, therefore the pods need to be deleted and recreated. Depending on the change, sometimes the pods can be gradually recreated, without taking the entire domain out of service
(for example, `rolling restarts`) and sometimes all the pods need to be deleted then recreated, taking the entire domain out of
service for a while (for example, `full restarts`).

The following types of server restarts are supported in Oracle WebLogic Server in Kubernetes:

* Rolling restarts - a coordinated and controlled shut down of all of the servers in a domain or cluster while ensuring that service to the end user is not interrupted.

   * Operator initiated - where the WebLogic Server Kubernetes Operator can detect some types of changes and will automatically initiate rolling restarts of server pods in a WebLogic domain.

   * Manually initiated - required when certain changes in the Oracle WebLogic Server in Kubernetes environment cannot be detected by the operator, so a rolling restart must be manually initiated.

* Full domain restarts - the Administration Server and all the Managed Servers in a domain are shutdown, impacting service availability to the end user, and then restarted.  Unlike a rolling restart, the operator cannot detect and initiate a full domain restart; it must always be manually initiated.

For detailed information on how to restart servers in a Oracle WebLogic Server in Kubernetes environment, see [Starting, stopping, and restarting servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md" >}}).

#### Common restart scenarios

This document describes what actions you need to take to properly restart your servers for a number of common scenarios:

* Modifying the WebLogic configuration
* Changing the custom domain configuration overrides (also called situational configuration) for Domain in PV and Domain in Image domains
* Changing the model files for Model in Image domains
* Changing the WebLogic Server credentials (the user name and password)
* Changing properties on the domain resource that affect server pods (such as `image`, `volumes`, and `env`)
* Applying WebLogic Server patches
* Updating deployed applications for domain home in image

### Use cases

#### Modifying the WebLogic Server configuration

Changes to the Oracle WebLogic Server configuration may require either a rolling or full domain restart depending on the domain home location and the type of configuration change.

* **Domain in Image:**
For a domain home in image, any changes (dynamic or non-dynamic) to the WebLogic configuration requires a full domain restart.  
    * If you create a new image with a new name, then you must avoid a rolling restart, which can cause unexpected behavior for the running domain due to configuration inconsistencies as seen by the various servers, by following the steps in [Avoiding a rolling restart when changing image property on a domain resource](#avoiding-a-rolling-restart-when-changing-image-property-on-a-domain-resource).
    * If you create a new image with the same name, then you must manually initiate a full domain restart. See [Full domain restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts" >}}).

* **Model in Image:**

    * Any image that supplies configuration changes that are incompatible with the current running domain require a full shutdown before changing the domain resource image setting, instead of a rolling restart. For changes that support a rolling restart, see [Supported and unsupported updates]({{< relref "/userguide/managing-domains/model-in-image/runtime-updates/_index.md#supported-and-unsupported-updates" >}}).

    * If you create a new image with a new name, and you want to avoid a rolling restart, see [Avoiding a rolling restart when changing image property on a domain resource](#avoiding-a-rolling-restart-when-changing-image-property-on-a-domain-resource).

    * If you create a new image with the same name, then you must manually initiate either a full domain restart or rolling restart for pods to run with the new image. To initiate a full restart, see [Full domain restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts" >}}). To initiate a rolling restart, change the value of your domain resource `restartVersion` field.  See [Restarting servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#restarting-servers" >}}) and [Rolling restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#rolling-restarts" >}}).

    * If you are supplying updated models or secrets for a running domain, and you want the configuration updates to take effect using a rolling restart:
      * You must either supply a new image name in the domain resource or change the domain resource `restartVersion` in order to force the operator to reload the configuration.
      * With either of these two changes, the operator will rerun the domain's introspector job, which will verify and apply the new configuration. If the introspector job's configuration verification succeeds, then it will subsequently roll (restart) the pods; if the job fails, then a roll will not occur.
      * If you change other fields that typically cause a restart, such as `volumes`, `env`, and such, then the introspector job will not rerun and a rolling restart will proceed without loading the configuration changes.

* **Domain in PV:**
For a domain home on PV, the type of restart needed to apply the changes depends on the nature of the WebLogic configuration change:
    * Changes to parts of the WebLogic configuration that the operator introspects, require a full restart, even if the changes are dynamic.
      The following are the types of changes to the WebLogic Server configuration that the operator introspects:
        * Adding or removing a cluster, server, dynamic server, or network access point
        * Changing a cluster, server, dynamic server, or network access point name
        * Enabling or disabling the listen port, SSL port, or admin port
        * Changing any port numbers
        * Changing a network access point's public address
    * Other dynamic WebLogic configuration changes do not require a restart.  For example, a change to a server's connection timeout property
is dynamic and does not require a restart.
    * Other non-dynamic WebLogic configuration changes require either a manually initiated rolling restart or a full domain restart, depending on the nature of the change.
      For example, a rolling restart is applicable when changing a WebLogic Server `stuck thread timer interval` property. See [Restart all the servers in the domain]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#restart-all-the-servers-in-the-domain" >}}).


#### Changing the custom domain configuration overrides

Any change to domain configuration overrides requires a full domain restart.  This includes:

  * Changing the domain resource's `configuration.overridesConfigMap` to point to a different configuration map
  * Changing the domain resource's `configuration.secrets` to point to a different list of secrets
  * Changing the contents of the configuration map referenced by `configuration.overridesConfigMap`
  * Changing the contents to any of the secrets referenced by `configuration.secrets`

#### Changing the WebLogic Server credentials

A change to the WebLogic Server credentials (the user name and password), contained in the Kubernetes Secret for the domain, requires a
_full domain restart_.  The Kubernetes Secret can be updated directly or a new secret can be created and then referenced by the `webLogicCredentialsSecret`
property in the domain resource.

#### Changing properties on the domain resource that affect server pods

The operator will initiate a rolling restart of the domain when you modify any of the domain resource properties that affect the server pods configuration,
such as `image`, `volumes`, and `env`.  For a complete list, see [Properties that cause servers to be restarted]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#properties-that-cause-servers-to-be-restarted" >}}).

You can modify these properties using the `kubectl` command-line tool's `edit` and `patch` commands or through the Kubernetes REST API.

For example, to edit the domain resource directly using the `kubectl` command-line tool:

```
kubectl edit domain <domain name> -n <domain namespace>
```

The `edit` command opens a text editor which lets you edit the domain resource in place.

{{% notice note %}}
Typically, it's better to edit the domain resource directly; otherwise, if you scaled the domain, and you edit only the original `domain.yaml` file and reapply it, you could go back to your old replicas count.
{{% /notice %}}

#### Applying WebLogic Server patches

Oracle provides different types of patches for WebLogic Server, such as Patch Set Updates, Security Patch Updates, and One-Off patches.
Information on whether a patch is rolling-compatible or requires a manual full domain restart usually can be found in the patch's documentation, such as the README file.

WebLogic Server patches can be applied to either a domain home in image or a domain home on PV.

With rolling-compatible patches:

* If you update the `image` property with a new image name, then the operator will initiate a rolling restart.
* If you keep the same image name, then you must manually initiate a rolling restart. See [Restart all the servers in the domain]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#restart-all-the-servers-in-the-domain" >}}).

With patches that are not rolling-compatible:

* If you keep the same image name, then you must manually initiate a full domain restart. See [Full domain restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts">}}).
* If you update the `image` property with a new image name, then you must avoid the rolling restart by following the steps in [Avoiding a rolling restart when changing image property on a domain resource](#avoiding-a-rolling-restart-when-changing-image-property-on-a-domain-resource).

#### Updating deployed applications

Frequent updates of deployed applications using a continuous integration/continuous delivery (CI/CD) process is a very common use case.
The process for applying an updated application is different for domain home in image and model in image than it is for domain home on PV.
A rolling-compatible application update is where some servers are running the old version and some are running the new version
of the application during the rolling restart process. On the other hand, an application update that is not rolling-compatible requires that all the servers
in the domain be shut down and restarted.

If the application update is rolling-compatible:

* If you update the `image` property with a new image name, then the operator will initiate a rolling restart.
* If you keep the same image name, then you must manually initiate a rolling restart. See [Restart all the servers in the domain]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#restart-all-the-servers-in-the-domain">}}).

If the application update is not rolling-compatible:

* If you keep the same image name, then you must manually initiate a full domain restart. See [Full domain restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts">}}).
* If you update the `image` property with a new image name, then you must avoid the rolling restart by following the steps in [Avoiding a rolling restart when changing image property on a domain resource](#avoiding-a-rolling-restart-when-changing-image-property-on-a-domain-resource).

#### Rolling out an updated domain home in image or model in image

Follow these steps to create new rolling-compatible image if you only need to patch your WebLogic Server domain or update application deployment files:

a. Select a different name for the new image.

b. For domain home in image domains, it is important to keep your original domain home in your new image.

Using the same domain home-in-image Docker image as a base, create a new Docker image by copying (`COPY`
command in a Dockerfile) the updated application deployment files or WebLogic Server patches into the Docker image during the Docker image build.

{{% notice note %}}
The key here is to make sure that you do not re-run WLST or WDT to create a new domain home even though it will
    have the same configuration. Creating a new domain will change the domain secret and you won't be able to do a
    rolling restart.
{{% /notice %}}

c. Deploy the new Docker image to your Docker repository with the new name.

d. Update the `image` property of the domain resource, specifying the new image name.

   For example:

     ```
     domain:
       spec:
         image: oracle/weblogic-updated:2.5.0
     ```
e. The operator will now initiate a rolling restart, which will apply the updated image, for all the server pods in the domain.

#### Avoiding a rolling restart when changing the `image` property on a domain resource
If you've created a new image that is not rolling-compatible, and you've changed the image name, then:

1. Bring the domain down (stopping all the server pods) by setting the `serverStartPolicy` to `NEVER`. See [Shut down all the servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#shut-down-all-the-servers">}}).

2. Update the `image` property with a new image name.

3. Start up the domain (starting all the server pods) by setting the `serverStartPolicy` to `IF_NEEDED`.

#### Other considerations for restarting a domain

* **Consider the order of changes**:

    If you need to make multiple changes to your domain at the same time, you'll want to be careful
    about the order in which you do your changes, so that servers aren't restarted prematurely or restarted needlessly.
    For example, if you want to change the readiness probe's tuning parameters and the Java options (both of which are rolling-compatible), then you should update the domain resource once, changing both values,
    so that the operator rolling restarts the servers once.  Or, if you want to change the readiness probe's tuning parameters (which is rolling-compatible)
    and change the domain customizations (which require a full restart), then you should do a full shutdown first,
    then make the changes, and then restart the servers.  

    Alternatively, if you know that your set of changes are not rolling-compatible, then you must avoiding a rolling restart by:

     1. Bringing the domain down (stopping all the server pods) by setting the `serverStartPolicy` to `NEVER`. See [Shut down all the servers]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#shut-down-all-the-servers">}}).

     2. Make all your changes to the Oracle WebLogic Server in Kubernetes environment.

     3. Starting up the domain (starting all the server pods) by setting the `serverStartPolicy` to `IF_NEEDED`.

* **Changes that require domain knowledge**.

    Sometimes you need to make changes that require server restarts, yet the changes are not to the WebLogic configuration,
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
