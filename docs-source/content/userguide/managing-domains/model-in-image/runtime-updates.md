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
 - [Changing a domain resource `restartVersion`](#changing-a-domain-resource-restartversion)
 - [Using the WDT Discover and Compare Model Tools](#using-the-wdt-discover-domain-and-compare-model-tools)
 - [Example of adding a data source](#example-of-adding-a-data-source)

#### Overview

If you want to make a configuration change to a running Model in Image domain, and you want the change to survive WebLogic Server pod restarts, then you can modify your existing model using one of the following approaches:

  - Changing secrets or environment variables that are referenced by macros in your model files.

  - Specifying a new or updated WDT ConfigMap that contains model files and use your domain resource `configuration.model.configMap` field to reference the map.

  - Supplying a new image with new or changed model files.

After the changes are in place, you can tell the operator to apply the changes and propagate them to a running domain by altering the domain resource's `image` or `restartVersion` attribute.

#### Important notes

 - Check for [Supported and unsupported updates](#supported-and-unsupported-updates).

 - If you specify multiple model files in your image or WDT ConfigMap, the order in which they're loaded and merged is determined as described in [Model file naming and loading order]({{< relref "/userguide/managing-domains/model-in-image/model-files/_index.md#model-file-naming-and-loading-order" >}}).

 - You can use the WDT Discover Domain and Compare Domain Tools to help generate your model file updates. See [Using the WDT Discover Domain and Compare Model Tools](#using-the-wdt-discover-domain-and-compare-model-tools).

 - For simple ways to change `restartVersion`, see [Changing a domain resource `restartVersion`](#changing-a-domain-resource-restartversion).

 - For a sample of adding a data source to a running domain, see [Example of adding a data source](#example-of-adding-a-data-source).

 - For a discussion of model file syntax, see the [WebLogic Deploy Tool](https://github.com/oracle/weblogic-deploy-tooling) documentation and [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}).

 - If the introspector job reports a failure, see [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) for debugging advice.

#### Frequently asked questions

_Why is it necessary to specify updates using model files?_

Similar to Domain in Image, if you make a direct runtime WebLogic configuration update of a Model in Image domain using the WebLogic Server Administration Console or WLST scripts, then the update will be ephemeral. This is because the domain home is stored in an image directory which will not survive the restart of the pod.

_How do Model in Image updates work during runtime?_

After you make a change to your domain resource `restartVersion` or `image` attribute, the operator will rerun the domain's introspector job. This job will reload all of your secrets and environment variables, merge all of your model files, and generate a new domain home. If the job succeeds, then the operator will make the updated domain home available to pods using a ConfigMap named `DOMAIN_UID-weblogic-domain-introspect-cm`. Finally, the operator will subsequently roll (restart) each running WebLogic Server pod in the domain so that it can load the new configuration. A domain roll begins by restarting the domain's Administration Server and then proceeds to restart each Managed Server in the domain.

_Can we use custom configuration overrides to do the updates instead?_

No. Custom configuration overrides, which are WebLogic configuration overrides specified using a domain resource `configuration.overridesConfigMap`, as described in [Configuration overrides]({{< relref "/userguide/managing-domains/configoverrides/_index.md" >}}), aren't supported in combination with Model in Image. Model in Image will generate an error if custom overrides are specified. This should not be a concern because model file, secret, or model image updates are simpler and more flexible than custom configuration override updates. Unlike configuration overrides, the syntax for a model file update exactly matches the syntax for specifying your model file in the first place.


#### Supported and unsupported updates

 - You can add new MBeans or resources simply by specifying their corresponding model file YAML snippet along with their parent bean hierarchy. See [Example of adding a data source](#example-of-adding-a-data-source).

 - You can change or add secrets that your model references. For example, you can change a database password secret.

 - You can change or add environment variables that your model macros reference (macros that use the `@@ENV:myenvvar@@` syntax).

 - You can remove a named MBean, application deployment, or resource by specifying a model file with an exclamation point (`!`) just before its name. For example, if you have a data source named `mynewdatasource` defined in your model, it can be removed by specifying a small model file that loads after the model file that defines the data source, where the small model file looks like this:

   ```
   resources:
     JDBCSystemResource:
       !mynewdatasource:
   ```

   For more information, see [Declaring Named MBeans to Delete](https://github.com/oracle/weblogic-deploy-tooling#declaring-named-mbeans-to-delete) in the WebLogic Deploying Tooling documentation.

 - You can add or alter an MBean attribute by specifying a YAML snippet along with its parent bean hierarchy that references an existing MBean and the attribute. For example, to add or alter the maximum capacity of a data source named `mynewdatasource`:

   ```
   resources:
     JDBCSystemResource:
       mynewdatasource:
         JdbcResource:
           JDBCConnectionPoolParams:
             MaxCapacity: 5
   ```

   For more information, see [Using Multiple Models](https://github.com/oracle/weblogic-deploy-tooling#using-multiple-models) in the WebLogic Deploy Tooling documentation.

 - There is no way to directly delete an attribute from an MBean that's already been specified by a model file. The work-around is to do this using two model files: add a model file that deletes the named bean/resource that is a parent to the attribute you want to delete, and add another model file that will be loaded after the first one, which fully defines the named bean/resource but without the attribute you want to delete.

 - There is no way to directly change the MBean name of an attribute. Instead, you can remove a named MBean using the `!` syntax as described above, and then add a new one as a replacement.

 - You cannot change the domain name at runtime.

 - The following types of runtime update configuration are _not_ supported in this release of Model in Image. If you need to make these kinds of updates, shut down your domain entirely before making the change:
   * Domain topology of an existing WebLogic cluster (cluster members)
   * Network channel listen address, port, and enabled configuration of an existing cluster or server
   * Server and domain log locations
   * Node Manager related configuration
   * Changing any existing MBean name

   Specifically, do not apply runtime updates for:

   * Adding WebLogic Servers to a cluster, or removing them
   * Adding or removing Network Access Points (custom channels) for existing servers
   * Changing any of the following:
     * Dynamic cluster size
     * Default, SSL, and Admin channel `Enabled`, listen address, and port
     * Network Access Point (custom channel), listen address, or port
     * Server and domain log locations -- use the `logHome` domain setting instead
     * Node Manager access credentials

   Note that it is permitted to override network access point `public` or `external` addresses and ports. External access to JMX (MBean) or online WLST requires that the network access point internal port and external port match (external T3 or HTTP tunneling access to JMS, RMI, or EJBs don't require port matching).

{{% notice warning %}}
Due to security considerations, we strongly recommend that T3 or any RMI protocol should not be exposed outside the cluster.
{{% /notice %}}

#### Changing a domain resource `restartVersion`

As was mentioned in the [overview](#overview), one way to tell the operator to apply your configuration changes to a running domain is by altering the domain resource `restartVersion`. Here are some common ways to do this:

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
