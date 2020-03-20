+++
title = "Runtime updates"
date = 2020-03-11T16:45:16-05:00
weight = 50
pre = "<b> </b>"
description = "Updating a running Model in Image domain's images and/or model files."
+++

### Contents

 - [Overview](#overview)
 - [Important notes](#important-notes)
 - [Frequently asked questions](#frequently-asked-questions)
 - [Supported and unsupported updates](#supported-and-unsupported-updates)
 - [Changing a domain resource 'restartVersion'](#changing-a-domain-resource-restartversion)
 - [Using the WDT discover tool](#using-the-wdt-discover-tool)
 - [Example of adding a datasource](#example-of-adding-a-datasource)

### Overview

If you want to make a WebLogic domain home configuration change to a running Model in Image domain, and you want the change to survive WebLogic pod restarts, then you can modify your existing model by one or more the following approaches:

  - Changing secrets or environment variables that are referenced by macros in your model files.

  - Specifying a new or updated WDT config map that contains model files and use your domain resource `configuration.model.configMap` field to reference the map. 

  - Supplying a new image with new and/or changed model files. 

Once changes are in place, you can tell the operator to load the changes and propagate them into a running domain by altering the domain resource's `image` or `restartVersion` attribute.

### Important notes

 - Check for [Supported and unsupported updates](#supported-and-unsupported-updates).

 - If you specify multiple model files in your image or WDT config map, the order in which they're loaded and merged is determined as described in [Model file naming and loading order]({{< relref "/userguide/managing-domains/model-in-image/model-files.md#model-file-naming-and-loading-order" >}}).

 - You can use the WDT discover tool to help generate your model file updates. See [Using the WDT discover tool](#using-the-wdt-discover-tool).

 - See [Changing a domain resource 'restartVersion'](#changing-a-domain-resource-restartversion) for sample ways to change `restartVersion`.

 - See [Example of adding a datasource](#example-of-adding-a-datasource) below for a sample of adding a datasource to a running domain.

 - See the [WebLogic Deploy Tool Documentation](https://github.com/oracle/weblogic-deploy-tooling) and [Model files]({{< relref "/userguide/managing-domains/model-in-image/model-files.md" >}}) for a discussion of model file syntax.

 - If the introspector job reports a failure, see [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) for debugging advice.

### Frequently asked questions

_Why is it necessary to specify updates using model files?_ 

Similar to Domain in Image, if you make a direct runtime WebLogic configuration update of a Model in Image domain using the WebLogic console or WLST scripts then the update is ephemeral. This is because the domain home is stored in an image directory which will not survive the restart of the owning pod. 

_How do Model in Image updates work during runtime?_ 

After you make a change to your domain resource `restartVersion` or `image` attribute, the Operator will rerun the domain's introspector job. This job will reload all of your secrets and environment variables, merge all of your model files, and generate a new domain home. If the job succeeds, the Operator will then make the updated domain home available to pods using a config map named `DOMAIN_UID-weblogic-domain-introspect-cm`. Finally, the operator will subsequently roll (restart) each running WebLogic server pod in the domain that it can load the new configuration. A domain roll begins by restarting the domain's admin server and then proceeds to roll each manager server in the domain.

_Can we use custom config overrides to do the updates instead?_ 

No. Custom config overrides, which are WebLogic configuration overrides specified via a domain resource `configuration.overridesConfigMap` as described in [Configuration overrides]({{< relref "/userguide/managing-domains/configoverrides/_index.md" >}}), aren't supported in combination with Model in Image. Model in Image will generate an error if customer overrides are specified. This should not be a concern because model file, secret, and/or model image updates are simpler and more flexible than custom configuration override updates. Unlike configuration overrdies, the syntax for a model file update exactly matches the syntax for specifying your model file in the first place.


### Supported and unsupported updates

 - You can add new mbeans or resources simply by specifying their corresponding model file yaml snippet along with their parent bean hierarchy. For example see [Example of adding a datasource](#example-of-adding-a-datasource) below.

 - You can recreate, change, or add secrets that your model depends on. For example, you can change a database password secret.

 - You can change or add environment variables that your model macros may depend on (macros that use the `@@ENV:myenvvar@@` syntax).

 - You can remove a named mbean or resource by specifying a model file with a `!` symbol just before the bean or resource name. For example, if you have a data source named `mynewdatasource` defined in your model, it can be removed by specifying a small model file that loads after the model file that defines the datasource, where the small model file looks like this:

  ```
  resources:
    JDBCSystemResource:
      !mynewdatasource:
  ```

  For more information see [Declaring Named MBeans to Delete](https://github.com/oracle/weblogic-deploy-tooling#declaring-named-mbeans-to-delete) in the WebLogic Deploying Tooling documentation.

 - You can add or alter an mbean attribute by specifying a yaml snippet along with its parent bean hierarchy that references an existing mbean and the attribute. For example, to add or alter the max capacity of a data source named `mynewdatasource`:

  ```
  resources:
    JDBCSystemResource:
      mynewdatasource:
        JdbcResource:
          JDBCConnectionPoolParams:
            MaxCapacity: 5
  ```

  For more information see [Using Multiple Models](https://github.com/oracle/weblogic-deploy-tooling#using-multiple-models) in the WebLogic Deploy Tooling documentation.

 - There is no way to directly delete an attribute from an mbean that's already been specified by a model file. The work-around is to do this via two model files: (a) add a model file that deletes the named bean/resource that is a parent to the attribute you want to delete, and (b) add another  subsequent model file that fully defines the named/resource but without the attribute you want to delete.

 - The following runtime updates haven't been tested and are not supported in the first release of the Model in Image feature. If you need to make this kind of update, consider shutting down your domain entirely before making the change.
   - Adding, removing, or altering the network configuration of an existing managed server. This includes but isn't limited to network channels, ports, and cluster addresses.
   - Adding a managed server to an existing configured cluster.
   - Altering the cluster-size or max-cluster-size of an existing dynamic cluster.
   - TBD This needs some research. Check with QA, etc.

### Changing a domain resource 'restartVersion'

As was mentioned in the [overview](#overview) one way to tell the operator to apply your configuration changes to a running domain is by altering the domain resource `restartVersion`. Here are some common ways to do this:

 - You can alter `restartVersion` interactively using `kubectl edit -n MY_NAMESPACE domain MY_DOMAINUID`.

 - If you have your domain's resource file you can alter this file and call `kubectl apply -f` on the file.

 - You can use the Kubernetes `get` and `patch` commands. Here's a sample automation script:

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

### Using the WDT discover tool

The [WebLogic Deploy Tooling discover tool](https://github.com/oracle/weblogic-deploy-tooling/blob/master/site/discover.md) generates model files from an existing domain home. You can use this tool to help determine the model file contents you would need to supply to update an existing model. 

For example, if you already have a running Model in Image domain in `sample-domain1-ns` with admin server pod `sample-domain1-admin-server` you can do the following:

  ```
  # (1) get a bash prompt in your admin server pod
  kubectl -n sample-domain1-ns \
    exec -it sample-domain1-admin-server /bin/bash

  # (2) In the pod, use the image's WDT discover script to
  # get the original WDT configuration. Notes:
  #  - WebLogic pods define ORACLE_HOME and DOMAIN_HOME
  #    for you.
  #  - A Model in Image image will already have WDT binaries 
  #    in '/u01/wdt/weblogic-deploy/bin'.
  #  - Set 'domain_type' to one of WLS, JRF, or
  #    RestrictedJRF.

  /u01/wdt/weblogic-deploy/bin/discoverDomain.sh \
    -oracle_home $ORACLE_HOME \
    -domain_home $DOMAIN_HOME \
    -domain_type WLS \
    -archive_file old.zip \
    -model_file old.yaml \
    -variable_file old.properties

  # (3) Now make some WebLogic config changes via the console or WLST.

  # (4) In the pod, use the image's WDT discover script to
  # get the latest WDT configuration after your changes. Notes:
  #  - Set 'domain_type' to one of WLS, JRF, or
  #    RestrictedJRF.

  /u01/wdt/weblogic-deploy/bin/discoverDomain.sh \
    -oracle_home $ORACLE_HOME \
    -domain_home $DOMAIN_HOME \
    -domain_type WLS \
    -archive_file new.zip \
    -model_file new.yaml \
    -variable_file new.properties

  # (5) In the pod, compare your old and new yaml to see what 
  # changed.

  diff new.yaml old.yaml
  ```

### Example of adding a datasource

Here's an example script for adding a data source to a WebLogic cluster named `cluster-1` for a domain resource in namespace `sample-domain1-ns` with domain uid `sample-domain1`. This example is designed to work on top of the [Model in Image sample]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}}).

This example references a DB running in the `default` namespace that is accessed with url `oracle-db.default.svc.cluster.local:1521/devpdb.k8s`, user `sys as dba`, and password `Oradoc_db1`. Note that you can still add this datasource even if there's no database running at this location.

> IMPORTANT: If you already have a WDT configmap deployed for your running domain, as is true for the [Model in Image sample]({{< relref "/samples/simple/domains/model-in-image/_index.md" >}}), then you should ensure that it includes any other neeeded files that were in the original WDT configmap in addition to the new datasource yaml file. For example, the Model in Image sample puts file(s) in a directory that it uses to stage its config map in `$WORKDIR/wdtconfigmap`.


  ```
  #!/bin/bash

  set -eu

  # Assume SAMPLEDIR references the 'kubernetes/samples/scripts/create-weblogic-domain/model-in-image' directory within the operator source.

  # Assume the DB is running in the following namespace

  DB_NAMESPACE="default"

  # Assume DOMAIN_UID is the following

  DOMAIN_UID=sample-domain1

  # Assume DOMAIN_NAMESPACE is the following

  DOMAIN_NAMESPACE=sample-domain1-ns

  # Assume the DB URL and password are defined in a secret as follows

  $SAMPLEDIR/create_secret.sh \
    -n ${DOMAIN_NAMESPACE} \
    -s ${DOMAIN_UID}-new-db-access-secret \
    -l password=Oradoc_db1 \
    -l url=oracle-db.${DB_NAMESPACE}.svc.cluster.local:1521/devpdb.k8s

  # Assume WORKDIR is your working directory

  cd $WORKDIR

  # Create a WDT configmap with the datasource WDT yaml snippet

  mkdir -p ./wdtconfigmap

  cat << EOF > wdtconfigmap/datasource.yaml
  resources:
    JDBCSystemResource:
      mynewdatasource:
        Target: 'cluster-1'
        JdbcResource:
          JDBCDataSourceParams:
            JNDIName: [
              jdbc/generic2,
              jdbc/special2
            ]
            GlobalTransactionsProtocol: TwoPhaseCommit
          JDBCDriverParams:
            DriverName: oracle.jdbc.xa.client.OracleXADataSource
            URL: '@@FILE:/weblogic-operator/config-overrides-secrets/${DOMAIN_UID}-new-db-access-secret/url@@'
            PasswordEncrypted: '@@FILE:/weblogic-operator/config-overrides-secrets/${DOMAIN_UID}-new-db-access-secret/password@@'
            Properties:
              user:
                Value: 'sys as sysdba'
              oracle.net.CONNECT_TIMEOUT:
                Value: 5000
              oracle.jdbc.ReadTimeout:
                Value: 30000
          JDBCConnectionPoolParams:
              InitialCapacity: 0
              MaxCapacity: 1                   # This is a comment
              TestTableName: SQL ISVALID       # This is a comment
              TestConnectionsOnReserve: true   # This is a comment
  EOF

  # Create a config map containing the model snippet

  $SAMPLEDIR/create_configmap.sh \
    -n ${DOMAIN_NAMESPACE} \
    -c ${DOMAIN_UID}-wdt-config-map \
    -f ./wdtconfigmap

  ```

  Now use `kubectl -n sample-domain1-ns edit domain sample-domain1` to:

  - Make sure your `spec.configuration.model.configMap` value is set to `sample-domain1-wdt-config-map`.

  - Add the new secret `sample-domain1-new-db-access-secret` to the `configuration.model.secrets` list.

  - Add the `spec.restartVersion` and set it if it's not already set, or alter the restartVersion. A restartVersion can be set to any value - it only needs to be different than the previous value.

  Your edit session, once completed, should look something like this:

  ```
  ...

  spec:
    
    ...

    configuration:
      model:
        configMap: sample-domain1-wdt-config-map
        domainType: WLS
        runtimeEncryptionSecret: sample-domain1-runtime-encryption-secret
      secrets:
      - sample-domain1-new-db-access-secret
      - sample-domain1-rcu-access
    domainHome: /u01/domains/sample-domain1
    domainHomeSourceType: FromModel
    image: model-in-image:v1

    ... 

    restartVersion: "15"

    ...
  ```

The operator should then rerun the introspector and subsequently roll your domain's WebLogic pods one-by-one. You can monitor this process using the command `kubectl -n sample-domain1-ns get pods --watch`. Here's some sample output:

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

To debug problem updates see [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}).

