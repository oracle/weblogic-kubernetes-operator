---
title: "Update 3"
date: 2019-02-23T17:32:31-05:00
weight: 5
---

The Update 3 use case demonstrates deploying an updated WebLogic application to the running [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case domain using an updated image.

In the use case, you will:

 - Create an image `model-in-image:WLS-v2` that is similar to the currently active `model-in-image:WLS-v1` image, but with the following updates:
   - An updated web application `v2` at the `myapp-v2` directory path within the WDT application archive instead of `myapp-v1`.
   - An updated model YAML file within the image that points to the new web application path.
 - Apply an updated Domain YAML file that references the new image while still referencing the original [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case secrets and model ConfigMap.

After the updated Domain YAML file is applied, the operator will:

 - Rerun the introspector job and generate a new domain home based on the new model.
 - Restart the domain's Administration Server pod so that it loads the new image and new domain home.
 - Roll the domain's cluster servers one at a time so that they each load the new image, new domain home, and revised application.

Finally, you will call the application to verify that its revision is active.

Note that the old version of the application `v1` remains in the new image's archive but is unused. We leave it there to demonstrate that the old version can remain in case you want to revert to it. After the new image is applied, you can revert by modifying your model's `configuration.model.configMap` to override the related application path in your image model.

Here are the steps for this use case:

1. Make sure you have deployed the domain from the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case.

2. Create an updated image.

   Recall that a goal of the [Initial]({{< relref "/samples/simple/domains/model-in-image/initial.md" >}}) use case was to demonstrate using the WebLogic Image Tool to create an image named `model-in-image:WLS-v1` from files that were staged in `/tmp/mii-sample/model-images/model-in-image:WLS-v1/`. The staged files included a web application in a WDT ZIP archive, and WDT model configuration for a WebLogic Administration Server called `admin-server` and a WebLogic cluster called `cluster-1`. The final image was called `model-in-image:WLS-v1` and, in addition to having a copy of the staged files in its `/u01/wdt/models` directory, also contained a WebLogic installation and a WebLogic Deploy Tooling installation.

   In this use case, you will follow similar steps to the [Initial]({{< relref "/samples/simple/domains/model-in-image/initial.md" >}}) use case in order to create a new image with an updated application and model, plus deploy the updated model and application to the running [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case domain.

   - Understanding your updated WDT archive.

     The updated archive for this use case is in directory `/tmp/mii-sample/archives/archive-v2`. You will use it to create an archive ZIP file for the image. This archive is similar to the `/tmp/mii-sample/archives/archive-v1` from the [Initial]({{< relref "/samples/simple/domains/model-in-image/initial.md" >}}) use case with the following differences:
     - It includes an updated version of the application in `./wlsdeploy/applications/myapp-v2` (while keeping the original application in directory `./wlsdeploy/applications/myapp-v1`).
     - The application in `./wlsdeploy/applications/myapp-v2/myapp_war/index.jsp` contains a single difference from the original application: it changes the line `out.println("Hello World! This is version 'v1' of the mii-sample JSP web-app.");` to `out.println("Hello World! This is version 'v2' of the mii-sample JSP web-app.");`.

     For additional information about archives, see [Understanding your first archive]({{< relref "/samples/simple/domains/model-in-image/initial#understanding-your-first-archive" >}}) in the Initial use case.

   - Stage a ZIP file of the WDT archive.

     > **Note**: If you are using JRF in this sample, substitute `JRF` for each occurrence of `WLS` in the paths below.

     When you create your updated image, you will use the files in staging directory `/tmp/mii-sample/model-in-image__WLS-v2`. In preparation, you need it to contain a ZIP file of the new WDT application archive.

     Run the following commands to create your application archive ZIP file and put it in the expected directory:

     ```
     # Delete existing archive.zip in case we have an old leftover version
     $ rm -f /tmp/mii-sample/model-images/model-in-image__WLS-v2/archive.zip

     # Move to the directory which contains the source files for our new archive
     $ cd /tmp/mii-sample/archives/archive-v2

     # Zip the archive to the location will later use when we run the WebLogic Image Tool
     $ zip -r /tmp/mii-sample/model-images/model-in-image__WLS-v2/archive.zip wlsdeploy
     ```

   - Understanding your staged model files.

     The WDT model YAML file and properties for this use case have already been staged for you to directory `/tmp/mii-sample/model-in-image__WLS-v2`.

     The `model.10.yaml` file in this directory has an updated path `wlsdeploy/applications/myapp-v2` that references the updated web application in your archive, but is otherwise identical to the model staged for the original image. The final related YAML file stanza looks like this:

     ```
     appDeployments:
         Application:
             myapp:
                 SourcePath: 'wlsdeploy/applications/myapp-v2'
                 ModuleType: ear
                 Target: 'cluster-1'
     ```

     If you would like to review the entire original model before this change, see [Staging model files]({{< relref "/samples/simple/domains/model-in-image/initial#staging-model-files" >}})  in the Initial use case.

   - Create a new image from your staged model files using WIT.

     > **Note**: If you are using JRF in this sample, substitute `JRF` for each occurrence of `WLS` in the `imagetool` command line below, plus substitute `container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4` for the `--fromImage` value.

     At this point, you have staged all of the files needed for image `model-in-image:WLS-v2`; they include:

     - `/tmp/mii-sample/model-images/weblogic-deploy.zip`
     - `/tmp/mii-sample/model-images/model-in-image__WLS-v2/model.10.yaml`
     - `/tmp/mii-sample/model-images/model-in-image__WLS-v2/model.10.properties`
     - `/tmp/mii-sample/model-images/model-in-image__WLS-v2/archive.zip`

     If you don't see the `weblogic-deploy.zip` file, then you missed a step in the [prerequisites]({{< relref "/samples/simple/domains/model-in-image/prerequisites.md" >}}).

     Now, you use the Image Tool to create an image named `model-in-image:WLS-v2` that's layered on a base WebLogic image. You've already set up this tool during the prerequisite steps.

     Run the following commands to create the model image and verify that it worked:

     {{% notice note %}}
     If you are taking the `JRF` path through the sample, then remove `--chown oracle:root` from the `imagetool.sh` command below.
     {{% /notice %}}

     ```
     $ cd /tmp/mii-sample/model-images
     $ ./imagetool/bin/imagetool.sh update \
       --tag model-in-image:WLS-v2 \
       --fromImage container-registry.oracle.com/middleware/weblogic:12.2.1.4 \
       --wdtModel      ./model-in-image__WLS-v2/model.10.yaml \
       --wdtVariables  ./model-in-image__WLS-v2/model.10.properties \
       --wdtArchive    ./model-in-image__WLS-v2/archive.zip \
       --wdtModelOnly \
       --wdtDomainType WLS \
       --chown oracle:root
     ```

     If you don't see the `imagetool` directory, then you missed a step in the [prerequisites]({{< relref "/samples/simple/domains/model-in-image/prerequisites.md" >}}).

     This command runs the WebLogic Image Tool in its Model in Image mode, and does the following:
     - Builds the final container image as a layer on the `container-registry.oracle.com/middleware/weblogic:12.2.1.4` base image.
     - Copies the WDT ZIP file that's referenced in the WIT cache into the image.
       - Note that you cached WDT in WIT using the keyword `latest` when you set up the cache during the sample prerequisites steps.
       - This lets WIT implicitly assume it's the desired WDT version and removes the need to pass a `-wdtVersion` flag.
     - Copies the specified WDT model, properties, and application archives to image location `/u01/wdt/models`.

     When the command succeeds, it will end with output like the following:

     ```
     [INFO   ] Build successful. Build time=36s. Image tag=model-in-image:WLS-v2
     ```

     Also, if you run the `docker images` command, then you will see an image named `model-in-image:WLS-v2`.

     > **Note**: If you have Kubernetes cluster worker nodes that are remote to your local machine, then you need to put the image in a location that these nodes can access. See [Ensuring your Kubernetes cluster can access images]({{< relref "/samples/simple/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

#### Deploy resources - Introduction

1. Set up and apply a Domain YAML file that is similar to your Update 1 use case Domain YAML file but with a different image:

   > **Note**: If you are using JRF in this sample, substitute `JRF` for each occurrence of `WLS` in the paths, files, and image names below.

   - Option 1: Update a copy of your Domain YAML file from the Update 1 use case.

     - In the [Update 1]({{< relref "/samples/simple/domains/model-in-image/update1.md" >}}) use case, we suggested creating a file named `/tmp/mii-sample/mii-update1.yaml` or using the `/tmp/mii-sample/domain-resources/WLS/mii-update1-d1-WLS-v1-ds.yaml` file that is supplied with the sample.

       - We suggest copying this Domain YAML file and naming the copy `/tmp/mii-sample/mii-update3.yaml` before making any changes.

       - Working on a copy is not strictly necessary, but it helps keep track of your work for the different use cases in this sample and provides you a backup of your previous work.

     - Change the `/tmp/mii-sample/mii-update3.yaml` Domain YAML file's `image` field to reference `model-in-image:WLS-v2` instead of `model-in-image:WLS-v1`.

        The final result will look something like this:

        ```
        ...
        spec:
          ...
          image: "model-in-image:WLS-v2"
        ```

      - Apply your changed Domain YAML file:

          > **Note**: Before you deploy the domain custom resource, determine if you have Kubernetes cluster worker nodes that are remote to your local machine. If so, you need to put the Domain YAML file's image in a location that these nodes can access and you may also need to modify your Domain YAML file to reference the new location. See [Ensuring your Kubernetes cluster can access images]({{< relref "/samples/simple/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

          ```
          $ kubectl apply -f /tmp/mii-sample/mii-update3.yaml
          ```

    - Option 2: Use the updated Domain YAML file that is supplied with the sample:

        > **Note**: Before you deploy the Domain YAML file, determine if you have Kubernetes cluster worker nodes that are remote to your local machine. If so, you need to put the Domain YAML file's image in a location that these nodes can access and you may also need to modify your Domain YAML file to reference the new location. See [Ensuring your Kubernetes cluster can access images]({{< relref "/samples/simple/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

        ```
        $ kubectl apply -f /tmp/miisample/domain-resources/WLS/mii-update3-d1-WLS-v2-ds.yaml
        ```


1. Wait for the roll to complete.

   Now that you've applied a Domain YAML file with an updated image, the operator will automatically rerun the domain's introspector job in order to generate a new domain home, and then will restart ('roll') each of the domain's pods so that they use the new domain home and the new image. You'll need to wait for this roll to complete before you can verify that the new image and its associated new application have been deployed.

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

     {{%expand "Click here to view sample output from `wl-pod-wait.sh` that shows a rolling domain." %}}
   ```
   $ ./wl-pod-wait.sh -n sample-domain1-ns -d sample-domain1 -p 3

   @@ [2020-05-14T17:28:47][seconds=1] Info: Waiting up to 1000 seconds for exactly '3' WebLogic Server pods to reach the following criteria:
   @@ [2020-05-14T17:28:47][seconds=1] Info:   ready='true'
   @@ [2020-05-14T17:28:47][seconds=1] Info:   image='model-in-image:WLS-v2'
   @@ [2020-05-14T17:28:47][seconds=1] Info:   domainRestartVersion='1'
   @@ [2020-05-14T17:28:47][seconds=1] Info:   namespace='sample-domain1-ns'
   @@ [2020-05-14T17:28:47][seconds=1] Info:   domainUID='sample-domain1'

   @@ [2020-05-14T17:28:47][seconds=1] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:28:47][seconds=1] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                                          VERSION  IMAGE                    READY   PHASE
   --------------------------------------------  -------  -----------------------  ------  ---------
   'sample-domain1-admin-server'                 '2'      'model-in-image:WLS-v1'  'true'  'Running'
   'sample-domain1-introspector-g5kzn'           ''       ''                       ''      'Running'
   'sample-domain1-managed-server1'              '2'      'model-in-image:WLS-v1'  'true'  'Running'
   'sample-domain1-managed-server2'              '2'      'model-in-image:WLS-v1'  'true'  'Running'

   @@ [2020-05-14T17:29:39][seconds=53] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:29:39][seconds=53] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY   PHASE
   --------------------------------  -------  -----------------------  ------  ---------
   'sample-domain1-admin-server'     '2'      'model-in-image:WLS-v1'  'true'  'Running'
   'sample-domain1-managed-server1'  '2'      'model-in-image:WLS-v1'  'true'  'Running'
   'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'true'  'Running'

   @@ [2020-05-14T17:29:50][seconds=64] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:29:50][seconds=64] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY   PHASE
   --------------------------------  -------  -----------------------  ------  ---------
   'sample-domain1-managed-server1'  '2'      'model-in-image:WLS-v1'  'true'  'Running'
   'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'true'  'Running'

   @@ [2020-05-14T17:29:58][seconds=72] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:29:58][seconds=72] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY    PHASE
   --------------------------------  -------  -----------------------  -------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'false'  'Pending'
   'sample-domain1-managed-server1'  '2'      'model-in-image:WLS-v1'  'true'   'Running'
   'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'true'   'Running'

   @@ [2020-05-14T17:29:59][seconds=73] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:29:59][seconds=73] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY    PHASE
   --------------------------------  -------  -----------------------  -------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'false'  'Running'
   'sample-domain1-managed-server1'  '2'      'model-in-image:WLS-v1'  'true'   'Running'
   'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'true'   'Running'

   @@ [2020-05-14T17:30:30][seconds=104] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:30:30][seconds=104] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY   PHASE
   --------------------------------  -------  -----------------------  ------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'true'  'Running'
   'sample-domain1-managed-server1'  '2'      'model-in-image:WLS-v1'  'true'  'Running'
   'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'true'  'Running'

   @@ [2020-05-14T17:31:13][seconds=147] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:31:13][seconds=147] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY    PHASE
   --------------------------------  -------  -----------------------  -------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'true'   'Running'
   'sample-domain1-managed-server1'  '2'      'model-in-image:WLS-v1'  'false'  'Running'
   'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'true'   'Running'

   @@ [2020-05-14T17:31:15][seconds=149] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:31:15][seconds=149] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY   PHASE
   --------------------------------  -------  -----------------------  ------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'true'  'Running'
   'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'true'  'Running'

   @@ [2020-05-14T17:31:41][seconds=175] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:31:41][seconds=175] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY    PHASE
   --------------------------------  -------  -----------------------  -------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'true'   'Running'
   'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v2'  'false'  'Pending'
   'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'true'   'Running'

   @@ [2020-05-14T17:31:42][seconds=176] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:31:42][seconds=176] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY    PHASE
   --------------------------------  -------  -----------------------  -------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'true'   'Running'
   'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v2'  'false'  'Running'
   'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'true'   'Running'

   @@ [2020-05-14T17:32:21][seconds=215] Info: '2' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:32:21][seconds=215] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY   PHASE
   --------------------------------  -------  -----------------------  ------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'true'  'Running'
   'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v2'  'true'  'Running'
   'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'true'  'Running'

   @@ [2020-05-14T17:32:31][seconds=225] Info: '2' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:32:31][seconds=225] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY    PHASE
   --------------------------------  -------  -----------------------  -------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'true'   'Running'
   'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v2'  'true'   'Running'
   'sample-domain1-managed-server2'  '2'      'model-in-image:WLS-v1'  'false'  'Running'

   @@ [2020-05-14T17:32:40][seconds=234] Info: '2' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:32:40][seconds=234] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY   PHASE
   --------------------------------  -------  -----------------------  ------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'true'  'Running'
   'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v2'  'true'  'Running'

   @@ [2020-05-14T17:32:51][seconds=245] Info: '2' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:32:51][seconds=245] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY    PHASE
   --------------------------------  -------  -----------------------  -------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'true'   'Running'
   'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v2'  'true'   'Running'
   'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v2'  'false'  'Pending'

   @@ [2020-05-14T17:32:52][seconds=246] Info: '2' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:32:52][seconds=246] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY    PHASE
   --------------------------------  -------  -----------------------  -------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'true'   'Running'
   'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v2'  'true'   'Running'
   'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v2'  'false'  'Running'

   @@ [2020-05-14T17:33:25][seconds=279] Info: '3' WebLogic Server pods currently match all criteria, expecting '3'.
   @@ [2020-05-14T17:33:25][seconds=279] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

   NAME                              VERSION  IMAGE                    READY   PHASE
   --------------------------------  -------  -----------------------  ------  ---------
   'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v2'  'true'  'Running'
   'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v2'  'true'  'Running'
   'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v2'  'true'  'Running'


   @@ [2020-05-14T17:33:25][seconds=279] Info: Success!

   ```
     {{% /expand %}}

1. After your domain roll is complete, you can call the sample web application to determine if the updated application was deployed.

   When the application is invoked, it will contain an output string like `Hello World! This is version 'v2' of the mii-sample JSP web-app.`.

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

A `TestPool Failure` is expected because we will demonstrate dynamically correcting the data source attributes in [Update 4]({{< relref "/samples/simple/domains/model-in-image/update4.md" >}}).

If you see an error other than the expected `TestPool Failure`, then consult [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) in the Model in Image user guide.

If you plan to run the [Update 4]({{< relref "/samples/simple/domains/model-in-image/update4.md" >}}) use case, then leave your domain running.

To remove the resources you have created in the samples, see [Cleanup]({{< relref "/samples/simple/domains/model-in-image/cleanup.md" >}}).
