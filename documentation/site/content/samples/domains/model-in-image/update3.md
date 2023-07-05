---
title: "Update 3"
date: 2019-02-23T17:32:31-05:00
weight: 5
---

The Update 3 use case demonstrates deploying an updated WebLogic application to the running [Update 1]({{< relref "/samples/domains/model-in-image/update1.md" >}}) use case domain using an updated image.

In the use case, you will:

 - Create an image `model-in-image:WLS-v2` that is similar to the currently active `model-in-image:WLS-v1` image, but with the following updates:
   - An updated web application `v2` at the `myapp-v2` directory path within the WDT application archive instead of `myapp-v1`.
   - An updated model YAML file within the image that points to the new web application path.
 - Apply an updated Domain YAML file that references the new image while still referencing the original [Update 1]({{< relref "/samples/domains/model-in-image/update1.md" >}}) use case secrets and model ConfigMap.

After the updated Domain YAML file is applied, the operator will:

 - Rerun the introspector job and generate a new domain home based on the new model.
 - Restart the domain's Administration Server pod so that it loads the new image and new domain home.
 - Roll the domain's cluster servers one at a time so that they each load the new image, new domain home, and revised application.

Finally, you will call the application to verify that its revision is active.

Note that the old version of the application `v1` remains in the new image's archive but is unused. We leave it there to demonstrate that the old version can remain in case you want to revert to it. After the new image is applied, you can revert by modifying your model's `configuration.model.configMap` to override the related application path in your image model.

Here are the steps for this use case:

1. Make sure you have deployed the domain from the [Update 1]({{< relref "/samples/domains/model-in-image/update1.md" >}}) use case.

