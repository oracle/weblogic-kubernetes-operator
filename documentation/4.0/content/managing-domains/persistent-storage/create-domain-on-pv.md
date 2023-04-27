+++
title = "Creating domain on persistent volume"
date = 2023-04-26T16:45:16-05:00
weight = 25
pre = "<b> </b>"
description = "Creating domain on PV."
+++

{{< table_of_contents >}}

### Introduction

Domain on persistent volume require the domain home exists on a persistent volume,  this can be done either manually 
or automated by specifying a section `domain.spec.configuration.initializeDomainOnPV` in the domain resource specification.

**The `domain.spec.configuration.initializeDomainOnPV` section provides a one time only domain home initialization,  the Operator will create the domain when the domain resource is first deployed, once the domain is created, this section will be ignored, subsequent domain updates should be controlled by
WebLogic console, WLST or other mechanism.**  

The `initializeDomainOnPv` provides the following functionalities:

- Create the `PersistentVolume` and/or `PersistenVolumeClaim` if requested.
- Create `JRF RCU schema` if requested.
- Create the domain home based on provided WDT models on the persistent volume.  (TODO: link needed)

### Configuration

Beginning with operator version 4.1.0, you can provide a section `domain.spec.configuraiton.initializeDomainOnPV` to initialize a WebLogic Domain on persistent volume when it is first deployed.
This is a **one time** only initialization, once the domain is created, subsequent updates to the domain resource YAML will not re-create or update the 
WebLogic domain.

In order to use this feature, provide the following information:

- WebLogic base image.  This is the WebLogic product to be used.
- `PersistentVolume` and `PersistentVolumeClaim`.   This is environment specific and usually require assistance from your administrator to provide the underlying details such as `storageClass` or any permissions.
- Domain information.  This describes the domain type, whether the Operator should create the RCU schema, and the domain image providing WDT binaries and WDT artifacts describing the domain.
- Domain resource YAML.  This is for deploying the domain in WebLogic Kubernetes Operator.


- For details about each field, see the
[schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md#initialize-domain-on-pv).

- For a basic configuration example, see [Configuration example 1](#example-1-basic-configuration).

#### WebLogic base image

Since the domain will be created on a persistent volume.  The main image should only contains the WebLogic product binary and `JDK`.  

```
spec:
  image: "container-registry.oracle.com/middleware/fmw-infrastructure_cpu:12.2.1.4-jdk8-ol8-221014"
```

You can specify your own image, reference a patched image in `container-registry.oracle.com`, or create and patch
a image using [WebLogic Image tool](https://github.com/oracle/weblogic-image-tool)


#### Persistent Volume and Persistent Volume Claim

The Kubernetes PersistentVolume (PV) and PersistentVolumeClaim (PVC) is used by Kubernetes to access
persistent storage in the Kubernetes environment. You can either use an existing (PV/PVC) or request Operator to create them 
for you.

The specifications of `PersistentVolume` and `PersistentVolumeClaim` is environment specific and often requires information 
from your Kubernetes cluster administrator to provide the information. See [PV and PVC in different environments](#references)

For example, specify the specification of the `Persistent Volume` and `Persistent Volume Claim` in the domain resource YAML.  

The operator will create the `PV` and `PVC` and mount the persistent volume to the `/share` directory.

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

For ths list of supported fields in `persistentVolume` and `persistentVolumeClaim`. (TODO: fix the link) 
See [supported fields](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md#initialize-domain-on-pv).

If the `PV` and `PVC` already existed your environment, you do not need
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

For `JRF` based domain, before proceeding, please be sure to visit [JRF domain]({{< relref "/managing-domains/persistent-storage/initial-domain-on-pv" >}}).

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

| Field                     | Notes                                                                                | Values                                          | Required                                                            |
|---------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------|---------------------------------------------------------------------|
| domainType                | Type of the domain creating                                                          | JRF or WLS                                      | N (default WLS)                                                     |
| createIfNotExists         | Specify whether the Operator to create `RCU schema` first before creating the domain | domain or domainAndRCU (create RCU first)       | N (default domain)                                                  |
| domainCreationImages      | WDT domain images                                                                    | Array of image                                  | Y                                                                   |
| domainCreationConfigMap   | Optional configmap containing extra WDT models                                       | Kubernetes ConfigMap name                       | N                                                                   |
| osss.walletPasswordSecret | Password for extracting `OPSS` wallet encryption key for `JRF` domain.               | Kuberntes secret name with key `walletPassword` | Y                                                                   |
| osss.walletFileSecret     | Extracted `OPSS wallet` file.                                                        | Kuberntes secret name with key `walletFile`     | N (Only needed when recreating the domain during disaster recovery) |


### WebLogic Deploy Tooling models

WDT models are a convenient and simple alternative to WebLogic Scripting Tool (WLST)
configuration scripts and templates.
They compactly define a WebLogic domain using YAML files and support including
application archives in a ZIP file. The WDT model format is fully described in the open source,
[WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) GitHub project.

### Diagnostics

1. Error in introspector job.  You can check the domain status of the domain 

```
kubectl -n <domain namespace> get domain <domain uid>
```
2. Check the operator log.  Additional error messages may be available in the Operator log.
```
kubectl -n <operator namespace> logs <operator pod name>
```
3. Updated the WDT models but changes are not reflected in the domain  This is the expected behavior. The WDT domain models specified in the domain image or configmap
is a **one time** only operation, they are only used for creating the initial domain, after the domain is created, they are not designed to participate in the lifecycle operations. 
You should use WebLogic console, WLST, or other means to update the domain.  In order to use the updated models to recreate the domain, you must delete the domain home directory and 
also the applications directory for JRF domain (`applications/<domain uid>` under the parent of the domain home directory) before trying to create the domain again.

### Clean up

If you need to delete the domain home to recover from an error or recreate the domain home, you can either:

1. Delete the `PVC` if the underlying storage volume is dynamically allocated and with `ReclaimPolcy: delete` and recreate the `PVC`
2. Attach a pod to the shared volume and the access the pod to remove the contents.  There is a sample script
[Domain on PV helper shell script](https://orahub.oci.oraclecorp.com/weblogic-cloud/weblogic-kubernete

Then finally delete the domain resource.

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

