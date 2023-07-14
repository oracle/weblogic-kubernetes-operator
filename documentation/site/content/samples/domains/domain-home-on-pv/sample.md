---
title: "Sample"
date: 2019-02-23T17:32:31-05:00
weight: 3
description: "Sample for creating a WebLogic domain home on a PV for deploying the generated WebLogic domain."
---

{{< table_of_contents >}}

{{% notice note %}}

**Before you begin**: Perform the steps in [Prerequisites]({{< relref "/samples/domains/domain-home-on-pv/prerequisites.md" >}}) and then build a Domain on PV `domain creation image` by completing the steps in [Build the domain creation image]({{< relref "/samples/domains/domain-home-on-pv/build-domain-creation-image#build-the-domain-creation-image" >}}).
If you are taking the `JRF` path through the sample, then substitute `JRF` for `WLS` in your image names and directory paths. Also note that the JRF-v1 model YAML file differs from the WLS-v1 YAML file (it contains an additional `domainInfo -> RCUDbInfo` stanza).
{{% /notice %}}

### Overview

The sample demonstrates setting up a WebLogic domain with a domain home on a Kubernetes PersistentVolume (PV) (**Domain on PV**). This involves:

  - Using the [domain creation image](#domain-creation-image) that you previously built.
  - Creating secrets for the domain.
  - Creating a Domain resource YAML file for the domain that:
    - References your Secrets and a WebLogic image.
    - References the `domain creation image` in the `spec.configuration.initializeDomainOnPV` section for the initial Domain on PV configuration defined using WDT model YAML.
    - Defines PV and PVC metadata and specifications in the `spec.configuration.initializeDomainOnPV` section to create a PV and PVC (optional).



**PV and PVC Notes:**
- The specifications of PersistentVolume and PersistentVolumeClaim defined in the `spec.configuration.initializeDomainOnPV` section of the Domain resource YAML file are environment specific and often require information from your Kubernetes cluster administrator to provide the information. See [Persistent volume and Persistent Volume Claim]({{< relref "/managing-domains/domain-on-pv/usage#persistent-volume-and-persistent-volume-claim" >}}) in the user documentation for more details.
- You must use a storage provider that supports the `ReadWriteMany` option.
- This sample will automatically set the owner of all files in the domain home on the persistent
volume to `uid 1000`. If you want to use a different user, configure the desired `runAsUser` and
`runAsGroup` in the security context under the `spec.serverPod.podSecurityContext` section of the Domain YAML file.
The operator will use these values when setting the owner for files in the domain home directory.

After the Domain is deployed, the operator creates the PV and PVC (if they are configured and do not already exist) and starts an 'introspector job' that converts your models included in the `domain creation image` and `config map` into a WebLogic configuration to initialize the Domain on PV.

### Domain creation image

The sample uses a `domain creation image` with the name `wdt-domain-image:WLS-v1` that you created in the [Build the domain creation image]({{< relref "/samples/domains/domain-home-on-pv/build-domain-creation-image#build-the-domain-creation-image" >}}) step. The WDT model files in this image define the initial WebLogic Domain on PV configuration. The image contains:
- The directory where the WebLogic Deploy Tooling software is installed (also known as WDT Home), expected in an image's `/auxiliary/weblogic-deploy` directory, by default.
- WDT model YAML, property, and archive files, expected in the directory `/auxiliary/models`, by default.

### Deploy resources - Introduction

In this section, you will define the PV and PVC configuration and reference the `domain creation image` created earlier in the domain resource YAML file. You will then deploy the domain resource YAML file to the namespace `sample-domain1-ns`, including the following steps:

  - Create a Secret containing your WebLogic administrator user name and password.
  - If your domain type is `JRF`, create secrets containing your RCU access URL, credentials, and prefix.
  - Deploy a Domain YAML file that references the PV/PVC configuration and a `domain creation image` under the `spec.configuration.initializeDomainOnPV` section.
  - Wait for the PV and PVC to be created if they do not already exist.
  - Wait for domain's Pods to start and reach their ready state.

#### Secrets

First, create the secrets needed by both WLS and JRF type model domains. You have to create the "WebLogic credentials secret" and any other secrets that are referenced from the macros in the WDT model file. For more details about using macros in the WDT model files, see [Working with the WDT model files]({{< relref "managing-domains/domain-on-pv/model-files.md" >}}).

Run the following `kubectl` commands to deploy the required secrets:

  **NOTE**: Substitute a password of your choice for `MY_WEBLOGIC_ADMIN_PASSWORD`. This
  password should contain at least seven letters plus one digit.

  ```shell
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-weblogic-credentials \
     --from-literal=username=weblogic --from-literal=password=MY_WEBLOGIC_ADMIN_PASSWORD
  ```
  ```shell
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-weblogic-credentials \
    weblogic.domainUID=sample-domain1
  ```

  Some important details about these secrets:

  - The WebLogic credentials secret is required and must contain `username` and `password` fields. You reference it in `spec.webLogicCredentialsSecret` field of Domain YAML file and macros in the `domainInfo.AdminUserName` and `domainInfo.AdminPassWord` fields your model YAML file.
  - Delete a secret before creating it, otherwise the create command will fail if the secret already exists..
  - Name and label the secrets using their associated domain UID to clarify which secrets belong to which domains and make it easier to clean up a domain.

  If you're following the `JRF` path through the sample, then you also need to deploy the additional secret referenced by macros in the JRF model `RCUDbInfo` clause, plus an `OPSS` wallet password secret. For details about the uses of these secrets, see the [Domain on PV]({{< relref "/managing-domains/domain-on-pv/usage.md" >}}) user documentation.

  {{%expand "Click here for the commands for deploying additional secrets for JRF." %}}

  **NOTE**: Replace `MY_RCU_SCHEMA_PASSWORD` with the RCU schema password
  that you chose in the prequisite steps when
  [setting up JRF]({{< relref "/samples/domains/domain-home-on-pv/prerequisites#additional-prerequisites-for-jrf-domains" >}}).

  ```shell
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-rcu-access \
     --from-literal=rcu_prefix=FMW1 \
     --from-literal=rcu_schema_password=MY_RCU_SCHEMA_PASSWORD \
     --from-literal=rcu_db_conn_string=oracle-db.default.svc.cluster.local:1521/devpdb.k8s
  ```
  ```shell
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-rcu-access \
    weblogic.domainUID=sample-domain1
  ```

  **NOTES**:
  - Replace `MY_OPSS_WALLET_PASSWORD` with a password of your choice.
    The password can contain letters and digits.
  - The domain's JRF RCU schema will be automatically initialized
    plus generate a JRF OPSS wallet file upon first use.
    If you plan to save and reuse this wallet file,
    as is necessary for reusing RCU schema data after a migration or restart,
    then it will also be necessary to use this same password again.

  ```shell
  $ kubectl -n sample-domain1-ns create secret generic \
    sample-domain1-opss-wallet-password-secret \
     --from-literal=walletPassword=MY_OPSS_WALLET_PASSWORD
  ```
  ```shell
  $ kubectl -n sample-domain1-ns label  secret \
    sample-domain1-opss-wallet-password-secret \
    weblogic.domainUID=sample-domain1
  ```

  {{% /expand %}}

#### Domain resource

Now, you deploy a `sample-domain1` domain resource and an associated `sample-domain1-cluster-1` cluster resource using a single YAML resource file which defines both resources. The domain resource and cluster resource tells the operator how to deploy a WebLogic domain. They do not replace the traditional WebLogic configuration files, but instead cooperate with those files to describe the Kubernetes artifacts of the corresponding domain.

Copy the contents of the [WLS domain resource YAML file](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/domain-on-pv/domain-resources/WLS/domain-on-pv-WLS-v1.yaml) file that is included in the sample source to a file called `/tmp/sample/domain-resource.yaml` or similar.

Click [here](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/domain-on-pv/domain-resources/WLS/domain-on-pv-WLS-v1.yaml) to view the WLS Domain YAML file.

Click [here](https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/{{< latestMinorVersion >}}/kubernetes/samples/scripts/create-weblogic-domain/domain-on-pv/domain-resources/JRF/domain-on-pv-JRF-v1.yaml) to view the JRF Domain YAML file.

Modify the PV and PVC specifications defined in the `spec.configuration.initializeDomainOnPV` section of the Domain resource YAML file based on your environment. These specifications often require your Kubernetes cluster administrator to provide the information. See [Persistent volume and Persistent Volume Claim]({{< relref "/managing-domains/domain-on-pv/usage#persistent-volume-and-persistent-volume-claim" >}}) in the user documentation for more details.

{{% notice note %}}
By default, this sample creates a Persistent Volume (PV) of type `hostPath`. This works only for a single node Kubernetes cluster for testing or proof of concept activities. In a multinode Kubernetes cluster, consider using a Kubernetes `StorageClass`, or PV of `nfs` type. If you use Oracle Container Engine for Kubernetes (OKE) and plan to use Oracle Cloud Infrastructure File Storage (FSS) for PV, then Oracle recommends creating a `StorageClass` and specifying the name of the `StorageClass` in your PersistentVolumeClaim (PVC) configuration in the `initializeDomainOnPV` section. See [Provisioning PVCs on the File Storage Service (FSS)](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingpersistentvolumeclaim_Provisioning_PVCs_on_FSS.htm#Provisioning_Persistent_Volume_Claims_on_the_FileStorageService) in the OCI documentation for more details.
{{% /notice %}}

  **NOTE**: Before you deploy the domain custom resource, ensure all nodes in your Kubernetes cluster [can access `domain-creation-image` and other images]({{< relref "/samples/domains/domain-home-on-pv#ensuring-your-kubernetes-cluster-can-access-images" >}}).

  Run the following command to apply the two sample resources.
  ```shell
  $ kubectl apply -f /tmp/sample/domain-resource.yaml
  ```

   The domain resource references the cluster resource, a WebLogic Server installation image, the secrets you defined, PV and PVC configuration details, and a sample `domain creation image`, which contains a traditional WebLogic configuration and a WebLogic application. For detailed information, see [Domain and cluster resources]({{< relref "/managing-domains/domain-resource.md" >}}).

### Verify the PV, PVC, and domain

To confirm that the PV, PVC, and domain were created, use the following instructions.

#### Verify the persistent volume
If the `spec.configuration.initializeDomainOnPV.persistentVolume` is configured for the operator to create the PV, then verify that a PV with the given name is created and is in `Bound` status. If the PV already exists, then ensure that the existing PV is in `Bound` status.

```shell
$ kubectl get pv
```

Here is an example output of this command:
```
NAME                                CAPACITY   ACCESS MODES   RECLAIM POLICY   STATUS   CLAIM                                                  STORAGECLASS   REASON   AGE
sample-domain1-weblogic-sample-pv   5Gi        RWX            Retain           Bound    sample-domain1-ns/sample-domain1-weblogic-sample-pvc   manual                  14m
```

#### Verify the persistent volume claim
If the `spec.configuration.initializeDomainOnPV.persistentVolumeClaim` is configured for the operator to create the PVC, then verify that the PVC with the given name is created and is in `Bound` status. If the PVC already exists, then ensure that the existing PVC is in `Bound` status.
```shell
$ kubectl get pvc -n sample-domain1-ns
```

Here is an example output of this command:
```
NAME                                 STATUS   VOLUME                              CAPACITY   ACCESS MODES   STORAGECLASS   AGE
sample-domain1-weblogic-sample-pvc   Bound    sample-domain1-weblogic-sample-pv   5Gi        RWX            manual         11m
```

#### Verify the domain
Run the following `kubectl describe domain` command to check the status and events for the created domain.

```shell
$ kubectl describe domain sample-domain1 -n sample-domain1-ns
```
{{%expand "Click here to see an example of the output of this command." %}}
```
Name:         sample-domain1
Namespace:    sample-domain1-ns
Labels:       weblogic.domainUID=sample-domain1
Annotations:  <none>
API Version:  weblogic.oracle/v9
Kind:         Domain
Metadata:
  Creation Timestamp:  2023-04-28T20:31:09Z
  Generation:          1
  Resource Version:  345297
  UID:               69b69019-c430-4f01-b96c-4364bf1f39c1
Spec:
  Clusters:
    Name:  sample-domain1-cluster-1
  Configuration:
    Initialize Domain On PV:
      Domain:
        Create If Not Exists:  domain
        Domain Creation Images:
          Image:      phx.ocir.io/weblogick8s/domain-on-pv-image:WLS-v1
        Domain Type:  WLS
      Persistent Volume:
        Metadata:
          Name:  sample-domain1-weblogic-sample-pv
        Spec:
          Capacity:
            Storage:  5Gi
          Host Path:
            Path:              /shared
          Storage Class Name:  manual
      Persistent Volume Claim:
        Metadata:
          Name:  sample-domain1-weblogic-sample-pvc
        Spec:
          Resources:
            Requests:
              Storage:               1Gi
          Storage Class Name:        manual
          Volume Name:               sample-domain1-weblogic-sample-pv
    Override Distribution Strategy:  Dynamic
  Domain Home:                       /shared/domains/sample-domain1
  Domain Home Source Type:           PersistentVolume
  Failure Retry Interval Seconds:    120
  Failure Retry Limit Minutes:       1440
  Http Access Log In Log Home:       true
  Image:                             container-registry.oracle.com/middleware/weblogic:12.2.1.4
  Image Pull Policy:                 IfNotPresent
  Include Server Out In Pod Log:     true
  Introspect Version:                1
  Log Home Layout:                   ByServers
  Max Cluster Concurrent Shutdown:   1
  Max Cluster Concurrent Startup:    0
  Max Cluster Unavailable:           1
  Replicas:                          1
  Restart Version:                   1
  Server Pod:
    Env:
      Name:   CUSTOM_DOMAIN_NAME
      Value:  domain1
      Name:   JAVA_OPTIONS
      Value:  -Dweblogic.StdoutDebugEnabled=false
      Name:   USER_MEM_ARGS
      Value:  -Djava.security.egd=file:/dev/./urandom -Xms256m -Xmx512m
    Resources:
      Requests:
        Cpu:     250m
        Memory:  768Mi
    Volume Mounts:
      Mount Path:  /shared
      Name:        weblogic-domain-storage-volume
    Volumes:
      Name:  weblogic-domain-storage-volume
      Persistent Volume Claim:
        Claim Name:     sample-domain1-weblogic-sample-pvc
  Server Start Policy:  IfNeeded
  Web Logic Credentials Secret:
    Name:  sample-domain1-weblogic-credentials
Status:
  Clusters:
    Cluster Name:  cluster-1
    Conditions:
      Last Transition Time:  2023-04-28T20:33:57.983431Z
      Status:                True
      Type:                  Available
      Last Transition Time:  2023-04-28T20:33:57.983495Z
      Status:                True
      Type:                  Completed
    Label Selector:          weblogic.domainUID=sample-domain1,weblogic.clusterName=cluster-1
    Maximum Replicas:        5
    Minimum Replicas:        0
    Observed Generation:     1
    Ready Replicas:          2
    Replicas:                2
    Replicas Goal:           2
  Conditions:
    Last Transition Time:  2023-04-28T20:33:53.450209Z
    Status:                True
    Type:                  Available
    Last Transition Time:  2023-04-28T20:33:58.076050Z
    Status:                True
    Type:                  Completed
  Observed Generation:     1
  Replicas:                2
  Servers:
    Health:
      Activation Time:  2023-04-28T20:33:01.803000Z
      Overall Health:   ok
      Subsystems:
        Subsystem Name:  ServerRuntime
        Symptoms:
    Node Name:     phx32822d1
    Pod Phase:     Running
    Pod Ready:     True
    Server Name:   admin-server
    State:         RUNNING
    State Goal:    RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2023-04-28T20:33:40.165000Z
      Overall Health:   ok
      Subsystems:
    Node Name:     phx32822d1
    Pod Phase:     Running
    Pod Ready:     True
    Server Name:   managed-server1
    State:         RUNNING
    State Goal:    RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2023-04-28T20:33:44.225000Z
      Overall Health:   ok
      Subsystems:
    Node Name:     phx32822d1
    Pod Phase:     Running
    Pod Ready:     True
    Server Name:   managed-server2
    State:         RUNNING
    State Goal:    RUNNING
    Cluster Name:  cluster-1
    Server Name:   managed-server3
    State:         SHUTDOWN
    State Goal:    SHUTDOWN
    Cluster Name:  cluster-1
    Server Name:   managed-server4
    State:         SHUTDOWN
    State Goal:    SHUTDOWN
    Cluster Name:  cluster-1
    Server Name:   managed-server5
    State:         SHUTDOWN
    State Goal:    SHUTDOWN
  Start Time:      2023-04-28T20:32:02.339082Z
Events:
  Type     Reason                      Age    From               Message
  ----     ------                      ----   ----               -------
  Warning  Failed                      4m7s   weblogic.operator  Domain sample-domain1 failed due to 'Persistent volume claim unbound': PersistentVolumeClaim 'sample-domain1-weblogic-sample-pvc' is not bound; the status phase is 'Pending'.. Operator is waiting for the persistent volume claim to be bound, it may be a temporary condition. If this condition persists, then ensure that the PVC has a correct volume name or storage class name and is in bound status..
  Normal   PersistentVolumeClaimBound  3m57s  weblogic.operator  The persistent volume claim is bound and ready.
  Normal   Available                   2m16s  weblogic.operator  Domain sample-domain1 is available: a sufficient number of its servers have reached the ready state.
  Normal   Completed                   2m11s  weblogic.operator  Domain sample-domain1 is complete because all of the following are true: there is no failure detected, there are no pending server shutdowns, and all servers expected to be running are ready and at their target image, auxiliary images, restart version, and introspect version.
```
{{% /expand %}}

In the `Status` section of the output, the available servers and clusters are listed.  Note that if this command is issued very soon after the domain resource is applied, there may be no servers available yet, or perhaps only the Administration Server but no Managed Servers.  The operator will start up the Administration Server first and wait for it to become ready before starting the Managed Servers.

### Verify the pods

If you run `kubectl get pods -n sample-domain1-ns --watch`, then you will see the introspector job run and your WebLogic Server pods start. The output will look something like this:
  {{%expand "Click here to expand." %}}
  ```shell
  $ kubectl get pods -n sample-domain1-ns --watch
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

For a more detailed view of this activity,
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

If you see an error, then consult [Debugging]({{< relref "/managing-domains/debugging.md" >}}).


### Verify the services

Use the following command to see the services for the domain:

```shell
$ kubectl get services -n sample-domain1-ns
```
Here is an example output of this command:
```
NAME                               TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)    AGE
sample-domain1-admin-server        ClusterIP   None             <none>        7001/TCP   10m
sample-domain1-cluster-cluster-1   ClusterIP   10.107.178.255   <none>        8001/TCP   9m49s
sample-domain1-managed-server1     ClusterIP   None             <none>        8001/TCP   9m49s
sample-domain1-managed-server2     ClusterIP   None             <none>        8001/TCP   9m43s
```

### Invoke the web application
Now that all the sample resources have been deployed, you can invoke the sample web application through the Traefik ingress controller's NodePort.

- Send a web application request to the load balancer URL for the application, as shown in the following example.

    {{< tabs groupId="config" >}}
    {{% tab name="Request from a local machine" %}}
        $ curl -s -S -m 10 -H 'host: sample-domain1-cluster-cluster-1.sample.org' http://localhost:30305/myapp_war/index.jsp

    {{% /tab %}}
    {{% tab name="Request from a remote machine" %}}

        $ K8S_CLUSTER_ADDRESS=$(kubectl cluster-info | grep DNS | sed 's/^.*https:\/\///g' | sed 's/:.*$//g')

        $ curl -s -S -m 10 -H 'host: sample-domain1-cluster-cluster-1.sample.org' http://${K8S_CLUSTER_ADDRESS}:30305/myapp_war/index.jsp

    {{% /tab %}}
    {{< /tabs >}}

- You will see output like the following:
  ```html
     <html><body><pre>
     *****************************************************************

     Hello World! This is version 'v1' of the sample JSP web-app.

     Welcome to WebLogic Server 'managed-server2'!

       domain UID  = 'sample-domain1'
       domain name = 'domain1'

     Found 1 local cluster runtime:
       Cluster 'cluster-1'

     Found min threads constraint runtime named 'SampleMinThreads' with configured count: 1

     Found max threads constraint runtime named 'SampleMaxThreads' with configured count: 10

     Found 0 local data sources:

     *****************************************************************
     </pre></body></html>
    ```

### Clean up resources and remove the generated domain home

Follow the cleanup instructions [here]({{< relref "samples/domains/domain-home-on-pv/cleanup.md" >}}) to remove the domain, cluster and other associated resources.

Sometimes in production, but most likely in testing environments, you might also want to remove the Domain on PV contents that are generated using this sample.
You can use the `pv-pvc-helper.sh` helper script in the domain lifecycle directory for this.
The script launches a Kubernetes pod named `pvhelper` using the provided persistent volume claim name and the mount path.
You can run `kubectl exec` to get a shell to the running pod container and run commands to examine or clean up the
contents of shared directories on the persistent volume.
For example:
```shell
$ cd /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/domain-lifecycle
$ ./pv-pvc-helper.sh -n sample-domain1-ns -r -c sample-domain1-weblogic-sample-pvc -m /shared
```
{{%expand "Click here to see the output." %}}
```
[2023-04-28T21:02:42.851294126Z][INFO] Creating pod 'pvhelper' using image 'ghcr.io/oracle/oraclelinux:8-slim', persistent volume claim 'sample-domain1-weblogic-sample-pvc' and mount path '/shared'.
pod/pvhelper created
[pvhelper] already initialized ..
Checking Pod READY column for State [1/1]
Pod [pvhelper] Status is NotReady Iter [1/120]
Pod [pvhelper] Status is Ready Iter [2/120]
NAME       READY   STATUS    RESTARTS   AGE
pvhelper   1/1     Running   0          10s
[2023-04-28T21:02:58.972826241Z][INFO] Executing 'kubectl -n sample-domain1-ns exec -i pvhelper -- ls -l /shared' command to print the contents of the mount path in the persistent volume.
[2023-04-28T21:02:59.115119523Z][INFO] =============== Command output ====================
total 0
drwxr-xr-x 4 1000 root 43 Apr 26 19:41 domains
drwxr-xr-x 4 1000 root 43 Apr 26 19:41 logs
[2023-04-28T21:02:59.118355423Z][INFO] ===================================================
[2023-04-28T21:02:59.121482356Z][INFO] Use command 'kubectl -n sample-domain1-ns exec -it pvhelper -- /bin/sh' and cd to '/shared' directory to view or delete the contents on the persistent volume.
[2023-04-28T21:02:59.124658180Z][INFO] Use command 'kubectl -n sample-domain1-ns delete pod pvhelper' to delete the pod created by the script.
```
```
$ kubectl -n sample-domain1-ns exec -it pvhelper -- /bin/sh
sh-4.4$ cd /shared
sh-4.4$ ls
domains
applications
```

{{% /expand %}}

After you get a shell to the running pod container, you can recursively delete the contents of the domain home and applications directories using `rm -rf /shared/domains/sample-domain1` and `rm -rf /shared/applications/sample-domain1` commands. Since these commands will actually delete files on the persistent storage, we recommend that you understand and execute these commands carefully.

#### Remove the PVC and PV
If the PVC and PV were created by the operator and you don't want to preserve them, then run following command to delete PVC and PV.
```
$ kubectl delete pvc sample-domain1-weblogic-sample-pvc -n sample-domain1-ns
$ kubectl delete pv sample-domain1-weblogic-sample-pv
```

#### Delete the domain namespace.

```
$ kubectl delete namespace sample-domain1-ns
```
