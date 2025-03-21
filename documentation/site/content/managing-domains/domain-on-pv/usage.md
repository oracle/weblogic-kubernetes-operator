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

- [WebLogic base image](#weblogic-base-image) - This is the Fusion Middleware Software to be used, for example, WebLogic Server or Fusion Middleware Infrastructure.
- [Volumes and VolumeMounts information](#volumes-and-volumemounts-information) - This follows the standard Kubernetes pod requirements for mounting persistent volumes.
- [PersistentVolume and PersistentVolumeClaim](#persistent-volume-and-persistent-volume-claim) - This is environment specific and usually requires assistance from your administrator to provide the underlying details, such as `storageClass` or any permissions.
- [Domain information](#domain-information) - This describes the domain type and whether the operator should create the RCU schema.
- [Domain WDT models](#domain-creation-models) - This is where the WDT Home, WDT model, WDT archive, and WDT variables files reside.
- [Optional WDT models ConfigMap](#optional-wdt-models-configmap) - Optional, WDT model, WDT variables files.
- [Using WDT model encryption](#using-wdt-model-encryption) - Optional, using WDT model encryption.
- [Domain resource YAML file]({{< relref "/reference/domain-resource.md">}}) - This is for deploying the domain in WebLogic Kubernetes Operator.


For details about each field, see the `initializeDomainOnPV` section
    in the domain resource
    [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md),
    or use the command `kubectl explain domain.spec.configuration.initializeDomainOnPV`.

For a basic configuration example, see [Basic configuration](#basic-configuration).

#### WebLogic base image

Because the domain will be created on a persistent volume, the base image should contain only the FMW product binary and the JDK.  

```
spec:
  image: "container-registry.oracle.com/middleware/fmw-infrastructure_cpu:12.2.1.4-jdk8-ol8-221014"
```

You can specify your own image, use a patched image from `container-registry.oracle.com`, or create and patch an image using the
[WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool) (WIT).

#### Domain creation WDT models

Specify an image that describes the domain topology, resources, and applications in the domain resource YAML file.

```
     domain:
          domainCreationImages:
            - image: 'myrepo/domain-images:v1'
```

| Field                     | Notes                                                                                | Values                                                                  | Required                                                            |
|---------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------|---------------------------------------------------------------------|
| `domainCreationImages`      | WDT domain images.                                                                    | An array of images.                                                          | Y                                |

In this image or images, you must provide the required WDT [installer](https://github.com/oracle/weblogic-deploy-tooling/releases),
and also the WDT model files, WDT variables files, and WDT archive files.  The operator will use them to create the initial domain.  

For additional options in `domainCreationImages`, use the following command to obtain the details.

```
$ kubectl explain domain.spec.configuration.initializeDomainOnPV.domain.domainCreationImages
```

The image layout follows this directory structure:

```
/auxiliary/weblogic-deploy - The directory where the WebLogic Deploy Tooling software is installed.
/auxiliary/models -  WDT model, WDT archive, and WDT variables files.
```

You can create your own image using your familiar method or use the [WebLogic Image Tool (WIT)](https://github.com/oracle/weblogic-image-tool).

For example, because the file structure is the same as an [Auxiliary Image]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}) that's used in the Model in Image domain home source type, you can
use the same WIT command `createAuxImage`.

```
$ imagetool.sh createAuxImage --wdtArchive /home/acme/myapp/wdt/myapp.zip \
   --wdtVersion latest \
   --wdtModel /home/acme/myapp/wdt/model1.yaml \
   --wdtVariables /home/acme/myapp/wdt/model1.properties \
   --tag myrepo/domain-images:v1   
```

#### Optional WDT models ConfigMap

Optionally, you can provide a Kubernetes ConfigMap with additional WDT models and WDT variables files as supplements or overrides to
those in `domainCreationImages`.

```
     domain:
          ...
          domainCreationImages:
              ...
          domainCreationConfigMap: mymodel-domain-configmap
```

| Field                     | Notes                                                 | Values                             | Required |
|---------------------------|-------------------------------------------------------|------------------------------------|----------|
| `domainCreationConfigMap`      | Optional WDT models and WDT variables files in ConfigMap. | ConfigMap name. | N        |

The files inside this ConfigMap must have file extensions, `.yaml`, `.properties`, or `.zip`.

#### Using WDT model encryption

Starting in WebLogic Kubernetes Operator version 4.2.16.  If the provided WDT models are encrypted using the WDT `encryptModel`
command.  You can specify the encryption passphrase as a secret in the domain resource YAML. WDT will use the value in the
secret to decrypt the models for domain creation.

```yaml
   initializeDomainOnPV:
      modelEncryptionPassphraseSecret: model-encryption-secret
```

The secret must have a key `passphrase` containing the value of the WDT encryption passphrase used to encrypt the models.

`kubectl create secret generic model-encrypion-secret --from-literal=passphrase=<encryption passphrase value>`

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

##### PV and PVC requirements
- Domain on PV requires that the PV and PVC are created with `Filesystem` volume mode; `Block` volume mode is **not** supported.
  - If you request that the operator creates the PV and PVC, then it uses the default `Filesystem` volume mode.
  - If you plan to use an existing PV and PVC, then ensure that it was created with `Filesystem` volume mode.
- You must use a storage provider that supports the `ReadWriteMany` option.
- The operator will automatically set the owner of all files in the domain home on the persistent volume to `uid 1000` with `gid 0`. If you want to use a different user and group, then configure the desired `runAsUser` and `runAsGroup` in the security context under the `spec.serverPod.podSecurityContext` section of the Domain YAML file. The operator will use these values when setting the owner for files in the domain home directory.

For example, if you provide the specification of the `Persistent Volume` and `Persistent Volume Claim` in the domain resource YAML file,  
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


Not all the fields in the standard Kubernetes PV and PVC are supported.  For the list of supported fields in `persistentVolume` and `persistentVolumeClaim`, see the `initializeDomainOnPV.peristentVolume` and `initializeDomainOnPV.peristentVolumeClaim` section
  in the domain resource
  [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md), or use the commands:
  ```
   $ kubectl explain domain.spec.configuration.initializeDomainOnPV.persistentVolume
   $ kubectl explain domain.spec.configuration.initializeDomainOnPV.persistentVolumeClaim
  ```

If the PV and PVC already exist in your environment, you do not need
to specify any `persistentVolume` or `persistentVolumeClaim`  under the `intializedDomainOnPV` section.


#### References

- [Oracle Kubernetes Engine Persistent Storage](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingpersistentvolumeclaim.htm)
- [Azure Kubernetes Service Persistent Storage](https://learn.microsoft.com/en-us/azure/aks/concepts-storage)
- [Amazon Kubernetes Service Persistent Storage](https://aws.amazon.com/blogs/storage/persistent-storage-for-kubernetes/)
- [OpenShift Persistent Storage](https://docs.openshift.com/container-platform/4.12/storage/understanding-persistent-storage.html)

#### Domain information

**For JRF-based domains, before proceeding, please be sure to read this document**, [JRF domains]({{< relref "/managing-domains/domain-on-pv/jrf-domain.md">}}).

This is the section describing the WebLogic domain. For example:

```
spec:
  domainHome: /share/domains/sample-domain1
  domainHomeSourceType: PersistentVolume

  configuration:
    secrets: [ sample-domain1-rcu-access ]
    initializeDomainOnPV:
      ...
      domain:
          # Domain | DomainAndRCU
          createIfNotExists: DomainAndRCU
          domainCreationImages:
            - image: 'myrepo/domain-images:v1'
          domainType: JRF
          domainCreationConfigMap: sample-domain1-wdt-config-map
          opss:
           walletPasswordSecret: sample-domain1-opss-wallet-password-secret
```

| Field                     | Notes                                                                                | Values                                                                          | Required                                                            |
|---------------------------|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|---------------------------------------------------------------------|
| `domainType`                | Type of domain being created.                                                          | `JRF` or `WLS`                                                                  | N (default `WLS`)                                                   |
| `createIfNotExists`         | Specifies whether the operator should create the RCU schema first, before creating the domain. | `Domain` or `DomainAndRCU` (drop existing RCU schema and create new RCU schema) | N (default `Domain`)                                                |
| `domainCreationImages`      | WDT domain images.                                                                    | An array of images.                                                             | Y                                                                   |
| `domainCreationConfigMap`   | Optional ConfigMap containing extra WDT models.                                       | Kubernetes ConfigMap name.                                                      | N                                                                   |
| `osss.walletPasswordSecret` | Password for extracting OPSS wallet for JRF domain.               | Kubernetes Secret name with the key `walletPassword`.                           | Y                                                                   |
| `osss.walletFileSecret`     | Extracted OPSS wallet file.                                                        | Kubernetes Secret name with the key `walletFile`.                               | N (Only needed when recreating the domain during disaster recovery) |

**After a JRF domain is successfully deployed**: follow the next section, [Best practices](#best-practices), to download and back up the OPSS wallet.

### Best practices

Oracle recommends that you save the OPSS wallet file in a safe, backed-up location __immediately__ after the initial JRF domain is created.
In addition, you should make sure to store the wallet in a Kubernetes Secret in the same namespace. This will allow the secret to be available when the domain needs to be recovered in a disaster scenario or if the domain directory gets corrupted. There is no way to reuse the original RCU schema without this specific wallet key.
Therefore, for disaster recovery, **you should back up this OPSS wallet**.


#### Back up the JRF domain home directory and database

Oracle recommends that you back up the domain home directory and database after the initial JRF domain is created, and then periodically, making sure all the latest changes are backed up.

A JRF domain has a one-to-one relationship with the RCU schema.  After a domain is created using a particular RCU schema,
that schema _cannot_ be reused by another domain and the same schema _cannot_ be shared across different domains. Any attempts to
create a new domain using the existing RCU schema will result in an error.

{{% notice warning %}}
If the domain home is not properly backed up, you potentially can lose existing data if the domain home is corrupted or deleted.
That's because recreating the domain requires dropping the existing RCU schema and creating a new RCU schema. Therefore, backing up the existing domain home should be
the highest priority in your Kubernetes environment.
{{% /notice %}}

A Domain on PV domain configuration might get updated after its initial deployment. For example, using WLST or the console, you might have deployed
 new applications, added custom OPSS keystores, and added OWSM policies, and such.
In that case, the original WDT model files used to create the initial domain will _not_ match the current state of the domain (the WDT model files are _not_ the source of truth).
Therefore, if you recreate the domain using the original WDT model files, you will lose all the subsequent updates. In order to preserve the domain updates, you should restore the domain from
the backup copy of the domain home directory and connect to the existing RCU schema from the database backup.

#### Store the OPSS wallet in a Kubernetes Secret and update `opss.walletFileSecret` in the domain resource
After the domain is created, the operator automatically exports the OPSS wallet and stores it in an introspector ConfigMap; the name of the ConfigMap follows the pattern `<domain uid>-weblogic-domain-introspect-cm` with the key `ewallet.p12`. Oracle recommends that you save the OPSS wallet file in a safe, backed-up location __immediately__ after the initial JRF domain is created. In addition, you should make sure to store the wallet in a Kubernetes Secret in the same namespace. This will allow the secret to be available when the domain needs to be recovered in a disaster scenario or if the domain directory gets corrupted.

The following are the high-level steps for storing the OPSS wallet in a Kubernetes Secret.
1. The operator provides a utility script, [OPSS wallet utility](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/scripts/domain-lifecycle/opss-wallet.sh), for extracting the wallet file and storing it in a Kubernetes `walletFileSecret`. In addition, you should also save the wallet file in a safely backed-up location outside of Kubernetes. For example, the following command saves the OPSS wallet for the `sample-domain1` domain in the `sample-ns` namespace to a file named `ewallet.p12` in the `/tmp` directory and also stores it in the wallet secret named `jrf-wallet-file-secret`.

   ```
   $ opss-wallet.sh -n sample-ns -d sample-domain1 -s -r -wf /tmp/ewallet.p12 -ws jrf-wallet-file-secret
   ```

   Replace `/tmp/ewallet.p12` in the previous command with the name of the file to be stored in a safe location and replace `jrf-wallet-file-secret` with the Secret name of your choice. For more information, see the `OPSS wallet utility` section in the [README](https://github.com/oracle/weblogic-kubernetes-operator/tree/{{< latestMinorVersion >}}/kubernetes/samples/scripts/domain-lifecycle/README.md) file.

2. Save the extracted wallet file (`/tmp/ewallet.p12` in the example) to a safe location, outside of Kubernetes.
3. Add the `opss.walletFileSecret` to the domain resource YAML file under `configuration.initializeDomainOnPV.domain`.

   ```
     ...
     configuration:
       initializeDomainOnPV:
       ...
         domain:
           ...
           opss:
             walletFileSecret: jrf-wallet-file-secret
             ...
   ```

   You can use the following `patch` command to add it to the domain resource.

   ```
   $ kubectl -n sample-ns patch domain sample-domain1 --type='JSON' -p='[ { "op" : "add", "path" : "/spec/configuration/initializeDomainOnPV/domain/opss/walletFileSecret", "value" : "jrf-wallet-file-secret" }]'
   ```

#### Recovering the domain when it's corrupted or in other disaster scenarios

If the domain home directory is corrupted, and you have a recent backup of the domain home directory, then perform the following steps to recover the domain.

1. Restore the domain home directory from the backup copy.
2. Update the `restartVersion` of the domain resource to restart the domain. For example,

   ```
   $ kubectl -n sample-ns patch domain sample-domain1 --type='JSON' -p='[ { "op" : "replace", "path" : "/spec/restartVersion", "value" : "15" }]'
   ```
3. After the domain is restarted, check the WebLogic domain configuration to ensure that it has the latest changes.
   **NOTE**: If you made any changes that are persisted in the domain home directory after your last backup, you must reapply those changes to the domain home directory.
   However, because the operator will reconnect to the same RCU schema, the data stored in the OPSS, MDS, or OWSM tables will be current.

4. Reapply any domain configuration changes persisted to the domain home directory, such as
   data source connections, JMS destinations, or new application EAR deployments, after your last backup. To make these changes, use WLST, the WebLogic Server Administration Console, or Enterprise Manager.

In the rare scenario where the domain home directory is corrupted, and you do **not** have a recent backup of the domain home directory, or if the backup copy is also corrupted, then you can recreate the domain from the WDT model files without losing any RCU schema data.

1. Delete the domain home directory from the persistent volume.
2. Add or replace the `domain.spec.introspectVersion` in the domain resource with a new value. The following is a sample `patch` command to update the `introspectVersion` for the sample domain.

   ```
   $ kubectl -n sample-ns patch domain sample-domain1 --type='JSON' -p='[ { "op" : "replace", "path" : "/spec/intropsectVersion", "value" : "15" }]'
   ```

3. The operator will then create a new domain from the existing WDT models and reuse the original RCU schema.

    **NOTE**:
    All the updates made to the domain after the initial deployment will **not** be available in the recovered domain.
    However, this allows you to access the original RCU schema database without losing all its data.

4. Apply all the domain configuration changes persisted to the domain home file system, such as data source connections, JMS destinations, or new application EAR deployments, that are not in the WDT model files. These are the changes you have made _after_ the initial domain deployment.

5. Update the `restartVersion` of the domain resource to restart the domain.

   ```
   $ kubectl -n sample-ns patch domain sample-domain1 --type='JSON' -p='[ { "op" : "replace", "path" : "/spec/restartVersion", "value" : "15" }]'
   ```

For more information, see [Disaster Recovery]({{< relref "/managing-domains/domain-on-pv/jrf-domain#disaster-recovery-for-domain-on-pv-deployment">}}).  

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
[PV and PVC helper script](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/scripts/domain-lifecycle/pv-pvc-helper.sh).
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
          # Domain | DomainAndRCU
          createIfNotExists: DomainAndRCU
          domainCreationImages:
            - image: 'myrepo/domain-images:v1'
          domainType: JRF
          domainCreationConfigMap: sample-domain1-wdt-config-map
          opss:
           walletPasswordSecret: sample-domain1-opss-wallet-password-secret
```
