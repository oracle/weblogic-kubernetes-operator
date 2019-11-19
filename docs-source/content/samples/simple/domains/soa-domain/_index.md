---
title: "SOA domain"
date: 2019-04-18T07:32:31-05:00
weight: 3
description: "Sample for creating a SOA Suite domain home on an existing PV or
PVC, and the domain resource YAML file for deploying the generated SOA domain."
---

{{% notice warning %}}
Oracle SOA Suite is currently only supported for non-production use in Docker and Kubernetes.  The information provided
in this document is a *preview* for early adopters who wish to experiment with Oracle SOA Suite in Kubernetes before
it is supported for production use.
{{% /notice %}}

The sample scripts demonstrate the creation of a SOA Suite domain home on an
existing Kubernetes persistent volume (PV) and persistent volume claim (PVC). The scripts
also generate the domain YAML file, which can then be used to start the Kubernetes
artifacts of the corresponding domain.

#### Prerequisites

Before you begin, read this document, [Domain resource]({{< relref "/userguide/managing-domains/domain-resource/_index.md" >}}).

The following prerequisites must be handled prior to running the create domain script:

* Make sure that Kubernetes is set up in the environment. For details, see the [Kubernetes setup guide]({{< relref "/userguide/overview/k8s-setup.md" >}}).
* Make sure that the WebLogic operator is running. See [Manage operators]({{< relref "/userguide/managing-operators/_index.md" >}}) for operator infrastructure setup and [Install the operator]({{< relref "/userguide/managing-operators/installation/_index.md" >}}) for operator installation.
* The operator requires SOA Suite 12.2.1.3.0 with patch 29135930 applied. For details on how to obtain or create the image, see [SOA domains]({{< relref "/userguide/managing-domains/soa-suite/_index.md#obtaining-the-soa-suite-docker-image" >}}).
* Create a Kubernetes namespace (for example, `soans`) for the domain unless you intend to use the default namespace. Use the newly created namespace in all the other steps.
For details, see [Prepare to run a domain]({{< relref "/userguide/managing-domains/prepare.md" >}}).

  ```
   $ kubectl create namespace soans
  ```

* In the Kubernetes namespace created above, create the PV and PVC for the database
by running the [create-pv-pvc.sh]({{< relref "/samples/simple/storage/_index.md" >}}) script.
Follow the instructions for using the scripts to create a PV and PVC.

    * Change the values in the [create-pv-pvc-inputs.yaml](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml) file based on your requirements.

    * Ensure that the path for the `weblogicDomainStoragePath` property exists (if not, you need to create it),
    has full access permissions, and that the folder is empty.

* Create the Kubernetes secrets `username` and `password` of the administrative account in the same Kubernetes
  namespace as the domain. For details, see this [document](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/scripts/create-weblogic-domain-credentials/README.md).

    ```bash
    $ cd kubernetes/samples/scripts/create-weblogic-domain-credentials
    $ ./create-weblogic-credentials.sh -u weblogic -p Welcome1 -n soans -d soainfra -s soainfra-domain-credentials
    ```

    You can check the secret with the `kubectl get secret` command. See the following example, including the output:

    ```bash
    $ kubectl get secret soainfra-domain-credentials -o yaml -n soans
    apiVersion: v1
    data:
      password: V2VsY29tZTE=
      username: d2VibG9naWM=
    kind: Secret
    metadata:
      creationTimestamp: 2019-06-02T07:05:25Z
      labels:
        weblogic.domainName: soainfra
        weblogic.domainUID: soainfra
      name: soainfra-domain-credentials
      namespace: soans
      resourceVersion: "11561988"
      selfLink: /api/v1/namespaces/soans/secrets/soainfra-domain-credentials
      uid: a91ef4e1-6ca8-11e9-8143-fa163efa261a
    type: Opaque
    ```

* Configure access to your database. For details, see [here]({{< relref "/userguide/managing-fmw-domains/soa-suite/_index.md#configuring-access-to-your-database" >}}).  
* Create a Kubernetes secret with the RCU credentials. For details, refer to this [document]({{< relref "/userguide/managing-fmw-domains/soa-suite/_index.md#running-the-repository-creation-utility-to-set-up-your-database-schema" >}}).

#### Use the script to create a domain

Please note that the sample scripts for SOASuite domain deployment are available at  `<weblogic-kubernetes-operator-project>/kubernetes/samples/scripts/create-soa-domain`.
  
