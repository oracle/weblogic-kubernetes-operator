+++
title = "Common mounts"
date = 2019-02-23T16:45:16-05:00
weight = 25
pre = "<b> </b>"
description = "Common mounts are an alternative approach for supplying a domain's model files or other types of files."
+++

### Contents

 - [Introduction](#introduction)
 - [References](#references)
 - [Configuration](#configuration)
   - [Common mount images](#common-mount-images)
   - [Common mount volumes and paths](#common-mount-volumes-and-paths)
   - [Model in Image paths](#model-in-image-paths)
 - [Sample](#sample)
    - [Step 1: Prerequisites](#step-1-prerequisites)
    - [Step 2: Create the common mounts image](#step-2-create-the-common-mounts-image)
    - [Step 3: Prepare and apply the domain resource](#step-3-prepare-and-apply-the-domain-resource)
    - [Step 4: Invoke the web application](#step-4-invoke-the-web-application)

### Introduction

{{% notice warning %}}
The common mounts feature is a work in progress and is currently unsupported.
Its configuration or behavior may change between releases and it is disabled by default.
If you want to enable this feature, then set your operator's `"featureGates"`
Helm configuration attribute to include `"CommonMounts=true"`.
The `"featureGates"` attribute acknowledges use of an unsupported feature,
will not be required after common mounts is fully supported,
defaults to being unset, and accepts a comma-separated list.
{{% /notice %}}

Common mounts are an alternative approach for including Model in Image model files,
application archive files, WebLogic Deploying Tooling installation files,
or other types of files, in your pods.
This feature eliminates the need to provide these files in the image specified
in `domain.spec.image`.

Instead:

- The domain resource's `domain.spec.image` directly references a base image
  that needs to include only a WebLogic installation and a Java installation.
- The domain resource's common mount related fields reference one or
  more smaller images that contain the desired Model in Image files.
- The domain resource's `domain.spec.configuration.model.wdtInstallHome`
  and `domain.spec.configuration.model.modelHome` fields are set to
  reference a directory that contains the files from the smaller images.

The advantages of common mounts for Model In Image domains are:

- Use or patch a WebLogic installation image without needing to include a WDT installation,
  application archive, or model artifacts within the image.
- Share one WebLogic installation image with multiple different model
  configurations that are supplied in specific images.
- Distribute or update model files, application archives, and the
  WebLogic Deploy Tooling executable using specific images
  that do not contain a WebLogic installation.

Common mounts internally
use a Kubernetes `emptyDir` volume and Kubernetes `init` containers to share files
from additional images.

### References

- Run the `kubectl explain domain.spec.commonMountVolumes`
  and `kubectl explain domain.spec.serverPod.commonMounts` commands.

- See the `spec.commonMountVolumes` and `serverPod.commonMounts` sections
  in the domain resource
  [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md)
  and [documentation]({{< relref "/userguide/managing-domains/domain-resource.md" >}}).

### Configuration

This section describes a typical common mount configuration for the
Model in Image use case.

#### Common mount images

One or more common mount images can be configured on a domain resource `serverPod`.
A `serverPod` can be defined at the domain scope, which applies to every pod in
the domain, plus the introspector job's pod, at a specific WebLogic cluster's scope,
or at a specific WebLogic Server pod's scope. Typically, the domain scope is
the most applicable for the Model in Image use case; for example:

```
spec:
  serverPod:
    commonMounts:
    - image: model-in-image:v1
      imagePullPolicy: IfNotPresent
      volume: commonMountsVolume1
```

{{% notice note %}}
If image pull secrets are required for pulling common mounts images,
then the secrets must be referenced using `domain.spec.imagePullSecrets`.
{{% /notice %}}

#### Common mount volumes and paths

The `serverPod.commonMounts.volume` field refers to the name of a common
mount volume defined in the `domain.spec.commonMountVolumes` section, and
a common mount volume, in turn, defines a `mountPath`. The `mountPath`
is the location of a directory in a common mount image, and
is also the location in the main pod container (which will automatically contain
a recursive copy of the common mount image directory). For example:

```
  spec:
    commonMountVolumes:
    - name: commonMountsVolume1
      mountPath: /common
```

#### Model in Image paths

For the Model In Image common mount use case, you also need to
configure the `domain.spec.configuration.model.modelHome`
and `domain.spec.configuration.model.wdtInstallHome` attributes
to specify the location of the domain's WebLogic Deploy Tool (WDT)
model files and the domain's WDT installation.
These default to `/u01/wdt/models` and `/u01/wdt/weblogic-deploy`
respectively, and must be changed to specify a directory in
`domain.spec.commonMountVolumes.mountPath`. For example:

```
  configuration:
    model:
      modelHome: "/common/models"
      wdtInstallHome: "/common/weblogic-deploy"
```

### Sample

This sample demonstrates deploying a Model in Image domain that uses
common mounts to supply the domain's WDT model files,
application archive ZIP files, and WDT installation in a small, separate
container image.

#### Step 1: Prerequisites

- First, follow all of the steps in the Model in Image
  [initial use case sample](/weblogic-kubernetes-operator/samples/simple/domains/model-in-image/initial/).

  This will:

  - Set up the operator and a namespace for the domain.
  - Download a WebLogic Deploy Tool ZIP installation.
  - Deploy a domain _without_ common mounts.

- Second, shut down the domain and wait for its pods to exit.
  - You can use the `wl-pod-wait.sh` script to wait.
  - For example, assuming that
    you have set up `/tmp/mii-sample` as your working directory:
    ```shell
    $ kubectl delete domain sample-domain1 -n sample-domain1-ns
    $ /tmp/mii-sample/utils/wl-pod-wait.sh -p 0
    ```

#### Step 2: Create the common mounts image

Follow these steps to create a common mounts image containing
Model In Image model files, application archives, and the WDT installation files:

1. Create a model ZIP application archive and place it in the same directory
   where the model YAML file and model properties files are already in place
   for the initial use case:
   ```shell
   $ rm -f /tmp/mii-sample/model-images/model-in-image__WLS-CM-v1/archive.zip
   $ cd /tmp/mii-sample/archives/archive-v1
   $ zip -r /tmp/mii-sample/model-images/model-in-image__WLS-CM-v1/archive.zip wlsdeploy
   ```
   The `rm -f` command is included in case there's an
   old version of the archive ZIP from a
   previous run of this sample.

1. Create a temporary directory for staging the common mount image's files and `cd` to this directory:
   ```shell
   $ mkdir /tmp/mii-sample/cm-image/WLS-CM-v1
   $ cd /tmp/mii-sample/cm-image/WLS-CM-v1
   ```
   We call this directory `WLS-CM-v1` to correspond with the image version tag that we plan to use for the common mount image.

1. Install WDT in the staging directory and remove its `weblogic-deploy/bin/*.cmd` files, which are not used in UNIX environments:
   ```shell
   $ unzip /tmp/mii-sample/model-images/weblogic-deploy.zip -d .
   $ rm ./weblogic-deploy/bin/*.cmd
   ```
   In a later step, we will specify a domain resource `domain.spec.configuration.model.wdtInstallHome`
   attribute that references this WDT installation directory.

   If the `weblogic-deploy.zip` file is missing, then you have skipped a step in the prerequisites.

1. Create a `models` directory in the staging directory and copy the model YAML file, properties, and archive into it:
   ```shell
   $ mkdir ./models
   $ cp /tmp/mii-sample/model-images/model-in-image__WLS-CM-v1/model.10.yaml ./models
   $ cp /tmp/mii-sample/model-images/model-in-image__WLS-CM-v1/model.10.properties ./models
   $ cp /tmp/mii-sample/model-images/model-in-image__WLS-CM-v1/archive.zip ./models
   ```
   In a later step, we will specify a domain resource `domain.spec.configuration.model.modelHome`
   attribute that references this directory.

1. Run `docker build` using `/tmp/mii-sample/cm-docker-file/Dockerfile` to create your common mount
   image using a small `busybox` image as the base image.

   ```shell
   $ docker build -f /tmp/mii-sample/cm-docker-file/Dockerfile \
     --build-arg COMMON_MOUNT_PATH=/common \
     --build-arg COMMON_MOUNT_STAGE=. \
     --tag model-in-image:WLS-CM-v1 .
   ```

   See `./Dockerfile` for an explanation of each build argument.

   {{%expand "Click here to view the Dockerfile." %}}
   ```
   # Copyright (c) 2021, Oracle and/or its affiliates.
   # Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

   # This is a sample Dockerfile for supplying Model in Image model files
   # and a WDT installation in a small separate "common mount"
   # image. This is an alternative to supplying the files directly
   # in the domain resource `domain.spec.image` image.

   # COMMON_MOUNT_PATH arg:
   #   Parent location for Model in Image model and WDT installation files.
   #   Must match domain resource 'domain.spec.commonMountVolumes.mountPath'
   #   For model-in-image, the following two domain resource attributes can
   #   be a directory in the mount path:
   #     1) 'domain.spec.configuration.model.modelHome'
   #     2) 'domain.spec.configuration.model.wdtInstallHome'
   #   Default '/common'.
   #
   # COMMON_MOUNT_STAGE arg:
   #   Local directory containing files to be copied to the COMMON_MOUNT_PATH.
   #   Default '.'.
   #

   FROM busybox
   ARG COMMON_MOUNT_PATH=/common
   ARG COMMON_MOUNT_STAGE=.
   ARG USER=oracle
   ARG USERID=1000
   ARG GROUP=root
   ENV COMMON_MOUNT_PATH=${COMMON_MOUNT_PATH}
   RUN adduser -D -u ${USERID} -G $GROUP $USER
   COPY ${COMMON_MOUNT_STAGE}/ ${COMMON_MOUNT_PATH}/
   RUN chown -R $USER:$GROUP ${COMMON_MOUNT_PATH}
   USER $USER
   ```
   {{% /expand %}}

1. After the image is created, it should have the WDT executables in
   `/common/weblogic-deploy`, and WDT model, property, and archive
   files in `/common/models`. You can run `ls` in the Docker
   image to verify this:

   ```shell
   $ docker run -it --rm model-in-image:WLS-CM-v1 ls -l /common
     total 8
     drwxr-xr-x    1 oracle   root          4096 Jun  1 21:53 models
     drwxr-xr-x    1 oracle   root          4096 May 26 22:29 weblogic-deploy

   $ docker run -it --rm model-in-image:WLS-CM-v1 ls -l /common/models
     total 16
     -rw-rw-r--    1 oracle   root          5112 Jun  1 21:52 archive.zip
     -rw-rw-r--    1 oracle   root           173 Jun  1 21:59 model.10.properties
     -rw-rw-r--    1 oracle   root          1515 Jun  1 21:59 model.10.yaml

   $ docker run -it --rm model-in-image:WLS-CM-v1 ls -l /common/weblogic-deploy
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
or you can directly use the file `/tmp/mii-sample/domain-resources/WLS-CM/mii-initial-d1-WLS-CM-v1.yaml`
that is included in the sample source.

  {{%expand "Click here to view the WLS Domain YAML file using common mounts." %}}
  ```yaml
    # Copyright (c) 2021, Oracle and/or its affiliates.
    # Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
    #
    # This is an example of how to define a Domain resource.
    #
    apiVersion: "weblogic.oracle/v8"
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

      # Settings for common mount volume(s), see also 'serverPod.commonMounts'.
      commonMountVolumes:
      - name: commonMountsVolume1
        mountPath: "/common"

      # Settings for all server pods in the domain including the introspector job pod
      serverPod:
        # Optional new or overridden environment variables for the domain's pods
        # - This sample uses CUSTOM_DOMAIN_NAME in its image model file
        #   to set the Weblogic domain name
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

        # Common mount image(s) containing WDT model, archives and install. See also:
        #    'spec.commonMountVolumes'.
        #    'spec.configuration.model.modelHome'
        #    'spec.configuration.model.wdtInstallHome'
        commonMounts:
        - image: "model-in-image:WLS-CM-v1"
          imagePullPolicy: IfNotPresent
          volume: commonMountsVolume1

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
          modelHome: "/common/models"
          wdtInstallHome: "/common/weblogic-deploy"

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
to see the changes required for common mounts. For example:

```
$ diff /tmp/mii-sample/domain-resources/WLS-CM/mii-initial-d1-WLS-CM-v1.yaml /tmp/mii-sample/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml
1c1
< # Copyright (c) 2021, Oracle and/or its affiliates.
---
> # Copyright (c) 2020, 2021, Oracle and/or its affiliates.
23c23
<   image: "container-registry.oracle.com/middleware/weblogic:12.2.1.4"
---
>   image: "model-in-image:WLS-v1"
53,57d52
<   # Settings for common mount volume(s), see also 'serverPod.commonMounts'.
<   commonMountVolumes:
<   - name: commonMountsVolume1
<     mountPath: "/common"
<
75,83d69
<     # Common mount image(s) containing WDT model, archives and install. See also:
<     #    'spec.commonMountVolumes'.
<     #    'spec.configuration.model.modelHome'
<     #    'spec.configuration.model.wdtInstallHome'
<     commonMounts:
<     - image: "model-in-image:WLS-CM-v1"
<       imagePullPolicy: IfNotPresent
<       volume: commonMountsVolume1
<
145,146d130
<       modelHome: "/common/models"
<       wdtInstallHome: "/common/weblogic-deploy"
```

Run the following command to deploy the domain custom resource:

```shell
$ kubectl apply -f /tmp/mii-sample/domain-resources/WLS-CM/mii-initial-d1-WLS-CM-v1.yaml
```

**Note**: If you are choosing _not_ to use the predefined Domain YAML file
  and instead created your own Domain YAML file earlier, then substitute your
  custom file name in the above command. Previously, we suggested naming it `/tmp/mii-sample/mii-initial.yaml`.

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
`spec.image`, and `spec.serverPod.commonMounts.image` values that match
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
                        - same common mount images as
                          the domain resource's 'spec.serverPod.commonMounts'
  
      -t <timeout>    : Timeout in seconds. Defaults to '1000'.
  
      -q              : Quiet mode. Show only a count of wl pods that
                        have reached the desired criteria.
  
      -?              : This help.
  ```
  {{% /expand %}}

  {{%expand "Click here to view sample output from `wl-pod-wait.sh`." %}}
  ```text
  @@ [2021-06-14T20:35:35][seconds=0] Info: Waiting up to 1000 seconds for exactly '3' WebLogic Server pods to reach the following criteria:
  @@ [2021-06-14T20:35:35][seconds=0] Info:   ready='true'
  @@ [2021-06-14T20:35:35][seconds=0] Info:   image='container-registry.oracle.com/middleware/weblogic:12.2.1.4'
  @@ [2021-06-14T20:35:35][seconds=0] Info:   commonMountImages='model-in-image:WLS-CM-v1'
  @@ [2021-06-14T20:35:35][seconds=0] Info:   domainRestartVersion='1'
  @@ [2021-06-14T20:35:35][seconds=0] Info:   introspectVersion='1'
  @@ [2021-06-14T20:35:35][seconds=0] Info:   namespace='sample-domain1-ns'
  @@ [2021-06-14T20:35:35][seconds=0] Info:   domainUID='sample-domain1'
  
  @@ [2021-06-14T20:35:35][seconds=0] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-06-14T20:35:35][seconds=0] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                                 RVER  IVER  IMAGE  CMIMAGES                    READY  PHASE
  ----                                 ----  ----  -----  --------                    -----  -----
  'sample-domain1-introspector-wz8q6'  ''    ''    ''     'model-in-image:WLS-CM-v1'  ''     'Pending'
  
  @@ [2021-06-14T20:35:39][seconds=4] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-06-14T20:35:39][seconds=4] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                                 RVER  IVER  IMAGE  CMIMAGES                    READY  PHASE
  ----                                 ----  ----  -----  --------                    -----  -----
  'sample-domain1-introspector-wz8q6'  ''    ''    ''     'model-in-image:WLS-CM-v1'  ''     'Running'
  
  @@ [2021-06-14T20:36:51][seconds=76] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-06-14T20:36:51][seconds=76] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                           RVER  IVER  IMAGE                                                         CMIMAGES                    READY    PHASE
  ----                           ----  ----  -----                                                         --------                    -----    -----
  'sample-domain1-admin-server'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'false'  'Pending'
  
  @@ [2021-06-14T20:36:55][seconds=80] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-06-14T20:36:55][seconds=80] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                           RVER  IVER  IMAGE                                                         CMIMAGES                    READY    PHASE
  ----                           ----  ----  -----                                                         --------                    -----    -----
  'sample-domain1-admin-server'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'false'  'Running'
  
  @@ [2021-06-14T20:37:34][seconds=119] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-06-14T20:37:34][seconds=119] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                              RVER  IVER  IMAGE                                                         CMIMAGES                    READY    PHASE
  ----                              ----  ----  -----                                                         --------                    -----    -----
  'sample-domain1-admin-server'     '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'false'  'Pending'
  'sample-domain1-managed-server2'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'false'  'Pending'
  
  @@ [2021-06-14T20:37:35][seconds=120] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-06-14T20:37:35][seconds=120] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                              RVER  IVER  IMAGE                                                         CMIMAGES                    READY    PHASE
  ----                              ----  ----  -----                                                         --------                    -----    -----
  'sample-domain1-admin-server'     '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'false'  'Running'
  'sample-domain1-managed-server2'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'false'  'Pending'
  
  @@ [2021-06-14T20:37:37][seconds=122] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-06-14T20:37:37][seconds=122] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                              RVER  IVER  IMAGE                                                         CMIMAGES                    READY    PHASE
  ----                              ----  ----  -----                                                         --------                    -----    -----
  'sample-domain1-admin-server'     '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'false'  'Running'
  'sample-domain1-managed-server2'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'false'  'Running'
  
  @@ [2021-06-14T20:38:18][seconds=163] Info: '2' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-06-14T20:38:18][seconds=163] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                              RVER  IVER  IMAGE                                                         CMIMAGES                    READY    PHASE
  ----                              ----  ----  -----                                                         --------                    -----    -----
  'sample-domain1-admin-server'     '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'false'  'Running'
  'sample-domain1-managed-server2'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'true'   'Running'
  
  @@ [2021-06-14T20:38:20][seconds=165] Info: '3' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-06-14T20:38:20][seconds=165] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                              RVER  IVER  IMAGE                                                         CMIMAGES                    READY   PHASE
  ----                              ----  ----  -----                                                         --------                    -----   -----
  'sample-domain1-admin-server'     '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'true'  'Running'
  'sample-domain1-managed-server1'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'true'  'Running'
  'sample-domain1-managed-server2'  '1'   '1'   'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'model-in-image:WLS-CM-v1'  'true'  'Running'
  
  
  @@ [2021-06-14T20:38:20][seconds=165] Info: Success!
  ```
  {{% /expand %}}

If you see an error, then consult [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) in the Model in Image user guide.

#### Step 4: Invoke the web application

To invoke the web application, follow the same steps as described in the
[Invoke the web application](/weblogic-kubernetes-operator/samples/simple/domains/model-in-image/initial/#invoke-the-web-application)
section of the initial use case.
