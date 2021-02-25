---
title: "Update 4"
date: 2019-02-23T17:32:31-05:00
weight: 6
---

This use case demonstrates dynamically changing the Work Manager threads constraint and data source configuration in your running domain without restarting (rolling) running WebLogic Servers. This use case requires that the Update 1 use case has been run and expects that its `sample-domain1` domain is deployed and running.

In the use case, you will:

 - Update the ConfigMap containing the WDT model created in the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case with changes to the Work Manager threads constraint configuration.
 - Update the data source secret created in the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case to provide the correct password and an increased maximum pool capacity.
 - Update the Domain YAML file to enable the Model in Image online update feature.
 - Update the Domain YAML file to trigger a domain introspection, which applies the new configuration values without restarting servers.
 - Optionally, start a database (to demonstrate that the updated data source attributes have taken effect).

Here are the steps:

1. Make sure that you have deployed the domain from the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case, or have deployed an updated version of this same domain from the [Update 3]({{< relref "/samples/simple/domains/model-in-image/update3.md" >}}) use case.

   There should be three WebLogic Server pods with names that start with `sample-domain1` running in the `sample-domain1-ns` namespace, a domain named `sample-domain1`, a ConfigMap named `sample-domain1-wdt-config-map`, and a Secret named `sample-domain1-datasource-secret`.

1. Add the Work Manager threads constraint configuration WDT model updates to the existing data source model updates in the Model in Image model ConfigMap.

   In this step, we will update the model ConfigMap from the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case with the desired changes to the minimum and maximum threads constraints.

   Here's an example model configuration that updates the configured count values for the `SampleMinThreads` minimum threads constraint and `SampleMaxThreads` maximum threads constraint:
   ```
   resources:
     SelfTuning:
       MinThreadsConstraint:
         SampleMinThreads:
           Count: 2
       MaxThreadsConstraint:
         SampleMaxThreads:
           Count: 20
   ```
   Optionally, place the preceding model snippet in a file named `/tmp/mii-sample/myworkmanager.yaml` and then use it when deploying the updated model ConfigMap, or simply use the same model snippet that's provided in `/tmp/mii-sample/model-configmaps/workmanager/model.20.workmanager.yaml`.

   Run the following commands:

   ```
   $ kubectl -n sample-domain1-ns delete configmap sample-domain1-wdt-config-map
   $ kubectl -n sample-domain1-ns create configmap sample-domain1-wdt-config-map \
     --from-file=/tmp/mii-sample/model-configmaps/workmanager \
     --from-file=/tmp/mii-sample/model-configmaps/datasource
   $ kubectl -n sample-domain1-ns label configmap sample-domain1-wdt-config-map \
     weblogic.domainUID=sample-domain1
   ```

   Notes:
     - If you've created your own model YAML file(s), then substitute the file names in the `--from-file=` parameters (we suggested `/tmp/mii-sample/myworkmanager.yaml` and `/tmp/mii-sample/mydatasource.xml` earlier).
     - The `-from-file=` parameter can reference a single file, in which case it puts the designated file in the ConfigMap, or it can reference a directory, in which case it populates the ConfigMap with all of the files in the designated directory. It can be specified multiple times on the same command line to load the contents from multiple locations into the ConfigMap.
     - You name and label the ConfigMap using its associated domain UID for two reasons:
       - To make it obvious which ConfigMap belong to which domains.
       - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all resources associated with a domain.

1. Update the data source secret that you created in the Update 1 use case with the correct password as well as with an increased maximum pool capacity:

   ```
   $ kubectl -n sample-domain1-ns delete secret sample-domain1-datasource-secret
   $ kubectl -n sample-domain1-ns create secret generic \
      sample-domain1-datasource-secret \
      --from-literal='user=sys as sysdba' \
      --from-literal='password=Oradoc_db1' \
      --from-literal='max-capacity=10' \
      --from-literal='url=jdbc:oracle:thin:@oracle-db.default.svc.cluster.local:1521/devpdb.k8s'
   $ kubectl -n sample-domain1-ns label  secret \
      sample-domain1-datasource-secret \
      weblogic.domainUID=sample-domain1
   ```

1. Optionally, start the database.

    - If the database is running, then the sample application that we will run at the end of this use case, will verify that your updates to the data source secret took effect.

    - If you are taking the `JRF` path through the sample, then the database will already be running.

    - If you are taking the `WLS` path through the sample, then you can deploy the database by:
      - Following the first step in [Set up and initialize an infrastructure database]({{< relref "/samples/simple/domains/model-in-image/prerequisites#set-up-and-initialize-an-infrastructure-database" >}}). This step is titled, "Ensure that you have access to the database image, and then create a deployment using it."
      - You can skip the remaining steps (they are only needed for `JRF`).

