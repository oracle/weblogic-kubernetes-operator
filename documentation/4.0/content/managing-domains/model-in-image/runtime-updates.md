+++
title = "Runtime updates"
date = 2020-03-11T16:45:16-05:00
weight = 50
pre = "<b> </b>"
description = "Updating a running Model in Image domain's images and model files."
+++

{{< table_of_contents >}}

### Overview

The WebLogic domain configuration deployed using Model in image deployment model is controlled by the operator, that is the source
of truth is the WDT model.  Any changes to the configuration must be done in the model, using the WebLogic Server Administration Console or WLST scripts,
then the update will be ephemeral and will not survive server restarts.

In general, updating the WebLogic domain configuration involves updating the `WDT` artifacts, updating any referenced macros, and then trigger an introspector job.  

There are two approaches of updating a running domain:

- _Offline updates_: [Offline updates](#offline-updates) A new domain configuration is created and then restart the entire domain. 

 - _Online updates_: [online update](#online-updates) A new domain configuration is created, and then the differences between the previously deployed model and the new model 
is used update the existing domain using `WDT` online update.  If model changes are only involving fully dynamic configuration MBean attributes,
   then all the changes are immediately available and the domain continue to be in operation. 
   If an online update involves any non-dynamic configuration MBean attributes, then you can control whether the operator will automatically restart the domain or manually restarting the domain
   at your convenience.  _The primary use case for online updates is to make small additions,
   undeploy of single resources or MBeans that have no dependencies,
   or changes to non-dynamic MBean attributes, for complex changes, use offline updates instead_.

   
**Note** changing `domain.spec.image`, `domain.spec.serverPod.env`, or any other domain resource YAML
[fields that cause servers to be restarted]({{< relref "/managing-domains/domain-lifecycle/startup/_index.md#fields-that-cause-servers-to-be-restarted" >}});
this will automatically and immediately result in a rerun of your introspector job,
a roll if the job succeeds, plus an offline update if there are any accompanying model changes.

The operator does not support all types of WebLogic configuration changes while a domain is still running.
If a change is unsupported for an online or offline update, then propagating
the change requires entirely shutting domain the domain,
applying the change, and finally restarting the domain. Full domain restarts are described in
[Full domain restarts]({{< relref "/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts">}}).

**NOTE**: Supported and unsupported changes are described in these sections: [Supported updates](#supported-updates) and [Unsupported updates](#unsupported-updates).
_It is the administrator's responsibility to make the necessary changes to a domain resource to initiate the correct approach for an update._

### Updating WDT artifacts

You can update any `WDT` artifacts in their source locations [WDT artifacts locations (TODO fix link)]({{< relref "/managing-domains/model-files#wdt-artifacts-source-location-and-loading-order">}}), you 
may also need to update any referenced macros [Model file macros](({{< relref "/managing-domains/model-files#model-file-macros">}})

Since all the models are merged into a single model before processing in all cases. You can, for example:

- Add a datasource by adding a new datasource entry in a model file.
- Undeploy an application by removing the entry from it's model file.
- Add or update any Mbean attribute.
- Add or remove any model file completely.
- Update any Kubernetes secrets or environment variables.

Although `WDT` supports deleting an mbean from the model [Deleting mbean](https://oracle.github.io/weblogic-deploy-tooling/concepts/model/#declaring-named-mbeans-to-delete), 
this is rarely needed.  You can simply remove the entry from the model instead of using the delete notation.


After your artifacts are updated, you can instruct the operator to propagate the changed model
to a running domain by following the steps in [Offline updates](#offline-updates)
or [Online updates](#online-updates).

### Offline updates

Use the following steps to initiate an offline configuration update to your model:

 1. Modify your domain resource YAML file:
    1. Update any newly referenced image(s), environment variables, and Kuberenetes secrets.
    1. Change an attribute that instructs the operator to roll the domain.
       For examples, see
       [change the domain `spec.restartVersion`](#changing-a-domain-restartversion-or-introspectversion)
       or change any of the other Domain resource YAML [fields that cause servers to be restarted]({{< relref "/managing-domains/domain-lifecycle/startup/_index.md#fields-that-cause-servers-to-be-restarted" >}}).

The operator will subsequently rerun the domain's introspector job.  This job will generate a new domain home, and 
restart the domain.

If the job reports a failure, see
[Debugging]({{< relref "/managing-domains/debugging.md" >}})
for advice.

### Online updates

Use the following steps to initiate an online configuration update to your model:

 1. Modify your domain resource YAML file:
    1. Update any newly referenced image(s), environment variables, and Kubernetes secrets.
    1. Set `domain.spec.configuration.model.onlineUpdate.enabled` to `true` (default is `false`).
    1. Set `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` to one of
       `CommitUpdateOnly` (default), and `CommitUpdateAndRoll`.
    1. Optionally, tune the WDT timeouts in `domain.spec.configuration.model.onlineUpdate.wdtTimeouts`.
       - This is only necessary in the rare case when an introspector job's WDT online update command timeout
         results in an error in the introspector job log or operator log.
       - All timeouts are specified in milliseconds and default to two or three minutes.
       - For a full list of timeouts, you can call
         `kubectl explain domain.spec.configuration.model.onlineUpdate.wdtTimeouts`.
    1. Change `domain.spec.introspectVersion` to a different value. For examples, see
       [change the domain `spec.introspectVersion`](#changing-a-domain-restartversion-or-introspectversion).

After you've completed these steps, the operator will trigger an introspector Job which
generates a new merged model, create a new domain, compares the newly merged model to the previously deployed model,
and using the differences to perform an online update using `WDT` online update domain command.  If the differences between
the models involving cross mbean dependencies, the online update will fail during the online update.  For example,
in the new model, a JDBC store cannot find the referenced data source.

 - If no restart is necessary, that is all the changes are dynamic, then no further actions are needed and the chagnes are 
available immediately.
 - If a restart is necessary, that is there are non dynamic changes, then depending on how you have configured 
   `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` to `CommitUpdateOnly` (default, manually restart the domain), or
   `CommitUpdateAndRoll` (automatically restart the domain).   You can check the domain and pod status to confirm whether restart is needed.  
   For details, see [Online update requiring manual restart](#online-update-requiring-manual-restart)

If the introspector job reports a failure or any other failure occurs, then
see [Debugging]({{< relref "/managing-domains/debugging.md" >}}) for advice.
When recovering from a failure, please keep the following points in mind:

 - The operator cannot automatically revert changes to resources that are under
   user control (just like with offline updates). For example, it is the administrator's
   responsibility to revert problem changes to an image, configMap, secrets, and domain resource YAML file.

 - If there is any failure during an online update, then no WebLogic configuration changes
   are made to the running domain and the introspector job retries up to the failure retry time
   limit specified in `domain.spec.failureRetryLimitMinutes`.
   To correct the problem, modify and reapply your model resources (ConfigMap and/or secrets),
   plus, if the introspector job has stopped retrying, you must also change your domain resource
   `domain.spec.introspectVersion` again. For more information, see [Domain failure retry processing]({{< relref "/managing-domains/domain-lifecycle/retry.md" >}}).

   
#### Online update requiring manual restart

1. Successful online update that includes non-dynamic WebLogic MBean attribute changes when `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` is `CommitUpdateOnly` (the default).
     * The domain status `ConfigChangesPendingRestart` condition will have a `Status` of `True` until an administrator subsequently rolls all WebLogic Server pods that are already running.
     * Each WebLogic Server pod that is already running will be given a `weblogic.configChangesPendingRestart=true` label until an administrator subsequently rolls the pod.
   * Actions required:
     See [Full domain restarts]({{< relref "/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts">}}).* 
 
### Appendices

Review the following appendices for additional, important information.

#### Supported updates

The following updates are *supported* for offline or online updates,
except when they reference an area that is specifically
documented as [unsupported](#unsupported-updates):

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

   For more information, see [Using Multiple Models](https://oracle.github.io/weblogic-deploy-tooling/concepts/model/#using-multiple-models) in the WebLogic Deploy Tooling documentation.

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
   just before its name plus ensuring the new model file is loaded _after_
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
   [Declaring Named MBeans to Delete](https://oracle.github.io/weblogic-deploy-tooling/concepts/model/#declaring-named-mbeans-to-delete)
   in the WebLogic Deploying Tooling documentation.

#### Unsupported updates

{{% notice warning %}}
It is important to avoid applying unsupported model updates to a running domain. An attempt to use an unsupported update may not always result in a clear error message, and the expected behavior may be undefined. If you need to make an unsupported update and no workaround is documented, then shut down your domain entirely before making the change. See [Full domain restarts]({{< relref "/managing-domains/domain-lifecycle/startup/_index.md#full-domain-restarts">}}).
{{% /notice %}}

The following summarizes the types of runtime update configuration that are _not_ supported in Model in Image unless a workaround or alternative is documented:

  - Altering cluster size:
     - You have a limited ability to change an existing WebLogic cluster's membership.
     - Specifically, do _not_ apply runtime updates for:
        - Adding WebLogic Servers to a configured cluster. As an alternative, consider using dynamic clusters instead of configured clusters.
        - Removing WebLogic Servers from a configured cluster. As an alternative, you can lower your cluster's domain resource YAML `replicas` attribute.
        - Decreasing the size of a dynamic cluster. As an alternative, you can lower your cluster's domain resource YAML `replicas` attribute.
  - You cannot change, add, or remove network listen address, port, protocol, and enabled configuration for existing clusters or servers at runtime.
     - Specifically, do not apply runtime updates for:
       - A Default, SSL, Admin channel `Enabled`, listen address, or port.
       - A Network Access Point (custom channel) `Enabled`, listen address, protocol, or port.
       - Note that it is permitted to override network access point `public` or `external` addresses and ports.
       External access to JMX (MBean) or online WLST requires that the network access point internal port
       and external port match (external T3 or HTTP tunneling access to JMS, RMI, or EJBs don't require port matching).

 {{% notice warning %}}
 Due to security considerations, we strongly recommend that T3 or any RMI protocol should not be exposed outside the cluster.
 {{% /notice %}}


  - Node Manager related configuration.
  - Log related settings. This applies to changing, adding, or removing server and domain log related settings in an MBean at runtime
    when the domain resource is configured to override the same MBeans using the `spec.logHome`,
    `spec.logHomeEnabled`, or `spec.httpAccessLogInLogHome` attributes.
  - Changing the domain name. You cannot change the domain name at runtime.
  - Deleting an MBean attribute:
     - There is no way to directly delete an attribute from an MBean that's already been specified by a model file.
     - The workaround is to do this using two model files:
        - Add a model file that deletes the named bean/resource that is a parent to
        the attribute you want to delete using the `!` syntax as described in [Supported Updates](#supported-updates).
        - Add another model file that will be loaded after the first one,
        which fully defines the named bean/resource but without the attribute you want to delete.
  - Changing any existing MBean name:
     - There is no way to directly change the MBean name of an attribute.
     - Instead, you can remove a named MBean using the `!` syntax as described in [Supported Updates](#supported-updates).
     - Then, you add a new one as a replacement.
  - Embedded LDAP entries:
     - Embedded LDAP security entries for [users, groups, roles](https://oracle.github.io/weblogic-deploy-tooling/samples/usersgroups-model/),
     and [credential mappings](https://oracle.github.io/weblogic-deploy-tooling/samples/pwcredentialmap-model/). For example, you cannot add a user to the default security realm.
     - Online update attempts in this area will fail during the introspector job, and offline update attempts may result in inconsistent security checks during the offline update's rolling cycle.
     - If you need to make these kinds of updates, then shut down your domain entirely before making the change,
     or switch to an [external security provider](https://oracle.github.io/weblogic-deploy-tooling/samples/securityproviders-model/).
  - Any Model YAML `topology:` stanza changes:
     - For example, `ConsoleEnabled`, `RootDirectory`, `AdminServerName`, and such.
     - For a complete list, run `/u01/wdt/weblogic-deploy/bin/modelHelp.sh -oracle_home $ORACLE_HOME topology`
     (this assumes you have installed WDT in the `/u01/wdt/weblogic-deploy` directory).
  - Dependency deletion in combination with online updates.
 - Deleting Model entries by type:
    - For example, you cannot delete an entire `SelfTuning` type stanza
    by omitting the stanza in an online update or by specifying an additional model with `!SelfTuning`
    in either an offline or an online update.
   - Instead, you can delete the specific MBean by omitting the MBean itself while leaving its `SelfTuning`
   parent in place or by specifying an additional model using the `!` syntax in combination
   with the name of the specific MBean.
   - For details, see [Online update handling of deletes](#online-update-handling-of-deletes).
 - Deleting multiple resources that have cross-references in combination with online updates:
    - For example, concurrently deleting a persistent store and a data source referenced by the persistent store.
    - For this type of failure, the introspection job will fail and log an error describing
    the failed reference, and the job will automatically retry up to its maximum retries.
    - For details, see [Online update handling of deletes](#online-update-handling-of-deletes).
 - Security related changes in combination with online updates:
    - Such changes included security changes in `domainInfo.Admin*`,
   `domainInfo.RCUDbinfo.*`, `topology.Security.*`, and `topology.SecurityConfiguration.*`.
    - Any online update changes in these sections will result in a failure.


#### Changing a Domain `restartVersion` or `introspectVersion`

As was mentioned in [Offline updates](#offline-updates), one way to tell the operator to
apply offline configuration changes to a running domain is by altering the Domain
`spec.restartVersion`. Similarly, an [online update](#online-updates) is initiated by altering
the Domain `spec.introspectVersion`. Here are some common ways to alter either of these fields:

 - You can alter `restartVersion` or `introspectVersion` interactively using `kubectl edit -n MY_NAMESPACE domain MY_DOMAINUID`.

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

 - You can use a WebLogic Kubernetes Operator sample script that invokes
   the same commands that are described in the previous bulleted item.
   - See `patch-restart-version.sh` and `patch-introspect-version.sh` in
     the `kubernetes/samples/scripts/create-weblogic-domain/model-in-image/utils/`
     directory.
   - Or, see the more advanced `introspectDomain.sh` and `rollDomain.sh` among
     the [Domain lifecycle sample scripts]({{< relref "/samples/domains/lifecycle/_index.md">}}).