2. Create an updated auxiliary image.

   Recall that a goal of the [Initial]({{< relref "/samples/domains/model-in-image/initial.md" >}}) use case was to demonstrate using the WebLogic Image Tool to create an auxiliary image named `wdt-domain-image:WLS-v1` from files that were staged in `/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v1/`. The staged files included a web application in a WDT ZIP archive, and WDT model configuration for a WebLogic Server Administration Server called `admin-server` and a WebLogic cluster called `cluster-1`. The final image was called `wdt-domain-image:WLS-v1` and, in addition to having a copy of the staged files in its `/auxiliary/models` directory, also contained a directory `/auxiliary/weblogic-deploy` where the WebLogic Deploy Tooling software is installed.

   In this use case, you will follow similar steps to the [Initial]({{< relref "/samples/domains/model-in-image/initial.md" >}}) use case to create a new image with an updated application and model, plus deploy the updated model and application to the running [Update 1]({{< relref "/samples/domains/model-in-image/update1.md" >}}) use case domain.

   - Understanding your updated WDT archive.

     The updated archive for this use case is in directory `/tmp/sample/wdt-artifacts/archives/archive-v2`. You will use it to create an archive ZIP file for the image. This archive is similar to the `/tmp/sample/wdt-artifacts/archives/archive-v1` from the [Initial]({{< relref "/samples/domains/model-in-image/initial.md" >}}) use case with the following differences:
     - It includes an updated version of the application in `./wlsdeploy/applications/myapp-v2` (while keeping the original application in directory `./wlsdeploy/applications/myapp-v1`).
     - The application in `./wlsdeploy/applications/myapp-v2/myapp_war/index.jsp` contains a single difference from the original application: it changes the line `out.println("Hello World! This is version 'v1' of the sample JSP web-app.");` to `out.println("Hello World! This is version 'v2' of the sample JSP web-app.");`.

     For additional information about archives, see [Understand your first archive]({{< relref "/samples/domains/model-in-image/auxiliary-image-creation#understand-your-first-archive" >}}).

   - Stage a ZIP file of the WDT archive.

     When you create your updated image, you will use the files in the staging directory `/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v2`. In preparation, you need it to contain a ZIP file of the new WDT application archive.

     Run the following commands to create your application archive ZIP file and put it in the expected directory:

     ```
     # Delete existing archive.zip in case we have an old leftover version
     ```
     ```shell
     $ rm -f /tmp/sample/wdt-artifacts/wdt-model-files/WLS-v2/archive.zip
     ```
     ```
     # Move to the directory which contains the source files for our new archive
     ```
     ```shell
     $ cd /tmp/sample/wdt-artifacts/archives/archive-v2
     ```
     Using the [WDT archive helper tool](https://oracle.github.io/weblogic-deploy-tooling/userguide/tools/archive_helper/), create the archive in the location that we will use later when we run the WebLogic Image Tool.
     `
     ```shell
     $ /tmp/sample/wdt-artifacts/weblogic-deploy/bin/archiveHelper.sh add application -archive_file=/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v2/archive.zip -source=wlsdeploy/applications/myapp-v2
     ```

   - Understanding your staged model files.

     The WDT model YAML file and properties for this use case have already been staged for you to directory `/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v2`.

     The `model.10.yaml` file in this directory has an updated path `wlsdeploy/applications/myapp-v2` that references the updated web application in your archive, but is otherwise identical to the model staged for the original image. The final related YAML file stanza looks like this:

     ```yaml
     appDeployments:
         Application:
             myapp:
                 SourcePath: 'wlsdeploy/applications/myapp-v2'
                 ModuleType: ear
                 Target: 'cluster-1'
     ```

     If you would like to review the entire original model before this change, see [Staging model files]({{< relref "/samples/domains/model-in-image/initial#staging-model-files" >}})  in the Initial use case.

   - Create a new auxiliary image from your staged model files using WIT.

     At this point, you have staged all of the files needed for image `wdt-domain-image:WLS-v2`; they include:

     - `/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v2/model.10.yaml`
     - `/tmp/sample/wdt-artifacts/wdt-model-files/WLS-v2/model.10.properties`
     - `/tmp/sample/model-images/wdt-artifacts/wdt-model-files/WLS-v2/archive.zip`

     Now, you use the Image Tool to create an auxiliary image named `wdt-domain-image:WLS-v2`. You've already set up this tool during the prerequisite steps.

     Run the following commands to create the auxiliary image and verify that it worked:

     ```shell
     $ cd /tmp/sample/wdt-artifacts/wdt-model-files/WLS-v2
     ```
     ```shell
     $ /tmp/sample/wdt-artifacts/imagetool/bin/imagetool.sh createAuxImage \
       --tag wdt-domain-image:WLS-v2 \
       --wdtModel ./model.10.yaml \
       --wdtVariables ./model.10.properties \
       --wdtArchive ./archive.zip
     ```

     If you don't see the `imagetool` directory, then you missed a step in the [prerequisites]({{< relref "/samples/domains/model-in-image/prerequisites.md" >}}).

     This command runs the WebLogic Image Tool in its Model in Image mode, and does the following:
     - Builds the final auxiliary image as a layer on a small `busybox` base image.
     - Copies the WDT ZIP file that's referenced in the WIT cache into the image.
       - Note that you cached WDT in WIT using the keyword `latest` when you set up the cache during the sample prerequisites steps.
       - This lets WIT implicitly assume it's the desired WDT version and removes the need to pass a `-wdtVersion` flag.
     - Copies the specified WDT model, properties, and application archives to image location `/auxiliary/models`.

     When the command succeeds, it will end with output like the following:

     ```
     [INFO   ] Build successful. Build time=36s. Image tag=wdt-domain-image:WLS-v2
     ```

     Also, if you run the `docker images` command, then you will see an image named `wdt-domain-image:WLS-v2`.

     **NOTE**: Before you deploy the domain custom resource, ensure all nodes in your Kubernetes cluster [can access `auxiliary-image` and other images]({{< relref "/samples/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

#### Deploy resources - Introduction

1. Set up and apply a Domain YAML file that is similar to your Update 1 use case Domain YAML file but with a different image:

   - Option 1: Update a copy of your Domain YAML file from the Update 1 use case.

     - In the [Update 1]({{< relref "/samples/domains/model-in-image/update1.md" >}}) use case, we suggested creating a file named `/tmp/sample/mii-update1.yaml` or using the `/tmp/sample/domain-resources/WLS/mii-update1-d1-WLS-v1-ds.yaml` file that is supplied with the sample.

       - We suggest copying this Domain YAML file and naming the copy `/tmp/sample/mii-update3.yaml` before making any changes.

       - Working on a copy is not strictly necessary, but it helps keep track of your work for the different use cases in this sample and provides you a backup of your previous work.

     - Change the `/tmp/sample/mii-update3.yaml` Domain YAML file's `image` field to reference `wdt-domain-image:WLS-v2` instead of `wdt-domain-image:WLS-v1`.

        The final result will look something like this:

        ```yaml
        ...
        spec:
          ...
          image: "wdt-domain-image:WLS-v2"
        ```

      - Apply your changed Domain YAML file:

          **NOTE**: Before you deploy the domain custom resource, ensure all nodes in your Kubernetes cluster [can access `auxiliary-image` and other images]({{< relref "/samples/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

          ```shell
          $ kubectl apply -f /tmp/sample/mii-update3.yaml
          ```

    - Option 2: Use the updated Domain YAML file that is supplied with the sample:

        **NOTE**: Before you deploy the domain custom resource, ensure all nodes in your Kubernetes cluster [can access `auxiliary-image` and other images]({{< relref "/samples/domains/model-in-image/_index.md#ensuring-your-kubernetes-cluster-can-access-images" >}}).

        ```shell
        $ kubectl apply -f /tmp/sample/domain-resources/WLS/mii-update3-d1-WLS-v2-ds.yaml
        ```


1. Wait for the roll to complete.

   Now that you've applied a Domain YAML file with an updated image, the operator will automatically rerun the domain's introspector job to generate a new domain home, and then will restart ('roll') each of the domain's pods so that they use the new domain home and the new image. You'll need to wait for this roll to complete before you can verify that the new image and its associated new application have been deployed.

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

1. After your domain roll is complete, you can call the sample web application to determine if the updated application was deployed.

   When the application is invoked, it will contain an output string like `Hello World! This is version 'v2' of the sample JSP web-app.`.

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

    Hello World! This is version 'v2' of the sample JSP web-app.

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

If you plan to run the [Update 4]({{< relref "/samples/domains/model-in-image/update4.md" >}}) use case, then leave your domain running.

To remove the resources you have created in the samples, see [Cleanup]({{< relref "/samples/domains/model-in-image/cleanup.md" >}}).