1. Update your Domain YAML file to enable `onlineUpdate`.

   If `onlineUpdate` is enabled for your domain and the only model changes are to WebLogic Domain dynamic attributes, then the operator will attempt to update the running domains online without restarting the servers when you update the domain's `introspectVersion`.

   - Option 1: Edit your domain custom resource.
     - Call `kubectl -n sample-domain1-ns edit domain sample-domain1`.
     - Add or edit the value of the `spec.configuration.model.onlineUpdate` stanza so it contains `enabled: true` and save.
     - The updated domain should look something like this:
       ```
       ...
       spec:
         ...
         configuration:
           ...
           model:
             ...
             onlineUpdate:
               enabled: true
       ```

    - Option 2: Dynamically change your domain using `kubectl patch`. For example:
      ```
      $ kubectl -n sample-domain1-ns patch domain sample-domain1 --type=json '-p=[{"op": "replace", "path": "/spec/configuration/model/onlineUpdate", "value": {"enabled" : 'true'} }]'
      ```

    - Option 3: Use the sample helper script.
      - Call `/tmp/mii-sample/utils/patch-enable-online-update.sh -n sample-domain1-ns -d sample-domain1`.
      - This will perform the same `kubectl patch` commands as Option 2.

1. Prompt the operator to introspect the updated WebLogic domain configuration.

   Now that the updated Work Manager configuration is deployed in an updated model ConfigMap and the updated
   data source configuration is reflected in the updated data source Secret, we need to have the operator
   rerun its introspector job in order to regenerate its configuration.

   Change the `spec.introspectVersion` of the domain to trigger domain introspection.
   To do this:

   - Option 1: Edit your domain custom resource.
     - Call `kubectl -n sample-domain1-ns edit domain sample-domain1`.
     - Change the value of the `spec.introspectVersion` field and save.
     - The field is a string; typically, you use a number in this field and increment it.

   - Option 2: Dynamically change your domain using `kubectl patch`.
     - Get the current `introspectVersion`:
       ```
       $ kubectl -n sample-domain1-ns get domain sample-domain1 '-o=jsonpath={.spec.introspectVersion}'
       ```
     - Choose a new introspect version that's different from the current introspect version.
       - The field is a string; typically, you use a number in this field and increment it.

     - Use `kubectl patch` to set the new value. For example, assuming the new introspect version is `2`:
       ```
       $ kubectl -n sample-domain1-ns patch domain sample-domain1 --type=json '-p=[{"op": "replace", "path": "/spec/introspectVersion", "value": "2" }]'
       ```
   - Option 3: Use the sample helper script.
     - Call `/tmp/mii-sample/utils/patch-introspect-version.sh -n sample-domain1-ns -d sample-domain1`.
     - This will perform the same `kubectl patch` command as Option 2.

   Because we have set the `enabled` value in `spec.configuration.model.onlineUpdate` to `true`, and all of
   the model changes we have specified are for WebLogic dynamic configuration attributes, we expect that the
   domain introspector job will apply the changes to the WebLogic Servers without restarting (rolling) their pods.

