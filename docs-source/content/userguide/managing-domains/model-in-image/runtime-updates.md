+++
title = "Runtime updates"
date = 2020-03-11T16:45:16-05:00
weight = 50
pre = "<b> </b>"
description = "Updating a running Model in Image domain's images and model files."
+++

#### Contents

 - [Overview](#overview)
 - [Important notes](#important-notes)
 - [Frequently asked questions](#frequently-asked-questions)
 - [Supported and unsupported updates](#supported-and-unsupported-updates)
 - [Changing a Domain `restartVersion`](#changing-a-domain-restartversion)
 - [Using the WDT Discover and Compare Model Tools](#using-the-wdt-discover-domain-and-compare-model-tools)
 - [Example of adding a data source using rolling upgrade](#example-of-adding-a-data-source)
 - [Dynamic online only updates to a running domain](#online-updates-to-a-running-domain)
 - [Example of configuration changes with online only updates](#example-of-online-updates)

#### Overview

If you want to make a configuration change to a running Model in Image domain, and you want the change to survive WebLogic Server pod restarts, then you can modify your existing model using one of the following approaches:

  - Changing secrets or environment variables that are referenced by macros in your model files.

  - Specifying a new or updated WDT ConfigMap that contains model files and use your Domain YAML file `configuration.model.configMap` field to reference the map.

  - Supplying a new image with new or changed model files.

After the changes are in place, you can tell the operator to apply the changes and propagate them to a running domain using a rolling upgrade by altering the Domain YAML file's `image` or `restartVersion` attribute. 

Alternatively, for changes that only affect WebLogic configuration mbean attributes that are fully dynamic, you can tell the operator to attempt online updates that don't require a rolling upgrade.  See [Dynamic updates of a running domain](#Updating-a-running-domain).

#### Important notes

 - Check for [Supported and unsupported updates](#supported-and-unsupported-updates).

 - If you specify multiple model files in your image or WDT ConfigMap, the order in which they're loaded and merged is determined as described in [Model file naming and loading order]({{< relref "/userguide/managing-domains/model-in-image/model-files/_index.md#model-file-naming-and-loading-order" >}}).

 - You can use the WDT Discover Domain and Compare Domain Tools to help generate your model file updates. See [Using the WDT Discover Domain and Compare Model Tools](#using-the-wdt-discover-domain-and-compare-model-tools).

 - For simple ways to change `restartVersion`, see [Changing a Domain `restartVersion`](#changing-a-domain-restartversion).

 - For a sample of adding a data source to a running domain, see [Example of adding a data source](#example-of-adding-a-data-source).

 - For a discussion of model file syntax, see the [WebLogic Deploy Tool](https://github.com/oracle/weblogic-deploy-tooling) documentation and [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}).

 - If the introspector job reports a failure, see [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) for debugging advice.

#### Frequently asked questions

_Why is it necessary to specify updates using model files?_

Similar to Domain in Image, if you make a direct runtime WebLogic configuration update of a Model in Image domain using the WebLogic Server Administration Console or WLST scripts, then the update will be ephemeral. This is because the domain home is stored in an image directory which will not survive the restart of the pod.

_How do Model in Image updates work during runtime?_

After you make a change to your Domain `restartVersion` or `image` attribute, the operator will rerun the domain's introspector job. This job will reload all of your secrets and environment variables, merge all of your model files, and generate a new domain home. If the job succeeds, then the operator will make the updated domain home available to pods using a ConfigMap named `DOMAIN_UID-weblogic-domain-introspect-cm`. Finally, the operator will subsequently roll (restart) each running WebLogic Server pod in the domain so that it can load the new configuration. A domain roll begins by restarting the domain's Administration Server and then proceeds to restart each Managed Server in the domain.

_Can we use custom configuration overrides to do the updates instead?_

No. Custom configuration overrides, which are WebLogic configuration overrides specified using a Domain YAML file `configuration.overridesConfigMap`, as described in [Configuration overrides]({{< relref "/userguide/managing-domains/configoverrides/_index.md" >}}), aren't supported in combination with Model in Image. Model in Image will generate an error if custom overrides are specified. This should not be a concern because model file, secret, or model image updates are simpler and more flexible than custom configuration override updates. Unlike configuration overrides, the syntax for a model file update exactly matches the syntax for specifying your model file in the first place.


#### Supported and unsupported updates

{{% notice warning %}}
The expected behavior is undefined when applying an unsupported update. If you need to make an unsupported update and no workaround is documented, then shut down your domain entirely before making the change.
{{% /notice %}}

##### Supported updates

The following updates are *supported* except when they reference an area that is specifically documented as [unsupported](#unsupported-updates) below:

 - You can add a new WebLogic cluster or standalone server.

 - You can add new MBeans or resources by specifying their corresponding model YAML file snippet along with their parent bean hierarchy. See [Example of adding a data source](#example-of-adding-a-data-source).

 - You can change or add MBean attributes by specifying a YAML file snippet along with its parent bean hierarchy that references an existing MBean and the attribute. For example, to add or alter the maximum capacity of a data source named `mynewdatasource`:

   ```
   resources:
     JDBCSystemResource:
       mynewdatasource:
         JdbcResource:
           JDBCConnectionPoolParams:
             MaxCapacity: 5
   ```

   For more information, see [Using Multiple Models](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/model.md#using-multiple-models) in the WebLogic Deploy Tooling documentation.

 - You can change or add secrets that your model macros reference (macros that use the `@@SECRET:secretname:secretkey@@` syntax). For example, you can change a database password secret.

 - You can change or add environment variables that your model macros reference (macros that use the `@@ENV:myenvvar@@` syntax).

 - You can remove a named MBean, application deployment, or resource by specifying a model file with an exclamation point (`!`) just before its name. For example, if you have a data source named `mynewdatasource` defined in your model, it can be removed by specifying a small model file that loads after the model file that defines the data source, where the small model file looks like this:

   ```
   resources:
     JDBCSystemResource:
       !mynewdatasource:
   ```

   For more information, see [Declaring Named MBeans to Delete](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/model.md#declaring-named-mbeans-to-delete) in the WebLogic Deploying Tooling documentation.

##### Unsupported updates

TBD Add information about online updates...

The following updates are *unsupported*. If you need to make an unsupported update and no workaround is documented, then shut down your domain entirely before making the change.

 - There is no way to directly delete an attribute from an MBean that's already been specified by a model file. The workaround is to do this using two model files: add a model file that deletes the named bean/resource that is a parent to the attribute you want to delete, and add another model file that will be loaded after the first one, which fully defines the named bean/resource but without the attribute you want to delete.

 - There is no way to directly change the MBean name of an attribute. Instead, you can remove a named MBean using the `!` syntax as described above, and then add a new one as a replacement.

 - You cannot change the domain name at runtime.

 - You cannot change the topology of an existing WebLogic cluster. Specifically, do not apply runtime updates for:
   - Dynamic cluster size
   - Adding WebLogic Servers to a cluster or removing them

 - You cannot change, add, or remove network listen address, port, protocol, and enabled configuration for existing clusters or servers at runtime.

   Specifically, do not apply runtime updates for:
   - A Default, SSL, Admin channel `Enabled`, listen address, or port.
   - A Network Access Point (custom channel) `Enabled`, listen address, protocol, or port.

   Note that it is permitted to override network access point `public` or `external` addresses and ports. External access to JMX (MBean) or online WLST requires that the network access point internal port and external port match (external T3 or HTTP tunneling access to JMS, RMI, or EJBs don't require port matching).

   {{% notice warning %}}
   Due to security considerations, we strongly recommend that T3 or any RMI protocol should not be exposed outside the cluster.
   {{% /notice %}}

 - You cannot change, add, or remove server and domain log related settings in an MBean at runtime when the domain resource is configured to override the same MBeans using the `spec.logHome`, `spec.logHomeEnabled`, or `spec.httpAccessLogInLogHome` attributes.

 - You cannot change embedded LDAP security entries for [users, groups, roles](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/use_cases.md#modeling-weblogic-users-groups-and-roles), and [credential mappings](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/use_cases.md#modeling-weblogic-user-password-credential-mapping). For example, you cannot add a user to the default security realm. If you need to make these kinds of updates, then shut down your domain entirely before making the change, or switch to an [external security provider](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/use_cases.md#modeling-security-providers).

 - The following summarizes the types of runtime update configuration that are _not_ supported in this release of Model in Image unless a workaround is documented:

   * Domain topology of an existing WebLogic cluster. Specifically:
     * Dynamic cluster size
     * Adding WebLogic Servers to a cluster or removing them
   * Default and custom network channel configuration for an existing WebLogic cluster or server. Specifically:
     * Adding or removing Network Access Points (custom channels) for existing servers
     * Changing a Default, SSL, Admin, or custom channel, `Enabled`, listen address, protocol, or port
   * Node Manager related configuration
   * Changing any existing MBean name
   * Deleting an MBean attribute
   * Embedded LDAP entries

#### Changing a Domain `restartVersion`

As was mentioned in the [overview](#overview), one way to tell the operator to apply your configuration changes to a running domain is by altering the Domain `restartVersion`. Here are some common ways to do this:

 - You can alter `restartVersion` interactively using `kubectl edit -n MY_NAMESPACE domain MY_DOMAINUID`.

 - If you have your domain's resource file, then you can alter this file and call `kubectl apply -f` on the file.

 - You can use the Kubernetes `get` and `patch` commands. Here's a sample automation script that takes a namespace as the first parameter (default `sample-domain1-ns`) and that takes a domainUID as the second parameter (default `sample-domain1`):

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

#### Using the WDT Discover Domain and Compare Model Tools

The WebLogic Deploy Tooling [Discover Domain Tool](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/discover.md) generates model files from an existing domain home, and its [Compare Model Tool](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/compare.md) compares two domain models and generates the YAML file for updating the first domain to the second domain. You can use these tools in combination to help determine the model file contents you would need to supply to update an existing model.

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

  # (6) The compareDomain will generate these files:
  #      /tmp/diffed_model.json
  #      /tmp/diffed_model.yaml, and
  #      /tmp/compare_model_stdout
  ```

> **Note**: If your domain type isn't `WLS`, remember to change the domain type to `JRF` or `RestrictedJRF` in the above `discoverDomain.sh` commands.

#### Example of adding a data source

For details on how to add a data source, see the [Update1 use case]({{% relref "/samples/simple/domains/model-in-image/_index.md#update1-use-case" %}})
in the Model in Image sample.

#### Online updates to a running domain

For configuration changes that only modify dynamic WebLogic mbean attributes (subject to the exceptions listed in section support/unsupported TBD), you can initiate an online update to a running domain managed by the Operator by doing the following:

 - Modifying contents of the secrets that are referenced by WDT model(s) and/or modifying the contents of `domain.spec.configuration.model.configMap`.
 - Setting `domain.spec.configuration.model.onlineUpdate.enabled` to `true`
 - Changing `domain.spec.configuration.introspectVersion`.

If any non-dynamic WebLogic mbean attribute is changed as part of the above actions.  Since the non-dynamic changes require 
domain restart, the default behavior of the Operator is commit all changes but the user has to manually restart the domain.  You can control
this behavior by setting the attribute `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` to one of these values: 
    
 | OnNonDynamicChanges Value | Behavior | 
    |---------------------|-------------|
    | CommitUpdateOnly | Default or if not set.  All changes are committed, but if there are non-dynamic mbean changes. The domain needs to restart manually. |
    | CommitUpdateAndRoll |  All changes are committed, but if there are non-dynamic mbean changes, the domain will rolling restart automatically; if not, no restart is necessary |
    | CancelUpdate | If there are non-dynamic mbean changes, all changes are canceled before they are committed. The domain will continue to run, but changes to the configmap and resources in the domain resource YAML should be reverted manually, otherwise in the next introspection will still use the same content in the changed configmap |

{{% notice warning %}}
When updating a domain with non-dynamic mbean changes with the behavior `CommitUpdateOnly`, the changes are not effective until the domain is restarted. However, if you scale up the domain simultaneously, the new server(s) will start with the new changes, and the cluster will be running in an inconsistent state. 
{{% /notice %}}

Sample domain resource YAML:

```
apiVersion: "weblogic.oracle/v8"
kind: Domain
metadata:
  name: sample-domain1
  namespace: sample-domain1-ns
  labels:
    weblogic.resourceVersion: domain-v2
    weblogic.domainUID: sample-domain1
spec:
  ...
  introspectVersion: 5
  configuration:
    ...
    model:
    ...
      onlineUpdate:
        # enable the update to use dynamic update method which does not require restart for dynamic attributes
        enabled: true
        # Control the restart behavior of the domain when non-dynamic changes are involved in the onlineupdate
        # 
        # CommitUpdateOnly (Default):  All changes are committed. If the changes involve non-dynamic mbean attributes 
        #                              User has to restart the domain manually, use `kubectl describe domain <domain uid> to view the condition afater the update.
        # CommitUpdateAndRoll:   All changes are cmmitted. If the changes involve non-dynamic mbean attribues, the Operator will restart the domain automatically.
        # CancelChanges:  If the changes involve non-dynamic mbean attributes, no changes will be applied, the introspector job will not retry.  User should revert the changes to the config map or resources manually, otherwise
        #                 if someone tries to trigger the introspector job again, the changes to the configmap and resources will be applied.
        OnNonDynamicChanges: "CommitUpdateAndRoll"
```

During an online update, the Operator will rerun the introspector job, attempting online updates on the running domain. This feature is useful for changing any dynamic attribute of the WebLogic Domain. No pod restarts are necessary, and the changes immediately take effect. 

Once the job is completed, you can check the domain status to view the online update status: `kubectl -n <namespace> describe domain <domain uid>`.  Upon success, each WebLogic pod will have a `weblogic.introspectVersion` label that matches the `domain.spec.introspectVersion` that you specified.

Below is a general description of how online update works and the expected outcome.

|Scenarios|Expected Outcome|Actions Required|
  |---------------------|-------------|-------|
  |Changing a dynamic attribute|Changes are committed in the running domain and effective immediately| No action required|
  |Changing a non-dynamic attribute|If `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` is `CommitUpdateAndRoll`, then changes are committed and domain pods are restarted (rolled). | No action required|
  |Changing a non-dynamic attribute|If `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` is `CommitUpdateOnly`, then changes are committed but the changes will not be effective until the domain is manually restarted. | Manually restart the domain|
  |Changing domain resource YAML other than `domain.spec.introspectVersion` and the fields in `spec.configuration.model.onlineUpdate` | No online update is attempted by the introspector job, and changes are treated the same as offline updates (which may result in restarts/roll after job success). | No action required|
  |Changing any mbean attribute that is unsupported (TBD link to unsupported section)|Expected behavior is undefined. In some cases there will be helpful error in the introspector job, and the job will periodically retry until the error is corrected or its maximum error count exceeded.|Use offline updates or recreate the domain|
  |Errors in the model|Error in the introspector job, it will retry periodically until the error is corrected or until maximum error count exceeded|Correct the model|
  |Other errors while updating the domain|Error in the introspector job, it will retry periodically until the error is corrected or until maximum error count exceeded|Check the introspection job or domain status|
  

  
Sample use cases:

Dynamic attributes examples:

|Use case|Expected Outcome|Actions Required|
  |---------------------|-------------|-------|
  |Changing a data source connection pool capacity (dynamic attribute)|Changes are committed in running domain and effective immediately| No action required|
  |Changing a data source credentials (dynamic attribute)|Changes are committed in running domain and effective immediately| No action required|
  |Changing an application targeting (dynamic attribute)|Changes are committed in running domain and effective immediately|No action required|
  |Adding a data source (dynamic changes)|Changes are committed in running domain and effective immediately| No action required|
  |Remove all targets of a datasource (dynamic changes)|Changes are committed in running domain and effective immediately| No action required|
  |Deleting an application (dynamic changes)|Changes are committed in running domain and effective immediately| No action required|

Non dynamic attributes examples:

|Use case|Expected Outcome|Actions Required|
  |---------------------|-------------|-------|
  |Changing a data source driver parameters properties (non dynamic attribute)|Changes are committed in running domain and effective immediately| No action required|
  |Changing WebLogic administrator credentials (non dynamic changes)|Changes are committed, domain will rolling restart|No action required|
  |Changing image in the domain resource YAML at the same time|Offline changes are applied and domain will rolling restart|No action required|
  |Changing security settings under domainInfo or SecurityConfiguration section (non dynamic changes)|Changes are committed, domain will rolling restart|No action required|

Unsupported Changes:

For any of these unsupported changes, the introspector job will fail and automatically retry up to maximum limit times.  You can either cancel the job, correct the problem, or wait for the job retry interval.

- Topology changes (listen-address, listen-port), including SSL, deleting Server or ServerTemplate; top level Toplogy attributes.  Any changes to these attributes will result in an error. . The introspection job will fail and automatically retry up to maximum retries.
- Dependency deletion. For example, trying to delete a datasource that is referenced by a persistent store, even if both of them are deleting at the same time. The introspection job will fail and automatically retry up to maximum retries.
- Security related changes in the model including in `domainInfo.Admin*`, `domainInfo.RCUDbinfo.*`, `topology.Security.*`, `toplogy.SecurityConfiguration.*`.  Any changes in these sections will automatically switched to use offline update.
 
Checking online update status

When the introspection job finished, the domain status will be updated according to the result.

You can use the command to display the domain status

`kubectl -n <ns> describe domain <domain name>`

|Scenarios|Domain status ||
  |---------------------|-------------|-------|
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
    Type:                        OnlineUpdateComplete

```

If the changes involve non-dynamic mbean attributes, and you have specified 'CancelUpdate' under `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges`, you will see this

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

If the changes involve non-dynamic mbean attributes, and you have specified 'CommitUpdateOnly' under `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` or not set, you will see this               
      
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
       
Error Recovery

- When updating a domain with `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` is set to `CancelUpdate`, if the changes involve non-dynamic changes, all changes are canceled. User must correct the models immediately or use offline update instead. This avoids the mismatch between the models in the configmap and the domain's configuration.   
- Changes to the image, configmap, and domain resource YAML are under user control.  The operator cannot revert the changes automatically just like offline updates. 
- In case of any failure in online updates, no changes will be made to running domain.  However, since the introspector job runs periodically against the domain resources up to 6 times.
You can delete the introspection job, correct the error and re-apply the changes; or just correct the error and wait for the next introspection job retry.

Specifying timeout

In a rare case if the WDT online update command timeout results in error in the operator log, you can specify the following attributes under `onlineUpdate` to override the default value:

|Attribute Name | Default value (milli seconds) |
|-------|------|
|deployTimeoutMilliSeconds| 180000 |
|redeployTimeoutMilliSeconds| 180000 |
|undeployTimeoutMilliSeconds| 180000 |
|startApplicationTimeoutMilliSeconds| 180000 |
|stopApplicationTimeoutMilliSeconds| 180000 |
|connectTimeoutMilliSeconds| 120000 |
|activateTimeoutMilliSeconds| 180000 |
|setServerGroupsTimeoutMilliSeconds| 180000 |