Make a copy of the `create-domain-inputs.yaml` file, and run the create script, pointing it at
your inputs file and an output directory:

```
$ ./create-domain.sh \
  -i create-domain-inputs.yaml \
  -o /<path to output-directory>
```

The script will perform the following steps:

* Create a directory for the generated Kubernetes YAML files for this domain if it does not
  already exist.  The path name is `/<path to output-directory>/weblogic-domains/<domainUID>`.
  If the directory already exists, its contents must be removed before using this script.
* Create a Kubernetes job that will start up a utility SOA Suite container and run
  offline WLST scripts to create the domain on the shared storage.
* Run and wait for the job to finish.
* Create a Kubernetes domain YAML file, `domain.yaml`, in the directory that is created above.
  This YAML file can be used to create the Kubernetes resource using the `kubectl create -f`
  or `kubectl apply -f` command:

    ```
    $ kubectl apply -f /<path to output-directory>/weblogic-domains/<domainUID>/domain.yaml
    ```

* Create a convenient utility script, `delete-domain-job.yaml`, to clean up the domain home
  created by the create script.

If you copy the sample scripts to a different location, make sure that you copy everything in
the `<weblogic-kubernetes-operator-project>/kubernetes/samples/scripts` directory together
into the target directory, maintaining the original directory hierarchy.

The default domain created by the script has the following characteristics:

* An Administration Server named `AdminServer` listening on port `7001`.
* A configured cluster named `soa_cluster-1` of size 5.
* Two Managed Servers, named `soa_server1` and `soa_server2`, listening on port `8001`.
* Log files that are located in `/shared/logs/<domainUID>`.
* SOA Infra, SOA composer and WorklistApp applications deployed.
* No data sources or JMS resources.
* A T3 channel.

The domain creation inputs can be customized by editing `create-domain-inputs.yaml`.

#### Configuration parameters
The following parameters can be provided in the inputs file.

