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
 - [Example of adding a data source](#example-of-adding-a-data-source)
 - [Dynamic updates of a running domain](#Updating-a-running-domain)

#### Overview

If you want to make a configuration change to a running Model in Image domain, and you want the change to survive WebLogic Server pod restarts, then you can modify your existing model using one of the following approaches:

  - Changing secrets or environment variables that are referenced by macros in your model files.

  - Specifying a new or updated WDT ConfigMap that contains model files and use your Domain YAML file `configuration.model.configMap` field to reference the map.

  - Supplying a new image with new or changed model files.

After the changes are in place, you can tell the operator to apply the changes and propagate them to a running domain by altering the Domain YAML file's `image` or `restartVersion` attribute.

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

#### Updating a running domain

When `useOnlineUpdate` is enabled in the `domain.spec.configuration`.  Operator will attempt to use the online update to the running domain, this 
feature is useful or changing any dynamic attribute of the Weblogic Domain, no restart is necessary, and the changes are immediate take effect.

You can update the configmap specified in `domain.spec.configuration.model.configmap` in the domain resource YAML or any associated secrets in the mdoel(s)
and a new `introspectVersion`, then apply the YAML, the operator will start the introspection job to update the domain.  Once the job is finished, you can use

`kubectl -n <namespace> dsecribe domain <domain uid>` to view the condition or message in the status section to see the results.

|Scenarios|Expected Outcome|Actions Required|
  |---------------------|-------------|-------|
  |Changing a dynamic attribute|Changes are committed in running domain and effective immediately| No action required|
  |Changing a non-dynamic attribute|Changes are committed, domain will rolling restart|No action required|
  |Unsupported changes for dynamic updates|Error in the introspect job, job will retry until error is corrected or cancel|Use offline updates or recreate the domain|
  |Errors in the model|Error in the introspect job, job will retry for 6 times until error is corrected or cancel|Correct the model|
  |Additional changes in domain resource YAML other than `userOnlineUpdate` and `introspectVersion`|Change are automatically switch to offline as before|No action required|
  |Errors while updating the domain|Error in the introspect job, job will retry for 6 times until error is corrected or cancel|Check the introspection job or domain status|
  
Example use cases:

|Scenarios|Expected Outcome|Actions Required|
  |---------------------|-------------|-------|
  |Changing a data source connection pool capacity (dynamic attribute)|Changes are committed in running domain and effective immediately| No action required|
  |Changing a data source credentials (dynamic attribute)|Changes are committed in running domain and effective immediately| No action required|
  |Changing a data source driver parameters properties (non dynamic attribute)|Changes are committed in running domain and effective immediately| No action required|
  |Changing an application targeting (dynamic attribute)|Changes are committed in running domain and effective immediately|No action required|
  |Changing the listen port, address, SSL on a server or channel (unsupported online changes)|Error in the introspect job, job will retry until error is corrected or cancel|Use offline updates or recreate the domain|
  |Adding a data source (dynamic changes)|Changes are committed in running domain and effective immediately| No action required|
  |Remove all targets of a datasource (dynamic changes)|Changes are committed in running domain and effective immediately| No action required|
  |Deleting an application|Changes are committed in running domain and effective immediately| No action required|
  |Changing Weblogic administrator credentials (non dynamic changes)|Changes are committed, domain will rolling restart|No action required|
  |Changing image in the domain resource YAML at the same time|Offline changes are applied and domain will rolling restart|No action required|
  |Changing security settings under domainInfo or SecurityConfiguration section (non dynamic changes)|Changes are committed, domain will rolling restart|No action required|

Unsupported Changes:

For any of these unsupported changes, the introspect job will fail and automatically retry up to 6 times.  You can either cancel the job, correct the problem, and wait for the job retry interval.

- Topology changes including SSL. The introspection job will fail and automatically retry up to 6 times.
- Dependency deletion. For example, trying to delete a datasource that is referenced by a persistent store, even if both of them are deleting at the same time. The introspection job will fail and automatically retry up to 6 times

Safeguarding domain restart 

In general, changes to a mission critical production running domain should be tested and make sure the changes do not accidentally restart the domain.

If you set domain.spec.configuration.rollBackIfRestartRequired` to `true`, then the operator will rollback all changes if changes require restart (i.e. involving non dynamic changes) 
The changes in your configmap and domain resources need to be reverted manually, the introspection job will not retry and the domain will remain unchanged.   
If the changes do not require restart then all changes are effective immediately. 

Status updates

|Scenarios|Domain status |
  |---------------------|-------------|-------|
  |Successful updates|Domain status will have a condition WLSDomainConfigurationStatus with message Successfully updated, reason with introspectionVersion|
  |Changes rolled back per request|Domain status will have a condition WLSDomainConfigurationStatus with message Online update rolledback, reason with introspectionVersion|
  |Any other errors| Domain status message will display the error message. No condition is set in the status condition|

Error Recovery

- When updating a domain it involves changes to the following, while user has an option to rollback the changes for changes that require restart. Any successful changes
to the domain are immediate.  
- Changes to the image, configmap and domain resource YAML are under user control.  The operator cannot revert the changes automatically just like offline updates.
- In case of any failure in online updates, no changes will be made to running domain.  However, since the introspect job runs periodically against the domain resources up to 6 times.
You can delete the introspect job, correct the error and re-apply the changes; or just correct the error and wait for the next introspect job retry.



