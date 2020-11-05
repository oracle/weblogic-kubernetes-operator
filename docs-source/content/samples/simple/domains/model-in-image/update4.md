---
title: "Update 4"
date: 2019-02-23T17:32:31-05:00
weight: 3
---

This use case demonstrates dynamically configuring Work Manager Threads Constraints in your running domain without restarting the servers. This use case requires Update1 use case to be run already. 

In the use case, you will:

 - Update the ConfigMap containing WDT model created in Update1 use case with changes to Work Manager Threads Constraint configuration
 - Update the Domain YAML file to enable online update feature
 - Update the Domain YAML file to trigger a domain introspection, which applies the new configuration values without restarting servers

Here are the steps:

1. Make sure you have deployed the domain from the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case.

1. Update the model YAML file from the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case, with the desired changes to the Minimum and Maximum Threads Constraints.

   Here's an example model configuration that updates the configured count values for the `SampleMinThreads` Minimum Threads Constraint and `SampleMaxThreads` Maximum Threads Constraint:


   ```
   resources:
     SelfTuning:
       MinThreadsConstraint:
         SampleMinThreads:
           Count: 2
       MaxThreadsConstraint:
         SampleMaxThreads:
           Count: 20
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
   Place the above model snippet in a file named `/tmp/mii-sample/mywmdatasource.yaml` and then use it in the later step where you deploy the model ConfigMap, or use the same data source that's provided in `/tmp/mii-sample/model-configmaps/wmdatasource/model.20.wmdatasource.yaml`.

   It is important to keep the datasource configuration from Update 1 use case in the ConfigMap. If it is removed from the ConfigMap, the introspector would delete the datasource from the servers.

1. Replace the ConfigMap created in Update1 use case with a ConfigMap with the WDT model containing both the data source and configuration updates.

   Run the following commands:


   ```
   $ kubectl -n sample-domain1-ns delete configmap sample-domain1-wdt-config-map
   $ kubectl -n sample-domain1-ns create configmap sample-domain1-wdt-config-map \
     --from-file=/tmp/mii-sample/model-configmaps/wmdatasource
   $ kubectl -n sample-domain1-ns label configmap sample-domain1-wdt-config-map \
     weblogic.domainUID=sample-domain1
   ```

     - If you've created your own model YAML file, then substitute the file name in the `--from-file=` parameter (we suggested `/tmp/mii-sample/mywmdatasource.yaml` earlier).
     - Note that the `-from-file=` parameter can reference a single file, in which case it puts the designated file in the ConfigMap, or it can reference a directory, in which case it populates the ConfigMap with all of the files in the designated directory.

   You name and label the ConfigMap using its associated domain UID for two reasons:
     - To make it obvious which ConfigMap belong to which domains.
     - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all resources associated with a domain.

1. Update your Domain YAML file to set `useOnlineUpdate` to `true`.

    - Operator will attempt to use the online update to the running domain without 
      restarting the servers if only changes made are updating dynamic attributes of the WebLogic Domain.

    - Option 1: Edit your domain custom resource.
      - Call `kubectl -n sample-domain1-ns edit domain sample-domain1`.
      - Add or edit the value of the `spec.configuration.useOnlineUpdate` field to `true` and save.

   - Option 2: Dynamically change your domain using `kubectl patch`.
     - Use `kubectl patch` to set the value. For example:
       ```
       $ kubectl -n sample-domain1-ns patch domain sample-domain1 --type=json '-p=[{"op": "replace", "path": "/spec/configuration/useOnlineUpdate", "value": "true" }]'
       ```
   - Option 3: Use the sample helper script.
     - Call `/tmp/mii-sample/utils/patch-use-online-update.sh -n sample-domain1-ns -d sample-domain1`.
     - This will perform the same `kubectl patch` commands as Option 2.

1. Inform the operator to introspect the WebLogic domain configuration.

   Now that the updated configuration is deployed in a ConfigMap, we need to tell the operator to 
   rerun its introspector job in order to regenerate its configuration. 
   When the `spec.configuration.useOnlineUpdate` value is `true`, and all the changes are 
   dynamic changes, the domain introspector can apply the changes to the servers without doing a server restart.
   Change the `spec.introspectVersion` of the domain to trigger domain instrospection to be performed. 
   To do this:

   - Option 1: Edit your domain custom resource.
     - Call `kubectl -n sample-domain1-ns edit domain sample-domain1`.
     - Edit the value of the `spec.introspectVersion` field and save.
       - The field is a string; typically, you use a number in this field and increment it with each restart.

   - Option 2: Dynamically change your domain using `kubectl patch`.
     - To get the current `introspectVersion` call:
       ```
       $ kubectl -n sample-domain1-ns get domain sample-domain1 '-o=jsonpath={.spec.introspectVersion}'
       ```
     - Choose a new restart version that's different from the current introspect version.
       - The field is a string; typically, you use a number in this field and increment it.

     - Use `kubectl patch` to set the new value. For example, assuming the new restart version is `2`:
       ```
       $ kubectl -n sample-domain1-ns patch domain sample-domain1 --type=json '-p=[{"op": "replace", "path": "/spec/introspectVersion", "value": "2" }]'
       ```
   - Option 3: Use the sample helper script.
     - Call `/tmp/mii-sample/utils/patch-introspect-version.sh -n sample-domain1-ns -d sample-domain1`.
     - This will perform the same `kubectl get` and `kubectl patch` commands as Option 2.


1. Wait for the introspector job to run to completion.

   - One way to do this is to call `kubectl get pods -n sample-domain1-ns --watch` and wait for the introspector pod to get into `Terminating` state.
       ```
       sample-domain1-introspector-vgxxl   0/1     Terminating         0          78s
       ```

1. Call the sample web application to determine if the configuration of the Minimum and Maximum Threads Constraints have been updated to the new values.

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

   Found min threads constraint runtime named 'SampleMinThreads' with configured count: 2
   
   Found max threads constraint runtime named 'SampleMaxThreads' with configured count: 20

    *****************************************************************
    </pre></body></html>

    ```

If you see an error, then consult [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) in the Model in Image user guide.

This completes the sample scenarios.

To remove the resources you have created in the samples, see [Cleanup]({{< relref "/samples/simple/domains/model-in-image/cleanup.md" >}}).
