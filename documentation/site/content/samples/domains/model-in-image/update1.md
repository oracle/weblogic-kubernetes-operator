---
title: "Update 1"
date: 2019-02-23T17:32:31-05:00
weight: 3
---

This use case demonstrates dynamically adding a data source to your running domain by updating your model and rolling your domain. It demonstrates several features of WDT and Model in Image:

- The syntax used for updating a model is the same syntax you use for creating the original model.
- A domain's model can be updated dynamically by supplying a model update in a file in a Kubernetes ConfigMap.
- Model updates can be as simple as changing the value of a single attribute, or more complex, such as adding a JMS Server.

For a detailed description of model updates, see [Runtime Updates]({{< relref "/managing-domains/model-in-image/runtime-updates.md" >}}).

{{% notice warning %}}
The operator does not support all possible dynamic model updates. For model update limitations, consult [Runtime Updates]({{< relref "/managing-domains/model-in-image/runtime-updates.md" >}}) in the Model in Image user docs, and carefully test any model update before attempting a dynamic update in production.
{{% /notice %}}

Here are the steps:

1. Ensure that you have a running domain.

    Make sure you have deployed the domain from the [Initial]({{< relref "/samples/domains/model-in-image/initial.md" >}}) use case.

1. Create a data source model YAML file.

    Create a WDT model snippet for a data source (or use the example provided).  Make sure that its target is set to `cluster-1`, and that its initial capacity is set to `0`.

   The reason for the latter is to prevent the data source from causing a WebLogic Server startup failure if it can't find the database, which would be likely to happen because you haven't deployed one.

   Here's an example data source model configuration that meets these criteria:


   ```yaml
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
                 Value: '@@SECRET:@@ENV:DOMAIN_UID@@-datasource-secret:user@@'
               oracle.net.CONNECT_TIMEOUT:
                 Value: 5000
               oracle.jdbc.ReadTimeout:
                 Value: 30000
           JDBCConnectionPoolParams:
               InitialCapacity: 0
               MaxCapacity: '@@SECRET:@@ENV:DOMAIN_UID@@-datasource-secret:max-capacity@@'
               TestTableName: SQL ISVALID
               TestConnectionsOnReserve: true

   ```

   Place the previous model snippet in a file named `/tmp/sample/mydatasource.yaml` and then use it in the later step where you deploy the model ConfigMap, or alternatively, use the same data source that's provided in `/tmp/sample/model-configmaps/datasource/model.20.datasource.yaml`.

1. Create the data source secret.

   The data source references a new secret that needs to be created. Run the following commands to create the secret:

   ```shell
   $ kubectl -n sample-domain1-ns create secret generic \
      sample-domain1-datasource-secret \
      --from-literal='user=sys as sysdba' \
      --from-literal='password=incorrect_password' \
      --from-literal='max-capacity=1' \
      --from-literal='url=jdbc:oracle:thin:@oracle-db.default.svc.cluster.local:1521/devpdb.k8s'
   ```
   ```shell
   $ kubectl -n sample-domain1-ns label  secret \
      sample-domain1-datasource-secret \
      weblogic.domainUID=sample-domain1
   ```

    We deliberately specify an incorrect password and a low maximum pool capacity because we will demonstrate dynamically correcting the data source attributes in the [Update 4]({{< relref "/samples/domains/model-in-image/update4.md" >}}) use case without requiring rolling the domain.

    You name and label secrets using their associated domain UID for two reasons:
     - To make it obvious which secret belongs to which domains.
     - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all the resources associated with a domain.


1. Create a ConfigMap with the WDT model that contains the data source definition.

   Run the following commands:


   ```shell
   $ kubectl -n sample-domain1-ns create configmap sample-domain1-wdt-config-map \
     --from-file=/tmp/sample/model-configmaps/datasource
   ```
   ```shell
   $ kubectl -n sample-domain1-ns label configmap sample-domain1-wdt-config-map \
     weblogic.domainUID=sample-domain1
   ```

     - If you've created your own data source file, then substitute the file name in the `--from-file=` parameter (we suggested `/tmp/sample/mydatasource.yaml` earlier).
     - Note that the `-from-file=` parameter can reference a single file, in which case it puts the designated file in the ConfigMap, or it can reference a directory, in which case it populates the ConfigMap with all of the files in the designated directory.

   You name and label the ConfigMap using its associated domain UID for two reasons:
     - To make it obvious which ConfigMap belong to which domains.
     - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all resources associated with a domain.