| Parameter | Definition | Default |
| --- | --- | --- |
| `adminPort` | Port number for the Administration Server inside the Kubernetes cluster. | `7001` |
| `adminNodePort` | Port number of the Administration Server outside the Kubernetes cluster. | `30701` |
| `adminServerName` | Name of the Administration Server. | `AdminServer` |
| `clusterName` | Name of the WebLogic cluster instance to generate for the domain. By default the cluster name is `soa_cluster` for the SOA domain. You can update this to `osb_cluster` for an OSB domain type or `soa_cluster` for SOAESS or SOAOSB or SOAESSOSB domain types.| `soa_cluster` |
| `configuredManagedServerCount` | Number of Managed Server instances to generate for the domain. | `5` |
| `createDomainFilesDir` | Directory on the host machine to locate all the files to create a WebLogic domain, including the script that is specified in the `createDomainScriptName` property. By default, this directory is set to the relative path `wlst`, and the create script will use the built-in WLST offline scripts in the `wlst` directory to create the WebLogic domain. It can also be set to the relative path `wdt`, and then the built-in WDT scripts will be used instead. An absolute path is also supported to point to an arbitrary directory in the file system. The built-in scripts can be replaced by the user-provided scripts or model files as long as those files are in the specified directory. Files in this directory are put into a Kubernetes config map, which in turn is mounted to the `createDomainScriptsMountPath`, so that the Kubernetes pod can use the scripts and supporting files to create a domain home. | `wlst` |
| `createDomainScriptsMountPath` | Mount path where the create domain scripts are located inside a pod. The `create-domain.sh` script creates a Kubernetes job to run the script (specified in the `createDomainScriptName` property) in a Kubernetes pod to create a domain home. Files in the `createDomainFilesDir` directory are mounted to this location in the pod, so that the Kubernetes pod can use the scripts and supporting files to create a domain home. | `/u01/weblogic` |
| `createDomainScriptName` | Script that the create domain script uses to create a WebLogic domain. The `create-domain.sh` script creates a Kubernetes job to run this script to create a domain home. The script is located in the in-pod directory that is specified in the `createDomainScriptsMountPath` property. If you need to provide your own scripts to create the domain home, instead of using the built-it scripts, you must use this property to set the name of the script that you want the create domain job to run. | `create-domain-job.sh` |
| `domainHome` | Home directory of the SOA domain. If not specified, the value is derived from the `domainUID` as `/shared/domains/<domainUID>`. | `/u01/oracle/user_projects/domains/soainfra` |
| `domainPVMountPath` | Mount path of the domain persistent volume. | `/u01/oracle/user_projects/domains` |
| `domainUID` | Unique ID that will be used to identify this particular domain. Used as the name of the generated WebLogic domain as well as the name of the Kubernetes domain resource. This ID must be unique across all domains in a Kubernetes cluster. This ID cannot contain any character that is not valid in a Kubernetes service name. | `soainfra` |
| `domainType` | Type of the domain. Mandatory input for SOA Suite domains. You must provide one of the supported domain type values: `soa` (deploys a SOA domain),`osb` (deploys an OSB (Oracle Service Bus) domain),`soaess` (deploys a SOA domain with Enterprise Scheduler (ESS)),`soaosb` (deploys a domain with SOA and OSB), and `soaessosb` (deploys a domain with SOA, OSB, and ESS). | `soa`
| `exposeAdminNodePort` | Boolean indicating if the Administration Server is exposed outside of the Kubernetes cluster. | `false` |
| `exposeAdminT3Channel` | Boolean indicating if the T3 administrative channel is exposed outside the Kubernetes cluster. | `false` |
| `image` | SOA Suite Docker image. The operator requires SOA Suite 12.2.1.3.0 with patch 29135930 applied. Refer to [SOA domains]({{< relref "/userguide/managing-fmw-domains/soa-suite/_index.md#obtaining-the-soa-suite-docker-image" >}}) for details on how to obtain or create the image. | `container-registry.oracle.com/middleware/soasuite:12.2.1.3` |
| `imagePullPolicy` | WebLogic Docker image pull policy. Legal values are `IfNotPresent`, `Always`, or `Never` | `IfNotPresent` |
| `imagePullSecretName` | Name of the Kubernetes secret to access the Docker Store to pull the WebLogic Server Docker image. The presence of the secret will be validated when this parameter is specified. |  |
| `includeServerOutInPodLog` | Boolean indicating whether to include the server .out to the pod's stdout. | `true` |
| `initialManagedServerReplicas` | Number of Managed Servers to initially start for the domain. | `2` |
| `javaOptions` | Java options for starting the Administration Server and Managed Servers. A Java option can have references to one or more of the following pre-defined variables to obtain WebLogic domain information: `$(DOMAIN_NAME)`, `$(DOMAIN_HOME)`, `$(ADMIN_NAME)`, `$(ADMIN_PORT)`, and `$(SERVER_NAME)`. | `-Dweblogic.StdoutDebugEnabled=false` |
| `logHome` | The in-pod location for the domain log, server logs, server out, and Node Manager log files. If not specified, the value is derived from the `domainUID` as `/shared/logs/<domainUID>`. | `/u01/oracle/user_projects/domains/logs/soainfra` |
| `managedServerNameBase` | Base string used to generate Managed Server names. | `soa_server` |
| `managedServerPort` | Port number for each Managed Server. | `8001` |
| `namespace` | Kubernetes namespace in which to create the domain. | `soans` |
| `persistentVolumeClaimName` | Name of the persistent volume claim created to host the domain home. If not specified, the value is derived from the `domainUID` as `<domainUID>-weblogic-sample-pvc`. | `soainfra-domain-pvc` |
| `productionModeEnabled` | Boolean indicating if production mode is enabled for the domain. | `true` |
| `serverStartPolicy` | Determines which WebLogic Server instances will be started. Legal values are `NEVER`, `IF_NEEDED`, `ADMIN_ONLY`. | `IF_NEEDED` |
| `t3ChannelPort` | Port for the T3 channel of the NetworkAccessPoint. | `30012` |
| `t3PublicAddress` | Public address for the T3 channel.  This should be set to the public address of the Kubernetes cluster.  This would typically be a load balancer address. <p/>For development environments only: In a single server (all-in-one) Kubernetes deployment, this may be set to the address of the master, or at the very least, it must be set to the address of one of the worker nodes. | If not provided, the script will attempt to set it to the IP address of the Kubernetes cluster |
| `weblogicCredentialsSecretName` | Name of the Kubernetes secret for the Administration Server's user name and password. If not specified, then the value is derived from the `domainUID` as `<domainUID>-weblogic-credentials`. | `soainfra-domain-credentials` |
| `weblogicImagePullSecretName` | Name of the Kubernetes secret for the Docker Store, used to pull the WebLogic Server image. |   |
| `serverPodCpuRequest`, `serverPodMemoryRequest`, `serverPodCpuCLimit`, `serverPodMemoryLimit` |  The maximum amount of compute resources allowed, and minimum amount of compute resources required, for each server pod. Please refer to the Kubernetes documentation on `Managing Compute Resources for Containers` for details. | Resource requests and resource limits are not specified. |
| `rcuSchemaPrefix` | The schema prefix to use in the database, for example `SOA1`.  You may wish to make this the same as the domainUID in order to simplify matching domains to their RCU schemas. | `SOA1` |
| `rcuDatabaseURL` | The database URL. | `soadb.soans:1521/soapdb.my.domain.com` |
| `rcuCredentialsSecret` | The Kubernetes secret containing the database credentials. | `soainfra-rcu-credentials` |