1. Wait for the introspector job to run to completion. You can:

   - Call `kubectl get pods -n sample-domain1-ns --watch` and wait for the introspector pod to get into `Terminating` state and exit.
       ```
       sample-domain1-introspector-vgxxl   0/1     Terminating         0          78s
       ```

   - Alternatively, run `/tmp/mii-sample/utils/wl-pod-wait.sh -p 3`.

     This is a utility script that provides useful information about a domain's pods and waits for them to reach a `ready` state, reach their target `restartVersion`, reach their target `introspectVersion`, and reach their target `image` before exiting.

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

       -p 0            : Wait until there are no running WebLogic Server pods
                         for a domain. The default.

       -p <pod_count>  : Wait until all of the following are true
                         for exactly 'pod_count' WebLogic Server pods
                         in the domain:
                         - ready
                         - same 'weblogic.domainRestartVersion' label value as
                           the domain resource's 'spec.restartVersion'
                         - same 'weblogic.introspectVersion' label value as
                           the domain resource's 'spec.introspectVersion'
                         - same image as the the domain resource's image

       -t <timeout>    : Timeout in seconds. Defaults to '1000'.

       -q              : Quiet mode. Show only a count of wl pods that
                         have reached the desired criteria.

       -?              : This help.

   ```
     {{% /expand %}}

     {{%expand "Click here to view sample output from `wl-pod-wait.sh` that shows the introspector running and that shows each domain pod reach its new `introspectVersion`." %}}
   ```
   $ ./wl-pod-wait.sh -n sample-domain1-ns -d sample-domain1 -p 3
   @@ [2020-11-21T05:55:26][seconds=0] Info: Waiting up to 1000 seconds for exactly '3' WebLogic Server pods to reach the following criteria:
   @@ [2020-11-21T05:55:26][seconds=0] Info:   ready='true'
   @@ [2020-11-21T05:55:26][seconds=0] Info:   image='model-in-image:WLS-v2'
   @@ [2020-11-21T05:55:26][seconds=0] Info:   domainRestartVersion='1'
   @@ [2020-11-21T05:55:26][seconds=0] Info:   introspectVersion='2'
   @@ [2020-11-21T05:55:26][seconds=0] Info:   namespace='sample-domain1-ns'
   @@ [2020-11-21T05:55:26][seconds=0] Info:   domainUID='sample-domain1'

   @@ [2020-11-21T05:55:26][seconds=0] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-11-21T05:55:26][seconds=0] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                                 RVERSION  IVERSION  IMAGE                    READY   PHASE      
   -----------------------------------  --------  --------  -----------------------  ------  ---------  
   'sample-domain1-admin-server'        '1'       '1'       'model-in-image:WLS-v2'  'true'  'Running'  
   'sample-domain1-introspector-4gpdj'  ''        ''        ''                       ''      'Pending'  
   'sample-domain1-managed-server1'     '1'       '1'       'model-in-image:WLS-v2'  'true'  'Running'  
   'sample-domain1-managed-server2'     '1'       '1'       'model-in-image:WLS-v2'  'true'  'Running'  

   @@ [2020-11-21T05:55:28][seconds=2] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-11-21T05:55:28][seconds=2] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                                 RVERSION  IVERSION  IMAGE                    READY   PHASE      
   -----------------------------------  --------  --------  -----------------------  ------  ---------  
   'sample-domain1-admin-server'        '1'       '1'       'model-in-image:WLS-v2'  'true'  'Running'  
   'sample-domain1-introspector-4gpdj'  ''        ''        ''                       ''      'Running'  
   'sample-domain1-managed-server1'     '1'       '1'       'model-in-image:WLS-v2'  'true'  'Running'  
   'sample-domain1-managed-server2'     '1'       '1'       'model-in-image:WLS-v2'  'true'  'Running'  

   @@ [2020-11-21T05:56:53][seconds=87] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-11-21T05:56:53][seconds=87] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                                 RVERSION  IVERSION  IMAGE                    READY   PHASE        
   -----------------------------------  --------  --------  -----------------------  ------  -----------  
   'sample-domain1-admin-server'        '1'       '1'       'model-in-image:WLS-v2'  'true'  'Running'    
   'sample-domain1-introspector-4gpdj'  ''        ''        ''                       ''      'Succeeded'  
   'sample-domain1-managed-server1'     '1'       '1'       'model-in-image:WLS-v2'  'true'  'Running'    
   'sample-domain1-managed-server2'     '1'       '1'       'model-in-image:WLS-v2'  'true'  'Running'    

   @@ [2020-11-21T05:56:54][seconds=88] Info: '3' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-11-21T05:56:54][seconds=88] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              RVERSION  IVERSION  IMAGE                    READY   PHASE      
   --------------------------------  --------  --------  -----------------------  ------  ---------  
   'sample-domain1-admin-server'     '1'       '2'       'model-in-image:WLS-v2'  'true'  'Running'  
   'sample-domain1-managed-server1'  '1'       '2'       'model-in-image:WLS-v2'  'true'  'Running'  
   'sample-domain1-managed-server2'  '1'       '2'       'model-in-image:WLS-v2'  'true'  'Running'  

   @@ [2020-11-21T05:56:54][seconds=88] Info: Success!
   ```
     {{% /expand %}}

   - If the introspector job fails, then consult [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) in the Model in Image user guide.

1. Call the sample web application to:

   - Determine if the configuration of the minimum and maximum threads constraints have been updated to the new values.
   - Determine if the data source can now contact the database (assuming you deployed the database).

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

    Hello World! This is version 'v2' of the mii-sample JSP web-app.

    Welcome to WebLogic server 'managed-server2'!

      domain UID  = 'sample-domain1'
      domain name = 'domain1'

    Found 1 local cluster runtime:
      Cluster 'cluster-1'

    Found min threads constraint runtime named 'SampleMinThreads' with configured count: 2

    Found max threads constraint runtime named 'SampleMaxThreads' with configured count: 20

    Found 1 local data source:
      Datasource 'mynewdatasource':  State='Running', testPool='Passed'

    *****************************************************************
    </pre></body></html>

    ```

The `testPool='Passed'` for `mynewdatasource` verifies that your update to the data source Secret to correct the password succeeded.

If you see a `testPool='Failed'` error, then it is likely you did not deploy the database or your database is not deployed correctly.

If you see any other error, then consult [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) in the Model in Image user guide.

This completes the sample scenarios.

To remove the resources you have created in the samples, see [Cleanup]({{< relref "/samples/simple/domains/model-in-image/cleanup.md" >}}).
