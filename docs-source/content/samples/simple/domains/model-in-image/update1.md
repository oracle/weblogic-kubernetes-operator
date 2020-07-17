---
title: "Update 1"
date: 2019-02-23T17:32:31-05:00
weight: 3
---

This use case demonstrates dynamically adding a data source to your running domain. It demonstrates several features of WDT and Model in Image:

- The syntax used for updating a model is the same syntax you use for creating the original model.
- A domain's model can be updated dynamically by supplying a model update in a file in a Kubernetes ConfigMap.
- Model updates can be as simple as changing the value of a single attribute, or more complex, such as adding a JMS Server.

For a detailed discussion of model updates, see [Runtime Updates]({{< relref "/userguide/managing-domains/model-in-image/runtime-updates.md" >}}) in the Model in Image user guide.

{{% notice warning %}}
The operator does not support all possible dynamic model updates. For model update limitations, consult [Runtime Updates]({{< relref "/userguide/managing-domains/model-in-image/runtime-updates.md" >}}) in the Model in Image user docs, and carefully test any model update before attempting a dynamic update in production.
{{% /notice %}}

Here are the steps:

1. Ensure that you have a running domain.

    Make sure you have deployed the domain from the [Initial]({{< relref "/samples/simple/domains/model-in-image/initial.md" >}}) use case.