Note that the names of the Kubernetes resources in the generated YAML files may be formed with the
value of some of the properties specified in the `create-inputs.yaml` file. Those properties include
the `adminServerName`, `clusterName` and `managedServerNameBase`. If those values contain any
characters that are invalid in a Kubernetes service name, those characters are converted to
valid values in the generated YAML files. For example, an uppercase letter is converted to a
lowercase letter and an underscore `("_")` is converted to a hyphen `("-")`.

The sample demonstrates how to create a SOA Suite domain home and associated Kubernetes resources for a domain
that has one cluster only. In addition, the sample provides the capability for users to supply their own scripts
to create the domain home for other use cases. The generated domain YAML file could also be modified to cover more use cases.

In addition, you should update the generated `domain.yaml` file by performing the following steps.

##### Mandatory Configurations

None.

##### Optional Configurations

You can assign pods to a given node. For more information, see [Assigning Pods to Nodes](https://kubernetes.io/docs/concepts/configuration/assign-pod-node/).

Before using the `nodeSelector` option, make sure that the nodes are already labelled; then use those labels in the `nodeSelector` definition.

Command:  
```
kubectl label node <node-name>  <label-with-value>
```

Example:
```
kubectl label node TestNode name=Test-Node
```

Then you can edit the `domain.yaml`  file to add the `nodeSelector` to the `serverPod` definition, as shown below.

##### nodeSelector for assigning pods:

```bash
managedServers:
 - serverName: soa_server1
   serverPod:
     nodeSelector:
       name: Test-Node
 - serverName: soa_server2
   serverPod:
     nodeSelector:
       name: Test-Node
```



#### Verify the results

The create script will verify that the domain was created, and will report failure if there was any error.
However, it may be desirable to manually verify the domain, even if just to gain familiarity with the
various Kubernetes objects that were created by the script.

Note that the example results below use the `default` Kubernetes namespace. If you are using a different
namespace, you need to replace `NAMESPACE` in the example `kubectl` commands with the actual Kubernetes namespace.

##### Generated YAML files with the default inputs

The content of the generated `domain.yaml`:

```
$ cat output/weblogic-domains/soainfra/domain.yaml
# Copyright 2017, 2019, Oracle Corporation and/or its affiliates. All rights reserved.

# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# This is an example of how to define a Domain resource.
#
apiVersion: "weblogic.oracle/v4"
kind: Domain
metadata:
  name: soainfra
  namespace: soans
  labels:
    weblogic.resourceVersion: domain-v2
    weblogic.domainUID: soainfra
spec:
  # The WebLogic Domain Home
  domainHome: /u01/oracle/user_projects/domains/soainfra
  # If the domain home is in the image
  domainHomeInImage: false
  # The WebLogic Server Docker image that the Operator uses to start the domain
  image: "container-registry.oracle.com/middleware/soasuite:12.2.1.3"
  # imagePullPolicy defaults to "Always" if image version is :latest
  imagePullPolicy: "IfNotPresent"
  # Identify which Secret contains the credentials for pulling an image
  #imagePullSecrets:
  #- name:
  # Identify which Secret contains the WebLogic Admin credentials (note that there is an example of
  # how to create that Secret at the end of this file)
  webLogicCredentialsSecret:
    name: soainfra-domain-credentials
  # Whether to include the server out file into the pod's stdout, default is true
  includeServerOutInPodLog: true
  # Whether to enable log home
  logHomeEnabled: true
  # The in-pod location for domain log, server logs, server out, and Node Manager log files
  logHome: /u01/oracle/user_projects/domains/logs/soainfra
  # serverStartPolicy legal values are "NEVER", "IF_NEEDED", or "ADMIN_ONLY"
  # This determines which WebLogic Servers the Operator will start up when it discovers this Domain
  # - "NEVER" will not start any server in the domain
  # - "ADMIN_ONLY" will start up only the administration server (no managed servers will be started)
  # - "IF_NEEDED" will start all non-clustered servers, including the administration server and clustered servers up to the replica count
  serverStartPolicy: "IF_NEEDED"
  serverPod:
    # an (optional) list of environment variable to be set on the servers
    env:
    - name: JAVA_OPTIONS
      value: "-Dweblogic.StdoutDebugEnabled=false"
    - name: USER_MEM_ARGS
      value: "-XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom "
    volumes:
    - name: weblogic-domain-storage-volume
      persistentVolumeClaim:
        claimName: soainfra-domain-pvc
    volumeMounts:
    - mountPath: /u01/oracle/user_projects/domains
      name: weblogic-domain-storage-volume
    serverService:
      precreateService: true
  # adminServer is used to configure the desired behavior for starting the administration server.
  adminServer:
    # serverStartState legal values are "RUNNING" or "ADMIN"
    # "RUNNING" means the listed server will be started up to "RUNNING" mode
    # "ADMIN" means the listed server will be start up to "ADMIN" mode
    serverStartState: "RUNNING"
    adminService:
      channels:
    # The Admin Server's NodePort
    #    - channelName: default
    #      nodePort: 30701
    # Uncomment to export the T3Channel as a service
       - channelName: T3Channel
  # clusters is used to configure the desired behavior for starting member servers of a cluster.
  # If you use this entry, then the rules will be applied to ALL servers that are members of the named clusters.
  clusters:
  - clusterName: soa_cluster
    serverStartState: "RUNNING"
    replicas: 2
    serverService:
      precreateService: true
  # The number of managed servers to start for unlisted clusters
  # replicas: 1
```

#### Verify the domain

To confirm that the domain was created, use this command:

```
$ kubectl describe domain DOMAINUID -n NAMESPACE
```

Replace `DOMAINUID` with the `domainUID` and `NAMESPACE` with the actual namespace.

Here is an example of the output of this command:

```
$ kubectl describe domain soainfra -n soans
Name:         soainfra
Namespace:    soans
Labels:       weblogic.domainUID=soainfra
              weblogic.resourceVersion=domain-v2
Annotations:  kubectl.kubernetes.io/last-applied-configuration={"apiVersion":"weblogic.oracle/v4","kind":"Domain","metadata":{"annotations":{},"labels":{"weblogic.domainUID":"soainfra","weblogic.resourceVersion":"d...
API Version:  weblogic.oracle/v4
Kind:         Domain
Metadata:
  Cluster Name:
  Creation Timestamp:  2019-07-04T14:12:16Z
  Generation:          0
  Resource Version:    21069865
  Self Link:           /apis/weblogic.oracle/v4/namespaces/soans/domains/soainfra
  UID:                 ba6ed779-9e65-11e9-b5ed-fa163efa261a
Spec:
  Admin Server:
    Admin Service:
      Annotations:
      Channels:
        Channel Name:  T3Channel
      Labels:
    Server Pod:
      Annotations:
      Container Security Context:
      Containers:
      Env:
      Init Containers:
      Labels:
      Liveness Probe:
      Node Selector:
      Pod Security Context:
      Readiness Probe:
      Resources:
        Limits:
        Requests:
      Shutdown:
      Volume Mounts:
      Volumes:
    Server Service:
      Annotations:
      Labels:
    Server Start State:  RUNNING
  Clusters:
    Cluster Name:  soa_cluster
    Cluster Service:
      Annotations:
      Labels:
    Replicas:  2
    Server Pod:
      Annotations:
      Container Security Context:
      Containers:
      Env:
      Init Containers:
      Labels:
      Liveness Probe:
      Node Selector:
      Pod Security Context:
      Readiness Probe:
      Resources:
        Limits:
        Requests:
      Shutdown:
      Volume Mounts:
      Volumes:
    Server Service:
      Annotations:
      Labels:
      Precreate Service:          true
    Server Start State:           RUNNING
  Domain Home:                    /u01/oracle/user_projects/domains/soainfra
  Domain Home In Image:           false
  Image:                          container-registry.oracle.com/middleware/soasuite:12.2.1.3
  Image Pull Policy:              IfNotPresent
  Include Server Out In Pod Log:  true
  Log Home:                       /u01/oracle/user_projects/domains/logs/soainfra
  Log Home Enabled:               true
  Managed Servers:
  Server Pod:
    Annotations:
    Container Security Context:
    Containers:
    Env:
      Name:   JAVA_OPTIONS
      Value:  -Dweblogic.StdoutDebugEnabled=false
      Name:   USER_MEM_ARGS
      Value:  -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom
    Init Containers:
    Labels:
    Liveness Probe:
    Node Selector:
    Pod Security Context:
    Readiness Probe:
    Resources:
      Limits:
      Requests:
    Shutdown:
    Volume Mounts:
      Mount Path:  /u01/oracle/user_projects/domains
      Name:        weblogic-domain-storage-volume
    Volumes:
      Name:  weblogic-domain-storage-volume
      Persistent Volume Claim:
        Claim Name:  soainfra-domain-pvc
  Server Service:
    Annotations:
    Labels:
  Server Start Policy:  IF_NEEDED
  Web Logic Credentials Secret:
    Name:  soainfra-domain-credentials
Status:
  Conditions:
  Modified:  true
  Replicas:  2
  Servers:
    Cluster Name:  soa_cluster
    Node Name:     TESTNODE
    Server Name:   soa_server2
    State:         UNKNOWN
    Cluster Name:  soa_cluster
    Node Name:     TESTNODE
    Server Name:   soa_server1
    State:         UNKNOWN
    Server Name:   soa_server4
    State:         SHUTDOWN
    Server Name:   soa_server3
    State:         SHUTDOWN
    Health:
      Activation Time:  2019-07-04T14:16:34.780Z
      Overall Health:   ok
      Subsystems:
    Node Name:    TESTNODE
    Server Name:  AdminServer
    State:        RUNNING
    Server Name:  soa_server5
    State:        SHUTDOWN
  Start Time:     2019-07-04T14:12:16.871Z
Events:           <none>
```

In the `Status` section of the output, the available servers and clusters are listed.
Note that if this command is issued very soon after the script finishes, there may be
no servers available yet, or perhaps only the Administration Server but no Managed Servers.
The operator will start up the Administration Server first and wait for it to become ready
before starting the Managed Servers.

#### Verify the pods

Use the following command to see the pods running the servers:

```
$ kubectl get pods -n NAMESPACE
```

Here is an example of the output of this command:

```
$ kubectl get pods -n soans
NAME                   READY     STATUS    RESTARTS   AGE
soainfra-adminserver   1/1       Running   0          1h
soainfra-soa-server1   1/1       Running   0          1h
soainfra-soa-server2   1/1       Running   0          1h
```

#### Verify the services

Use the following command to see the services for the domain:

```
$ kubectl get services -n NAMESPACE
```

Here is an example of the output of this command:
```
$ kubectl get services -n soans
NAME                            TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)              AGE
soainfra-adminserver            ClusterIP   None             <none>        30012/TCP,7001/TCP   1h
soainfra-adminserver-external   NodePort    10.99.147.149    <none>        30012:30012/TCP      1h
soainfra-cluster-soa-cluster    ClusterIP   10.103.205.66    <none>        8001/TCP             1h
soainfra-soa-server1            ClusterIP   None             <none>        8001/TCP             1h
soainfra-soa-server2            ClusterIP   None             <none>        8001/TCP             1h
soainfra-soa-server3            ClusterIP   10.109.227.78    <none>        8001/TCP             1h
soainfra-soa-server4            ClusterIP   10.101.147.207   <none>        8001/TCP             1h
soainfra-soa-server5            ClusterIP   10.105.14.5      <none>        8001/TCP             1h
```

#### Delete the generated domain home

Sometimes in production, but most likely in testing environments, you might want to remove the domain
home that is generated using the `create-domain.sh` script. Do this by running the generated
`delete domain job` script in the `/<path to weblogic-operator-output-directory>/weblogic-domains/<domainUID>` directory.

```
$ kubectl create -f delete-domain-job.yaml

```
