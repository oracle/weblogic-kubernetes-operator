+++
title = "Initialize Domain On PV"
date = 2023-04-26T16:45:16-05:00
weight = 25
pre = "<b> </b>"
description = "Initialize domain on PV."
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
[schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md#auxiliary-image).

- For a basic configuration example, see [Configuration example 1](#example-1-basic-configuration).

#### WebLogic base image

Since the domain will be created on a persistent volume.  The main image will not have any domain created in it.  

```
spec:
  image: "container-registry.oracle.com/middleware/fmw-infrastructure_cpu:12.2.1.4-jdk8-ol8-221014"
```

You can specify your own image, reference a patched image in `container-registry.oracle.com`, or create and patch
a image using `WebLogic Image Tool`


#### Persistent Volume and Persistent Volume Claim

The `Persistent Volume` and `Persistent Volume Claim` is used by Kubernetes to mount shared persistent storage.
Specify the specification of the `Persistent Volume` and `Persistent Volume Claim` in the domain resource YAML.  For example,

The operator will creates the `PV` and `PVC` and mount the persistent volume to `/share`

```
spec:
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

The operator will use existing `PV` and `PVC` and mount the persistent volume to `/share`

```
spec:
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

#### Domain information

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

`domainType`:  specify this is a JRF domain
`createIfNotExists`:  specify for Operator to create RCU schema first before creating the `JRF` domain.  If you specify `domain` then it will not just use existing RCU schema and will not create it.
`domainCreationImages` specify the image containing the WDT binaries and WDT artifacts describing the domain configuration.
`domainCreationConfigMap` specify an optional configmap containing extra WDT models, they can be used to add/modify models that are in the `domainCreationImages`.
`opss` specify a secret password for extracting `OPSS` wallet encryption key for `JRF` domain.  See (link for details)

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

