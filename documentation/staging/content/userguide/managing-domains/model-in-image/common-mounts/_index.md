+++
title = "Common Mounts"
date = 2019-02-23T16:45:16-05:00
weight = 70
pre = "<b> </b>"
+++

#### Contents

 - [Introduction](#introduction)
 - [Configuration](#configuration)
 - [Common Mounts Fields](#common-mounts-fields)
 - [Common Mount Volumes Fields](#common-mount-volumes-fields)
 - [Running Model in Image initial use case using common mounts](#running-model-in-image-initial-use-case-using-common-mounts)
    - [Creating the common mounts image](#creating-the-common-mounts-image)
    - [Domain resource](#domain-resource)

### Introduction
Use common mounts to automatically include the directory content from additional images. This is a useful alternative for including Model in Image model files, or other types of files, in a Pod without requiring modifications to the Pod's base image specified using `domain.spec.image`. This feature internally uses a Kubernetes [emptyDir](https://kubernetes.io/docs/concepts/storage/volumes/#emptydir) volume and Kubernetes [init containers](https://kubernetes.io/docs/concepts/workloads/pods/init-containers/) to share the files from the additional images with the Pod.

When using the common mounts feature with the Model In Image use case, the WebLogic image would be the Domain resource's `domain.spec.image` field. The model files, application archives, and the WDT installation files can be provided in a separate image specified as part of the common mounts. The application archive could also be provided by a second container image in the common mounts section. Specific advantages of the common mounts feature for Model In Image domains are:
  - Use a WebLogic install image, or patch a WebLogic install image, without needing to include model artifacts within the image.
  - Share one large WebLogic install image with multiple different model configurations.
  - Distribute model files, application archives, and WebLogic Deploy Tooling executable using very small images instead of a large image that also contains a WebLogic install.

### Configuration
Here's an example configuration for the Common Mounts. 

```
  serverPod:
    commonMounts:
    - image: model-in-image:v1
      imagePullPolicy: IfNotPresent
      command: cp -R $COMMON_MOUNT_PATH/* $COMMON_MOUNT_TARGET_PATH
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


#### Common Mounts Fields
Use the below `kubectl explain` command or see the Domain Resource [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md) and [documentation]({{< relref "/userguide/managing-domains/domain-resource.md" >}}) for a full list and the description of the common mounts fields.

```shell
kubectl explain domain.spec.serverPod.commonMounts
```

#### Common Mount Volumes Fields
Use the below `kubectl explain` command or see the Domain Resource [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md) and [documentation]({{< relref "/userguide/managing-domains/domain-resource.md" >}}) for a full list and the description of the common mount volumes fields.

```shell
$ kubectl explain domain.spec.commonMountVolumes
```

### Running Model in Image initial use case using common mounts
The initial use case for the Model in Image is described [here](/weblogic-kubernetes-operator/samples/simple/domains/model-in-image/initial/). The goal for this section is to create the initial domain using the common mounts feature where you provide the WDT model files and archive ZIP file in a separate container image.

You will begin by following the steps described in the [initial use case sample](/weblogic-kubernetes-operator/samples/simple/domains/model-in-image/initial/). Once you have completed the entire use case, you will have the initial use case resources deployed and a running domain. To run the initial use case with common mounts, delete the existing domain by running the `kubectl delete domain sample-domain1 -n sample-domain1-ns` command to bring down the domain. Afterward, run the steps described in the following section to create the common mounts image that will host the Model in Image model files and other related files. Once the image is created, execute the steps in the [Domain Resource](#domain-resource) section below to create the new domain. 

#### Creating the common mounts image 
Run the following steps to create the common mounts image containing Model In Image model files, application archives, and the WDT installation files.
1. Create a temporary directory for docker build context `/tmp/cm-image`.
2. Copy the Dockefile in `kubernetes/samples/scripts/create-weblogic-domain/model-in-image/cm-docker-file` directory to the context root.
3. Unzip the WDT executable to the context root. Remove all the `weblogic-deploy/bin/*.cmd` files which are not used in Unix environment.
4. Copy all WDT models, variables, and archives into the models directory under the context root i.e. under `/tmp/cm-image/models`.
5. Build the docker image by running the command `docker build --build-arg MOUNT_PATH=/common --tag model-in-image:v1 .`.
6. Optionally, you can customize the mount path by using MOUNT_PATH build argument (it defaults to /common). 

Once the image is created, it will have the WDT executables copied to `/${MOUNT_PATH}/weblogic-deploy`, and all the WDT models, variables, and archives are copied to `/${MOUNT_PATH}/models`. If you use the default mount path '/common', you can verify the contents of the image using the following commands:

  ```shell
  $ docker run -it --rm model-in-image:v1 ls -l /common
  ```

  ```shell
  $ docker run -it --rm model-in-image:v1 ls -l /common/models
  ```

  ```shell
  $ docker run -it --rm model-in-image:v1 ls -l /common/weblogic-deploy
  ```

#### Domain Resource
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

You can compare this domain resource YAML file with the domain resource YAML file from the initial use case (`/tmp/mii-sample/domain-resources/WLS/mii-initial-d1-WLS-v1.yaml`) to see the changes required for the common mounts option. If you created your own YAML file, then you can make the required changes for the common mounts option and create the new domain using the modified YAML file.

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
  NAME                                         READY   STATUS    RESTARTS   AGE
  sample-domain1-introspector-lqqj9   0/1   Pending   0     0s
  sample-domain1-introspector-lqqj9   0/1   ContainerCreating   0     0s
  sample-domain1-introspector-lqqj9   1/1   Running   0     1s
  sample-domain1-introspector-lqqj9   0/1   Completed   0     65s
  sample-domain1-introspector-lqqj9   0/1   Terminating   0     65s
  sample-domain1-admin-server   0/1   Pending   0     0s
  sample-domain1-admin-server   0/1   ContainerCreating   0     0s
  sample-domain1-admin-server   0/1   Running   0     1s
  sample-domain1-admin-server   1/1   Running   0     32s
  sample-domain1-managed-server1   0/1   Pending   0     0s
  sample-domain1-managed-server2   0/1   Pending   0     0s
  sample-domain1-managed-server1   0/1   ContainerCreating   0     0s
  sample-domain1-managed-server2   0/1   ContainerCreating   0     0s
  sample-domain1-managed-server1   0/1   Running   0     2s
  sample-domain1-managed-server2   0/1   Running   0     2s
  sample-domain1-managed-server1   1/1   Running   0     43s
  sample-domain1-managed-server2   1/1   Running   0     42s
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
  @@ [2020-04-30T13:50:42][seconds=0] Info: Waiting up to 1000 seconds for exactly '3' WebLogic Server pods to reach the following criteria:
  @@ [2020-04-30T13:50:42][seconds=0] Info:   ready='true'
  @@ [2020-04-30T13:50:42][seconds=0] Info:   image='model-in-image:WLS-v1'
  @@ [2020-04-30T13:50:42][seconds=0] Info:   domainRestartVersion='1'
  @@ [2020-04-30T13:50:42][seconds=0] Info:   namespace='sample-domain1-ns'
  @@ [2020-04-30T13:50:42][seconds=0] Info:   domainUID='sample-domain1'

  @@ [2020-04-30T13:50:42][seconds=0] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:50:42][seconds=0] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

  NAME                                          VERSION  IMAGE  READY  PHASE
  --------------------------------------------  -------  -----  -----  ---------
  'sample-domain1-introspector-rkdkg'           ''       ''     ''     'Pending'

  @@ [2020-04-30T13:50:45][seconds=3] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:50:45][seconds=3] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

  NAME                                          VERSION  IMAGE  READY  PHASE
  --------------------------------------------  -------  -----  -----  ---------
  'sample-domain1-introspector-rkdkg'           ''       ''     ''     'Running'


  @@ [2020-04-30T13:51:50][seconds=68] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:51:50][seconds=68] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

  NAME  VERSION  IMAGE  READY  PHASE
  ----  -------  -----  -----  -----

  @@ [2020-04-30T13:51:59][seconds=77] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:51:59][seconds=77] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

  NAME                           VERSION  IMAGE                    READY    PHASE
  -----------------------------  -------  -----------------------  -------  ---------
  'sample-domain1-admin-server'  '1'      'model-in-image:WLS-v1'  'false'  'Pending'

  @@ [2020-04-30T13:52:02][seconds=80] Info: '0' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:52:02][seconds=80] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

  NAME                           VERSION  IMAGE                    READY    PHASE
  -----------------------------  -------  -----------------------  -------  ---------
  'sample-domain1-admin-server'  '1'      'model-in-image:WLS-v1'  'false'  'Running'

  @@ [2020-04-30T13:52:32][seconds=110] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:52:32][seconds=110] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

  NAME                              VERSION  IMAGE                    READY    PHASE
  --------------------------------  -------  -----------------------  -------  ---------
  'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v1'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'false'  'Pending'
  'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'false'  'Pending'

  @@ [2020-04-30T13:52:34][seconds=112] Info: '1' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:52:34][seconds=112] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

  NAME                              VERSION  IMAGE                    READY    PHASE
  --------------------------------  -------  -----------------------  -------  ---------
  'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v1'  'true'   'Running'
  'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'false'  'Running'
  'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'false'  'Running'

  @@ [2020-04-30T13:53:14][seconds=152] Info: '3' WebLogic Server pods currently match all criteria, expecting '3'.
  @@ [2020-04-30T13:53:14][seconds=152] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:

  NAME                              VERSION  IMAGE                    READY   PHASE
  --------------------------------  -------  -----------------------  ------  ---------
  'sample-domain1-admin-server'     '1'      'model-in-image:WLS-v1'  'true'  'Running'
  'sample-domain1-managed-server1'  '1'      'model-in-image:WLS-v1'  'true'  'Running'
  'sample-domain1-managed-server2'  '1'      'model-in-image:WLS-v1'  'true'  'Running'


  @@ [2020-04-30T13:53:14][seconds=152] Info: Success!

  ```
  {{% /expand %}}

If you see an error, then consult [Debugging]({{< relref "/userguide/managing-domains/model-in-image/debugging.md" >}}) in the Model in Image user guide.

#### Invoke the web application
You can follow the same steps as described in [Invoke the web application](#/weblogic-kubernetes-operator/samples/simple/domains/model-in-image/initial/#invoke-the-web-application) section of the initial use case to invoke the web application. 
