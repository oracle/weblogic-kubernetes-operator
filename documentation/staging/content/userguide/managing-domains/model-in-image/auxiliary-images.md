+++
title = "Auxiliary images"
date = 2019-02-23T16:45:16-05:00
weight = 25
pre = "<b> </b>"
description = "Auxiliary images are an alternative approach for supplying a domain's model files or other types of files."
+++

### Contents

 - [Introduction](#introduction)
 - [References](#references)
 - [Configuration](#configuration)
   - [Source locations](#source-locations)
   - [Multiple auxiliary images](#multiple-auxiliary-images)
   - [Model and WDT install homes](#model-and-wdt-install-homes)
   - [Configuration examples](#configuration-examples)
     - [Configuration example 1: Basic configuration](#configuration-example-1-basic-configuration)
     - [Configuration example 2: Source locations](#configuration-example-2-source-locations)
     - [Configuration example 3: Multiple images](#configuration-example-3-multiple-images)
 - [Sample](#sample)
   - [Step 1: Prerequisites](#step-1-prerequisites)
   - [Step 2: Create the auxiliary image](#step-2-create-the-auxiliary-image)
   - [Step 3: Prepare and apply the domain resource](#step-3-prepare-and-apply-the-domain-resource)
   - [Step 4: Invoke the web application](#step-4-invoke-the-web-application)

### Introduction

Auxiliary images are an alternative approach for including Model in Image model files,
application archive files, and WebLogic Deploy Tooling installation files, in your pods.
This feature eliminates the need to provide these files in the image specified
in `domain.spec.image`.

Instead:

- The domain resource's `domain.spec.image` directly references a base image
  that needs to include only a WebLogic installation and a Java installation.
- The domain resource's auxiliary image related fields reference one or
  more smaller images that contain the desired Model in Image files.

The advantages of auxiliary images for Model In Image domains are:

- Use or patch a WebLogic installation image without needing to include a WDT installation,
  application archive, or model artifacts within the image.
- Share one WebLogic installation image with multiple different model
  configurations that are supplied in specific images.
- Distribute or update model files, application archives, and the
  WebLogic Deploy Tooling executable using specific images
  that do not contain a WebLogic installation.

Auxiliary images internally
use a Kubernetes `emptyDir` volume and Kubernetes `init` containers to share files
from additional images.

### References

- Run the `kubectl explain domain.spec.configuration.model.auxiliaryImages` command.

- See the `model.auxiliaryImages` section
  in the domain resource
  [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md)
  and [documentation]({{< relref "/userguide/managing-domains/domain-resource.md" >}}).

### Configuration

Beginning with operator version 4.0, you can configure one or more auxiliary images in a domain resource
`configuration.model.auxiliaryImages` array.
Each array entry must define an `image` which is the name of an auxiliary image.
Optionally, you can set the `imagePullPolicy`,
which defaults to `Always` if the `image` ends in `:latest` and `IfNotPresent`,
otherwise.
If image pull secrets are required for pulling auxiliary images, then the secrets must be referenced using `domain.spec.imagePullSecrets`.

Also, optionally, you can configure the [source locations](#source-locations) of the WebLogic Deploy Tooling model 
and installation files in the auxiliary image using the `sourceModelHome` and `sourceWDTInstallHome` fields described in the following 
[section](#source-locations).  

For details about each field, see the 
[schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md#auxiliary-image).

For a basic configuration example, see [Configuration example 1](#configuration-example-1-basic-configuration).

#### Source locations
Use the optional attributes `configuration.model.auxiliaryImages[].sourceModelHome` and
`configuration.model.auxiliaryImages[].sourceWdtInstallHome` to specify non-default locations of 
WebLogic Deploy Tooling model and installation files in your auxiliary image(s).
Allowed values for `sourceModelHome` and `sourceWdtInstallHome`:
- Unset - Default to `/auxiliary/models` and `/auxiliary/weblogic-deploy`, respectively.
- Set to a path - Must point to an existing location containing WDT model and installation files, respectively.
- `None` - Indicates that the image has no WDT models or installation files, respectively. 

If you set the `sourceModelHome` or `sourceWDTInstallHome` to `None` or,
the source attributes are left unset and there are no files at the default locations,
then the operator will ignore the source directories. Otherwise,
note that if you set a source directory attribute to a specific value
and there are no files in the specified directory in the auxiliary image,
then the domain deployment will fail.


The files in `sourceModelHome` and `sourceWDTInstallHome` directories will be made available in `/aux/models` 
and `/aux/weblogic-deploy` directories of the WebLogic Server container in all pods, respectively.

For example source locations, see [Configuration example 2](#configuration-example-2-source-locations).

#### Multiple auxiliary images
If specifying multiple auxiliary images with model files in their respective `configuration.model.auxiliaryImages[].sourceModelHome`
directories, then model files are merged. 
The operator will merge the model files from multiple auxiliary images in the same order in which images appear under `model.auxiliaryImages`. 
Files from later images in the merge overwrite same-named files from earlier images.

When specifying multiple auxiliary images, ensure that only one of the images supplies a WDT installation location using 
`configuration.model.auxiliaryImages[].sourceWDTInstallHome`. 
{{% notice warning %}}
If you provide more than one WDT install home using `sourceWDTInstallHome`, then the domain deployment will fail.
{{% /notice %}}

For an example of configuring multiple auxiliary images, see [Configuration example 3](#configuration-example-3-multiple-images).

#### Model and WDT install homes
If you are using auxiliary images, typically, it should not be necessary to set `domain.spec.configuration.models.modelHome` and
`domain.spec.configuration.models.wdtInstallHome`. The model and WDT install files you supply in the auxiliary image
(see [source locations](#source-locations)) are always placed in the `/aux/models` and `/aux/weblogic-deploy` directories,
respectively, in all WebLogic Server pods. When auxiliary image(s) are configured, the operator automatically changes
the default for `modelHome` and `wdtInstallHome` to match.

{{% notice warning %}}
If you set `modelHome` and `wdtInstallHome` to a non-default value,
then the operator will ignore the WDT model and installation files from the auxiliary image(s).
{{% /notice %}}

#### Configuration examples
The following configuration examples illustrate each of the previously described sections.

##### Configuration example 1: Basic configuration
This example specifies the required image parameter for the auxiliary image(s); all other fields are at default values.

```
spec:
  configuration:
    model:
      auxiliaryImages:
      - image: model-in-image:v1
```

##### Configuration example 2: Source locations
This example is same as Example 1 except that it specifies the source locations for the WebLogic Deploy Tooling model and installation files.
```
spec:
  configuration:
    model:
      auxiliaryImages:
      - image: model-in-image:v1
        sourceModelHome: /foo/models
        sourceWDTInstallHome: /bar/weblogic-deploy
```

##### Configuration example 3: Multiple images
This example is the same as Example 1, except it configures multiple auxiliary images and sets the `sourceWDTInstallHome` 
for the second image to `None`.
In this case, the source location of the WebLogic Deploy Tooling installation from the second image `new-model-in-image:v1` will be ignored.

```
spec:
  configuration:
    model:
      auxiliaryImages:
      - image: model-in-image:v1
      - image: new-model-in-image:v1
        sourceWDTInstallHome: None
```

### Sample

This sample demonstrates deploying a Model in Image domain that uses
auxiliary images to supply the domain's WDT model files,
application archive ZIP files, and WDT installation in a small, separate
container image.

#### Step 1: Prerequisites

- First, follow all of the steps in the Model in Image
  [initial use case sample](/weblogic-kubernetes-operator/samples/domains/model-in-image/initial/).

  This will:

  - Set up the operator and a namespace for the domain.
  - Download a WebLogic Deploy Tool ZIP file installation.
  - Deploy a domain _without_ auxiliary images.

- Second, shut down the domain and wait for its pods to exit.
  - You can use the `wl-pod-wait.sh` script to wait.
  - For example, assuming that
    you have set up `/tmp/mii-sample` as your working directory:
    ```shell
    $ kubectl delete domain sample-domain1 -n sample-domain1-ns
    $ /tmp/mii-sample/utils/wl-pod-wait.sh -p 0
    ```

#### Step 2: Create the auxiliary image

Follow these steps to create an auxiliary image containing
Model In Image model files, application archives, and the WDT installation files:

1. Create a model ZIP file application archive and place it in the same directory
   where the model YAML file and model properties files are already in place
   for the initial use case:
   ```shell
   $ rm -f /tmp/mii-sample/model-images/model-in-image__WLS-AI-v1/archive.zip
   $ cd /tmp/mii-sample/archives/archive-v1
   $ zip -r /tmp/mii-sample/model-images/model-in-image__WLS-AI-v1/archive.zip wlsdeploy
   ```
   The `rm -f` command is included in case there's an
   old version of the archive ZIP file from a
   previous run of this sample.

1. Create a temporary directory for staging the auxiliary image's files and `cd` to this directory:
   ```shell
   $ mkdir -p /tmp/mii-sample/ai-image/WLS-AI-v1
   $ cd /tmp/mii-sample/ai-image/WLS-AI-v1
   ```
   We call this directory `WLS-AI-v1` to correspond with the image version tag that we plan to use for the auxiliary image.

1. Create a `models` directory in the staging directory and copy the model YAML file, properties, and archive into it:
   ```shell
   $ mkdir ./models
   $ cp /tmp/mii-sample/model-images/model-in-image__WLS-AI-v1/model.10.yaml ./models
   $ cp /tmp/mii-sample/model-images/model-in-image__WLS-AI-v1/model.10.properties ./models
   $ cp /tmp/mii-sample/model-images/model-in-image__WLS-AI-v1/archive.zip ./models
   ```

1. Use one of the two options listed below to create the auxiliary image using a small `busybox` image as the base image. 
   - Option 1 - Use the `createAuxImage` option of the [_WebLogic Image Tool_ (WIT)](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-aux-image/) to create the auxiliary image. Run the following command:

     ```shell
     $ /tmp/mii-sample/model-images/imagetool/bin/imagetool.sh createAuxImage \
       --tag model-in-image:WLS-AI-v1 \
       --wdtModel ./models/model.10.yaml \
       --wdtVariables ./models/model.10.properties \
       --wdtArchive ./models/archive.zip
     ```
  
     If you don't see the `imagetool` directory under `/tmp/mii-sample/model-images` or the WDT installer is not in the Image Tool cache, then repeat the step to set up the WebLogic Image Tool in the [prerequisites]({{< relref "/samples/domains/model-in-image/prerequisites.md" >}}).
 
     When you run this command, the Image Tool will create an auxiliary image with the specified model, variables, and archive files in the
     image's `/auxiliary/models` directory. It will also add the latest version of the WDT installation in its `/auxiliary/weblogic-deploy` directory.
     See [Create Auxiliary Image](https://oracle.github.io/weblogic-image-tool/userguide/tools/create-aux-image/) for additional Image Tool options.
     The operator auxiliary image feature looks for WDT model and WDT install files in these specific directories by default; if you change
     the location of these directories, then change the corresponding domain resource auxiliary image [source locations attributes](#source-locations).

   - Option 2 - Alternatively, you can create the auxiliary image manually by following these steps.

     - First, install WDT in the staging directory and remove its `weblogic-deploy/bin/*.cmd` files, which are not used in UNIX environments:
       ```shell
       $ unzip /tmp/mii-sample/model-images/weblogic-deploy.zip -d .
       $ rm ./weblogic-deploy/bin/*.cmd
       ```
       If the `weblogic-deploy.zip` file is missing, then repeat the step to download the latest WebLogic Deploy Tooling (WDT) in the [prerequisites]({{< relref "/samples/domains/model-in-image/prerequisites.md" >}}).
  
     - Run the `docker build` command using `/tmp/mii-sample/ai-docker-file/Dockerfile`.
  
       ```shell
       $ docker build -f /tmp/mii-sample/ai-docker-file/Dockerfile \
         --build-arg AUXILIARY_IMAGE_PATH=/auxiliary \
         --tag model-in-image:WLS-AI-v1 .
       ```

       See `./Dockerfile` for an explanation of each build argument.

       {{%expand "Click here to view the Dockerfile." %}}
       ```
       # Copyright (c) 2021, 2022, Oracle and/or its affiliates.
       # Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
       
       # This is a sample Dockerfile for supplying Model in Image model files
       # and a WDT installation in a small separate auxiliary image
       # image. This is an alternative to supplying the files directly
       # in the domain resource `domain.spec.image` image.
              
       # AUXILIARY_IMAGE_PATH arg:
       #   Parent location for Model in Image model and WDT installation files.
       #   The default is '/auxiliary', which matches the parent directory in the default values for
       #   'domain.spec.configuration.model.auxiliaryImages.sourceModelHome' and
       #   'domain.spec.configuration.model.auxiliaryImages.sourceWDTInstallHome', respectively.
       #
       
       FROM busybox
       ARG AUXILIARY_IMAGE_PATH=/auxiliary
       ARG USER=oracle
       ARG USERID=1000
       ARG GROUP=root
       ENV AUXILIARY_IMAGE_PATH=${AUXILIARY_IMAGE_PATH}
       RUN adduser -D -u ${USERID} -G $GROUP $USER
       # ARG expansion in COPY command's --chown is available in docker version 19.03.1+.
       # For older docker versions, change the Dockerfile to use separate COPY and 'RUN chown' commands.
       COPY --chown=$USER:$GROUP ./ ${AUXILIARY_IMAGE_PATH}/
       USER $USER
       ```
       {{% /expand %}}

1. If you have successfully created the image, then it should now be in your local machine's Docker repository. For example:

    ```
    $ docker images model-in-image:WLS-AI-v1
    REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
    model-in-image      WLS-AI-v1           eac9030a1f41        1 minute ago        4.04MB
    ```


1. After the image is created, it should have the WDT executables in
   `/auxiliary/weblogic-deploy`, and WDT model, property, and archive
   files in `/auxiliary/models`. You can run `ls` in the Docker
   image to verify this:

   ```shell
   $ docker run -it --rm model-in-image:WLS-AI-v1 ls -l /auxiliary
     total 8
     drwxr-xr-x    1 oracle   root          4096 Jun  1 21:53 models
     drwxr-xr-x    1 oracle   root          4096 May 26 22:29 weblogic-deploy

   $ docker run -it --rm model-in-image:WLS-AI-v1 ls -l /auxiliary/models
     total 16
     -rw-rw-r--    1 oracle   root          5112 Jun  1 21:52 archive.zip
     -rw-rw-r--    1 oracle   root           173 Jun  1 21:59 model.10.properties
     -rw-rw-r--    1 oracle   root          1515 Jun  1 21:59 model.10.yaml

   $ docker run -it --rm model-in-image:WLS-AI-v1 ls -l /auxiliary/weblogic-deploy
     total 28
     -rw-r-----    1 oracle   root          4673 Oct 22  2019 LICENSE.txt
     -rw-r-----    1 oracle   root            30 May 25 11:40 VERSION.txt
     drwxr-x---    1 oracle   root          4096 May 26 22:29 bin
     drwxr-x---    1 oracle   root          4096 May 25 11:40 etc
     drwxr-x---    1 oracle   root          4096 May 25 11:40 lib
     drwxr-x---    1 oracle   root          4096 Jan 22  2019 samples

   ```

#### Step 3: Prepare and apply the domain resource

Copy the following to a file called `/tmp/mii-sample/mii-initial.yaml` or similar,
or you can directly use the file `/tmp/mii-sample/domain-resources/WLS-AI/mii-initial-d1-WLS-AI-v1.yaml`
that is included in the sample source.

  {{%expand "Click here to view the WLS Domain YAML file using auxiliary images." %}}
  ```yaml
  # Copyright (c) 2021, 2022, Oracle and/or its affiliates.
  # Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
  #
  # This is an example of how to define a Domain resource.
  #
  apiVersion: "weblogic.oracle/v9"
  kind: Domain
  metadata:
    name: sample-domain1
    namespace: sample-domain1-ns
    labels:
      weblogic.domainUID: sample-domain1
  
  spec:
    # Set to 'FromModel' to indicate 'Model in Image'.
    domainHomeSourceType: FromModel
  
    # The WebLogic Domain Home, this must be a location within
    # the image for 'Model in Image' domains.
    domainHome: /u01/domains/sample-domain1
  
    # The WebLogic Server image that the Operator uses to start the domain
    image: "container-registry.oracle.com/middleware/weblogic:12.2.1.4"
  
    # Defaults to "Always" if image tag (version) is ':latest'
    imagePullPolicy: "IfNotPresent"
  
    # Identify which Secret contains the credentials for pulling an image
    #imagePullSecrets:
    #- name: regsecret
    
    # Identify which Secret contains the WebLogic Admin credentials,
    # the secret must contain 'username' and 'password' fields.
    webLogicCredentialsSecret: 
      name: sample-domain1-weblogic-credentials
  
    # Whether to include the WebLogic Server stdout in the pod's stdout, default is true
    includeServerOutInPodLog: true
  
    # Whether to enable overriding your log file location, see also 'logHome'
    #logHomeEnabled: false
    
    # The location for domain log, server logs, server out, introspector out, and Node Manager log files
    # see also 'logHomeEnabled', 'volumes', and 'volumeMounts'.
    #logHome: /shared/logs/sample-domain1
    
    # Set which WebLogic Servers the Operator will start
    # - "NEVER" will not start any server in the domain
    # - "ADMIN_ONLY" will start up only the administration server (no managed servers will be started)
    # - "IF_NEEDED" will start all non-clustered servers, including the administration server, and clustered servers up to their replica count.
    serverStartPolicy: "IF_NEEDED"
  
    # Settings for all server pods in the domain including the introspector job pod
    serverPod:
      # Optional new or overridden environment variables for the domain's pods
      # - This sample uses CUSTOM_DOMAIN_NAME in its image model file 
      #   to set the WebLogic domain name
      env:
      - name: CUSTOM_DOMAIN_NAME
        value: "domain1"
      - name: JAVA_OPTIONS
        value: "-Dweblogic.StdoutDebugEnabled=false"
      - name: USER_MEM_ARGS
        value: "-Djava.security.egd=file:/dev/./urandom -Xms256m -Xmx512m "
      resources:
        requests:
          cpu: "250m"
          memory: "768Mi"
  
      # Optional volumes and mounts for the domain's pods. See also 'logHome'.
      #volumes:
      #- name: weblogic-domain-storage-volume
      #  persistentVolumeClaim:
      #    claimName: sample-domain1-weblogic-sample-pvc
      #volumeMounts:
      #- mountPath: /shared
      #  name: weblogic-domain-storage-volume
  
    # The desired behavior for starting the domain's administration server.
    adminServer:
      # The serverStartState legal values are "RUNNING" or "ADMIN"
      # "RUNNING" means the listed server will be started up to "RUNNING" mode
      # "ADMIN" means the listed server will be start up to "ADMIN" mode
      serverStartState: "RUNNING"
      # Setup a Kubernetes node port for the administration server default channel
      #adminService:
      #  channels:
      #  - channelName: default
      #    nodePort: 30701
     
    # The number of managed servers to start for unlisted clusters
    replicas: 1
  
    # The desired behavior for starting a specific cluster's member servers
    clusters:
    - clusterName: cluster-1
      serverStartState: "RUNNING"
      serverPod:
        # Instructs Kubernetes scheduler to prefer nodes for new cluster members where there are not
        # already members of the same cluster.
        affinity:
          podAntiAffinity:
            preferredDuringSchedulingIgnoredDuringExecution:
              - weight: 100
                podAffinityTerm:
                  labelSelector:
                    matchExpressions:
                      - key: "weblogic.clusterName"
                        operator: In
                        values:
                          - $(CLUSTER_NAME)
                  topologyKey: "kubernetes.io/hostname"
      # The number of managed servers to start for this cluster
      replicas: 2
  
    # Change the restartVersion to force the introspector job to rerun
    # and apply any new model configuration, to also force a subsequent
    # roll of your domain's WebLogic Server pods.
    restartVersion: '1'
  
    # Changes to this field cause the operator to repeat its introspection of the
    #  WebLogic domain configuration.
    introspectVersion: '1'
  
    configuration:
  
      # Settings for domainHomeSourceType 'FromModel'
      model:
        # Valid model domain types are 'WLS', 'JRF', and 'RestrictedJRF', default is 'WLS'
        domainType: "WLS"
  
        # Optional auxiliary image(s) containing WDT model, archives, and install.
        # Files are copied from `sourceModelHome` in the aux image to the `/aux/models` directory
        # in running WebLogic Server pods, and files are copied from `sourceWDTInstallHome` 
        # to the `/aux/weblogic-deploy` directory. Set `sourceModelHome` and/or `sourceWDTInstallHome` 
        # to "None" if you want skip such copies.
        #   `image`                - Image location
        #   `imagePullPolicy`      - Pull policy, default `IfNotPresent`
        #   `sourceModelHome`      - Model file directory in image, default `/auxiliary/models`.
        #   `sourceWDTInstallHome` - WDT install directory in image, default `/auxiliary/weblogic-deploy`.
        auxiliaryImages:
        - image: "model-in-image:WLS-AI-v1"
          #imagePullPolicy: IfNotPresent
          #sourceWDTInstallHome: /auxiliary/weblogic-deploy
          #sourceModelHome: /auxiliary/models
  
        # Optional configmap for additional models and variable files
        #configMap: sample-domain1-wdt-config-map
  
        # All 'FromModel' domains require a runtimeEncryptionSecret with a 'password' field
        runtimeEncryptionSecret: sample-domain1-runtime-encryption-secret
  
      # Secrets that are referenced by model yaml macros
      # (the model yaml in the optional configMap or in the image)
      #secrets:
      #- sample-domain1-datasource-secret
  ```
  {{% /expand %}}
  
You can compare this domain resource YAML file with the domain resource YAML file
from the original initial use case (`/tmp/mii-sample/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml`)
to see the changes required for auxiliary images. For example:

```
$ diff /tmp/mii-sample/domain-resources/WLS-AI/mii-initial-d1-WLS-AI-v1.yaml /tmp/mii-sample/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml
1c1
< # Copyright (c) 2021, 2022, Oracle and/or its affiliates.
---
> # Copyright (c) 2020, 2021, Oracle and/or its affiliates.
23c23
<   image: "container-registry.oracle.com/middleware/weblogic:12.2.1.4"
---
>   image: "model-in-image:WLS-v1"
131,145d130
<
<       # Optional auxiliary image(s) containing WDT model, archives, and install.
<       # Files are copied from `sourceModelHome` in the aux image to the `/aux/models` directory
<       # in running WebLogic Server pods, and files are copied from `sourceWDTInstallHome`
<       # to the `/aux/weblogic-deploy` directory. Set `sourceModelHome` and/or `sourceWDTInstallHome`
<       # to "None" if you want skip such copies.
<       #   `image`                - Image location
<       #   `imagePullPolicy`      - Pull policy, default `IfNotPresent`
<       #   `sourceModelHome`      - Model file directory in image, default `/auxiliary/models`.
<       #   `sourceWDTInstallHome` - WDT install directory in image, default `/auxiliary/weblogic-deploy`.
<       auxiliaryImages:
<       - image: "model-in-image:WLS-AI-v1"
<         #imagePullPolicy: IfNotPresent
<         #sourceWDTInstallHome: /auxiliary/weblogic-deploy
<         #sourceModelHome: /auxiliary/models
```

Run the following command to deploy the domain custom resource:

```shell
$ kubectl apply -f /tmp/mii-sample/domain-resources/WLS-AI/mii-initial-d1-WLS-AI-v1.yaml
```

**Note**: If you are choosing _not_ to use the predefined Domain YAML file
  and instead created your own Domain YAML file earlier, then substitute your
  custom file name in the previous command. Previously, we suggested naming it `/tmp/mii-sample/mii-initial.yaml`.

Now, if you run `kubectl get pods -n sample-domain1-ns --watch`, then you will see
the introspector job run and your WebLogic Server pods start. The output will look something like this:

  {{%expand "Click here to expand." %}}
  ```shell
  $ kubectl get pods -n sample-domain1-ns --watch
  ```
  ```text
  NAME                                READY   STATUS    RESTARTS   AGE
  sample-domain1-introspector-z5vmp   0/1     Pending   0          0s
  sample-domain1-introspector-z5vmp   0/1     Pending   0          0s
  sample-domain1-introspector-z5vmp   0/1     Init:0/1   0          0s
  sample-domain1-introspector-z5vmp   0/1     PodInitializing   0          2s
  sample-domain1-introspector-z5vmp   1/1     Running           0          3s
  sample-domain1-introspector-z5vmp   0/1     Completed         0          71s
  sample-domain1-admin-server         0/1     Pending           0          0s
  sample-domain1-admin-server         0/1     Pending           0          0s
  sample-domain1-admin-server         0/1     Init:0/1          0          0s
  sample-domain1-introspector-z5vmp   0/1     Terminating       0          71s
  sample-domain1-introspector-z5vmp   0/1     Terminating       0          71s
  sample-domain1-admin-server         0/1     PodInitializing   0          2s
  sample-domain1-admin-server         0/1     Running           0          3s
  sample-domain1-admin-server         1/1     Running           0          41s
  sample-domain1-managed-server1      0/1     Pending           0          0s
  sample-domain1-managed-server1      0/1     Pending           0          0s
  sample-domain1-managed-server1      0/1     Init:0/1          0          0s
  sample-domain1-managed-server2      0/1     Pending           0          0s
  sample-domain1-managed-server2      0/1     Pending           0          0s
  sample-domain1-managed-server2      0/1     Init:0/1          0          0s
  sample-domain1-managed-server2      0/1     Init:0/1          0          1s
  sample-domain1-managed-server1      0/1     Init:0/1          0          1s
  sample-domain1-managed-server1      0/1     PodInitializing   0          2s
  sample-domain1-managed-server2      0/1     PodInitializing   0          2s
  sample-domain1-managed-server2      0/1     Running           0          3s
  sample-domain1-managed-server1      0/1     Running           0          3s
  sample-domain1-managed-server2      1/1     Running           0          39s
  sample-domain1-managed-server1      1/1     Running           0          43s
  ```
  {{% /expand %}}

Alternatively, you can run `/tmp/mii-sample/utils/wl-pod-wait.sh -p 3`.
This utility script exits successfully when the designated number of WebLogic
Server pods reach a `ready` state and have `restartVersion`, `introspectVersion`,
`spec.image`, and `spec.serverPod.auxiliaryImages.image` values that match
their corresponding values in their domain resource.

  {{%expand "Click here to display the `wl-pod-wait.sh` usage." %}}
  ```shell
    $ ./wl-pod-wait.sh -?
  ```

  ```text
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
                        - same image as the domain resource's 'spec.image'
                        - same auxiliary images as
                          the domain resource's 'spec.serverPod.auxiliaryImages'

      -t <timeout>    : Timeout in seconds. Defaults to '1000'.

      -q              : Quiet mode. Show only a count of wl pods that
                        have reached the desired criteria.

      -?              : This help.
  ```
  {{% /expand %}}

  {{%expand "Click here to view sample output from `wl-pod-wait.sh`." %}}
  ```text
  @@ [2022-01-21T18:39:48][seconds=1] Info: Waiting up to 1000 seconds for exactly '3' WebLogic Server pods to reach the following criteria:
  @@ [2022-01-21T18:39:48][seconds=1] Info:   ready='true'
  @@ [2022-01-21T18:39:48][seconds=1] Info:   image='container-registry.oracle.com/middleware/weblogic:12.2.1.4'
  @@ [2022-01-21T18:39:48][seconds=1] Info:   auxiliaryImages='model-in-image:WLS-AI-v1'
  @@ [2022-01-21T18:39:48][seconds=1] Info:   domainRestartVersion='1'
  @@ [2022-01-21T18:39:48][seconds=1] Info:   introspectVersion='1'
  @@ [2022-01-21T18:39:48][seconds=1] Info:   namespace='sample-domain1-ns'
  @@ [2022-01-21T18:39:48][seconds=1] Info:   domainUID='sample-domain1'
  
  @@ [2022-01-21T18:39:48][seconds=1] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2022-01-21T18:39:48][seconds=1] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                                 RVER  IVER  IMAGE  AIIMAGES                    READY  PHASE
  ----                                 ----  ----  -----  --------                    -----  -----
  'sample-domain1-introspector-xndwj'  ''    ''    ''     'model-in-image:WLS-AI-v1'  ''     'Pending'
  
  @@ [2022-01-21T18:39:51][seconds=4] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2022-01-21T18:39:51][seconds=4] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                                 RVER  IVER  IMAGE  AIIMAGES                    READY  PHASE
  ----                                 ----  ----  -----  --------                    -----  -----
  'sample-domain1-introspector-xndwj'  ''    ''    ''     'model-in-image:WLS-AI-v1'  ''     'Running'
  
  @@ [2022-01-21T18:41:02][seconds=75] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2022-01-21T18:41:02][seconds=75] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                                 RVER  IVER  IMAGE                                                         AIIMAGES                    READY    PHASE
  ----                                 ----  ----  -----                                                         --------                    -----    -----
  'sample-domain1-admin-server'        '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'false'  'Pending'
  'sample-domain1-introspector-xndwj'  ''    ''    ''                                                            'model-in-image:WLS-AI-v1'  ''       'Succeeded'
  
  @@ [2022-01-21T18:41:03][seconds=76] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2022-01-21T18:41:03][seconds=76] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                           RVER  IVER  IMAGE                                                         AIIMAGES                    READY    PHASE
  ----                           ----  ----  -----                                                         --------                    -----    -----
  'sample-domain1-admin-server'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'false'  'Pending'
  
  @@ [2022-01-21T18:41:05][seconds=78] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2022-01-21T18:41:05][seconds=78] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                           RVER  IVER  IMAGE                                                         AIIMAGES                    READY    PHASE
  ----                           ----  ----  -----                                                         --------                    -----    -----
  'sample-domain1-admin-server'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'false'  'Running'
  
  @@ [2022-01-21T18:41:44][seconds=117] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2022-01-21T18:41:44][seconds=117] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                              RVER  IVER  IMAGE                                                         AIIMAGES                    READY    PHASE
  ----                              ----  ----  -----                                                         --------                    -----    -----
  'sample-domain1-admin-server'     '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'false'  'Pending'
  'sample-domain1-managed-server2'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'false'  'Pending'
  
  @@ [2022-01-21T18:41:48][seconds=121] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2022-01-21T18:41:48][seconds=121] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                              RVER  IVER  IMAGE                                                         AIIMAGES                    READY    PHASE
  ----                              ----  ----  -----                                                         --------                    -----    -----
  'sample-domain1-admin-server'     '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'false'  'Running'
  'sample-domain1-managed-server2'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'false'  'Running'
  
  @@ [2022-01-21T18:42:22][seconds=155] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2022-01-21T18:42:22][seconds=155] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                              RVER  IVER  IMAGE                                                         AIIMAGES                    READY    PHASE
  ----                              ----  ----  -----                                                         --------                    -----    -----
  'sample-domain1-admin-server'     '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'true'   'Running'
  'sample-domain1-managed-server2'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'false'  'Running'
  
  @@ [2022-01-21T18:42:29][seconds=162] Info: '3' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2022-01-21T18:42:29][seconds=162] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                              RVER  IVER  IMAGE                                                         AIIMAGES                    READY   PHASE
  ----                              ----  ----  -----                                                         --------                    -----   -----
  'sample-domain1-admin-server'     '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'true'  'Running'
  'sample-domain1-managed-server1'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'true'  'Running'
  'sample-domain1-managed-server2'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-AI-v1'  'true'  'Running'
  
  
  @@ [2022-01-21T18:42:29][seconds=162] Info: Success!
  ```
  {{% /expand %}}

If you see an error, then consult [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) in the Model in Image user guide.

#### Step 4: Invoke the web application

To invoke the web application, follow the same steps as described in the
[Invoke the web application](/weblogic-kubernetes-operator/samples/domains/model-in-image/initial/#invoke-the-web-application)
section of the initial use case.