1. Create a data source model YAML file.

    Create a WDT model snippet for a data source (or use the example provided).  Make sure that its target is set to `cluster-1`, and that its initial capacity is set to `0`.

   The reason for the latter is to prevent the data source from causing a WebLogic Server startup failure if it can't find the database, which would be likely to happen because you haven't deployed one (unless you're using the `JRF` path through the sample).

   Here's an example data source model configuration that meets these criteria:


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

   Place the above model snippet in a file named `/tmp/mii-sample/mydatasource.yaml` and then use it in the later step where you deploy the model ConfigMap, or alternatively, use the same data source that's provided in `/tmp/mii-sample/model-configmaps/datasource/model.20.datasource.yaml`.

1. Create the data source secret.

   The data source references a new secret that needs to be created. Run the following commands to create the secret:

   ```
   $ kubectl -n sample-domain1-ns create secret generic \
     sample-domain1-datasource-secret \
      --from-literal=password=Oradoc_db1 \
      --from-literal=url=jdbc:oracle:thin:@oracle-db.default.svc.cluster.local:1521/devpdb.k8s
   $ kubectl -n sample-domain1-ns label  secret \
     sample-domain1-datasource-secret \
     weblogic.domainUID=sample-domain1
   ```

    You name and label secrets using their associated domain UID for two reasons:
     - To make it obvious which secret belongs to which domains.
     - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all the resources associated with a domain.


1. Create a ConfigMap with the WDT model that contains the data source definition.

   Run the following commands:


   ```
   $ kubectl -n sample-domain1-ns create configmap sample-domain1-wdt-config-map \
     --from-file=/tmp/mii-sample/model-configmaps/datasource
   $ kubectl -n sample-domain1-ns label configmap sample-domain1-wdt-config-map \
     weblogic.domainUID=sample-domain1
   ```

     - If you've created your own data source file, then substitute the file name in the `--from-file=` parameter (we suggested `/tmp/mii-sample/mydatasource.yaml` earlier).
     - Note that the `-from-file=` parameter can reference a single file, in which case it puts the designated file in the ConfigMap, or it can reference a directory, in which case it populates the ConfigMap with all of the files in the designated directory.

   You name and label the ConfigMap using its associated domain UID for two reasons:
     - To make it obvious which ConfigMap belong to which domains.
     - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all resources associated with a domain.

1. Update your Domain YAML file to refer to the ConfigMap and Secret.

    - Option 1: Update a copy of your Domain YAML file from the Initial use case.

      - In the [Initial]({{< relref "/samples/simple/domains/model-in-image/initial.md" >}}) use case, we suggested creating a Domain YAML file named `/tmp/mii-sample/mii-initial.yaml` or using the `/tmp/mii-sample/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml` file that is supplied with the sample.
        - We suggest copying the original Domain YAML file and naming the copy `/tmp/mii-sample/mii-update1.yaml` before making any changes.

        - Working on a copy is not strictly necessary, but it helps keep track of your work for the different use cases in this sample and provides you a backup of your previous work.

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

      - Change its `spec.configuration.model.configMap` to look like the following:

          ```
          spec:
            ...
            configuration:
              ...
              model:
                ...
                configMap: sample-domain1-wdt-config-map
          ```

      - Apply your changed Domain YAML file:

        > **Note**: Before you deploy the domain custom resource, determine if you have Kubernetes cluster worker nodes that are remote to your local machine. If so, then you need to put the Domain YAML file's image in a location that these nodes can access and you may also need to modify your Domain YAML file to reference the new location. See [Ensuring your Kubernetes cluster can access images]({{< relref "/samples/simple/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

        ```
        $ kubectl apply -f /tmp/mii-sample/mii-update1.yaml
        ```

    - Option 2: Use the updated Domain YAML file that is supplied with the sample:

        > **Note**: Before you deploy the domain custom resource, determine if you have Kubernetes cluster worker nodes that are remote to your local machine. If so, then you need to put the Domain YAML file's image in a location that these nodes can access and you may also need to modify your Domain YAML file to reference the new location. See [Ensuring your Kubernetes cluster can access images]({{< relref "/samples/simple/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

        ```
        $ kubectl apply -f /tmp/miisample/domain-resources/WLS/mii-update1-d1-WLS-v1-ds.yaml
        ```


1. Restart ('roll') the domain.

   Now that the data source is deployed in a ConfigMap and its secret is also deployed, and you have applied an updated Domain YAML file with its `spec.configuration.model.configMap` and `spec.configuration.secrets` referencing the ConfigMap and secret, tell the operator to roll the domain.

   When a model domain restarts, it will rerun its introspector job in order to regenerate its configuration, and it will also pass the configuration changes found by the introspector to each restarted server.
   One way to cause a running domain to restart is to change the domain's `spec.restartVersion`. To do this:

   - Option 1: Edit your domain custom resource.
     - Call `kubectl -n sample-domain1-ns edit domain sample-domain1`.
     - Edit the value of the `spec.restartVersion` field and save.
       - The field is a string; typically, you use a number in this field and increment it with each restart.

   - Option 2: Dynamically change your domain using `kubectl patch`.
     - To get the current `restartVersion` call:
       ```
       $ kubectl -n sample-domain1-ns get domain sample-domain1 '-o=jsonpath={.spec.restartVersion}'
       ```
     - Choose a new restart version that's different from the current restart version.
       - The field is a string; typically, you use a number in this field and increment it with each restart.

     - Use `kubectl patch` to set the new value. For example, assuming the new restart version is `2`:
       ```
       $ kubectl -n sample-domain1-ns patch domain sample-domain1 --type=json '-p=[{"op": "replace", "path": "/spec/restartVersion", "value": "2" }]'
       ```
   - Option 3: Use the sample helper script.
     - Call `/tmp/mii-sample/utils/patch-restart-version.sh -n sample-domain1-ns -d sample-domain1`.
     - This will perform the same `kubectl get` and `kubectl patch` commands as Option 2.


1. Wait for the roll to complete.

    Now that you've started a domain roll, you'll need to wait for it to complete if you want to verify that the data source was deployed.

   - One way to do this is to call `kubectl get pods -n sample-domain1-ns --watch` and wait for the pods to cycle back to their `ready` state.

   - Alternatively, you can run `/tmp/mii-sample/utils/wl-pod-wait.sh -p 3`. This is a utility script that provides useful information about a domain's pods and waits for them to reach a `ready` state, reach their target `restartVersion`, and reach their target `image` before exiting.

     {{%expand "Click here to display the `wl-pod-wait.sh` usage." %}}
   ```
     $ ./wl-pod-wait.sh -?

       Usage:

         wl-pod-wait.sh [-n mynamespace] [-d mydomainuid] \
            [-p expected_pod_count] \
            [-t timeout_secs] \
            [-q]

         Exits non-zero if 'timeout_secs' is reached before 'pod_count' is reached.

       Parameters:

         -d <domain_uid> : Defaults to 'sample-domain1'.

         -n <namespace>  : Defaults to 'sample-domain1-ns'.

         pod_count > 0   : Wait until exactly 'pod_count' WebLogic Server pods for
                           a domain all (a) are ready, (b) have the same
                           'domainRestartVersion' label value as the
                           current Domain's 'spec.restartVersion, and
                           (c) have the same image as the current Domain's
                           image.

         pod_count = 0   : Wait until there are no running WebLogic Server pods
                           for a domain. The default.

         -t <timeout>    : Timeout in seconds. Defaults to '1000'.

         -q              : Quiet mode. Show only a count of wl pods that
                           have reached the desired criteria.

         -?              : This help.
   ```
     {{% /expand %}}

     {{%expand "Click here to view sample output from `wl-pod-wait.sh` that shows a rolling domain." %}}
   ```
     @@ [2020-04-30T13:53:19][seconds=0] Info: Waiting up to 1000 seconds for exactly '3' WebLogic Server pods to reach the following criteria:
     @@ [2020-04-30T13:53:19][seconds=0] Info:   ready='true'
     @@ [2020-04-30T13:53:19][seconds=0] Info:   image='model-in-image:WLS-v1'
     @@ [2020-04-30T13:53:19][seconds=0] Info:   domainRestartVersion='2'
     @@ [2020-04-30T13:53:19][seconds=0] Info:   namespace='sample-domain1-ns'
     @@ [2020-04-30T13:53:19][seconds=0] Info:   domainUID='sample-domain1'

     @@ [2020-04-30T13:53:19][seconds=0] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:53:19][seconds=0] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                                          VERSION  IMAGE                    READY   PHASE
     --------------------------------------------  -------  -----------------------  ------  ---------
     'sample-domain1-admin-server'                 '1'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-introspect-domain-job-wlkpr'  ''       ''                       ''      'Pending'
     'sample-domain1-managed-server1'              '1'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server2'              '1'      'model-in-image:WLS-v1'  'true'  'Running'

     @@ [2020-04-30T13:53:20][seconds=1] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:53:20][seconds=1] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                                          VERSION  IMAGE                    READY   PHASE
     --------------------------------------------  -------  -----------------------  ------  ---------
     'sample-domain1-admin-server'                 '1'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-introspect-domain-job-wlkpr'  ''       ''                       ''      'Running'
     'sample-domain1-managed-server1'              '1'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server2'              '1'      'model-in-image:WLS-v1'  'true'  'Running'

     @@ [2020-04-30T13:54:18][seconds=59] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:54:18][seconds=59] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                                          VERSION  IMAGE                    READY   PHASE
     --------------------------------------------  -------  -----------------------  ------  -----------
     'sample-domain1-admin-server'                 '1'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-introspect-domain-job-wlkpr'  ''       ''                       ''      'Succeeded'
     'sample-domain1-managed-server1'              '1'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server2'              '1'      'model-in-image:WLS-v1'  'true'  'Running'

     @@ [2020-04-30T13:54:19][seconds=60] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:54:19][seconds=60] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY   PHASE
     --------------------------------  -------  -----------------------  ------  ---------
     'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'  'Running'

     @@ [2020-04-30T13:54:31][seconds=72] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:54:31][seconds=72] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY    PHASE
     --------------------------------  -------  -----------------------  -------  ---------
     'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v1'  'false'  'Running'
     'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'   'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'   'Running'

     @@ [2020-04-30T13:54:40][seconds=81] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:54:40][seconds=81] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY   PHASE
     --------------------------------  -------  -----------------------  ------  ---------
     'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'  'Running'

     @@ [2020-04-30T13:54:52][seconds=93] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:54:52][seconds=93] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY   PHASE
     --------------------------------  -------  -----------------------  ------  ---------
     'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'  'Running'

     @@ [2020-04-30T13:54:58][seconds=99] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:54:58][seconds=99] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY    PHASE
     --------------------------------  -------  -----------------------  -------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'false'  'Pending'
     'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'   'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'   'Running'

     @@ [2020-04-30T13:55:00][seconds=101] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:55:00][seconds=101] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY    PHASE
     --------------------------------  -------  -----------------------  -------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'false'  'Running'
     'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'   'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'   'Running'

     @@ [2020-04-30T13:55:12][seconds=113] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:55:12][seconds=113] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY    PHASE
     --------------------------------  -------  -----------------------  -------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'false'  'Running'
     'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'   'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'   'Running'

     @@ [2020-04-30T13:55:24][seconds=125] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:55:24][seconds=125] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:


     NAME                              VERSION  IMAGE                    READY    PHASE
     --------------------------------  -------  -----------------------  -------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'false'  'Running'
     'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'   'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'   'Running'

     @@ [2020-04-30T13:55:33][seconds=134] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:55:33][seconds=134] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY   PHASE
     --------------------------------  -------  -----------------------  ------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'  'Running'

     @@ [2020-04-30T13:55:34][seconds=135] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:55:34][seconds=135] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY    PHASE
     --------------------------------  -------  -----------------------  -------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'true'   'Running'
     'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'false'  'Pending'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'   'Running'

     @@ [2020-04-30T13:55:40][seconds=141] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:55:40][seconds=141] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY   PHASE
     --------------------------------  -------  -----------------------  ------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'  'Running'

     @@ [2020-04-30T13:55:44][seconds=145] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:55:44][seconds=145] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY    PHASE
     --------------------------------  -------  -----------------------  -------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'true'   'Running'
     'sample-domain1-managed-server1'  '2'      'model-in-image:WLS-v1'  'false'  'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'   'Running'

     @@ [2020-04-30T13:56:25][seconds=186] Info: '2' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:56:25][seconds=186] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY   PHASE
     --------------------------------  -------  -----------------------  ------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server1'  '2'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'  'Running'

     @@ [2020-04-30T13:56:26][seconds=187] Info: '2' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:56:26][seconds=187] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY    PHASE
     --------------------------------  -------  -----------------------  -------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'true'   'Running'
     'sample-domain1-managed-server1'  '2'      'model-in-image:WLS-v1'  'true'   'Running'
     'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'false'  'Pending'

     @@ [2020-04-30T13:56:30][seconds=191] Info: '2' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:56:30][seconds=191] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY   PHASE
     --------------------------------  -------  -----------------------  ------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server1'  '2'      'model-in-image:WLS-v1'  'true'  'Running'

     @@ [2020-04-30T13:56:34][seconds=195] Info: '2' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:56:34][seconds=195] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY    PHASE
     --------------------------------  -------  -----------------------  -------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'true'   'Running'
     'sample-domain1-managed-server1'  '2'      'model-in-image:WLS-v1'  'true'   'Running'
     'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'false'  'Pending'

     @@ [2020-04-30T13:57:09][seconds=230] Info: '3' WebLogic Server pods currently match all criteria, expecting '3'.
     @@ [2020-04-30T13:57:09][seconds=230] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

     NAME                              VERSION  IMAGE                    READY   PHASE
     --------------------------------  -------  -----------------------  ------  ---------
     'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server1'  '2'      'model-in-image:WLS-v1'  'true'  'Running'
     'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'true'  'Running'


     @@ [2020-04-30T13:57:09][seconds=230] Info: Success!
  ```
     {{% /expand %}}

1. After your domain is running, you can call the sample web application to determine if the data source was deployed.

   Send a web application request to the ingress controller:

   ```
   $ curl -s -S -m 10 -H 'host: sample-domain1-cluster-cluster-1.mii-sample.org' \
      http://localhost:30305/myapp_war/index.jsp
   ```

   Or, if Traefik is unavailable and your Administration Server pod is running, you can run `kubectl exec`:

   ```
   $ kubectl exec -n sample-domain1-ns sample-domain1-admin-server -- bash -c \
     "curl -s -S -m 10 http://sample-domain1-cluster-cluster-1:8001/myapp_war/index.jsp"
   ```

   You will see something like the following:

    ```
    <html><body><pre>
    *****************************************************************

    Hello World! This is version 'v1' of the mii-sample JSP web-app.

    Welcome to WebLogic Server 'managed-server1'!

     domain UID  = 'sample-domain1'
     domain name = 'domain1'

    Found 1 local cluster runtime:
      Cluster 'cluster-1'

    Found 1 local data source:
      Datasource 'mynewdatasource': State='Running'

    *****************************************************************
    </pre></body></html>

    ```

If you see an error, then consult [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) in the Model in Image user guide.

If you plan to run the [Update 3]({{< relref "/samples/simple/domains/model-in-image/update3.md" >}}) use case, then leave your domain running.

To remove the resources you have created in the samples, see [Cleanup]({{< relref "/samples/simple/domains/model-in-image/cleanup.md" >}}).
