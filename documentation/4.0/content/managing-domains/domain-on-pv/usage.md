+++
title = "Usage"
date = 2023-04-26T16:45:16-05:00
weight = 25
pre = "<b> </b>"
description = "Steps in using domain on PV."
+++

This document describes what's needed to create and deploy a typical Domain on persistent volume domain.

{{< table_of_contents >}}

### WebLogic Kubernetes Operator

Deploy the operator and ensure that it is monitoring the desired namespace for your Model in Image domain. See [Manage operators]({{< relref "/managing-operators/_index.md" >}}) and [Quick Start]({{< relref "/quickstart/_index.md" >}}).


### Configuration

Beginning with operator version 4.1.0, you can provide a section `domain.spec.configuraiton.initializeDomainOnPV` to initialize a WebLogic Domain on persistent volume when it is first deployed.
This is a **one time** only initialization, once the domain is created, subsequent updates to the domain resource YAML will not re-create or update the 
WebLogic domain.

In order to use this feature, provide the following information:

- [WebLogic base image](#weblogic-base-image).  This is the WebLogic product to be used.
- [Volumes and VolumeMounts information](#volumes-and-volumemounts-information).  This follows the standard Kubernetes pod requirements for mounting persistent storage.
- [PersistentVolume and PersistentVolumeClaim](#persistent-volume-and-persistent-volume-claim).   This is environment specific and usually require assistance from your administrator to provide the underlying details such as `storageClass` or any permissions.
- [Domain information](#domain-information).  This describes the domain type, whether the Operator should create the RCU schema. 
- [Domain WDT artifacts](#domain-creation-wdt-artifacts).  This is where the WDT binaries and WDT artifacts reside.
- [Optional WDT artifacts ConfigMap](#optional-wdt-artifacts-config-map).  Optional WDT artifacts.
- [Domain resource YAML]({{< relref "/reference/domain-resource.md">}}).  This is for deploying the domain in WebLogic Kubernetes Operator.


- For details about each field, see the
[schema TODO LINK].

  
- For a basic configuration example, see [Configuration example 1](#example-1-basic-configuration).

#### WebLogic base image

Since the domain will be created on a persistent volume.  The base image should only contain the WebLogic product binary and `JDK`.  

```
spec:
  image: "container-registry.oracle.com/middleware/fmw-infrastructure_cpu:12.2.1.4-jdk8-ol8-221014"
```

You can specify your own image, use a patched image from `container-registry.oracle.com`, or create and patch using
[WebLogic Image tool](https://github.com/oracle/weblogic-image-tool)

#### Domain creation WDT artifacts

You can specify an image that describe the domain topology, resources, and applications in the domain resource YAML.

```
     domain:
          domainCreationImages:
            - image: 'mymodel-domain:v1'
```

In this image(s), you provide the [WebLogic Deploy Tooling Binaries](https://github.com/oracle/weblogic-deploy-tooling/releases),
and also the `WDT` artifacts.  The Operator will use the tool and the `WDT` artifacts to create the initial domain.

The image layout follows this directory structure:

```
/auxiliary/weblogic-delpoy - unzipped WebLogic Deploy Tooling release file
/auxiliary/models -  All WDT artifacts such as model YAML, model properties, model archives
```

You can create your own image using your familiar method or use [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool)

For example, using `WebLogic Image Tool`,  since the file structure is the same as `Auxiliary Image` in `Model in image`, you can 
use the same command `createAuxImage`.

```
imagetool.sh createAuxImage --wdtArchive /home/acme/myapp/wdt/myapp.zip \
   --wdtVersion latest \
   --wdtModel /home/acme/myapp/wdt/model1.yaml \
   --wdtVariables /home/acme/myapp/wdt/model1.properties \
   --tag mydomain-image:v1   
```

#### Optional WDT artifacts config map

You can optionally provide a Kubernetes `ConfigMap` with additional `WDT` artifacts as supplements or overrides to
those in the `domainCreationImages`.

```
     domain:
          domainCreationImages:
            - image: 'mymodel-domain:v1'
          domainCreationConfigMap: mymodle-domain-configmap 
```

The files inside this ConfigMap must have file extensions `.yaml`, `.properties`, or `.zip`.

#### Volumes and VolumeMounts information

You must provide the `volumes` and `volumeMounts` information in `domain.spec.serverPod`, this allows the pod to mount the persistent
storage in runtime.  The `mountPath` needs to be part of the domain home and log home,  `persistentVolumeClaim.claimName` needs to 
be a valid `PVC` name whether it is a pre-existing `PVC` or one to be created by the operator.  See [Creating PVC by the operator](#persistent-volume-and-persistent-volume-claim)

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
persistent storage in the Kubernetes environment. You can either use an existing (PV/PVC) or request Operator to create them 
for you.

The specifications of `PersistentVolume` and `PersistentVolumeClaim` is environment specific and often requires information 
from your Kubernetes cluster administrator to provide the information. See [PV and PVC in different environments](#references)

For example, specify the specification of the `Persistent Volume` and `Persistent Volume Claim` in the domain resource YAML,  
the Operator will create the `PV` and `PVC` and mount the persistent volume to the `/share` directory.

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
| waitForPvcToBind      | Specify whether operator will wait for the `PVC` to `bound` state when it is creating it. In some cloud environment, the `PVC` is in `pending` state until it is actually used in a running pod, set it to `false` if necessary. | boolean                                                                     | N (deault true)                                          |
| persistentVolume      | Specification of the persistent volume if operator is creating it                                                                                                                                                                | specification of persistent volume if you want operator to create it)       | N  (operator will not create any persistent volume)      |
| persistentVolumeClaim | Specification of the persistent volume claim if operator is creating it                                                                                                                                                          | specification of persistent volume claim if you want operator to create it) | N (operator will not create any persistent volume claim) |


Not all the fields in standard Kubernetes `PV` and `PVC` are supported.  For the list of supported fields in `persistentVolume` and `persistentVolumeClaim`. 
See [supported fields TODO LINK].

If the `PV` and `PVC` already existed in your environment, you do not need
to specify any `persistentVolume` or `persistentVolumeClaim`  under `intializedDomainOnPV` section.

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
     ....
```

#### References

[Oracle Kubernetes Engine Persistent Storage](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingpersistentvolumeclaim.htm)
[Azure Kubernetes Service Persistent Storage](https://learn.microsoft.com/en-us/azure/aks/concepts-storage)
[Amazon Kubernetes Service Persistent Storage](https://aws.amazon.com/blogs/storage/persistent-storage-for-kubernetes/)
[OpenShift Persistent Storage](https://docs.openshift.com/container-platform/4.12/storage/understanding-persistent-storage.html)

#### Domain information

For `JRF` based domain, before proceeding, please be sure to read this first [JRF domain]({{< relref "/managing-domains/domain-on-pv/jrf-domain.md">}}).

This is the section describing the WebLogic Domain. For example,

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
            - image: 'myaux:v1'
          domainType: JRF
          domainCreationConfigMap: sample-domain1-wdt-config-map
          opss:
           walletPasswordSecret: sample-domain1-opss-wallet-password-secret
```

| Field                     | Notes                                                                                | Values                                                                  | Required                                                            |
|---------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------|---------------------------------------------------------------------|
| domainType                | Type of the domain creating                                                          | JRF or WLS                                                              | N (default WLS)                                                     |
| createIfNotExists         | Specify whether the Operator to create `RCU schema` first before creating the domain | domain or domainAndRCU (drop existing RCU schema and create RCU schema) | N (default domain)                                                  |
| domainCreationImages      | WDT domain images                                                                    | Array of image                                                          | Y                                                                   |
| domainCreationConfigMap   | Optional configmap containing extra WDT models                                       | Kubernetes ConfigMap name                                               | N                                                                   |
| osss.walletPasswordSecret | Password for extracting `OPSS` wallet encryption key for `JRF` domain.               | Kubernetes secret name with key `walletPassword`                        | Y                                                                   |
| osss.walletFileSecret     | Extracted `OPSS wallet` file.                                                        | Kubernetes secret name with key `walletFile`                            | N (Only needed when recreating the domain during disaster recovery) |


### WebLogic Deploy Tooling models

WDT models are a convenient and simple alternative to WebLogic Scripting Tool (WLST)
configuration scripts and templates.
They compactly define a WebLogic domain using YAML files and support including
application archives in a ZIP file. The WDT model format is fully described in the open source,
[WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) GitHub project.

See [Working with WDT models in operator]({{< relref "/managing-domains/working-with-wdt-models/model-files.md">}}).

### Trouble shootings

1. Error in introspector job.  You can check the domain status of the domain.

```
kubectl -n <domain namespace> get domain <domain uid>
```

1. Check the domain log files in `logHome`.

By default, the Operator persists the log files for the introspector job, RCU logs, and WebLogic servers logs in `domain.spec.logHome`
(default: /share/logs/<domain uid>).

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

1. Check the Operator log.  Additional error messages may be available in the Operator log.
```
kubectl -n <operator namespace> logs <operator pod name>
```

1. Check the Kubernetes events.

```
kubectl -n <domain namespace> get events
```

1. Updated the WDT models but changes are not reflected in the domain  This is the expected behavior. The WDT domain models specified in the domain image or configmap
is a **one time** only operation, they are only used for creating the initial domain, after the domain is created, they are not designed to participate in the lifecycle operations. 
You should use WebLogic console, WLST, or other means to update the domain.  In order to use the updated models to recreate the domain, you must delete the domain home directory and 
also the applications directory for JRF domain (`applications/<domain uid>` under the parent of the domain home directory) before trying to create the domain again.

### Clean up

If you need to delete the domain home and the application directory for JRF domain to recover from an error or there is a need to recreate the domain home, the domain home is
in the directory specified in `domain.spec.domainHome`, and the application directory for JRF domain (if not specified in your WDT model) is in 
`<parent directory of domain home>/applications/<domain uid>`, you can:

1. Delete the `PVC` if the underlying storage volume is dynamically allocated and with `ReclaimPolcy: delete` and recreate the `PVC`
2. Attach a pod to the shared volume and the access the pod to remove the contents.  There is a sample script
[Domain on PV helper script(https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/scripts/domain-lifecycle/domain-on-pv-helper.sh)
3. Delete the domain resource.

```
kubectl -n <namespace> delete -f <domain resource YAML> 
```

### Configuration examples

The following configuration examples illustrate each of the previously described sections.

#### Example 1: Basic configuration

This example specifies the required image parameter for the auxiliary image(s); all other fields are at default values.

```
spec:
  domainHome: /share/domains/sample-domain1
  domainHomeSourceType: PersistentVolume
  image: "container-registry.oracle.com/middleware/fmw-infrastructure_cpu:12.2.1.4-jdk8-ol8-221014"
  imagePullPolicy: "IfNotPresent"
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
            - image: 'myaux:v1'
          domainType: JRF
          domainCreationConfigMap: sample-domain1-wdt-config-map
          opss:
           walletPasswordSecret: sample-domain1-opss-wallet-password-secret
```


#### Example 2: Multiple images

This example is the same as Example 1, except it configures multiple domain creation images and sets the `sourceWDTInstallHome`
for the second image to `None`.
In this case, the source location of the WebLogic Deploy Tooling installation from the second image `new-model-in-image:v1` will be ignored.

```
spec:
  configuration:
    initializeDomainOnPV:        
      domainCreationImages:
      - image: domain-images:v1
      - image: domain-addtional-images:v1
```

