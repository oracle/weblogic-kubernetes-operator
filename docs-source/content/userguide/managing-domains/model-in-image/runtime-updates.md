+++
title = "Runtime updates"
date = 2020-03-11T16:45:16-05:00
weight = 50
pre = "<b> </b>"
description = "Updating a running Model in Image domain's images and model files."
+++

#### Contents

 - [Overview](#overview)
 - [Supported updates](#supported-updates)
 - [Unsupported updates](#unsupported-updates)
 - [Updating an existing model](#updating-an-existing-model)
 - [Offline updates](#offline-updates)
   - [Offline update steps](#offline-update-steps)
   - [Offline update sample](#offline-update-sample)
 - [Online updates](#online-updates)
   - [Online update steps](#online-update-steps)
   - [Online update handling of non-dynamic WebLogic configuration changes](#online-update-handling-of-non-dynamic-weblogic-configuration-changes)
   - [Online update handling of deletes](#online-update-handling-of-deletes)
   - [Online update examples](#online-update-examples)
   - [Online update sample](#online-update-sample)
 - [Appendices](#appendices)
   - [Using the WDT Discover and Compare Model Tools](#using-the-wdt-discover-domain-and-compare-model-tools) below.
   - [Changing a Domain `restartVersion` or `introspectVersion`](#changing-a-domain-restartversion-or-introspectversion)
   - [Checking domain status conditions for online update results](#checking-domain-status-conditions-for-online-update-results)

#### Overview

If you want to make a WebLogic domain home configuration update to a running Model in Image domain,
and you want the update to survive WebLogic Server pod restarts,
then you must modify your existing model and tell the WebLogic Kubernetes Operator to propagate the change.

If you instead make a direct runtime WebLogic configuration update of a Model in Image domain
using the WebLogic Server Administration Console or WLST scripts,
then the update will be ephemeral.
This is because a Model in Image domain home is regenerated from the model on every pod restart.

There are two approaches for propagating model updates to a running Model in Image domain: 

 - _Offline updates_: Offline updates are propagated to WebLogic pods by updating your model
   and then initiating a domain roll, which generates a new domain configuration,
   restarts the domain's WebLogic administration server with the updated configuration,
   and then restarts the other pods in the cluster.

 - _Online updates_: If model changes are configured to fully dynamic configuration MBean attributes,
   then you can optionally propagate changes to WebLogic pods without a roll using an online update.
   If an online update request includes model updates that can only be achieved using an offline update,
   then the resulting behavior is controlled by the Domain Yaml
   `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` attribute,
   which is discussed in detail later in this chapter.

The operator does not support all types of WebLogic configuration changes to a running domain.
If a change is unsupported, then propagating the change requires entirely shutting domain the domain,
applying the change, and finally restarting the domain. Full domain restarts are discussed in
[Full domain restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts">}}).
Supported and unsupported changes are discussed in detail later in this chapter in
[Supported and unsupported updates](#supported-and-unsupported-updates).

{{% notice warning %}}
Custom configuration overrides, which are WebLogic configuration overrides
specified using a Domain YAML file `configuration.overridesConfigMap`, as described in
[Configuration overrides]({{< relref "/userguide/managing-domains/configoverrides/_index.md" >}}),
aren't supported in combination with Model in Image.
Model in Image will generate an error if custom overrides are specified.
This should not be a concern because model file, secret, or model image updates are simpler
and more flexible than custom configuration override updates.
Unlike configuration overrides, the syntax for a model file update exactly matches
the syntax for specifying your model file in the first place.
{{% /notice %}}

#### Supported updates

The following updates are *supported* except when they reference an area that is specifically
documented as [unsupported](#unsupported-updates) below:

 - You can add a new WebLogic cluster or standalone server.

 - You can increase the size of a dynamic WebLogic cluster.

 - You can add new MBeans or resources by specifying their corresponding model YAML file snippet
   along with their parent bean hierarchy. For example, you can add a data source.

 - You can change or add MBean attributes by specifying a YAML file snippet
   along with its parent bean hierarchy that references an existing MBean and the attribute.
   For example, to add or alter the maximum capacity of a data source named `mynewdatasource`:

   ```
   resources:
     JDBCSystemResource:
       mynewdatasource:
         JdbcResource:
           JDBCConnectionPoolParams:
             MaxCapacity: 5
   ```

   For more information, see [Using Multiple Models](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/model.md#using-multiple-models) in the WebLogic Deploy Tooling documentation.

 - You can change or add secrets that your model macros reference
   (macros that use the `@@SECRET:secretname:secretkey@@` syntax).
   For example, you can change a database password secret.

 - For offline updates, you can change or add environment variables
   that your model macros reference
   (macros that use the `@@ENV:myenvvar@@` syntax).

 - You can remove an MBean, application deployment, or resource by omitting any
   reference to it in your image model files and WDT config map.
   You can also remove a named MBean, application deployment, or resource
   by specifying an additional model file with an exclamation point (`!`)
   just before its name plus ensuring the new model file is loaded after
   the original model file that contains the original named configuration.
   For example, if you have a data source named `mynewdatasource` defined
   in your model, it can be removed by specifying a small model file that
   loads after the model file that defines the data source, where the
   small model file looks like this:

   ```
   resources:
     JDBCSystemResource:
       !mynewdatasource:
   ```
   There are [some exceptions for online updates](#online-update-handling-of-deletes).

   For more information, see
   [Declaring Named MBeans to Delete](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/model.md#declaring-named-mbeans-to-delete)
   in the WebLogic Deploying Tooling documentation.

#### Unsupported updates

{{% notice warning %}}
It is important to avoid applying unsupported model updates to a running domain. An attempt to use an unsupported update may not always result in a clear error message, and the expected behavior may be undefined. If you need to make an unsupported update and no workaround is documented, then shut down your domain entirely before making the change. See [Full domain restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts">}}).
{{% /notice %}}

The following summarizes the types of runtime update configuration that are _not_ supported in this release of Model in Image unless a workaround or alternative is documented:

  - Decreasing dynamic cluster size (see detailed discussion below for an alternative)
  - Adding WebLogic Servers to a configured cluster or removing them
  - Default and custom network channel configuration for an existing WebLogic cluster or server. Specifically:
    - Adding or removing Network Access Points (custom channels) for existing servers
    - Changing a Default, SSL, Admin, or custom channel, `Enabled`, listen address, protocol, or port
  - Node Manager related configuration
  - Log related settings (see the detailed discussion below for when this applies)
  - Changing the domain name
  - Deleting an MBean attribute (see the detailed discussion below for workaround)
  - Changing any existing MBean name (see the detailed discussion below for workaround)
  - Embedded LDAP entries (see detailed discussion below for alternatives)
  - Any Model YAML `topology:` stanza changes
  - Dependency deletion in combination with online updates.
  - Various security related changes in combination with online updates.

Here is a detailed discussion of each unsupported runtime update
and a discussion of workarounds and alternatives when applicable:

 - There is no way to directly delete an attribute from an MBean that's already been specified by a model file.
   The workaround is to do this using two model files:
   add a model file that deletes the named bean/resource that is a parent to
   the attribute you want to delete using the `!` syntax as described above,
   and add another model file that will be loaded after the first one,
   which fully defines the named bean/resource but without the attribute you want to delete.

 - There is no way to directly change the MBean name of an attribute.
   Instead, you can remove a named MBean using the `!` syntax as described
   in [Supported Updates](supported-updates), and then add a new one as a replacement.

 - You cannot change the domain name at runtime.

 - You have a limited ability to change an existing WebLogic cluster's membership.
   Specifically, do not apply runtime updates for:
   - Adding WebLogic Servers to a configured cluster.
     As an alternative, consider using dynamic clusters instead of configured clusters.
   - Removing WebLogic Servers from a configured cluster.
     As an alternative, you can lower your cluster's Domain YAML `replicas` attribute.
   - Decreasing the size of a dynamic cluster.
     As an alternative, you can lower your cluster's Domain YAML `replicas` attribute. 

 - You cannot change, add, or remove network listen address, port, protocol, and enabled configuration
   for existing clusters or servers at runtime.

   Specifically, do not apply runtime updates for:
   - A Default, SSL, Admin channel `Enabled`, listen address, or port.
   - A Network Access Point (custom channel) `Enabled`, listen address, protocol, or port.

   Note that it is permitted to override network access point `public` or `external` addresses and ports.
   External access to JMX (MBean) or online WLST requires that the network access point internal port
   and external port match (external T3 or HTTP tunneling access to JMS, RMI, or EJBs don't require port matching).

   {{% notice warning %}}
   Due to security considerations, we strongly recommend that T3 or any RMI protocol should not be exposed outside the cluster.
   {{% /notice %}}

 - Changing, adding, or removing server and domain log related settings in an MBean at runtime
   when the domain resource is configured to override the same MBeans using the `spec.logHome`,
   `spec.logHomeEnabled`, or `spec.httpAccessLogInLogHome` attributes.

 - Embedded LDAP security entries for
   [users, groups, roles](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/use_cases.md#modeling-weblogic-users-groups-and-roles),
   and [credential mappings](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/use_cases.md#modeling-weblogic-user-password-credential-mapping).
   For example, you cannot add a user to the default security realm.
   Online update attempts in this area will fail during the introspector job, and offline update attempts 
   may result in inconsistent security checks during the offline update's rolling cycle.
   If you need to make these kinds of updates, then shut down your domain entirely before making the change,
   or switch to an [external security provider](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/use_cases.md#modeling-security-providers).

 - Any Model YAML `topology:` stanza changes.
   For example, `ConsoleEnabled`, `RootDirectory`, `AdminServerName`, etc.
   For a complete list, run `/u01/wdt/weblogic-deploy/bin/modelHelp.sh -oracle_home $ORACLE_HOME topology`
   (this assumes you have installed WDT in the `/u01/wdt/weblogic-deploy` directory).

 - Deleting Model entries by type. For example, you cannot delete an entire 'SelfTuning' type stanza
   by omitting the stanza in an online update or by specifying an additional model with '!SelfTuning'
   in either an offline or an online update.
   Instead, you can delete the specific MBean by omitting the MBean itself while leaving its 'SelfTuning'
   parent in place or by specifying an additional model using the '!' syntax in combination
   with the name of the specific MBean.
   See [online update handling of deletes](#online-update-handling-of-deletes) for details.

 - Deleting multiple resources that have cross-references in combination with online updates.
   For example, concurrently deleting a persistent store and a datasource referenced by the persistent store,
   For this type of failure, the introspection job will fail and log an error describing
   the failed reference, and the job will automatically retry up to its maximum retries.
   See [online update handling of deletes](#online-update-handling-of-deletes) for details.

 - Security related changes in combination with online updates.
   Such changes included security changes in `domainInfo.Admin*`,
   `domainInfo.RCUDbinfo.*`, `topology.Security.*`, and `topology.SecurityConfiguration.*`. 
   Any online update changes in these sections will result in a failure.

#### Updating an existing model

If you have verified your proposed model updates to a running
Model in Image domain are supported by consulting
[Supported and unsupported updates](#supported-and-unsupported-updates) above,
then you can use one or more of the following approaches for an online or offline
model update:

  - Specify a new or changed WDT ConfigMap that contains model files
    and use your Domain YAML file `configuration.model.configMap` field to reference the map.
    The model files in the ConfigMap will be merged with any model files in the image.
    Ensure the ConfigMap is deployed to the same namespace as your domain. 

  - Change, add, or delete secrets that are referenced by macros in your model files
    and use your Domain YAML file `configuration.secrets` field to reference the secrets.
    Ensure the secrets are deployed to the same namespace as your domain.

For offline updates only, there are two additional options:

  - Supply a new image with new or changed model files
    and use your Domain YAML `spec.image` field to reference the image.

  - Change, add, or delete environment variables that are referenced by macros in your model files.
    Environment variables are specified in the Domain YAML `spec.serverPod.env`
    or `spec.serverPod.adminServer.env` attributes.

{{% notice note %}}
It is advisable to defer the last two modification options above, or similar Domain YAML changes to
[fields that cause servers to be restarted]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#fields-that-cause-servers-to-be-restarted" >}}),
until all of your other modifications are ready.
This is because such changes automatically and immediately result in a rerun of your introspector job,
a roll if the job succeeds,
plus an offline update if there are any accompanying model changes.
{{% /notice %}}

Model updates can include additions, changes, and deletions. For help generating model model changes:

 - For a discussion of model file syntax, see the 
   [WebLogic Deploy Tool](https://github.com/oracle/weblogic-deploy-tooling) documentation
   and Model in Image [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}) documentation.

 - For a discussion about helper tooling that you can use to generate model change YAML,
   see [Using the WDT Discover and Compare Model Tools](#using-the-wdt-discover-domain-and-compare-model-tools) below.

 - If you specify multiple model files in your image or WDT ConfigMap,
   then the order in which they're loaded and merged is determined as described in
   [Model file naming and loading order]({{< relref "/userguide/managing-domains/model-in-image/model-files/_index.md#model-file-naming-and-loading-order" >}}).

 - If you are performing an online update and the update includes deletes, then
   see [Online update handling of deletes](#online-update-handling-of-deletes).

Once your model updates are prepared, you can tell the Operator to propagate the changed model
to a running domain by following the steps in [Offline updates](#offline-updates)
or [Online updates](#online-updates) below.

#### Offline updates

##### Offline update steps

Use the following steps to initiate an offline configuration update to your model:

 1. Ensure your updates are supported by checking [Supported and unsupported updates](#supported-and-unsupported-updates).
 1. Modify, add, or delete your model resources as per [Updating an existing model](#updating-an-existing-model).
 1. Modify your domain resource YAML:
    1. If you have updated your image, change `domain.spec.image` accordingly.
    1. If you are updating environment variables, change `domain.spec.serverPod.env`
       or `domain.spec.adminServer.serverPod.env` accordingly.
    1. If you are specifying a WDT ConfigMap, then set `domain.spec.configuration.model.configMap`
       to the name of the ConfigMap.
    1. If you are adding or deleting secrets as part of your change, then ensure
       the `domain.spec.configuration.secrets` array reflects all current secrets.
    1. If have modified your image or environment variables,
       then no more domain resource YAML changes are needed,
       otherwise change an attribute that tells the operator to roll the domain.
       For examples, see
       [change the domain `spec.restartVersion`](#changing-a-domain-restartversion-or-introspectversion)
       or change any of the other
       [Domain YAML fields that cause servers to be restarted]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#fields-that-cause-servers-to-be-restarted" >}}). 

The operator will subsequently rerun the domain's introspector job.
This job will reload all of your secrets and environment variables,
merge all of your model files, and generate a new domain home.

If the job succeeds, then the operator will make the updated domain home available to pods
using a ConfigMap named `DOMAIN_UID-weblogic-domain-introspect-cm` and the operator will
subsequently roll (restart) each running WebLogic Server pod in the domain
so that it can load the new configuration.
A domain roll begins by restarting the domain's Administration Server and
then proceeds to restart each Managed Server in the domain.

If the job reports a failure, see 
[Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}})
for debugging advice.

##### Offline update sample

For an offline update sample which adds data source, see the 
[Update 1 use case]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}})
in the Model in Image sample.

#### Online updates

##### Online update steps

Use the following steps to initiate an online configuration update to your model:

 1. Ensure your updates are supported by checking
    [Supported and unsupported updates](#supported-and-unsupported-updates).
 1. Modify, add, or delete your model secrets or WDT ConfigMap
    as per [Updating an existing model](#updating-an-existing-model).
 1. Modify your domain resource YAML:
    1. **Do not** change `domain.spec.image`, `domain.spec.serverPod.env`, or any other Domain YAML
       [fields that cause servers to be restarted]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#fields-that-cause-servers-to-be-restarted" >}}):
       this will automatically and immediately result in a rerun of your introspector job,
       a roll if the job succeeds, plus an offline update if there are any accompanying model changes.
    1. If you are specifying a WDT ConfigMap, then set `domain.spec.configuration.model.configMap`
       to the name of the ConfigMap.
    1. If you are adding or deleting secrets as part of your change, then ensure the
       `domain.spec.configuration.secrets` array reflects all current secrets.
    1. Set `domain.spec.configuration.model.onlineUpdate.enabled` to `true` (default is `false`).
    1. Set `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` to one of 
       `CommitUpdateOnly` (default), `CommitUpdateAndRoll`, and `CancelUpdate`.
       For details, see 
       [online update handling of non-dynamic WebLogic configuration changes](#online-update-handling-of-non-dynamic-weblogic-configuration-changes)
       below.
    1. Optionally tune the WDT timeouts in `domain.spec.configuration.model.onlineUpdate.wdtTimeouts`.
       - This is only necessary in the rare case when an introspector job's WDT online update command timeout
         results in an error in the introspector job log or operator log.
       - All timeouts are specified in milliseconds and default to two or three minutes.
       - For a full list of timeouts, you can call
         `kubectl explain domain.spec.configuration.model.onlineUpdate.wdtTimeouts`.
    1. Change `domain.spec.introspectVersion` to a different value. For examples, see
       [change the domain `spec.introspectVersion`](#changing-a-domain-restartversion-or-introspectversion).

Once you've completed the above steps, the operator will subsequently run an introspector Job which
generates a new merged model,
compares the new merged model to the previously deployed merged model,
and runs the WebLogic Deploy Tool to process the differences:

 - If the introspector job WDT determines that the differences are confined to
   fully dynamic WebLogic configuration MBean changes,
   then the operator will send delta online updates to the running WebLogic pods. 

 - If WDT detects non-dynamic WebLogic configuration MBean changes, then the operator may ignore the updates,
   honor only the online updates, or initiate an offline update (roll) depending on whether you have configured
   `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` to `CommitUpdateOnly` (default),
   `CommitUpdateAndRoll`, or `CancelUpdate`. 
   For details, see 
   [online update handling of non-dynamic WebLogic configuration changes](#online-update-handling-of-non-dynamic-weblogic-configuration-changes)
   below.

If the introspector job reports a failure or any other failure occurs, then
see [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) for debugging advice.
When recovering from a failure, please keep the following points in mind:

 - The operator cannot automatically revert changes to resources that are under
   user control (just like with offline updates). For example, it is the administrator's
   responsibility to revert problem changes to an image, configMap, secrets, and domain resource YAML.

 - If there is any failure during an online update, then no WebLogic configuration changes
   are made to the running domain and the introspector job retries up to a maximum number of times.
   To correct the problem, modify and reapply your model resources (ConfigMap and/or secrets),
   plus, if the introspector job has stopped retrying, you must also change your domain resource
   `domain.spec.introspectVersion` again.

 - If updating a domain resource YAML with
   `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` set to `CancelUpdate`
   and non-dynamic changes are detected,
   then the domain will enter a failed stated
   and the user must correct the models, shutdown and restart the entire domain,
   or use offline update instead.


Sample domain resource YAML for an online update:

```
...
kind: Domain
metadata:
  name: sample-domain1
  namespace: sample-domain1-ns
...
spec:
  ...
  introspectVersion: 5
  configuration:
    ...
    model:
      domainType: "WLS"
      configMap: sample-domain1-wdt-config-map
      runtimeEncryptionSecret: sample-domain1-runtime-encryption-secret
      onlineUpdate:
        enabled: true
        OnNonDynamicChanges: "CommitUpdateAndRoll"
    secrets:
    - sample-domain1-datasource-secret
    - sample-domain1-another-secret
```

##### Online update handling of non-dynamic WebLogic configuration changes

The domain resource YAML `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` attribute
controls behavior when
non-dynamic WebLogic configuration changes are detected during an online update introspector job.
Non-dynamic changes are changes that require a domain restart to take effect. 
Valid values are `CommitUpdateOnly` (default), `CommitUpdateAndRoll`, and `CancelUpdate`:

  * If set to `CommitUpdateOnly` and any non-dynamic changes are detected,
    then all changes will be committed,
    dynamic changes will take effect immediately,
    the domain will not automatically restart (roll),
    and any non-dynamic changes will become effective on a pod only when
    the pod is later restarted.

  * If set to `CommitUpdateAndRoll` and any non-dynamic changes are detected,
    then all changes will be committed, dynamic changes will take effect immediately,
    the domain will automatically restart (roll),
    and non-dynamic changes will take effect on each pod once the pod restarts.

  * If set to `CancelUpdate` and any non-dynamic changes are detected,
    then all changes are ignored,
    the domain continues to run without interruption, 
    and the domain Failed condition will become true.
    There are four ways to correct:
    either revert non-dynamic changes to your model resources and retry the online update,
    change to `CommitUpdateAndRoll` and retry the online update,
    shutdown the domain and then restart it,
    or change the domain resource to initiate an offline update and a roll (such as by changing its `restartVersion`).

{{% notice note %}}
When updating a domain with non-dynamic MBean changes with `onNonDynamicUpdates=CommitUpdateOnly` (the default),
the non-dynamic changes are not effective on a WebLogic pod until the pod is restarted.
However, if you scale up a cluster or otherwise start any new servers in the domain, 
then the new servers will start with the new non-dynamic changes
and the domain will then be running in an inconsistent state until its older servers are restarted.
{{% /notice %}}

##### Online update handling of deletes

**Summary**

The primary use case for online update is to make small additions,
deletions of single resources or MBeans that have no dependencies, 
or changes to non-dynamic MBean attributes. 

Deletion can be problematic for online update in two cases:
 - Deleting multiple resources that have cross dependencies.
 - Deleting the parent type section in an MBean hierarchy.

In general, complex deletion should be handled by offline updates
in order to avoid these problems. 

> Note that implicitly removing a model's parent type
section may sometimes work depending
on the type of the section. For example, if you have an application
in the model under `appDeployments:` in a `model.configMap` and you
subsequently update the ConfigMap using an online update so that it
no longer includes the `appDeployment` section, then the online update
will delete the application from the domain.)

**MBean type section deletion**

For an example of an MBean deletion, consider a WDT config map that starts with:

```
resources:
  SelfTuning:
    WorkManager:
      wm1:
        Target: 'cluster-1'
      wm2:
        Target: 'cluster-1'
  JDBCSystemResource:
    ...
```

If you want to online update to a new model without work-managers,
then you can change the ConfigMap to the following:

```
resources:
  SelfTuning:
    WorkManager:
  JDBCSystemResource:
    ...
```

or supply an additional configmap with:

```
resources:
  SelfTuning:
    WorkManager:
      '!wm1':
      '!wm2':
```

But the online update will fail if you try replace the ConfigMap with the `SelfTuning` section omitted:

```
resources:
  JDBCSystemResource:
    ...
```

The above will fail as this implicitly removes the MBean types `SelfTuning` and `WorkManager`.

**Deleting cross-referenced MBeans**

For an example of an unsupported online update delete of MBeans
with cross references, consider the case of a work manager
configured with constraints where you want to delete the entire work manager:

```
resources:
  SelfTuning:
    WorkManager:
      newWM:
        Target: 'cluster-1'
        MinThreadsConstraint: 'SampleMinThreads'
        MaxThreadsConstraint: 'SampleMaxThreads'
    MinThreadsConstraint:
      SampleMinThreads:
        Count: 1
    MaxThreadsConstraint:
      SampleMaxThreads:
        Count: 10
```
 
If you try to specify the updated model in the configmap as

```
resources:
    SelfTuning:
        WorkManager:
        MinThreadsConstraint:
        MaxThreadsConstraint:

```

Then the Operator will try use this delta to online update the domain:

```
resources:
    SelfTuning:
        MaxThreadsConstraint:
            '!SampleMaxThreads':
        WorkManager:
            '!newWM':
        MinThreadsConstraint:
            '!SampleMinThreads':
```

This can fail because online update can fail to delete all referenced `Contraints` first
before deleting the actual `WorkManager`.

To work-around problems with online updates to objects with cross dependencies you can
use a series of online updates to make the change in stages. For example, continuing the work manager
example above, first perform an online update to omit the work manager but not the constraints with:

```
resources:
  SelfTuning:
    WorkManager:
    MinThreadsConstraint:
      SampleMinThreads:
        Count: 1
    MaxThreadsConstraint:
      SampleMaxThreads:
        Count: 10
```

And, once that update completes, then perform another online update with:

```
resources:
    SelfTuning:
        WorkManager:
        MinThreadsConstraint:
        MaxThreadsConstraint:

```

##### Online update examples

**High level examples:**

|Scenarios|Expected Outcome|Actions Required|
  |---------------------|-------------|-------|
  | Changing a dynamic WebLogic MBean attribute. | Changes are committed in the running domain and effective immediately. | No action required. |
  | Changing a non-dynamic WebLogic MBean attribute. | The outcome depends on the `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` attribute. | See [Online update handling of non-dynamic WebLogic configuration changes](#online-update-handling-of-non-dynamic-weblogic-configuration-changes). |
  | Changing any model attribute that is [unsupported](#unsupported-updates). | The expected behavior is often undefined, but in some cases there will be helpful error in the introspector job, events, and/or domain status, and the job will periodically retry until the error is corrected or its maximum error count exceeded. | Use offline updates if they are supported, or, if not, shutdown the entire domain and restart it. |
  | Changing domain resource YAML other than `domain.spec.introspectVersion`, `spec.configuration.secrets`, `spec.configuration.model.onlineUpdate`, and `spec.configuration.model.configMap`. | No online update is attempted by the introspector job, and changes are treated the same as offline updates (which may result in restarts/roll after job success). | No action required. |
  | Errors in the model; for example, a syntax error. | Error in the introspector job, domain events, and domain status. The job will retry periodically until the error is corrected or until maximum error count is exceeded. | Correct the model. |
  | Other errors while updating the domain. | Error in the introspector job, domain events, and/or domain status. A failed introspector job will retry periodically until the error is corrected or until it exceeds its maximum error count. | See  [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}). | 
    
**Dynamic WebLogic configuration examples:**

The following are examples of WebLogic configuration changes that can be dynamic
and are effective immediately in an online update once the introspection job completes:

  - Data source connection pool capacity, password, and targets.
  - Application targets.
  - Deleting or adding a data source.
  - Deleting or adding an application.

**Non-dynamic WebLogic configuration examples:**

The following are examples of WebLogic configuration changes that are non-dynamic
and are only effective after an offline update and subsequent roll:

  - Data source driver parameter properies (such as `username`). 

##### Online update sample

For an online update sample which alters a data source and work manager, see the 
[Update 4 use case]({{< relref "/samples/simple/domains/model-in-image/update4.md" >}})
in the Model in Image sample.

#### Appendices

##### Using the WDT Discover Domain and Compare Model Tools

You can optionally use the WDT Discover Domain and Compare Domain Tools to help generate your model file updates.
The WebLogic Deploy Tooling
[Discover Domain Tool](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/discover.md)
generates model files from an existing domain home,
and its [Compare Model Tool](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/compare.md)
compares two domain models and generates the YAML file for updating the first domain to the second domain.

For example, assuming you've installed WDT in `/u01/wdt/weblogic-deploy` and assuming your domain type is `WLS`:

  ```

  # (1) Run discover for your existing domain home.

  $ /u01/wdt/weblogic-deploy/bin/discoverDomain.sh \
    -oracle_home $ORACLE_HOME \
    -domain_home $DOMAIN_HOME \
    -domain_type WLS \
    -archive_file old.zip \
    -model_file old.yaml \
    -variable_file old.properties

  # (2) Now make some WebLogic config changes using the console or WLST.

  # (3) Run discover for your changed domain home.

  $ /u01/wdt/weblogic-deploy/bin/discoverDomain.sh \
    -oracle_home $ORACLE_HOME \
    -domain_home $DOMAIN_HOME \
    -domain_type WLS \
    -archive_file new.zip \
    -model_file new.yaml \
    -variable_file new.properties

  # (4) Compare your old and new yaml using diff

  $ diff new.yaml old.yaml

  # (5) Compare your old and new yaml using compareDomain to generate
  #     the YAML update file you can use for transforming the old to new.

  # /u01/wdt/weblogic-deploy/bin/compareModel.sh \
    -oracle_home $ORACLE_HOME \
    -output_dir /tmp \
    -variable_file old.properties \
    old.yaml \
    new.yaml

  # (6) The compareModel will generate these files:
  #      /tmp/diffed_model.json
  #      /tmp/diffed_model.yaml, and
  #      /tmp/compare_model_stdout
  ```

> **Note**: If your domain type isn't `WLS`, remember to change the domain type to `JRF` or `RestrictedJRF` in the above `discoverDomain.sh` commands.

##### Changing a Domain `restartVersion` or `introspectVersion`

As was mentioned in the [offline updates](#offline-updates) section, one way to tell the operator to 
apply offline configuration changes to a running domain is by altering the Domain 
`spec.restartVersion`. Similarly, an [online update](#online-updates) is iniatated by altering
thye Domain `spec.introspectVersion`. Here are some common ways to alter either of these fields:

 - You can alter `restartVersion` interactively using `kubectl edit -n MY_NAMESPACE domain MY_DOMAINUID`.

 - If you have your domain's resource file, then you can alter this file and call `kubectl apply -f` on the file.

 - You can use the Kubernetes `get` and `patch` commands.

   Here's a sample automation script for `restartVersion`
   that takes a namespace as the first parameter (default `sample-domain1-ns`)
   and that takes a domainUID as the second parameter (default `sample-domain1`):

   ```
   #!/bin/bash
   NAMESPACE=${1:-sample-domain1-ns}
   DOMAINUID=${2:-sample-domain1}
   currentRV=$(kubectl -n ${NAMESPACE} get domain ${DOMAINUID} -o=jsonpath='{.spec.restartVersion}')
   if [ $? = 0 ]; then
     # we enter here only if the previous command succeeded

     nextRV=$((currentRV + 1))

     echo "@@ Info: Rolling domain '${DOMAINUID}' in namespace '${NAMESPACE}' from restartVersion='${currentRV}' to restartVersion='${nextRV}'."

     kubectl -n ${NAMESPACE} patch domain ${DOMAINUID} --type='json' \
       -p='[{"op": "replace", "path": "/spec/restartVersion", "value": "'${nextRV}'" }]'
   fi
   ```

   Here's a similar sample script for `introspectVersion`:

   ```
   #!/bin/bash
   NAMESPACE=${1:-sample-domain1-ns}
   DOMAINUID=${2:-sample-domain1}
   currentIV=$(kubectl -n ${NAMESPACE} get domain ${DOMAINUID} -o=jsonpath='{.spec.introspectVersion}')
   if [ $? = 0 ]; then
     # we enter here only if the previous command succeeded

     nextIV=$((currentIV + 1))

     echo "@@ Info: Rolling domain '${DOMAINUID}' in namespace '${NAMESPACE}' from introspectVersion='${currentIV}' to introspectVersion='${nextIV}'."

     kubectl -n ${NAMESPACE} patch domain ${DOMAINUID} --type='json' \
       -p='[{"op": "replace", "path": "/spec/introspectVersion", "value": "'${nextIV}'" }]'
   fi
   ```

 - You can use a WebLogic Kubernetes Operator sample script that invokes
   the same commands that are described in the previous bullet. See
   `patch-restart-version.sh` and `patch-introspect-version.sh` in
   the 
   `kubernetes/samples/scripts/create-weblogic-domain/model-in-image/utils/`
   Model in Image sample directory.

##### Checking domain status conditions for online update results

**WIP/TBD: DO NOT REVIEW THIS SECTION YET. THIS SECTION IS A WORK IN PROGRESS PENDING ONGING DEV CHANGES IN THIS AREA.**
TBD this section may fit better in the Debugging chapter or the 'online' section above

During an online update, the Operator will rerun the introspector job, attempting online updates on the running domain. This feature is useful for changing any dynamic attribute of the WebLogic Domain. No pod restarts are necessary, and the changes immediately take effect. 
Once the job is completed, you can check the domain status to view the online update status: `kubectl -n <namespace> describe domain <domain uid>`.  Upon success, each WebLogic pod will have a `weblogic.introspectVersion` label that matches the `domain.spec.introspectVersion` that you specified.
 
When the introspector job finished, the domain status will be updated according to the result.

You can use the command to display the domain status

`kubectl -n <ns> describe domain <domain name>`

|Scenarios|Domain status|
  |---------------------|-------------|
  |Successful updates|Domain status will have a condition OnlineUpdateComplete with message and introspectionVersion|
  |Changes rolled back per request|Domain status will have a condition OnlineUpdateCanceled with message and introspectionVersion|
  |Any other errors| Domain status message will display the error message. No condition is set in the status condition|

For example, after a successful online update, you will see this in the `Domain Status` section

```
Status:
  Clusters:
    Cluster Name:      cluster-1
    Maximum Replicas:  5
    Minimum Replicas:  0
    Ready Replicas:    2
    Replicas:          2
    Replicas Goal:     2
  Conditions:
    Last Transition Time:        2020-11-18T15:19:11.837Z
    Message:                     Online update successful. No restart necessary
    Reason:                      Online update applied, introspectVersion updated to 67
    Status:                      True
    Type:                        OnlineUpdateComplete
    Last Transition Time:        2020-11-18T15:19:11.867Z
    Reason:                      ServersReady
    Status:                      True
    Type:                        Available

```

If the changes involve non-dynamic MBean attributes, and you have specified 'CancelUpdate' under `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges`, you will see this

```
  Conditions:
    Last Transition Time:  2020-11-20T17:13:00.170Z
    Message:               Online update completed successfully, but the changes require restart and the domain resource specified option to cancel all changes if restart require. The changes are: Server re-start is REQUIRED for the set of changes in progress.

The following non-dynamic attribute(s) have been changed on MBeans 
that require server re-start:
MBean Changed : com.bea:Name=oracle.jdbc.fanEnabled,Type=weblogic.j2ee.descriptor.wl.JDBCPropertyBean,Parent=[sample-domain1]/JDBCSystemResources[Bubba-DS],Path=JDBCResource[Bubba-DS]/JDBCDriverParams/Properties/Properties[oracle.jdbc.fanEnabled]
Attributes changed : Value
    Reason:                      Online update applied, introspectVersion updated to 67
    Status:                      True
    Type:                        OnlineUpdateCanceled

```

If the changes involve non-dynamic MBean attributes, and you have specified 'CommitUpdateOnly' under `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` or not set, you will see this               
      
```
  Conditions:
    Last Transition Time:  2020-11-20T17:13:00.170Z
    Message:               Online update completed successfully, but the changes require restart and the domain resource specified 'spec.configuration.model.onlineUpdate.onNonDynamicChanges=CommitUpdateOnly' or not set. The changes are committed but the domain require manually restart to 
                                 make the changes effective. The changes are:

The following non-dynamic attribute(s) have been changed on MBeans 
that require server re-start:
MBean Changed : com.bea:Name=oracle.jdbc.fanEnabled,Type=weblogic.j2ee.descriptor.wl.JDBCPropertyBean,Parent=[sample-domain1]/JDBCSystemResources[Bubba-DS],Path=JDBCResource[Bubba-DS]/JDBCDriverParams/Properties/Properties[oracle.jdbc.fanEnabled]
Attributes changed : Value
    Reason:                      Online update applied, introspectVersion updated to 67
    Status:                      True
    Type:                        OnlineUpdateComplete

```

