+++
title = "Common Mounts"
date = 2019-02-23T16:45:16-05:00
weight = 70
pre = "<b> </b>"
+++

#### Contents

 - [Introduction](#introduction)
 - [Configuration](#configuration)
 - [References](#references)
 - [Common Mount Volumes Fields](#common-mount-volumes-fields)
 - [Running Model in Image sample initial use case using common mounts](#running-model-in-image-sample-initial-use-case-using-common-mounts)
    - [Prerequisite Steps](#prerequisite-steps)
    - [Creating the common mounts image](#creating-the-common-mounts-image)
    - [Prepare and Apply the Domain Resource](#prepare-and-apply-the-domain-resource)

### Introduction
Common mounts are an alternative approach for including Model in Image model files, application archive files, Weblogic Deploying Tooling install files, or other types of files, in your pods. The common mounts feature internally uses a Kubernetes emptyDir volume and Kubernetes init containers to share files from additional images within a WebLogic Server pod or the Introspector job pod. 

This feature eliminates the need to provide these files in the image specified in `domain.spec.image`. Instead:
- The domain resource's `domain.spec.image` directly reference a base image that only needs to include a WebLogic install and a Java install
- The domain resource's common mount related fields reference one or more smaller images that contain the desired Model in Image files.
- The domain resource's `domain.spec.configuration.model.wdtInstallHome` and `domain.spec.configuration.model.modelHome` fields are set to reference a directory that contains the files from the smaller images.

The advantages of the common mounts feature for Model In Image domains are:
- Use or patch a WebLogic install image without needing to include WDT install, application archive, or model artifacts within the image.
- Share one large WebLogic install image with multiple different model configurations that are supplied in smaller images.
- Distribute or update model files, application archives, and the WebLogic Deploy Tooling executable using very small images instead of a large image that also contains a WebLogic install.

### Configuration
Here's an example configuration for the Common Mounts. 

```
  serverPod:
    commonMounts:
    - image: model-in-image:v1
      imagePullPolicy: IfNotPresent
      volume: commonMountsVolume1
```

The `volume` field in the common mounts refers to the name of a common mount volume defined in `spec.commonMountVolumes` section. Here's an example configuration for the common mount volumes.
```
  spec:
    commonMountVolumes:
    - name: commonMountsVolume1
      mountPath: /common
```
For the Model In Image use case using common mounts, you also need to configure the `configuration.model.modelHome` and `configuration.model.wdtInstallHome` attributes to specify the location of Model In Image files and WebLogic Deploy Tool (WDT) installation files. Here's an example configuration for the `configuration.model.modelHome` and `configuration.model.wdtInstallHome`.

```
  configuration:

    # Settings for domainHomeSourceType 'FromModel'
    model:
      modelHome: "/common/models"
      wdtInstallHome: "/common/weblogic-deploy"
```

{{% notice note %}}  The `imagePullSecrets` required for pulling the common mounts images should be specified at the Pod level using `spec.imagePullSecrets`.
{{% /notice %}}


#### References
- Run the `kubectl explain domain.spec.commonMountVolumes` and `kubectl explain domain.spec.serverPod.commonMounts` commands.
- See the `spec.commonMountVolumes` and `serverPod.commonMounts` sections in the Domain Resource [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md) and [documentation]({{< relref "/userguide/managing-domains/domain-resource.md" >}}).

### Running Model in Image sample initial use case using common mounts
The initial use case for the Model in Image is described [here](/weblogic-kubernetes-operator/samples/simple/domains/model-in-image/initial/). The goal for this section is to create the initial domain using the common mounts feature where you provide the WDT model files and archive ZIP file in a separate container image.

#### Prerequisite Steps
- Begin by following the steps described in the [initial use case sample](/weblogic-kubernetes-operator/samples/simple/domains/model-in-image/initial/). 
- Once you have completed the entire use case, you will have the initial use case resources deployed and a running domain. 
- To run the initial use case with common mounts:
  - Delete the existing domain by running the `kubectl delete domain sample-domain1 -n sample-domain1-ns` command to bring down the domain. 
  - Run the steps described in the [following section](#creating-the-common-mounts-image) to create the common mounts image that will host the model files, application archives, and the WDT installation files. 
  - Once the image is created, execute the steps in the [Prepare and Apply the Domain Resource](#prepare-and-apply-the-domain-resource) section below to create the new domain. 

#### Creating the common mounts image 
Run the following steps to create the common mounts image containing Model In Image model files, application archives, and the WDT installation files.
1. Create a temporary directory for docker build context `/tmp/cm-image`.
2. Copy the Dockefile in `/tmp/mii-sample/cm-docker-file` directory to the build context root i.e `/tmp/cm-image`.
3. Unzip the WDT installation zip into the build context root. Remove all the `weblogic-deploy/bin/*.cmd` files which are not used in Unix environment.
4. Create a `models` directory under the `/tmp/cm-image` directory.
5. Copy the WDT model YAML files and properties files in `/tmp/mii-sample/model-images/model-in-image__WLS-CM-v1` directory to `/tmp/cm-image/models` directory.
6. Stage the application archive ZIP in `/tmp/mii-sample/model-images/model-in-image__WLS-CM-v1` directory and copy the archive file to `/tmp/cm-image/models` directory.
  - Run the following commands to create your application archive ZIP file and put it in the expected directory:
    ```
    # Delete existing archive.zip in case we have an old leftover version
    ```
    ```shell
    $ rm -f /tmp/mii-sample/model-images/model-in-image__WLS-CM-v1/archive.zip
    ```
    ```
    # Move to the directory which contains the source files for our archive
    ```
    ```shell
    $ cd /tmp/mii-sample/archives/archive-v1
    ```
    ```
    # Zip the archive to the location will later use when we use docker to build the common mount image.
    ```
    ```shell
    $ zip -r /tmp/mii-sample/model-images/model-in-image__WLS-CM-v1/archive.zip wlsdeploy
    ```
7. Copy the archive ZIP file from `/tmp/mii-sample/model-images/model-in-image__WLS-CM-v1` directory to `/tmp/cm-image/models` directory..
8. Build the docker image by running `docker build --build-arg COMMON_MOUNT_PATH=/common --build-arg WDT_MODEL_HOME=/common/models --build-arg WDT_INSTALL_HOME=/common/weblogic-deploy --tag model-in-image:v1 .` command. 
9. Optionally, you can customize the values of COMMON_MOUNT_PATH, WDT_MODEL_HOME or WDT_INSTALL_HOME build args or use additional docker build-arg as necessary to override the defaults by using --build-arg=NAME=VALUE. See file `/tmp/cm-image/Dockerfile` for an explanation of each --build-arg.

Once the image is created, it will have the WDT executables copied to `/${MOUNT_PATH}/weblogic-deploy`, and all the WDT models, variables, and archives are copied to `/${MOUNT_PATH}/models`. If you use the default mount path '/common', you can verify the contents of the image using the following commands:

  ```shell
  $ docker run -it --rm model-in-image:v1 ls -l /common
    total 8
    drwxr-xr-x    1 oracle   root          4096 Jun  1 21:53 models
    drwxr-xr-x    1 oracle   root          4096 May 26 22:29 weblogic-deploy
  ```

  ```shell
  $ docker run -it --rm model-in-image:v1 ls -l /common/models
    total 16
    -rw-rw-r--    1 oracle   root          5112 Jun  1 21:52 archive.zip
    -rw-rw-r--    1 oracle   root           173 Jun  1 21:59 model.10.properties
    -rw-rw-r--    1 oracle   root          1515 Jun  1 21:59 model.10.yaml

  ```

  ```shell
  $ docker run -it --rm model-in-image:v1 ls -l /common/weblogic-deploy
    total 28
    -rw-r-----    1 oracle   root          4673 Oct 22  2019 LICENSE.txt
    -rw-r-----    1 oracle   root            30 May 25 11:40 VERSION.txt
    drwxr-x---    1 oracle   root          4096 May 26 22:29 bin
    drwxr-x---    1 oracle   root          4096 May 25 11:40 etc
    drwxr-x---    1 oracle   root          4096 May 25 11:40 lib
    drwxr-x---    1 oracle   root          4096 Jan 22  2019 samples

  ```

#### Prepare and Apply the Domain Resource
Copy the following to a file called `/tmp/mii-sample/mii-initial.yaml` or similar, or use the file `/tmp/mii-sample/domain-resources/WLS-CM/mii-initial-d1-WLS-CM-v1.yaml` that is included in the sample source to create the new domain using common mounts.

  {{%expand "Click here to view the WLS Domain YAML file using the common mounts feature." %}}
  ```yaml
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
    
      # settings for common mount volume, see also 'serverPod.commonMounts'.
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
    
        # Settings for common mounts with images containing model, archives and WDT instal. See also 'spec.commonMountVolumes'.
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

You can compare this domain resource YAML file with the domain resource YAML file from the initial use case (`/tmp/mii-sample/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml`) to see the changes required for the common mounts option. For example:

```
$ diff /tmp/mii-sample/domain-resources/WLS-CM/mii-initial-d1-WLS-CM-v1.yaml /tmp/mii-sample/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml
23c23
<   image: "container-registry.oracle.com/middleware/weblogic:12.2.1.4"
---
>   image: "model-in-image:WLS-v1"
53,57d52
<   # settings for common mount volume, see also 'serverPod.commonMounts'.
<   commonMountVolumes:
<   - name: commonMountsVolume1
<     mountPath: "/common"
<
75,80d69
<     # Settings for common mounts with images containing model, archives and WDT instal. See also 'spec.commonMountVolumes'.
<     commonMounts:
<     - image: "model-in-image:WLS-CM-v1"
<       imagePullPolicy: IfNotPresent
<       volume: commonMountsVolume1
<
142,143d130
<       modelHome: "/common/models"
<       wdtInstallHome: "/common/weblogic-deploy"
```

If you created your own YAML file, then you can make the required changes for the common mounts option and create the new domain using the modified YAML file.

Run the following command to create the domain custom resource:

```shell
$ kubectl apply -f /tmp/mii-sample/domain-resources/WLS-CM/mii-initial-d1-WLS-CM-v1.yaml
```
  > Note: If you are choosing _not_ to use the predefined Domain YAML file and instead created your own Domain YAML file earlier, then substitute your custom file name in the above command. Previously, we suggested naming it `/tmp/mii-sample/mii-initial.yaml`.

  If you run `kubectl get pods -n sample-domain1-ns --watch`, then you will see the introspector job run and your WebLogic Server pods start. The output will look something like this:

  {{%expand "Click here to expand." %}}
  ```shell
  $ kubectl get pods -n sample-domain1-ns --watch
  ```
  ```
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

Alternatively, you can run `/tmp/mii-sample/utils/wl-pod-wait.sh -p 3`. This is a utility script that provides useful information about a domain's pods and waits for them to reach a `ready` state, reach their target `restartVersion`, and reach their target `image` before exiting.

  {{%expand "Click here to display the `wl-pod-wait.sh` usage." %}}
  ```shell
    $ ./wl-pod-wait.sh -?
    ```
    ```
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

  {{%expand "Click here to view sample output from `wl-pod-wait.sh`." %}}
  ```
  @@ [2021-05-26T22:31:49][seconds=0] Info: Waiting up to 1000 seconds for exactly '3' WebLogic Server pods to reach the following criteria:
  @@ [2021-05-26T22:31:49][seconds=0] Info:   ready='true'
  @@ [2021-05-26T22:31:49][seconds=0] Info:   image='container-registry.oracle.com/middleware/weblogic:12.2.1.4'
  @@ [2021-05-26T22:31:49][seconds=0] Info:   domainRestartVersion='1'
  @@ [2021-05-26T22:31:49][seconds=0] Info:   introspectVersion='1'
  @@ [2021-05-26T22:31:49][seconds=0] Info:   namespace='sample-domain1-ns'
  @@ [2021-05-26T22:31:49][seconds=0] Info:   domainUID='sample-domain1'
  
  @@ [2021-05-26T22:31:49][seconds=0] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-05-26T22:31:49][seconds=0] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                                 RVERSION  IVERSION  IMAGE  READY  PHASE
  -----------------------------------  --------  --------  -----  -----  ---------
  'sample-domain1-introspector-l7nql'  ''        ''        ''     ''     'Pending'
  
  @@ [2021-05-26T22:31:52][seconds=3] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-05-26T22:31:52][seconds=3] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                                 RVERSION  IVERSION  IMAGE  READY  PHASE
  -----------------------------------  --------  --------  -----  -----  ---------
  'sample-domain1-introspector-l7nql'  ''        ''        ''     ''     'Running'
  
  @@ [2021-05-26T22:33:01][seconds=72] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-05-26T22:33:01][seconds=72] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                                 RVERSION  IVERSION  IMAGE                                                         READY    PHASE
  -----------------------------------  --------  --------  ------------------------------------------------------------  -------  -----------
  'sample-domain1-admin-server'        '1'       '1'       'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'false'  'Pending'
  'sample-domain1-introspector-l7nql'  ''        ''        ''                                                            ''       'Succeeded'
  
  @@ [2021-05-26T22:33:03][seconds=74] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-05-26T22:33:03][seconds=74] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                           RVERSION  IVERSION  IMAGE                                                         READY    PHASE
  -----------------------------  --------  --------  ------------------------------------------------------------  -------  ---------
  'sample-domain1-admin-server'  '1'       '1'       'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'false'  'Pending'
  
  @@ [2021-05-26T22:33:04][seconds=75] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-05-26T22:33:04][seconds=75] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                           RVERSION  IVERSION  IMAGE                                                         READY    PHASE
  -----------------------------  --------  --------  ------------------------------------------------------------  -------  ---------
  'sample-domain1-admin-server'  '1'       '1'       'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'false'  'Running'
  
  @@ [2021-05-26T22:33:36][seconds=107] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-05-26T22:33:36][seconds=107] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                              RVERSION  IVERSION  IMAGE                                                         READY    PHASE
  --------------------------------  --------  --------  ------------------------------------------------------------  -------  ---------
  'sample-domain1-admin-server'     '1'       '1'       'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'       '1'       'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'false'  'Pending'
  'sample-domain1-managed-server2'  '1'       '1'       'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'false'  'Pending'
  
  @@ [2021-05-26T22:33:39][seconds=110] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-05-26T22:33:39][seconds=110] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                              RVERSION  IVERSION  IMAGE                                                         READY    PHASE
  --------------------------------  --------  --------  ------------------------------------------------------------  -------  ---------
  'sample-domain1-admin-server'     '1'       '1'       'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'       '1'       'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'false'  'Running'
  'sample-domain1-managed-server2'  '1'       '1'       'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'false'  'Running'
  
  @@ [2021-05-26T22:34:20][seconds=151] Info: '3' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2021-05-26T22:34:20][seconds=151] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:
  
  NAME                              RVERSION  IVERSION  IMAGE                                                         READY   PHASE
  --------------------------------  --------  --------  ------------------------------------------------------------  ------  ---------
  'sample-domain1-admin-server'     '1'       '1'       'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'true'  'Running'
  'sample-domain1-managed-server1'  '1'       '1'       'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'true'  'Running'
  'sample-domain1-managed-server2'  '1'       '1'       'container-registry.oracle.com/middleware/weblogic:12.2.1.4'  'true'  'Running'
  
  
  @@ [2021-05-26T22:34:20][seconds=151] Info: Success!
  ```
  {{% /expand %}}



If you see an error, then consult [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) in the Model in Image user guide.

#### Invoke the web application
You can follow the same steps as described in [Invoke the web application](/weblogic-kubernetes-operator/samples/simple/domains/model-in-image/initial/#invoke-the-web-application) section of the initial use case to invoke the web application. 
