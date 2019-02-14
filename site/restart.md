# Restarting Oracle WebLogic Server in Kubernetes

This document describes when to restart servers in the Oracle WebLogic Server in Kubernetes environment.

## Overview

There are many situations where changes to the Oracle WebLogic Server in Kubernetes environment require that all the servers in
a domain or cluster be restarted, for example, when applying a WebLogic Server patch or when upgrading an application.

One of the operator's most important jobs is to start and stop WebLogic Servers by creating and deleting their corresponding Kubernetes pods. Sometimes, you need to make changes that make the pods obsolete, therefore the pods need to be deleted and recreated. Depending on the change, sometimes the pods can be gradually recreated, without taking the entire domain out of service
(for example, `rolling restarts`) and sometimes all the pods need to be deleted then recreated, taking the entire domain out of
service for a while (for example, `full restarts`).

The following types of server restarts are supported in Oracle WebLogic Server in Kubernetes:

* Rolling restarts - a coordinated and controlled shut down of all of the servers in a domain or cluster while ensuring that service to the end user is not interrupted.

   * Operator initiated - where the WebLogic Kubernetes Operator can detect some types of changes and will automatically initiate rolling restarts of server pods in a WebLogic domain.

   * Manually initiated - required when certain changes in the Oracle WebLogic Server in Kubernetes environment cannot be detected by the operator, so a rolling restart must be manually initiated.

* Full domain restarts - the Administration Server and all the Managed Servers in a domain are shutdown, impacting service availability to the end user, and then restarted.  Unlike a rolling restart, the operator cannot detect and initiate a full domain restart; it must always be manually initiated.

For detailed information on how to restart servers in a Oracle WebLogic Server in Kubernetes environment, see [Starting, stopping, and restarting servers](server-lifecycle.md).

## Common restart scenarios

This document describes what actions you need to take to properly restart your servers for a number of common scenarios:

* Modifying the WebLogic configuration
* Changing the custom domain configuration overrides (also called situational configuration)
* Changing the WebLogic Server credentials (the user name and password)
* Changing properties on the domain resource that affect server pods (such as `image`, `volumes`, and `env`)
* Applying WebLogic Server patches
* Updating deployed applications for domain home in image

### Use cases

#### Modifying the WebLogic configuration

Changes to the Oracle WebLogic Server configuration may require either a rolling or full domain restart depending on the domain home location and the type of configuration change.