1. Update your Domain YAML file to refer to the ConfigMap and Secret.

    - Option 1: Update a copy of your Domain YAML file from the Initial use case.

      - In the [Initial]({{< relref "/samples/domains/model-in-image/initial.md" >}}) use case, we suggested creating a Domain YAML file named `/tmp/sample/mii-initial-domain.yaml` or using the [domain resource](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/model-in-image/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml) file that is supplied with the sample.
        - We suggest copying the original Domain YAML file and naming the copy `/tmp/sample/mii-update1.yaml` before making any changes.

        - Working on a copy is not strictly necessary, but it helps keep track of your work for the different use cases in this sample and provides you a backup of your previous work.

      - Add the secret to its `spec.configuration.secrets` stanza:

          ```yaml
          spec:
            ...
            configuration:
            ...
              secrets:
              - sample-domain1-datasource-secret
          ```
         (Leave any existing secrets in place.)

      - Change its `spec.configuration.model.configMap` to look like the following:

          ```yaml
          spec:
            ...
            configuration:
              ...
              model:
                ...
                configMap: sample-domain1-wdt-config-map
          ```

      - Apply your changed Domain YAML file:

        **NOTE**: Before you deploy the domain custom resource, ensure all nodes in your Kubernetes cluster [can access `auxliary-image` and other images]({{< relref "/samples/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

        ```shell
        $ kubectl apply -f /tmp/sample/mii-update1.yaml
        ```

    - Option 2: Use the updated Domain YAML file that is supplied with the sample:

        **NOTE**: Before you deploy the domain custom resource, ensure all nodes in your Kubernetes cluster [can access `auxliary-image` and other images]({{< relref "/samples/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

        ```shell
        $ kubectl apply -f /tmp/sample/domain-resources/WLS/mii-update1-d1-WLS-v1-ds.yaml
        ```

1. Restart ('roll') the domain.

   Now that the data source is deployed in a ConfigMap and its secret is also deployed, and you have applied an updated Domain YAML file with its `spec.configuration.model.configMap` and `spec.configuration.secrets` referencing the ConfigMap and secret, tell the operator to roll the domain.

   When a model domain restarts, it will rerun its introspector job to regenerate its configuration, and it will also pass the configuration changes found by the introspector to each restarted server.
   One way to cause a running domain to restart is to change the domain's `spec.restartVersion`. To do this:

   - Option 1: Edit your domain custom resource.
     - Call `kubectl -n sample-domain1-ns edit domain sample-domain1`.
     - Edit the value of the `spec.restartVersion` field and save.
       - The field is a string; typically, you use a number in this field and increment it with each restart.

   - Option 2: Dynamically change your domain using `kubectl patch`.
     - To get the current `restartVersion` call:
       ```shell
       $ kubectl -n sample-domain1-ns get domain sample-domain1 '-o=jsonpath={.spec.restartVersion}'
       ```
     - Choose a new restart version that's different from the current restart version.
       - The field is a string; typically, you use a number in this field and increment it with each restart.

     - Use `kubectl patch` to set the new value. For example, assuming the new restart version is `2`:
       ```shell
       $ kubectl -n sample-domain1-ns patch domain sample-domain1 --type=json '-p=[{"op": "replace", "path": "/spec/restartVersion", "value": "2" }]'
       ```
   - Option 3: Use the sample helper script.
     - Call `/tmp/sample/utils/patch-restart-version.sh -n sample-domain1-ns -d sample-domain1`.
     - This will perform the same `kubectl get` and `kubectl patch` commands as Option 2.


1. Wait for the roll to complete.

    Now that you've started a domain roll, you'll need to wait for it to complete if you want to verify that the data source was deployed.

   - One way to do this is to call `kubectl get pods -n sample-domain1-ns --watch` and wait for the pods to cycle back to their `ready` state.

   - For a more detailed view of this activity,
     you can use the `waitForDomain.sh` sample lifecycle script.
     This script provides useful information about a domain's pods and
     optionally waits for its `Completed` status condition to become `True`.
     A `Completed` domain indicates that all of its expected
     pods have reached a `ready` state
     plus their target `restartVersion`, `introspectVersion`, and `image`.
     For example:
     ```shell
     $ cd /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/domain-lifecycle
     $ ./waitForDomain.sh -n sample-domain1-ns -d sample-domain1 -p Completed
     ```

1. After your domain is running, you can call the sample web application to determine if the data source was deployed.

   Send a web application request to the ingress controller:

   ```shell
   $ curl -s -S -m 10 -H 'host: sample-domain1-cluster-cluster-1.sample.org' \
      http://localhost:30305/myapp_war/index.jsp
   ```

   Or, if Traefik is unavailable and your Administration Server pod is running, you can run `kubectl exec`:

   ```shell
   $ kubectl exec -n sample-domain1-ns sample-domain1-admin-server -- bash -c \
     "curl -s -S -m 10 http://sample-domain1-cluster-cluster-1:8001/myapp_war/index.jsp"
   ```

   You will see something like the following:

    ```html
    <html><body><pre>
    *****************************************************************

    Hello World! This is version 'v1' of the sample JSP web-app.

    Welcome to WebLogic Server 'managed-server1'!

      domain UID  = 'sample-domain1'
      domain name = 'domain1'

    Found 1 local cluster runtime:
      Cluster 'cluster-1'

    Found min threads constraint runtime named 'SampleMinThreads' with configured count: 1

    Found max threads constraint runtime named 'SampleMaxThreads' with configured count: 10

    Found 1 local data source:
      Datasource 'mynewdatasource':  State='Running', testPool='Failed'
        ---TestPool Failure Reason---
        NOTE: Ignore 'mynewdatasource' failures until the sample's Update 4 use case.
        ---
        ...
        ... invalid host/username/password
        ...
        -----------------------------

    *****************************************************************
    </pre></body></html>

    ```

A `TestPool Failure` is expected because we will demonstrate dynamically correcting the data source attributes in [Update 4]({{< relref "/samples/domains/model-in-image/update4.md" >}}).

If you see an error other than the expected `TestPool Failure`, then consult [Debugging]({{< relref "/managing-domains/debugging.md" >}}).

If you plan to run the [Update 3]({{< relref "/samples/domains/model-in-image/update3.md" >}}) or [Update 4]({{< relref "/samples/domains/model-in-image/update4.md" >}}) use case, then leave your domain running.

To remove the resources you have created in the samples, see [Cleanup]({{< relref "/samples/domains/model-in-image/cleanup.md" >}}).
