+++
title = "Usage"
date = 2023-04-26T16:45:16-05:00
weight = 2
pre = "<b> </b>"
description = "Instructions for using Domain on PV."
+++

This document describes how to create and deploy a typical Domain home on persistent volume (Domain on PV).

{{< table_of_contents >}}

### WebLogic Kubernetes Operator

Deploy the operator and ensure that it is monitoring the desired namespace for your Domain on PV domain. See [Manage operators]({{< relref "/managing-operators/_index.md" >}}) and [Quick Start]({{< relref "/quickstart/_index.md" >}}).


### Configuration

Beginning with operator version 4.1.0, you can provide a section, `domain.spec.configuraiton.initializeDomainOnPV`, to initialize a WebLogic domain on a persistent volume when it is first deployed.
This is a _one time only_ initialization. After the domain is created, subsequent updates to this section in the domain resource YAML file will not recreate or update the
WebLogic domain.

To use this feature, provide the following information:

- [WebLogic base image](#weblogic-base-image) - This is the WebLogic product to be used. For example, WebLogic Server or Fusion Middleware Infrastructure.
- [Volumes and VolumeMounts information](#volumes-and-volumemounts-information) - This follows the standard Kubernetes pod requirements for mounting persistent volumes.
- [PersistentVolume and PersistentVolumeClaim](#persistent-volume-and-persistent-volume-claim) - This is environment specific and usually requires assistance from your administrator to provide the underlying details, such as `storageClass` or any permissions.
- [Domain information](#domain-information) - This describes the domain type and whether the operator should create the RCU schema.
- [Domain WDT artifacts](#domain-creation-wdt-artifacts) - This is where the WDT binaries, WDT model, WDT archive, and WDT variables files reside.
- [Optional WDT artifacts ConfigMap](#optional-wdt-artifacts-configmap) - Optional, WDT model, WDT variables files.
- [Domain resource YAML file]({{< relref "/reference/domain-resource.md">}}) - This is for deploying the domain with WebLogic Kubernetes Operator.


- For details about each field,
  - See the `initializeDomainOnPV` section
    in the domain resource
    [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md),
    or use the command `kubectl explain domain.spec.configuration.initializeDomainOnPV`

- For a basic configuration example, see [Basic configuration](#basic-configuration).

#### WebLogic base image

Because the domain will be created on a persistent volume, the base image should contain only the WebLogic product binary and JDK.  

```
spec:
  image: "container-registry.oracle.com/middleware/fmw-infrastructure_cpu:12.2.1.4-jdk8-ol8-221014"
```

You can specify your own image, use a patched image from `container-registry.oracle.com`, or create and patch an image using the
[WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool) (WIT).

#### Domain creation WDT artifacts

Specify an image that describes the domain topology, resources, and applications in the domain resource YAML file.

```
     domain:
          domainCreationImages:
            - image: 'myrepo/domain-images:v1'
```

| Field                     | Notes                                                                                | Values                                                                  | Required                                                            |
|---------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------|---------------------------------------------------------------------|
| `domainCreationImages`      | WDT domain images.                                                                    | An array of images.                                                          | Y                                |

In this image or images, you must provide the required WDT [binaries](https://github.com/oracle/weblogic-deploy-tooling/releases),
and also the WDT artifacts.  The operator will use the tool and the WDT artifacts to create the initial domain.  

For additional options in `domainCreationImages`, use the follow command to obtain the details.

```
kubectl explain domain.spec.configuration.initializeDomainOnPV.domain.domainCreationImages
```

The image layout follows this directory structure:

```
/auxiliary/weblogic-delpoy - Unzipped WebLogic Deploy Tooling release file.
/auxiliary/models -  WDT model, WDT archive, and WDT variables files.
```

You can create your own image using your familiar method or use the [WebLogic Image Tool (WIT)](https://github.com/oracle/weblogic-image-tool).

For example, because the file structure is the same as an [Auxiliary Image]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}) that's used in the Model in Image domain home type, you can
use the same WIT command `createAuxImage`.

```
$ imagetool.sh createAuxImage --wdtArchive /home/acme/myapp/wdt/myapp.zip \
   --wdtVersion latest \
   --wdtModel /home/acme/myapp/wdt/model1.yaml \
   --wdtVariables /home/acme/myapp/wdt/model1.properties \
   --tag myrepo/domain-images:v1   
```

#### Optional WDT artifacts ConfigMap

Optionally, you can provide a Kubernetes ConfigMap with additional WDT artifacts as supplements or overrides to
those in `domainCreationImages`.

```
     domain:
          ...
          domainCreationImages:
              ...
          domainCreationConfigMap: mymodel-domain-configmap
```

| Field                     | Notes                                | Values                             | Required |
|---------------------------|--------------------------------------|------------------------------------|----------|
| `domainCreationConfigMap`      | Optional WDT artifacts in ConfigMap. | ConfigMap name. | N        |

The files inside this ConfigMap must have file extensions, `.yaml`, `.properties`, or `.zip`.

#### Volumes and VolumeMounts information

You must provide the `volumes` and `volumeMounts` information in `domain.spec.serverPod`. This allows the pod to mount the persistent
storage at runtime.  The `mountPath` needs to be part of the domain home and log home and `persistentVolumeClaim.claimName` needs to
be a valid PVC name, whether it is a pre-existing PVC or one to be created by the operator.  See [Creating PVC by the operator](#persistent-volume-and-persistent-volume-claim).

```yaml
spec:
  domainHome: /share/domains/domain1
  logHome: /share/logs/domain1
  serverPod:
    volumes:
      - name: weblogic-domain-storage-volume
        persistentVolumeClaim:
          claimName: sample-domain1-pvc-rwm1
    volumeMounts:
      - mountPath: /share
        name: weblogic-domain-storage-volume
```

#### Persistent Volume and Persistent Volume Claim

The Kubernetes PersistentVolume (PV) and PersistentVolumeClaim (PVC) is used by Kubernetes to access
persistent storage in the Kubernetes environment. You can either use an existing PV/PVC or request that the operator create them
for you.

The specifications of `PersistentVolume` and `PersistentVolumeClaim` are environment specific and often require information
from your Kubernetes cluster administrator. See [Persistent Storage](#references) in different environments.

For example, if you specify the specification of the `Persistent Volume` and `Persistent Volume Claim` in the domain resource YAML file,  
then the operator will create the `PV` and `PVC`, and mount the persistent volume to the `/share` directory.

```
spec:
  domainHome: /share/domains/domain1
  serverPod:
    volumes:
    - name: weblogic-domain-storage-volume
      persistentVolumeClaim:
         claimName: sample-domain1-pvc-rwm1
    volumeMounts:
    - mountPath: /share
      name: weblogic-domain-storage-volume

  configuration:
    initializeDomainOnPV:
      waitForPvcToBind: true
      persistentVolume:
        metadata:
            name: sample-domain1-pv-rwm1
        spec:
            storageClassName: my-storage-class
            capacity:
                storage: 20Gi
            persistentVolumeReclaimPolicy: Retain
            hostPath:
                path: "/share"
      persistentVolumeClaim:
        metadata:
            name: sample-domain1-pvc-rwm1
        spec:
            storageClassName: my-storage-class
            resources:
                requests:
                    storage: 10Gi
```

| Field                 | Notes                                                                                                                                                                                                                            | Values                                                                      | Required                                                 |
|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|----------------------------------------------------------|
| `waitForPvcToBind`      | Specify whether the operator will wait for the PVC to reach the `bound` state when it is creating it. In some cloud environments, the PVC is in the `pending` state until it is actually used in a running pod; set it to `false` if necessary. | Boolean                                                                     | N (default `true`)                                          |
| `persistentVolume`      | Specification of the persistent volume if the operator is creating it.                                                                                                                                                                | Specification of the persistent volume if you want the operator to create it.       | N  (operator will not create any persistent volume)      |
| `persistentVolumeClaim` | Specification of the persistent volume claim if the operator is creating it.                                                                                                                                                          | Specification of the persistent volume claim if you want operator to create it. | N (operator will not create any persistent volume claim) |


Not all the fields in the standard Kubernetes PV and PVC are supported.  For the list of supported fields in `persistentVolume` and `persistentVolumeClaim`, see
- See the `initializeDomainOnPV.peristentVolume` and `initializeDomainOnPV.peristentVolumeClaim` section
  in the domain resource
  [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md), or use the commands
  ```
   kubectl explain domain.spec.configuration.initializeDomainOnPV.persistentVolume
   kubectl explain domain.spec.configuration.initializeDomainOnPV.persistentVolumeClaim
  ```

If the PV and PVC already exist in your environment, you do not need
to specify any `persistentVolume` or `persistentVolumeClaim`  under the `intializedDomainOnPV` section.


#### References

- [Oracle Kubernetes Engine Persistent Storage](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingpersistentvolumeclaim.htm)
- [Azure Kubernetes Service Persistent Storage](https://learn.microsoft.com/en-us/azure/aks/concepts-storage)
- [Amazon Kubernetes Service Persistent Storage](https://aws.amazon.com/blogs/storage/persistent-storage-for-kubernetes/)
- [OpenShift Persistent Storage](https://docs.openshift.com/container-platform/4.12/storage/understanding-persistent-storage.html)

#### Domain information

For JRF-based domains, before proceeding, please be sure to read this document, [JRF domains]({{< relref "/managing-domains/working-with-wdt-models/jrf-domain.md">}}).

This is the section describing the WebLogic domain. For example,

```
spec:
  domainHome: /share/domains/sample-domain1
  domainHomeSourceType: PersistentVolume

  configuration:
    secrets: [ sample-domain1-rcu-access ]
    initializeDomainOnPV:
      ...
      domain:
          # domain |  domainAndRCU
          createIfNotExists: domainAndRCU
          domainCreationImages:
            - image: 'myrepo/domain-images:v1'
          domainType: JRF
          domainCreationConfigMap: sample-domain1-wdt-config-map
          opss:
           walletPasswordSecret: sample-domain1-opss-wallet-password-secret
```

| Field                     | Notes                                                                                | Values                                                                  | Required                                                            |
|---------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------|---------------------------------------------------------------------|
| `domainType`                | Type of domain being created.                                                          | `JRF` or `WLS`                                                              | N (default `WLS`)                                           |
| `createIfNotExists`         | Specifies whether the operator should create the RCU schema first, before creating the domain. | `domain` or `domainAndRCU` (drop existing RCU schema and create new RCU schema) | N (default `domain`) |
| `domainCreationImages`      | WDT domain images.                                                                    | An array of images.                                                          | Y                                |
| `domainCreationConfigMap`   | Optional ConfigMap containing extra WDT models.                                       | Kubernetes ConfigMap name.                                               | N                                                  |
| `osss.walletPasswordSecret` | Password for extracting OPSS wallet encryption key for JRF domain.               | Kubernetes secret name with key `walletPassword`.                       | Y                                                                   |
| `osss.walletFileSecret`     | Extracted OPSS wallet file.                                                        | Kubernetes secret name with key `walletFile`.                            | N (Only needed when recreating the domain during disaster recovery) |


### Troubleshooting

**Problem**: An error in the introspector job.
- Check the domain status of the domain.
  ```
  $ kubectl -n <domain namespace> get domain <domain uid>
  ```

- Check the domain log files in `logHome`.

  By default, the operator persists the log files for the introspector job, RCU logs, and WebLogic server logs in `domain.spec.logHome`
  (default: `/share/logs/<domain uid>`).

  If there are errors, the related output files can be found in the `logHome` directory.  For example:

  ```
  admin-server.log                  
  introspector_script.out
  createDomain.log
  wdt_output.log             
  admin-server.out
  ...                 
  rculogdir/**
  ```

- Check the operator log.  Additional error messages may be available in the operator log.
  ```
  $ kubectl -n <operator namespace> logs <operator pod name>
  ```

- Check the Kubernetes events.

  ```
  $ kubectl -n <domain namespace> get events
  ```

**Problem**: Updated the WDT models but changes are not reflected in the domain.  

This is the expected behavior. The WDT domain models specified in the domain image or ConfigMap
is a **one time only** operation. They are used only for creating the initial domain. After the domain is created, they do not participate in the lifecycle operations.
You should use the WebLogic console, WLST, or other means to update the domain.  In order to use the updated models to recreate the domain, you must delete the domain home directory and
also the applications directory for the JRF domain (`applications/<domain uid>` under the parent of the domain home directory) before trying to create the domain again.

If you see any other error, then consult [Debugging]({{< relref "/managing-domains/debugging.md" >}})

### Cleanup

If you need to delete the domain home and the application directory for a JRF domain, to recover from an error or if there is a need to recreate the domain home, the domain home is
in the directory specified in `domain.spec.domainHome`, and the application directory for JRF domains (if not specified in your WDT model) is in
`<parent directory of domain home>/applications/<domain uid>`, you can:

1. If the underlying storage volume is dynamically allocated, then delete the PVC with `ReclaimPolcy: delete` and recreate the PVC.
2. Attach a pod to the shared volume and then access the pod to remove the contents.  See the sample script,
[Domain on PV helper script](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/scripts/domain-lifecycle/domain-on-pv-helper.sh).
3. Delete the domain resource.

   ```
   $ kubectl -n <namespace> delete -f <domain resource YAML>
   ```

### Configuration example

The following configuration example illustrates a basic configuration.

#### Basic configuration


```
spec:
  domainHome: /share/domains/sample-domain1
  domainHomeSourceType: PersistentVolume
  image: "container-registry.oracle.com/middleware/fmw-infrastructure_cpu:12.2.1.4-jdk8-ol8-221014"
  imagePullPolicy: "IfNotPresent"
  imagePullSecrets:
  - name: ocir-credentials
  webLogicCredentialsSecret:
    name: sample-domain1-weblogic-credentials
  logHomeEnabled: true
  logHome: /share/logs/sample-domain1
  serverPod:
    env:
    - name: JAVA_OPTIONS
      value: "-Dweblogic.StdoutDebugEnabled=false -Dweblogic.security.SSL.ignoreHostnameVerification=true -Dweblogic.security.TrustKeyStore=DemoTrust -Dweblogic.debug.DebugManagementServicesResource=true"
    - name: USER_MEM_ARGS
      value: "-XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom "
    # set up pod to use persistent storage using pvc
    # domain home must be under the mountPath  
    volumes:
    - name: weblogic-domain-storage-volume
      persistentVolumeClaim:
         claimName: sample-domain1-pvc-rwm1
    volumeMounts:
    - mountPath: /share
      name: weblogic-domain-storage-volume
  clusters:
  - name: sample-domain1-cluster-1
  replicas: 2

  configuration:
    secrets: [ sample-domain1-rcu-access ]
    initializeDomainOnPV:
      persistentVolume:
        metadata:
            name: sample-domain1-pv-rwm1
        spec:
            storageClassName: my-storage-class
            capacity:
                storage: 20Gi
            persistentVolumeReclaimPolicy: Retain
            hostPath:
                path: "/share"
      persistentVolumeClaim:
        metadata:
            name: sample-domain1-pvc-rwm1
        spec:
            storageClassName: my-storage-class
            resources:
                requests:
                    storage: 10Gi
      domain:
          # domain |  domainAndRCU
          createIfNotExists: domainAndRCU
          domainCreationImages:
            - image: 'myrepo/domain-images:v1'
          domainType: JRF
          domainCreationConfigMap: sample-domain1-wdt-config-map
          opss:
           walletPasswordSecret: sample-domain1-opss-wallet-password-secret
```