* **Domain home in image:**
For domain home in image, any changes (dynamic or non-dynamic) to the WebLogic configuration requires a full domain restart.  
    * If you create a new image with a new name, then you must avoid a rolling restart, which can cause unexpected behavior for the running domain due to configuration inconsistencies as seen by the various servers, by following the steps in [Avoiding a rolling restart when changing image property on a domain resource](restart.md#avoiding-a-rolling-restart-when-changing-image-property-on-a-domain-resource).
    * If you create a new image with the same name, then you must manually initiate a full domain restart. See [Full domain restarts](server-lifecycle.md#full-domain-restarts) in Starting, stopping, and restarting servers.


* **Domain home on PV:**
For domain home on PV, the type of restart needed to apply the changes, depends on the nature of the WebLogic configuration change:
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
      For example, a rolling restart is applicable when changing a WebLogic Server's `stuck thread timer interval` property. See [Restart all the servers in the domain](server-lifecycle.md#restart-all-the-servers-in-the-domain) in Starting, stopping, and restarting servers.


#### Changing the custom domain configuration overrides

Any change to domain configuration overrides requires a full domain restart.  This includes:
  * Changing the domain resource's `configOverides` to point to a different configuration map
  * Changing the domain resource's `configOverridesSecrets` to point to a different list of secrets
  * Changing the contents of the configuration map referenced by `configOverrides`
  * Changing the contents to any of the secrets referenced by `configOverridesSecrets`

#### Changing the WebLogic Server credentials

A change to the WebLogic Server credentials (the user name and password), contained in the Kubernetes secret for the domain, requires a
_full domain restart_.  The Kubernetes secret can be updated directly or a new secret can be created and then referenced by the `webLogicCredentialsSecret`
property in the domain resource.

#### Changing properties on the domain resource that affect server pods

The operator will initiate a rolling restart of the domain when you modify any of the domain resource properties that affect the server pods configuration,
such as `image`, `volumes`, and `env`.  For a complete list, see [Properties that cause servers to be restarted](server-lifecycle.md#properties-that-cause-servers-to-be-restarted) in Starting, stopping, and restarting servers.

You can modify these properties using the `kubectl` command-line tool's `edit` and `patch` commands or through the Kubernetes REST API.

For example, to edit the domain resource directly using the `kubectl` command-line tool:

```
kubectl edit domain <domain name> -n <domain namespace>
```

The `edit` command opens a text editor which lets you edit the domain resource in place.

**Note**: Typically, it's better to edit the domain resource directly; otherwise, if you scaled the domain, and you just edit the original `domain.yaml` file and reapply it, you could go back to your old replicas count.

#### Applying WebLogic Server patches

Oracle provides different types of patches for WebLogic Server, such as Patch Set Updates, Security Patch Updates, and One-Off patches.
Information on whether a patch is rolling compatible or requires a manual full domain restart usually can be found in the patch's documentation, such as the README file.

WebLogic Server patches can be applied to either a domain home in image or a domain home on PV:

With rolling compatible patches:
* If you update the `image` property with a new image name, then the operator will initiate a rolling restart.
* If you keep the same image name, then you must manually initiate a rolling restart. See [Restart all the servers in the domain](server-lifecycle.md#restart-all-the-servers-in-the-domain) in Starting, stopping, and restarting servers.

With patches that are not rolling compatible:
* If you keep the same image name, then you must manually initiate a full domain restart. See [Full domain restarts](server-lifecycle.md#full-domain-restarts) in Starting, stopping, and restarting servers.
* If you update the `image` property with a new image name, then you must avoid the rolling restart by following the steps in [Avoiding a rolling restart when changing image property on a domain resource](restart.md#Avoiding-a-rolling-restart-when-changing-image-property-on-a-domain-resource).

#### Updating deployed applications for domain home in image

Frequent updates of deployed applications using a continuous integration/continuous delivery (CI/CD) process is a very common use case.
The process for applying an updated application is different for domain home in image than it is for domain home on PV.
A rolling compatible application update is where some servers are running the old version and some are running the new version
of the application during the rolling restart process. On the other hand, an application update that is not rolling compatible requires that all the servers
in the domain be shutdown and restarted.

If the application update is rolling compatible:
* If you update the `image` property with a new image name, then the operator will initiate a rolling restart.
* If you keep the same image name, then you must manually initiate a rolling restart. See [Restart all the servers in the domain](server-lifecycle.md#restart-all-the-servers-in-the-domain) in Starting, stopping, and restarting servers.

If the application update is not rolling compatible:
* If you keep the same image name, then you must manually initiate a full domain restart. See [Full domain restarts](server-lifecycle.md#full-domain-restarts) in Starting, stopping, and restarting servers.
* If you update the `image` property with a new image name, then you must avoid the rolling restart by following the steps in [Avoiding a rolling restart when changing image property on a domain resource](restart.md#Avoiding-a-rolling-restart-when-changing-image-property-on-a-domain-resource).

## Rolling out an updated domain home in image

Follow these steps to create new rolling compatible image if you only need to patch your WebLogic Server domain or update application deployment files:

1. Select a different name for the new image.

2. Using the same domain home-in-image Docker image as a base, create a new Docker image by copying (`COPY`
command in a Dockerfile) the updated application deployment files or WebLogic Server patches into the Docker image during the Docker image build.

    **NOTE**: The key here is to make sure that you do not re-run WLST or WDT to create a new domain home even though it will
    have the same configuration. Creating a new domain will change the domain secret and you won't be able to do a
    rolling restart.

3. Deploy the new Docker image to your Docker repository with the new name.
4. Update the `image` property of the domain resource specifying the new image name.

   For example:

     ```
        domain:
              spec:
                   image: oracle/weblogic-updated:2.0
     ```
5. The operator will now initiate a rolling restart, which will apply the updated image, for all the server pods in the domain.

## Avoiding a rolling restart when changing `image` property on a domain resource
If you've created a new image that is not rolling compatible, and you've changed the image name, then:

1. Bring the domain down (stopping all the server pods) by setting the `serverStartPolicy` to `NEVER`. See [Shut down all the servers](server-lifecycle.md#shut-down-all-the-servers) in Starting, stopping, and restarting servers.

2. Update the `image` property with a new image name.

3. Start up the domain (starting all the server pods) by setting the `serverStartPolicy` to `IF_NEEDED`.

## Other considerations for restarting a domain

* **Consider the order of changes**:

    If you need to make multiple changes to your domain at the same time, you'll want to be careful
    about the order in which you do your changes, so that servers aren't restarted prematurely or restarted needlessly.
    For example, if you want to change the readiness probe's tuning parameters and the Java options (both of which are rolling compatible), then you should update the domain resource once, changing both values,
    so that the operator rolling restarts the servers once.  Or, if you want to change the readiness probe's tuning parameters (which is rolling compatible)
    and change the domain customizations (which require a full restart), then you should do a full shutdown first,
    then make the changes, and then restart the servers.  

    Alternatively, if you know that your set of changes are not rolling compatible, then you must avoiding a rolling restart by:

     1. Bringing the domain down (stopping all the server pods) by setting the `serverStartPolicy` to `NEVER`. See [Shut down all the servers](server-lifecycle.md#shut-down-all-the-servers) in Starting, stopping, and restarting servers.

     2. Make all your changes to the Oracle WebLogic Server in Kubernetes environment.

     3. Starting up the domain (starting all the server pods) by setting the `serverStartPolicy` to `IF_NEEDED`.

* **Changes that require domain knowledge**.

    Sometimes you need to make changes that require server restarts, yet the changes are not to the WebLogic configuration,
    the image, or the Kubernetes resources that register your domain with the operator.  For example, your servers are caching information from an external database and you've modified the contents of the database.

    In these cases, you must manually initiate a restart.
