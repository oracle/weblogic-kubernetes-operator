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
   - [Online update status and labels](#online-update-status-and-labels)
   - [Online update scenarios](#online-update-scenarios)
   - [Online update sample](#online-update-sample)
 - [Appendices](#appendices)
   - [Using the WDT Discover and Compare Model Tools](#using-the-wdt-discover-domain-and-compare-model-tools) below.
   - [Changing a Domain `restartVersion` or `introspectVersion`](#changing-a-domain-restartversion-or-introspectversion)

#### Overview

If you want to make a WebLogic domain home configuration update to a running Model in Image domain,
and you want the update to survive WebLogic Server pod restarts,
then you must modify your existing model and instruct the WebLogic Server Kubernetes Operator to propagate the change.

If instead you make a direct runtime WebLogic configuration update of a Model in Image domain
using the WebLogic Server Administration Console or WLST scripts,
then the update will be ephemeral.
This is because a Model in Image domain home is regenerated from the model on every pod restart.

There are two approaches for propagating model updates to a running Model in Image domain
without first shutting down the domain:

 - _Online updates_: If model changes are configured to fully dynamic configuration MBean attributes,
   then you can optionally propagate changes to WebLogic pods without a roll using an [online update](#online-updates).
   If an online update request includes non-dynamic model updates that can only be achieved
   using an offline update,
   then the resulting behavior is controlled by the Domain YAML
   `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` attribute,
   which is discussed in detail later in this document.

 - _Offline updates_: [Offline updates](#offline-updates) are propagated to WebLogic pods by updating your model
   and then initiating a domain roll, which generates a new domain configuration,
   restarts the domain's WebLogic Administration Server with the updated configuration,
   and then restarts the other pods in the cluster.

The operator does not support all types of WebLogic configuration changes while a domain is still running.
If a change is unsupported for an online or offline update, then propagating
the change requires entirely shutting domain the domain,
applying the change, and finally restarting the domain. Full domain restarts are discussed in
[Full domain restarts]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts">}}).
Supported and unsupported changes are discussed in
[Supported and unsupported updates](#supported-and-unsupported-updates).

_It is the administrator's responsibility to make the necessary changes to a domain resource in order to initiate the correct approach for an update._

{{% notice warning %}}
Custom configuration overrides, which are WebLogic configuration overrides
specified using a Domain YAML file `configuration.overridesConfigMap`, as described in
[Configuration overrides]({{< relref "/userguide/managing-domains/configoverrides/_index.md" >}}),
are _not_ supported in combination with Model in Image.
Model in Image will generate an error if custom overrides are specified.
This should not be a concern because model file, secret, or model image updates are simpler
and more flexible than custom configuration override updates.
Unlike configuration overrides, the syntax for a model file update exactly matches
the syntax for specifying your model file originally.
{{% /notice %}}

#### Supported updates

The following updates are *supported* for offline or online updates,
except when they reference an area that is specifically
documented as [unsupported](#unsupported-updates) below:

 - You can add a new WebLogic cluster or standalone server.

 - You can increase the size of a dynamic WebLogic cluster.

 - You can add new MBeans or resources by specifying their corresponding model YAML file snippet
   along with their parent bean hierarchy. For example, you can add a data source.

 - You can change or add MBean attributes by specifying a YAML file snippet
   along with its parent bean hierarchy that references an existing MBean and the attribute.
   For example, to add or alter the maximum capacity of a data source named `mynewdatasource`:

   ```yaml
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

 - For offline updates only, you can change or add environment variables
   that your model macros reference
   (macros that use the `@@ENV:myenvvar@@` syntax).

 - You can remove an MBean, application deployment, or resource by omitting any
   reference to it in your image model files and WDT config map.
   You can also remove a named MBean, application deployment, or resource
   by specifying an additional model file with an exclamation point (`!`)
   just before its name plus ensuring the new model file is loaded after
   the original model file that contains the original named configuration.
   For example, if you have a data source named `mynewdatasource` defined
   in your model, then it can be removed by specifying a small model file that
   loads after the model file that defines the data source, where the
   small model file looks like this:

   ```yaml
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
  - Dependency deletion in combination with online updates
  - Various security related changes in combination with online updates

Here is a detailed discussion of each unsupported runtime update
and a description of workarounds and alternatives when applicable:

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

 - Any Model YAML `topology:` stanza changes,
   for example, `ConsoleEnabled`, `RootDirectory`, `AdminServerName`, and such.
   For a complete list, run `/u01/wdt/weblogic-deploy/bin/modelHelp.sh -oracle_home $ORACLE_HOME topology`
   (this assumes you have installed WDT in the `/u01/wdt/weblogic-deploy` directory).

 - Deleting Model entries by type. For example, you cannot delete an entire `SelfTuning` type stanza
   by omitting the stanza in an online update or by specifying an additional model with `!SelfTuning`
   in either an offline or an online update.
   Instead, you can delete the specific MBean by omitting the MBean itself while leaving its `SelfTuning`
   parent in place or by specifying an additional model using the `!` syntax in combination
   with the name of the specific MBean.
   See [online update handling of deletes](#online-update-handling-of-deletes) for details.

 - Deleting multiple resources that have cross-references in combination with online updates.
   For example, concurrently deleting a persistent store and a data source referenced by the persistent store,
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
[Supported and unsupported updates](#supported-and-unsupported-updates),
then you can use the following approaches.

For online or offline updates:

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
It is advisable to defer the last two modification options, or similar Domain YAML changes to
[fields that cause servers to be restarted]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#fields-that-cause-servers-to-be-restarted" >}}),
until all of your other modifications are ready.
This is because such changes automatically and immediately result in a rerun of your introspector job,
a roll if the job succeeds,
plus an offline update if there are any accompanying model changes.
{{% /notice %}}

Model updates can include additions, changes, and deletions. For help generating model changes:

 - For a discussion of model file syntax, see the
   [WebLogic Deploy Tool](https://github.com/oracle/weblogic-deploy-tooling) documentation
   and Model in Image [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}) documentation.

 - For a discussion about helper tooling that you can use to generate model change YAML,
   see [Using the WDT Discover and Compare Model Tools](#using-the-wdt-discover-domain-and-compare-model-tools).

 - If you specify multiple model files in your image or WDT ConfigMap,
   then the order in which they're loaded and merged is determined as described in
   [Model file naming and loading order]({{< relref "/userguide/managing-domains/model-in-image/model-files/_index.md#model-file-naming-and-loading-order" >}}).

 - If you are performing an online update and the update includes deletes, then
   see [Online update handling of deletes](#online-update-handling-of-deletes).

After your model updates are prepared, you can instruct the operator to propagate the changed model
to a running domain by following the steps in [Offline updates](#offline-updates)
or [Online updates](#online-updates).

#### Offline updates

##### Offline update steps

Use the following steps to initiate an offline configuration update to your model:

 1. Ensure your updates are supported by checking [Supported](#supported-updates) and [Unsupported](#unsupported-updates) updates.
 1. Modify, add, or delete your model resources as per [Updating an existing model](#updating-an-existing-model).
 1. Modify your domain resource YAML file:
    1. If you have updated your image, change `domain.spec.image` accordingly.
    1. If you are updating environment variables, change `domain.spec.serverPod.env`
       or `domain.spec.adminServer.serverPod.env` accordingly.
    1. If you are specifying a WDT ConfigMap, then set `domain.spec.configuration.model.configMap`
       to the name of the ConfigMap.
    1. If you are adding or deleting secrets as part of your change, then ensure
       the `domain.spec.configuration.secrets` array reflects all current secrets.
    1. If you have modified your image or environment variables,
       then no more domain resource YAML file changes are needed;
       otherwise, change an attribute that instructs the operator to roll the domain.
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
for advice.

##### Offline update sample

For an offline update sample which adds a data source, see the
[Update 1 use case]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}})
in the Model in Image sample.

#### Online updates

##### Online update steps

Use the following steps to initiate an online configuration update to your model:

 1. Ensure your updates are supported by checking [Supported](#supported-updates) and [Unsupported](#unsupported-updates) updates.
 1. Modify, add, or delete your model secrets or WDT ConfigMap
    as per [Updating an existing model](#updating-an-existing-model).
 1. Modify your domain resource YAML file:
    1. **Do not** change `domain.spec.image`, `domain.spec.serverPod.env`, or any other Domain YAML
       [fields that cause servers to be restarted]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#fields-that-cause-servers-to-be-restarted" >}});
       this will automatically and immediately result in a rerun of your introspector job,
       a roll if the job succeeds, plus an offline update if there are any accompanying model changes.
    1. If you are specifying a WDT ConfigMap, then set `domain.spec.configuration.model.configMap`
       to the name of the ConfigMap.
    1. If you are adding or deleting secrets as part of your change, then ensure the
       `domain.spec.configuration.secrets` array reflects all current secrets.
    1. Set `domain.spec.configuration.model.onlineUpdate.enabled` to `true` (default is `false`).
    1. Set `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` to one of
       `CommitUpdateOnly` (default), and `CommitUpdateAndRoll`.
       For details, see
       [online update handling of non-dynamic WebLogic configuration changes](#online-update-handling-of-non-dynamic-weblogic-configuration-changes).

    1. Optionally, tune the WDT timeouts in `domain.spec.configuration.model.onlineUpdate.wdtTimeouts`.
       - This is only necessary in the rare case when an introspector job's WDT online update command timeout
         results in an error in the introspector job log or operator log.
       - All timeouts are specified in milliseconds and default to two or three minutes.
       - For a full list of timeouts, you can call
         `kubectl explain domain.spec.configuration.model.onlineUpdate.wdtTimeouts`.
    1. Change `domain.spec.introspectVersion` to a different value. For examples, see
       [change the domain `spec.introspectVersion`](#changing-a-domain-restartversion-or-introspectversion).

After you've completed these steps, the operator will subsequently run an introspector Job which
generates a new merged model,
compares the new merged model to the previously deployed merged model,
and runs the WebLogic Deploy Tool to process the differences:

 - If the introspector job WDT determines that the differences are confined to
   fully dynamic WebLogic configuration MBean changes,
   then the operator will send delta online updates to the running WebLogic pods.

 - If WDT detects non-dynamic WebLogic configuration MBean changes, then the operator may ignore the updates,
   honor only the online updates, or initiate an offline update (roll) depending on whether you have configured
   `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` to `CommitUpdateOnly` (default), or
   `CommitUpdateAndRoll`.
   For details, see
   [online update handling of non-dynamic WebLogic configuration changes](#online-update-handling-of-non-dynamic-weblogic-configuration-changes).


If the introspector job reports a failure or any other failure occurs, then
see [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) for advice.
When recovering from a failure, please keep the following points in mind:

 - The operator cannot automatically revert changes to resources that are under
   user control (just like with offline updates). For example, it is the administrator's
   responsibility to revert problem changes to an image, configMap, secrets, and domain resource YAML file.

 - If there is any failure during an online update, then no WebLogic configuration changes
   are made to the running domain and the introspector job retries up to a maximum number of times.
   To correct the problem, modify and reapply your model resources (ConfigMap and/or secrets),
   plus, if the introspector job has stopped retrying, you must also change your domain resource
   `domain.spec.introspectVersion` again.


Sample domain resource YAML file for an online update:

```yaml
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
        onNonDynamicChanges: "CommitUpdateAndRoll"
    secrets:
    - sample-domain1-datasource-secret
    - sample-domain1-another-secret
```

##### Online update handling of non-dynamic WebLogic configuration changes

The domain resource YAML `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` attribute
controls behavior when
non-dynamic WebLogic configuration changes are detected during an online update introspector job.
Non-dynamic changes are changes that require a domain restart to take effect.
Valid values are `CommitUpdateOnly` (default), or `CommitUpdateAndRoll`:

  * If set to `CommitUpdateOnly` (the default) and any non-dynamic changes are detected,
    then all changes will be committed,
    dynamic changes will take effect immediately,
    the domain will not automatically restart (roll),
    and any non-dynamic changes will become effective on a pod only when
    the pod is later restarted.

  * If set to `CommitUpdateAndRoll` and any non-dynamic changes are detected,
    then all changes will be committed, dynamic changes will take effect immediately,
    the domain will automatically restart (roll),
    and non-dynamic changes will take effect on each pod after the pod restarts.

{{% notice note %}}
When updating a domain with non-dynamic MBean changes with
`domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges=CommitUpdateOnly` (the default),
the non-dynamic changes are not effective on a WebLogic pod until the pod is restarted.
However, if you scale up a cluster or otherwise start any new servers in the domain,
then the new servers will start with the new non-dynamic changes
and the domain will then be running in an inconsistent state until its older servers are restarted.
{{% /notice %}}

##### Online update handling of deletes

The primary use case for online updates is to make small additions,
deletions of single resources or MBeans that have no dependencies,
or changes to non-dynamic MBean attributes.

Deletion can be problematic for online updates in two cases:
 - Deleting multiple resources that have cross dependencies.
 - Deleting the parent type section in an MBean hierarchy.

In general, complex deletion should be handled by offline updates
in order to avoid these problems.

**Note**: Implicitly removing a model's parent type
section may sometimes work depending
on the type of the section. For example, if you have an application
in the model under `appDeployments:` in a `model.configMap` and you
subsequently update the ConfigMap using an online update so that it
no longer includes the `appDeployment` section, then the online update
will delete the application from the domain.

_MBean type section deletion_

For an example of an MBean deletion, consider a WDT ConfigMap that starts with:

```yaml
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

If you want to online update to a new model without `work-managers`,
then change the ConfigMap to the following:

```yaml
resources:
  SelfTuning:
    WorkManager:
  JDBCSystemResource:
    ...
```

or supply an additional ConfigMap:

```yaml
resources:
  SelfTuning:
    WorkManager:
      '!wm1':
      '!wm2':
```

The online update will fail if you try replace the ConfigMap
with the `SelfTuning` section omitted:

```yaml
resources:
  JDBCSystemResource:
    ...
```

The above will fail as this implicitly removes
the MBean types `SelfTuning` and `WorkManager`.

_Deleting cross-referenced MBeans_

For an example of an unsupported online update delete of MBeans
with cross references, consider the case of a Work Manager
configured with constraints where you want to delete the entire Work Manager:

```yaml
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

If you try to specify the updated model in the ConfigMap as:

```yaml
resources:
    SelfTuning:
        WorkManager:
        MinThreadsConstraint:
        MaxThreadsConstraint:

```

Then, the operator will try use this delta to online update the domain:

```yaml
resources:
    SelfTuning:
        MaxThreadsConstraint:
            '!SampleMaxThreads':
        WorkManager:
            '!newWM':
        MinThreadsConstraint:
            '!SampleMinThreads':
```

This can fail because an online update might not delete all the referenced `Constraints` first
before deleting the `WorkManager`.

To work around problems with online updates to objects with cross dependencies, you can
use a series of online updates to make the change in stages. For example, continuing the previous Work Manager
example, first perform an online update to omit the Work Manager but not the constraints:

```yaml
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

After that update completes, then perform another online update:

```yaml
resources:
    SelfTuning:
        WorkManager:
        MinThreadsConstraint:
        MaxThreadsConstraint:
```

##### Online update status and labels

During an online update, the operator will rerun the introspector job, which
in turn attempts online WebLogic configuration changes to the running domain.
You can monitor an update's status using its domain resource's status conditions
and its WebLogic Server pod labels.

For example, for the domain status
you can check the domain resource `domain.status` stanza
using `kubectl -n MY_NAMESPACE get domain MY_DOMAINUID -o yaml`,
and for the WebLogic pod labels you can use
`kubectl -n MY_NAMESPACE get pods --show-labels` plus
optionally add `--watch` to watch the pods as they change over time.

_Here is how to interpret each domain resource's `domain.status.conditions` type:_

 1. The `Progressing` type.
    * Status attribute is True when a domain resource change is being processed by the operator,
      such as when:
       * The operator is processing a new domain resource or a change to an existing domain resource.
       * The introspector is running.
       * Servers are starting.
       * A roll is in progress
    * Status becomes False or unset after a failure, but can return to True if/when there is a retry.
    * Status becomes False or unset after a success.
    * _Note:_ This condition may 'blink' on and off during a roll or pod restart.

 1. The `Available` type.
    * Status attribute is True when:
      * Processing successfully completes without error
        (introspect job, syntax checks, and such)
      * The operator is starting or has started all desired WebLogic Server pods
        (not including any servers that may be shutting down).
    * Status is False or unset:
      * Servers are rolling/starting or a failure has occurred.
    * _Note:_ This condition may 'blink' on and off while
      processing a domain resource change or during a roll.
    * For example, after a successful online update,
      you will see something like this in the domain resource `domain.status` section:
      ```yaml
      status:
        clusters:
        - clusterName: cluster-1
          maximumReplicas: 5
          minimumReplicas: 0
          readyReplicas: 2
          replicas: 2
          replicasGoal: 2
        conditions:
        - lastTransitionTime: "2021-01-26T18:43:14.377Z"
          reason: ServersReady
          status: "True"
          type: Available
      ```

 1. The `Failed` type.
    * Status attribute is True after a failure
      including validation errors or an introspector failure.
      * In this case, the condition's `message` attribute will display an error message.
    * Status becomes False or unset after any succesful retry.
    * _Note:_ A `Failed` condition's status is _not_ set to True if the
      domain resource is successfully processed but one or more WebLogic Server
      pods is failing to start. In this case, Kubernetes will periodically
      try and restart the pod(s), and the `Available` type
      may remain True.

 1. The `ConfigChangesPendingRestart` type.
    * Status attribute is True if all of the following are true:
      * The domain resource attribute
        `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` is `CommitUpdateOnly`.
      * The domain resource attribute
        `domain.spec.configuration.model.onlineUpdate.enabled` is `True`.
      * There were model changes and these changes modify non-dynamic WebLogic configuration.
      * Processing successfully completed, including the introspector job.
      * The administrator has not subsequently rolled/restarted each WebLogic Server pod
        (in order to propagate the pending non-dynamic changes).
        * See the following discussion of WebLogic pod labels to see which pods are awaiting restart.
    * For example:
      ```
      Status:
        ...
        Conditions:
          Last Transition Time:  2021-01-20T15:09:15.209Z
          Message:               Online update completed successfully, but the changes require restart and the domain resource specified 'spec.configuration.model.onlineUpdate.onNonDynamicChanges=CommitUpdateOnly' or not set. The changes are committed but the domain require manually restart to  make the changes effective. The changes are: Server re-start is REQUIRED for the set of changes in progress.

      The following non-dynamic attribute(s) have been changed on MBeans
      that require server re-start:
      MBean Changed : com.bea:Name=oracle.jdbc.fanEnabled,Type=weblogic.j2ee.descriptor.wl.JDBCPropertyBean,Parent=[sample-domain1]/JDBCSystemResources[Bubba-DS],Path=JDBCResource[Bubba-DS]/JDBCDriverParams/Properties/Properties[oracle.jdbc.fanEnabled]
      Attributes changed : Value
          Reason:                      Online update applied, introspectVersion updated to 82
          Status:                      True
          Type:                        ConfigChangesPendingRestart
      ```

_Here are some of the expected WebLogic pod labels after an online update success:_

 1. Each WebLogic Server pod's `weblogic.introspectVersion` label value
    will eventually match the `domain.spec.introspectVersion` value that you defined.
    * If the domain resource attribute
      `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` is `CommitUpdateOnly` (the default),
      then the introspect version label on all pods is immediately updated
      after the introspect job successfully completes.
    * If the domain resource attribute
      `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` is `CommitUpdateAndRoll`
      and there are no non-dynamic configuration changes to the model,
      then the introspect version label on all pods is immediately updated
      after the introspect job successfully completes.
    * If the domain resource attribute
      `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` is `CommitUpdateAndRoll`
      and there are non-dynamic clabel onfiguration changes to the model,
      then the introspect version label on each pod is updated after the pod is rolled.

 1. There will be a `weblogic.configChangesPendingRestart=true` label on each
    WebLogic Server pod until the pod is restarted (rolled) by an administrator
    if all of the following are true:
    * The domain resource `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges`
      attribute is `CommitUpdateOnly` (the default).
    * Non-dynamic WebLogic configuration changes were included in a
      successful online model update.

##### Online update scenarios

1. _Successful online update that includes only dynamic WebLogic MBean changes._
   * Example dynamic WebLogic MBean changes:
     * Changing data source connection pool capacity, password, and targets.
     * Changing application targets.
     * Deleting or adding a data source.
     * Deleting or adding an application.
     * The MBean changes are committed in the running domain and effective immediately.
   * Expected outcome after the introspector job completes:
     * The domain `Available` condition status is set to `True`.
     * The `weblogic.introspectVersion` label on all pods will be set to match the `domain.spec.introspectVersion`.
   * Actions required:
     * None.

1. _Successful online update that includes non-dynamic WebLogic MBean attribute changes when `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` is `CommitUpdateOnly` (the default)._
   * Example non-dynamic WebLogic MBean change:
     * Changing a data source driver parameter property (such as `username`).
   * Expected outcome after the introspector job completes:
     * Any dynamic WebLogic configuration changes are committed in the running domain and effective immediately.
     * Non-dynamic WebLogic configuration changes will not take effect on already running WebLogic Server pods until an administrator subsequently rolls the pod.
     * The domain status `Available` condition will have a `Status` of `True`.
     * The domain status `ConfigChangesPendingRestart` condition will have a `Status` of `True` until an administrator subsequently rolls all WebLogic Server pods that are already running.
     * Each WebLogic Server pod's `weblogic.introspectVersion` label will match `domain.spec.introspectVersion`.
     * Each WebLogic Server pod that is already running will be given a `weblogic.configChangesPendingRestart=true` label until an administrator subsequently rolls the pod.
   * Actions required:
     * If you want the non-dynamic changes to take effect, then restart the pod(s) with the `weblogic.configChangesPendingRestart=true` label (such as by initiating a domain roll).
     * See [Online update handling of non-dynamic WebLogic configuration changes](#online-update-handling-of-non-dynamic-weblogic-configuration-changes).

1. _Successful online update that includes non-dynamic WebLogic MBean attribute changes when `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` is `CommitUpdateAndRoll`._
   * Expected outcome after the introspector job completes:
     * Any dynamic WebLogic configuration changes are committed in the running domain and effective immediately.
     * The operator will initiate a domain roll.
     * Non-dynamic WebLogic configuration changes will take effect on each pod when the pod is rolled.
     * Each WebLogic Server pod's `weblogic.introspectVersion` label will match `domain.spec.introspectVersion` after it is rolled.
     * The domain status `Available` condition will have a `Status` of `True` after the roll completes.
   * Actions required:
     * If you want the non-dynamic changes to take effect, then restart the pod(s) with the `weblogic.configChangesPendingRestart=true` label (such as by initiating a domain roll).
     * See [Online update handling of non-dynamic WebLogic configuration changes](#online-update-handling-of-non-dynamic-weblogic-configuration-changes).

1. _Changing any of the domain resource [fields that cause servers to be restarted]({{< relref "/userguide/managing-domains/domain-lifecycle/startup/_index.md#fields-that-cause-servers-to-be-restarted" >}}) in addition to `domain.spec.introspectVersion`, `spec.configuration.secrets`, `spec.configuration.model.onlineUpdate`, or `spec.configuration.model.configMap`._
   * Expected outcome after the introspector job completes:
     * No online update was attempted by the introspector job.
     * All model changes are treated the same as offline updates (which may result in restarts/roll after job success).
   * Actions required:
     * None.

1. _Changing any model attribute that is [unsupported](#unsupported-updates)._
   * Expected outcome:
     * The expected behavior is often undefined, but in some cases there will be helpful error in the introspector job, events, and/or domain status, and the job will periodically retry until the error is corrected or its maximum error count exceeded.
   * Actions required:
     * Use offline updates if they are supported, or, if not, shutdown the entire domain and restart it.
     * See [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}).

1. _Errors in the model; for example, a syntax error._
   * Expected outcome after the introspector job completes:
     * Error in the introspector job's pod log, domain events, and domain status.
     * The domain status `Failed` condition will have a `Status` of `True`.
     * Periodic job retries until the error is corrected or until a maximum error count is exceeded.
   * Actions required:
     * Correct the model.
     * If retries have halted, then alter the `spec.introspectVersion`.

1. _Other errors while updating the domain._
   * Expected outcome:
     * Error in the introspector job, domain events, and/or domain status.
     * The domain status `Failed` condition will have a `Status` of `True`.
     * If there's a failed introspector job, the job will retry periodically until the error is corrected or until it exceeds its maximum error count. Other types of errors will also usually incur periodic retries.
   * Actions required:
     * See [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}).
     * Make corrections to the domain resource and/or model.
     * If retries have halted, then alter the `spec.introspectVersion`.

##### Online update sample

For an online update sample which alters a data source and Work Manager, see the
[Update 4 use case]({{< relref "/samples/simple/domains/model-in-image/update4.md" >}})
in the Model in Image sample.

#### Appendices

##### Using the WDT Discover Domain and Compare Model Tools

Optionally, you can use the WDT Discover Domain and Compare Domain Tools to help generate your model file updates.
The WebLogic Deploy Tooling
[Discover Domain Tool](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/discover.md)
generates model files from an existing domain home,
and its [Compare Model Tool](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/compare.md)
compares two domain models and generates the YAML file for updating the first domain to the second domain.

For example, assuming you've installed WDT in `/u01/wdt/weblogic-deploy` and assuming your domain type is `WLS`:

  ```
  # (1) Run discover for your existing domain home.

  ```
  ```shell
  $ /u01/wdt/weblogic-deploy/bin/discoverDomain.sh \
    -oracle_home $ORACLE_HOME \
    -domain_home $DOMAIN_HOME \
    -domain_type WLS \
    -archive_file old.zip \
    -model_file old.yaml \
    -variable_file old.properties
  ```
  ```
  # (2) Now make some WebLogic config changes using the console or WLST.

  # (3) Run discover for your changed domain home.

  ```
  ```shell
  $ /u01/wdt/weblogic-deploy/bin/discoverDomain.sh \
    -oracle_home $ORACLE_HOME \
    -domain_home $DOMAIN_HOME \
    -domain_type WLS \
    -archive_file new.zip \
    -model_file new.yaml \
    -variable_file new.properties
  ```
  ```
  # (4) Compare your old and new yaml using diff

  ```
  ```shell
  $ diff new.yaml old.yaml
  ```
  ```
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
`spec.restartVersion`. Similarly, an [online update](#online-updates) is initiated by altering
the Domain `spec.introspectVersion`. Here are some common ways to alter either of these fields:

 - You can alter `restartVersion` interactively using `kubectl edit -n MY_NAMESPACE domain MY_DOMAINUID`.

 - If you have your domain's resource file, then you can alter this file and call `kubectl apply -f` on the file.

 - You can use the Kubernetes `get` and `patch` commands.

   Here's a sample automation script for `restartVersion`
   that takes a namespace as the first parameter (default `sample-domain1-ns`)
   and a domainUID as the second parameter (default `sample-domain1`):

   ```bash
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

   ```bash
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

 - You can use a WebLogic Server Kubernetes Operator sample script that invokes
   the same commands that are described in the previous bulleted item. See
   `patch-restart-version.sh` and `patch-introspect-version.sh` in
   the
   `kubernetes/samples/scripts/create-weblogic-domain/model-in-image/utils/`
   Model in Image sample directory.
