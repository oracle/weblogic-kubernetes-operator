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
 - [Using the WDT Discover Domain Tool](#using-the-wdt-discover-domain-tool)
 - [Example of adding a data source](#example-of-adding-a-data-source)

#### Overview

If you want to make a WebLogic domain home configuration change to a running Model in Image domain, and you want the change to survive WebLogic pod restarts, then you can modify your existing model by one or more the following approaches:

  - Changing secrets or environment variables that are referenced by macros in your model files.

  - Specifying a new or updated WDT ConfigMap that contains model files and use your domain resource `configuration.model.configMap` field to reference the map.

  - Supplying a new image with new or changed model files.

After the changes are in place, you can tell the operator to load the changes and propagate them to a running domain by altering the domain resource's `image` or `restartVersion` attribute.

#### Important notes

 - Check for [Supported and unsupported updates](#supported-and-unsupported-updates).

 - If you specify multiple model files in your image or WDT ConfigMap, the order in which they're loaded and merged is determined as described in [Model file naming and loading order]({{< relref "/userguide/managing-domains/model-in-image/model-files/_index.md#model-file-naming-and-loading-order" >}}).

 - You can use the WDT Discover Domain Tool to help generate your model file updates. See [Using the WDT Discover Domain Tool](#using-the-wdt-discover-domain-tool).

 - For simple ways to change `restartVersion`, see [Changing a domain resource `restartVersion`](#changing-a-domain-resource-restartversion).

 - For a sample of adding a data source to a running domain, see [Example of adding a data source](#example-of-adding-a-data-source).

 - For a discussion of model file syntax, see the [WebLogic Deploy Tool](https://github.com/oracle/weblogic-deploy-tooling) documentation and [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}).

 - If the introspector job reports a failure, see [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) for debugging advice.

#### Frequently asked questions

_Why is it necessary to specify updates using model files?_

Similar to Domain in Image, if you make a direct runtime WebLogic configuration update of a Model in Image domain using the WebLogic Server Administration Console or WLST scripts, then the update is ephemeral. This is because the domain home is stored in an image directory which will not survive the restart of the owning pod.

_How do Model in Image updates work during runtime?_

After you make a change to your domain resource `restartVersion` or `image` attribute, the operator will rerun the domain's introspector job. This job will reload all of your secrets and environment variables, merge all of your model files, and generate a new domain home. If the job succeeds, then the operator will make the updated domain home available to pods using a ConfigMap named `DOMAIN_UID-weblogic-domain-introspect-cm`. Finally, the operator will subsequently roll (restart) each running WebLogic Server pod in the domain so that it can load the new configuration. A domain roll begins by restarting the domain's Administration Server and then proceeds to restart each Manager Server in the domain.

_Can we use custom configuration overrides to do the updates instead?_

No. Custom configuration overrides, which are WebLogic configuration overrides specified using a domain resource `configuration.overridesConfigMap`, as described in [Configuration overrides]({{< relref "/userguide/managing-domains/configoverrides/_index.md" >}}), aren't supported in combination with Model in Image. Model in Image will generate an error if custom overrides are specified. This should not be a concern because model file, secret, or model image updates are simpler and more flexible than custom configuration override updates. Unlike configuration overrides, the syntax for a model file update exactly matches the syntax for specifying your model file in the first place.


#### Supported and unsupported updates

 - You can add new MBeans or resources simply by specifying their corresponding model file YAML snippet along with their parent bean hierarchy. See [Example of adding a data source](#example-of-adding-a-data-source).

 - You can recreate, change, or add secrets that your model depends on. For example, you can change a database password secret.

 - You can change or add environment variables that your model macros may depend on (macros that use the `@@ENV:myenvvar@@` syntax).

 - You can remove a named MBean or resource by specifying a model file with an exclamation point (`!`) symbol just before the bean or resource name. For example, if you have a data source named `mynewdatasource` defined in your model, it can be removed by specifying a small model file that loads after the model file that defines the data source, where the small model file looks like this:

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

 - There is no way to directly delete an attribute from an MBean that's already been specified by a model file. The work-around is to do this using two model files: (a) add a model file that deletes the named bean/resource that is a parent to the attribute you want to delete, and (b) add another subsequent model file that fully defines the named bean/resource but without the attribute you want to delete.

 - There is no way to directly change the MBean name of an attribute. Instead, you can remove a named MBean using the `!` syntax as described above, and then add a new one as a replacement.

 - You cannot change the domain name at runtime.

 - The following types of runtime update configuration haven't been tested and are _not_ supported in this release of Model in Image. If you need to make these kinds of updates, consider shutting down your domain entirely before making the change:
   * Domain topology (cluster members)
   * Network channel listen address, port, and enabled configuration
   * Server and domain log locations
   * Node Manager related configuration
   * Changing any existing MBean name

   **Specifically, do not apply runtime updates for:**

   * Adding or removing:
     * Servers
     * Clusters
     * Network Access Points (custom channels)
   * Changing any of the following:
     * Dynamic cluster size
     * Default, SSL, and Admin channel `Enabled`, listen address, and port
     * Network Access Point (custom channel), listen address, or port
     * Server and domain log locations -- use the `logHome` domain setting instead
     * Node Manager access credentials

   Note that it's OK, even expected, to override network access point `public` or `external` addresses and ports. Also note that external access to JMX (MBean) or online WLST requires that the network access point internal port and external port match (external T3 or HTTP tunneling access to JMS, RMI, or EJBs don't require port matching).

#### Changing a domain resource `restartVersion`

As was mentioned in the [overview](#overview), one way to tell the operator to apply your configuration changes to a running domain is by altering the domain resource `restartVersion`. Here are some common ways to do this:

 - You can alter `restartVersion` interactively using `kubectl edit -n MY_NAMESPACE domain MY_DOMAINUID`.

 - If you have your domain's resource file, then you can alter this file and call `kubectl apply -f` on the file.

 - You can use the Kubernetes `get` and `patch` commands. Here's a sample automation script that takes a namespace as the first parameter (default `sample-domain1-ns`) and that takes a domain uid as the second parameter (default `sample-domain1`):

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

#### Using the WDT Discover Domain Tool

The WebLogic Deploy Tooling [Discover Domain Tool](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/discover.md) generates model files from an existing domain home. You can use this tool to help determine the model file contents you would need to supply to update an existing model.

For example, assuming you've installed WDT in `/u01/wdt/weblogic-deploy` and assuming your domain type is `WLS`:

  ```

  # (1) Run discover for your existing domain home.

  /u01/wdt/weblogic-deploy/bin/discoverDomain.sh \
    -oracle_home $ORACLE_HOME \
    -domain_home $DOMAIN_HOME \
    -domain_type WLS \
    -archive_file old.zip \
    -model_file old.yaml \
    -variable_file old.properties

  # (2) Now make some WebLogic config changes using the console or WLST.

  # (3) Run discover for your existing domain home.

  /u01/wdt/weblogic-deploy/bin/discoverDomain.sh \
    -oracle_home $ORACLE_HOME \
    -domain_home $DOMAIN_HOME \
    -domain_type WLS \
    -archive_file new.zip \
    -model_file new.yaml \
    -variable_file new.properties

  # (4) Compare your old and new yaml to see what changed.

  diff new.yaml old.yaml
  ```

> **Note: If your domain type isn't `WLS`, remember to change the domain type to `JRF` or `RestrictedJRF` in the above commands.**

#### Example of adding a data source

Here's an example for adding a data source to a running model domain. We make the following assumptions about the domain resource, the database, and the data source:

 - Domain resource:
   - Is in namespace `sample-domain1-ns`.
   - Has domain UID `sample-domain1`.
   - Has a cluster named `cluster-1`.
 - Data source:
   - Targeted to WebLogic cluster `cluster-1`.
   - References an Oracle database `devpdb.k8s`.
     - Running in the `default` Kubernetes namespace with port 1521 on the `oracle-db` Kubernetes service.
     - Therefore, can be accessed with a `jdbc:oracle:thin:@oracle-db.default.svc.cluster.local:1521/devpdb.k8s` thin driver URL.
   - Assumes the user is `sys as dba` and the password `Oradoc_db1`.
   - Sets `JDBCConnectionPoolParams.InitialCapacity` to `0`.
     - This allows the data source to deploy without errors even when there's no database running at this location.

This example is designed to work on top of the 'Initial use case' described in the [Model in Image]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}}) sample, and is the same as the [Update1 use case]({{< relref "/samples/simple/domains/model-in-image/_index.md#update1-use-case" >}}) in the sample.

Here are the steps.

1. Copy the following data source WDT model to a file with the `.yaml` extension; let's put it in `~/datasource.yaml`.

   ```
   resources:
     JDBCSystemResource:
       mynewdatasource:
         Target: 'cluster-1'
         JdbcResource:
           JDBCDataSourceParams:
             JNDIName: [
               jdbc/mydatasource1,
               jdbc/mydatasource2
             ]
             GlobalTransactionsProtocol: TwoPhaseCommit
           JDBCDriverParams:
             DriverName: oracle.jdbc.xa.client.OracleXADataSource
             URL: '@@SECRET:@@ENV:DOMAIN_UID@@-datasource-secret:url@@'
             PasswordEncrypted: '@@SECRET:@@ENV:DOMAIN_UID@@-datasource-secret:password@@'
             Properties:
               user:
                 Value: 'sys as sysdba'
               oracle.net.CONNECT_TIMEOUT:
                 Value: 5000
               oracle.jdbc.ReadTimeout:
                 Value: 30000
           JDBCConnectionPoolParams:
               InitialCapacity: 0
               MaxCapacity: 1
               TestTableName: SQL ISVALID
               TestConnectionsOnReserve: true

   ```

1. Create a secret with the expected URL, user name, and password for the database and with a name and keys that correspond to the `@@SECRET` macros in the data source model:

   ```
   kubectl -n sample-domain1-ns delete secret \
     sample-domain1-datasource-secret \
     --ignore-not-found
   kubectl -n sample-domain1-ns create secret generic \
     sample-domain1-datasource-secret \
      --from-literal=password=Oradoc_db1 --from-literal=url=jdbc:oracle:thin:@oracle-db.default.svc.cluster.local:1521/devpdb.k8s
   kubectl -n sample-domain1-ns label  secret \
     sample-domain1-datasource-secret \
     weblogic.domainUID=sample-domain1
   ```

   - About deleting and recreating the secret:
     - We delete a secret before creating it, otherwise the create command will fail if the secret already exists.
     - This allows us to change the secret when using the `kubectl create secret` verb.

   - We name and label secrets using their associated domain UID for two reasons:
     - To make it obvious which secret belongs to which domains.
     - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all the resources associated with a domain.

1. Deploy the data source YAML in a ConfigMap.

   This step can be done before or after deploying the secret. The ConfigMap can have any name but the same name must be referenced by the domain resource `spec.configuration.model.configMap`, and the ConfigMap must be deployed to the same namespace as the domain resource.  (We will update the domain resource in the next step.)

   Run the following commands:

   ```
   kubectl -n sample-domain1-ns delete configmap sample-domain1-wdt-config-map --ignore-not-found
   kubectl -n sample-domain1-ns create configmap sample-domain1-wdt-config-map --from-file=~/datasource.yaml
   kubectl -n sample-domain1-ns label  configmap sample-domain1-wdt-config-map weblogic.domainUID=sample-domain1
   ```

   - Note that if you already have a WDT ConfigMap deployed for your running domain, then you should ensure that any updated ConfigMap that you supply includes any needed files that were in the original WDT ConfigMap. You can do this by adding additional `--from-file` parameters to the `kubectl create configmap` command line.
     - Note that the `-from-file=` parameter can reference a single file, in which case it puts the designated file in the ConfigMap, or it can reference a directory, in which case it populates the ConfigMap with all of the files in the designated directory.

   - About deleting and recreating the ConfigMap:
     - We delete a ConfigMap before creating it, otherwise the create command will fail if the ConfigMap already exists.
     - This allows us to change the ConfigMap when using the `kubectl create configmap` verb.

   - We name and label the ConfigMap using their associated domain UID for two reasons:
     - To make it obvious which ConfigMap belong to which domains.
     - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all the resources associated with a domain.

1. Update your domain resource file to refer to the ConfigMap and its secret, and apply it.

   - Add the secret to its `spec.configuration.secrets` stanza:

     ```
     spec:
       ...
       configuration:
         ...
         secrets:
         - sample-domain1-datasource-secret
     ```
     (Leave any existing secrets in place.)

   - Change its `spec.configuration.model.configMap` to look like:

     ```
     spec:
       ...
       configuration:
         ...
         model:
           ...
           configMap: sample-domain1-wdt-config-map
      ```
    - Apply your changed domain resource:

      ```
      kubectl apply -f your-domain-resource.yaml
      ```

1. Restart ('roll') the domain.

   Now that the data source is deployed in a ConfigMap and its secret is also deployed, and now that we have applied an updated domain resource with its `spec.configuration.model.configMap` and `spec.configuration.secrets` referencing the ConfigMap and secret, let's tell the operator to roll the domain.

   When a running model domain restarts, it will rerun its introspector job in order to regenerate its configuration, and it will also pass the configuration changes found by the introspector to each restarted server.

   One way to cause a running domain to restart is to change the domain's `spec.restartVersion`. There are multiple ways to make this modification, here are two:

   - Option 1: Live edit your domain.
     - Call `kubectl -n sample-domain1-ns edit domain sample-domain1`.
     - Edit the value of the `spec.restartVersion` field and save.
       - The field is a string; typically, you use a number in this field and increment it with each restart.
   - Option 2: Or, dynamically change your domain using `kubectl patch`.
     - To get the current `restartVersion` call:
       ```
       kubectl -n sample-domain1-ns get domain sample-domain1 '-o=jsonpath={.spec.restartVersion}'
       ```
     - Choose a new restart version that's different from the current restart version.
       - The field is a string; typically, you use a number in this field and increment it with each restart.
     - Use `kubectl patch` to set the new value. For example, assuming the new restart version is `2`:
       ```
       kubectl -n sample-domain1-ns patch domain sample-domain1 --type=json '-p=[{"op": "replace", "path": "/spec/restartVersion", "value": "2" }]'
       ```

1. Wait for your domain to roll.

   The operator should then rerun the introspector job and subsequently restart your domain's WebLogic pods, one-by-one. You can monitor this process using the command `kubectl -n sample-domain1-ns get pods --watch`. Here's some sample output:

   ```
   NAME                                         READY STATUS              RESTARTS   AGE
   sample-domain1-admin-server                  1/1   Running             0          132m
   sample-domain1-managed-server1               1/1   Running             0          129m
   sample-domain1-managed-server2               1/1   Running             0          131m
   sample-domain1-introspect-domain-job-tmxmh   0/1   Pending             0          0s
   sample-domain1-introspect-domain-job-tmxmh   0/1   ContainerCreating   0          0s
   sample-domain1-introspect-domain-job-tmxmh   1/1   Running             0          1s
   sample-domain1-introspect-domain-job-tmxmh   0/1   Completed           0          56s
   sample-domain1-introspect-domain-job-tmxmh   0/1   Terminating         0          57s
   sample-domain1-admin-server                  1/1   Terminating         0          133m
   sample-domain1-admin-server                  0/1   Pending             0          0s
   sample-domain1-admin-server                  0/1   ContainerCreating   0          0s
   sample-domain1-admin-server                  0/1   Running             0          2s
   sample-domain1-admin-server                  1/1   Running             0          32s
   sample-domain1-managed-server2               0/1   Terminating         0          133m
   sample-domain1-managed-server2               0/1   Pending             0          0s
   sample-domain1-managed-server2               0/1   ContainerCreating   0          0s
   sample-domain1-managed-server2               0/1   Running             0          2s
   sample-domain1-managed-server2               1/1   Running             0          40s
   sample-domain1-managed-server1               0/1   Terminating         0          134m
   sample-domain1-managed-server1               0/1   Pending             0          0s
   sample-domain1-managed-server1               0/1   ContainerCreating   0          0s
   sample-domain1-managed-server1               1/1   Running             0          33s
   ```

To debug problem updates, for example if the introspector enters an "Error" status, see [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}).
