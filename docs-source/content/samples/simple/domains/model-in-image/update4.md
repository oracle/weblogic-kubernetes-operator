---
title: "Update 4"
date: 2019-02-23T17:32:31-05:00
weight: 6
---

This use case demonstrates dynamically configuring Work Manager Threads Constraint and data source WebLogic configuration in your running domain without restarting (rolling) running WebLogic Servers. This use case requires that the Update 1 use case has been run and expects that its `sample-domain1` domain is deployed and running.

In the use case, you will:

 - Update the ConfigMap containing the WDT model created in the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case with changes to Work Manager Threads Constraint configuration.
 - Update the data source secret created in the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case to provide the correct URL and password, and to 
 - Update the Domain YAML file to enable online update feature.
 - Update the Domain YAML file to trigger a domain introspection, which applies the new configuration values without restarting servers.
 - Optionally start a database (in order to demonstrate that the updated datasource attributes have taken effect).

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
   ```
   Place the above model snippet in a file named `/tmp/mii-sample/myworkmanager.yaml` and then use it in the later step where you deploy the model ConfigMap, or use the same data source that's provided in `/tmp/mii-sample/model-configmaps/workmanager/model.20.workmanager.yaml`.

1. Replace the ConfigMap created in Update 1 use case with a ConfigMap with the WDT model containing both the data source and configuration updates.

   Run the following commands:


   ```
   $ kubectl -n sample-domain1-ns delete configmap sample-domain1-wdt-config-map
   $ kubectl -n sample-domain1-ns create configmap sample-domain1-wdt-config-map \
     --from-file=/tmp/mii-sample/model-configmaps/workmanager \
     --from-file=/tmp/mii-sample/model-configmaps/datasource
   $ kubectl -n sample-domain1-ns label configmap sample-domain1-wdt-config-map \
     weblogic.domainUID=sample-domain1
   ```

     - If you've created your own model YAML file, then substitute the file names in the `--from-file=` parameters (we suggested `/tmp/mii-sample/myworkmanager.yaml` and `/tmp/mii-sample/mydatasource.xml` earlier).
     - Note that the `-from-file=` parameter can reference a single file, in which case it puts the designated file in the ConfigMap, or it can reference a directory, in which case it populates the ConfigMap with all of the files in the designated directory.
     - The `-from-file=` paramater can be specified multiple times to put contents from all specified files or directories into the ConfigMap.

   You name and label the ConfigMap using its associated domain UID for two reasons:
     - To make it obvious which ConfigMap belong to which domains.
     - To make it easier to clean up a domain. Typical cleanup scripts use the `weblogic.domainUID` label as a convenience for finding all resources associated with a domain.

1. Update the data source secret that you created in the Update 1 use case with a correct URL, a correct password, as well as with an increased maximum pool capacity:

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

1. Optionally start the database.

    - If the database is running, then the sample application that we will run at the end of this step will verify that your updates to the data source secret took effect.

    - If you are taking the `JRF` path through the sample, then the database will already be running. 

    - If you are taking the `WLS` path through the sample, then you can deploy the database by:
      - Following the the first step in [Set up and initialize an infrastructure database]({{< relref "/samples/simple/domains/model-in-image/prerequisites.md#set-up-and-initialize-an-infrastructure-database" >}}). This step is titled `Ensure that you have access to the database image, and then create a deployment`.
      - You can skip the remaining steps (they are only needed for `JRF`).

1. Update your Domain YAML file to enable `onlineUpdate`.

    - Operator will attempt to use the online update to the running domain without 
      restarting the servers if only changes made are updating dynamic attributes of the WebLogic Domain.

    - Option 1: Edit your domain custom resource.
      - Call `kubectl -n sample-domain1-ns edit domain sample-domain1`.
      - Add or edit the value of the `spec.configuration.model.onlineUpdate` field so it contains  `enabled: true` and save.

   - Option 2: Dynamically change your domain using `kubectl patch`.
     - Use `kubectl patch` to set the value. For example:
       ```
       $ kubectl -n sample-domain1-ns patch domain sample-domain1 --type=json '-p=[{"op": "replace", "path": "/spec/configuration/model/onlineUpdate", "value": {"enabled" : "true"} }]' 
       ```
   - Option 3: Use the sample helper script.
     - Call `/tmp/mii-sample/utils/patch-enable-online-update.sh -n sample-domain1-ns -d sample-domain1`.
     - This will perform the same `kubectl patch` commands as Option 2.

1. Inform the operator to introspect the WebLogic domain configuration.

   Now that the updated configuration is deployed in a ConfigMap, we need to tell the operator to 
   rerun its introspector job in order to regenerate its configuration. 
   When the `enabled` value in `spec.configuration.model.onlineUpdate` `true`, and all the changes are 
   dynamic changes, the domain introspector can apply the changes to the servers without doing a server restart.
   Change the `spec.introspectVersion` of the domain to trigger domain instrospection to be performed. 
   To do this:

   - Option 1: Edit your domain custom resource.
     - Call `kubectl -n sample-domain1-ns edit domain sample-domain1`.
     - Edit the value of the `spec.introspectVersion` field and save.
       - The field is a string; typically, you use a number in this field and increment it.

   - Option 2: Dynamically change your domain using `kubectl patch`.
     - To get the current `introspectVersion` call:
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


1. Wait for the introspector job to run to completion.

   - One way to do this is to call `kubectl get pods -n sample-domain1-ns --watch` and wait for the introspector pod to get into `Terminating` state.
       ```
       sample-domain1-introspector-vgxxl   0/1     Terminating         0          78s
       ```

   - Alternatively, you can run `/tmp/mii-sample/utils/wl-pod-wait.sh -p 3`. This is a utility script that provides useful information about a domain's pods and waits for them to reach a `ready` state, reach their target `restartVersion`, reach their target `introspectVersion`, and reach their target `image` before exiting.

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
   TBD
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

If you see a `testPool='Failed'` error, then it is likely you did not deploy the database or your database is not deployed correctly.

If you see any other error, then consult [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) in the Model in Image user guide.

This completes the sample scenarios.

To remove the resources you have created in the samples, see [Cleanup]({{< relref "/samples/simple/domains/model-in-image/cleanup.md" >}}).
