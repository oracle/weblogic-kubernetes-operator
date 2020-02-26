---
title: "FMW Infrastructure domain"
date: 2019-04-18T07:32:31-05:00
weight: 2
description: "Sample for creating an FMW Infrastructure domain home on an existing PV or
PVC, and the domain resource YAML file for deploying the generated WebLogic domain."
---


The sample scripts demonstrate the creation of an FMW Infrastructure domain home on an
existing Kubernetes persistent volume (PV) and persistent volume claim (PVC). The scripts
also generate the domain YAML file, which can then be used to start the Kubernetes
artifacts of the corresponding domain. Optionally, the scripts start up the domain,
and WebLogic Server pods and services.

#### Prerequisites

Before you begin, read this document, [Domain resource]({{< relref "/userguide/managing-domains/domain-resource/_index.md" >}}).

The following prerequisites must be handled prior to running the create domain script:

* Make sure the WebLogic Kubernetes Operator is running.
* The operator requires FMW Infrastructure 12.2.1.3.0 with patch 29135930 applied. For details on how to obtain or create the image, refer to [FMW Infrastructure domains]({{< relref "/userguide/managing-domains/fmw-infra/_index.md#obtaining-the-fmw-infrastructure-docker-image" >}}).
* Create a Kubernetes namespace for the domain unless you intend to use the default namespace.
* In the same Kubernetes namespace, create the Kubernetes persistent volume (PV) where the domain
  home will be hosted, and the Kubernetes persistent volume claim (PVC) for the domain. For samples
  to create a PV and PVC, see [Create sample PV and PVC]({{< relref "/samples/simple/storage/_index.md" >}}).
  By default, the `create-domain.sh` script creates a domain with the `domainUID` set to `domain1`
  and expects the PVC `domain1-weblogic-sample-pvc` to be present. You can create
  `domain1-weblogic-sample-pvc` using
  [create-pv-pvc.sh](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc.sh)
  with an inputs file that has the `domainUID` set to `domain1`.
* Create the Kubernetes secrets `username` and `password` of the administrative account in the same Kubernetes
  namespace as the domain.
* Configure access to your database. For details, see [here]({{< relref "/userguide/managing-fmw-domains/fmw-infra/_index.md#configuring-access-to-your-database" >}}).  
* Create a Kubernetes secret with the RCU credentials. For details, refer to this [document](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/kubernetes/samples/scripts/create-rcu-credentials/README.md).

#### Use the script to create a domain

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
* Create a Kubernetes job that will start up a utility FMW Infrastructure container and run
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

As a convenience, using the `-e` option, the script can optionally create the domain object,
which in turn results in the creation of the corresponding WebLogic Server pods and services as well.

The usage of the create script is as follows:

```
$ sh create-domain.sh -h
usage: create-domain.sh -o dir -i file [-e] [-v] [-h]
  -i Parameter inputs file, must be specified.
  -o Output directory for the generated YAML files, must be specified.
  -e Also create the resources in the generated YAML files, optional.
  -v Validate the existence of persistentVolumeClaim, optional.
  -h Help

```

If you copy the sample scripts to a different location, make sure that you copy everything in
the `<weblogic-kubernetes-operator-project>/kubernetes/samples/scripts` directory together
into the target directory, maintaining the original directory hierarchy.

The default domain created by the script has the following characteristics:

* An Administration Server named `admin-server` listening on port `7001`.
* A configured cluster named `cluster-1` of size 5.
* Five Managed Servers, named `managed-server1`, `managed-server2`, and so on, listening on port `8001`.
* Log files that are located in `/shared/logs/<domainUID>`.
* No applications deployed.
* No data sources or JMS resources.
* A T3 channel.

The domain creation inputs can be customized by editing `create-domain-inputs.yaml`.

#### Configuration parameters
The following parameters can be provided in the inputs file.

| Parameter | Definition | Default |
| --- | --- | --- |
| `adminPort` | Port number for the Administration Server inside the Kubernetes cluster. | `7001` |
| `adminNodePort` | Port number of the Administration Server outside the Kubernetes cluster. | `30701` |
| `adminServerName` | Name of the Administration Server. | `admin-server` |
| `clusterName` | Name of the WebLogic cluster instance to generate for the domain. | `cluster-1` |
| `configuredManagedServerCount` | Number of Managed Server instances to generate for the domain. | `5` |
| `createDomainFilesDir` | Directory on the host machine to locate all the files to create a WebLogic domain, including the script that is specified in the `createDomainScriptName` property. By default, this directory is set to the relative path `wlst`, and the create script will use the built-in WLST offline scripts in the `wlst` directory to create the WebLogic domain. It can also be set to the relative path `wdt`, and then the built-in WDT scripts will be used instead. An absolute path is also supported to point to an arbitrary directory in the file system. The built-in scripts can be replaced by the user-provided scripts or model files as long as those files are in the specified directory. Files in this directory are put into a Kubernetes config map, which in turn is mounted to the `createDomainScriptsMountPath`, so that the Kubernetes pod can use the scripts and supporting files to create a domain home. | `wlst` |
| `createDomainScriptsMountPath` | Mount path where the create domain scripts are located inside a pod. The `create-domain.sh` script creates a Kubernetes job to run the script (specified in the `createDomainScriptName` property) in a Kubernetes pod to create a domain home. Files in the `createDomainFilesDir` directory are mounted to this location in the pod, so that the Kubernetes pod can use the scripts and supporting files to create a domain home. | `/u01/weblogic` |
| `createDomainScriptName` | Script that the create domain script uses to create a WebLogic domain. The `create-domain.sh` script creates a Kubernetes job to run this script to create a domain home. The script is located in the in-pod directory that is specified in the `createDomainScriptsMountPath` property. If you need to provide your own scripts to create the domain home, instead of using the built-it scripts, you must use this property to set the name of the script that you want the create domain job to run. | `create-domain-job.sh` |
| `domainHome` | Home directory of the WebLogic domain. If not specified, the value is derived from the `domainUID` as `/shared/domains/<domainUID>`. | `/shared/domains/domain1` |
| `domainPVMountPath` | Mount path of the domain persistent volume. | `/shared` |
| `domainUID` | Unique ID that will be used to identify this particular domain. Used as the name of the generated WebLogic domain as well as the name of the Kubernetes domain resource. This ID must be unique across all domains in a Kubernetes cluster. This ID cannot contain any character that is not valid in a Kubernetes service name. | `domain1` |
| `exposeAdminNodePort` | Boolean indicating if the Administration Server is exposed outside of the Kubernetes cluster. | `false` |
| `exposeAdminT3Channel` | Boolean indicating if the T3 administrative channel is exposed outside the Kubernetes cluster. | `false` |
| `image` | WebLogic Docker image. The operator requires FMW Infrastructure 12.2.1.3.0 with patch 29135930 applied. Refer to [FMW Infrastructure domains]({{< relref "/userguide/managing-fmw-domains/fmw-infra/_index.md#obtaining-the-fmw-infrastructure-docker-image" >}}) for details on how to obtain or create the image. | `container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3` |
| `imagePullPolicy` | WebLogic Docker image pull policy. Legal values are `IfNotPresent`, `Always`, or `Never` | `IfNotPresent` |
| `imagePullSecretName` | Name of the Kubernetes secret to access the Docker Store to pull the WebLogic Server Docker image. The presence of the secret will be validated when this parameter is specified. |  |
| `includeServerOutInPodLog` | Boolean indicating whether to include the server .out to the pod's stdout. | `true` |
| `initialManagedServerReplicas` | Number of Managed Servers to initially start for the domain. | `2` |
| `javaOptions` | Java options for starting the Administration Server and Managed Servers. A Java option can have references to one or more of the following pre-defined variables to obtain WebLogic domain information: `$(DOMAIN_NAME)`, `$(DOMAIN_HOME)`, `$(ADMIN_NAME)`, `$(ADMIN_PORT)`, and `$(SERVER_NAME)`. | `-Dweblogic.StdoutDebugEnabled=false` |
| `logHome` | The in-pod location for the domain log, server logs, server out, and Node Manager log files. If not specified, the value is derived from the `domainUID` as `/shared/logs/<domainUID>`. | `/shared/logs/domain1` |
| `managedServerNameBase` | Base string used to generate Managed Server names. | `managed-server` |
| `managedServerPort` | Port number for each Managed Server. | `8001` |
| `namespace` | Kubernetes namespace in which to create the domain. | `default` |
| `persistentVolumeClaimName` | Name of the persistent volume claim. If not specified, the value is derived from the `domainUID` as `<domainUID>-weblogic-sample-pvc`. | `domain1-weblogic-sample-pvc` |
| `productionModeEnabled` | Boolean indicating if production mode is enabled for the domain. | `true` |
| `serverStartPolicy` | Determines which WebLogic Server instances will be started. Legal values are `NEVER`, `IF_NEEDED`, `ADMIN_ONLY`. | `IF_NEEDED` |
| `t3ChannelPort` | Port for the T3 channel of the NetworkAccessPoint. | `30012` |
| `t3PublicAddress` | Public address for the T3 channel.  This should be set to the public address of the Kubernetes cluster.  This would typically be a load balancer address. <p/>For development environments only: In a single server (all-in-one) Kubernetes deployment, this may be set to the address of the master, or at the very least, it must be set to the address of one of the worker nodes. | If not provided, the script will attempt to set it to the IP address of the Kubernetes cluster |
| `weblogicCredentialsSecretName` | Name of the Kubernetes secret for the Administration Server's user name and password. If not specified, then the value is derived from the `domainUID` as `<domainUID>-weblogic-credentials`. | `domain1-weblogic-credentials` |
| `weblogicImagePullSecretName` | Name of the Kubernetes secret for the Docker Store, used to pull the WebLogic Server image. | `docker-store-secret` |
| `serverPodCpuRequest`, `serverPodMemoryRequest`, `serverPodCpuCLimit`, `serverPodMemoryLimit` |  The maximum amount of compute resources allowed, and minimum amount of compute resources required, for each server pod. Please refer to the Kubernetes documentation on `Managing Compute Resources for Containers` for details. | Resource requests and resource limits are not specified. |
| `rcuSchemaPrefix` | The schema prefix to use in the database, for example `SOA1`.  You may wish to make this the same as the domainUID in order to simplify matching domains to their RCU schemas. | `domain1` |
| `rcuDatabaseURL` | The database URL. | `database:1521/service` |
| `rcuCredentialsSecret` | The Kubernetes secret containing the database credentials. | `domain1-rcu-credentials` |

Note that the names of the Kubernetes resources in the generated YAML files may be formed with the
value of some of the properties specified in the `create-inputs.yaml` file. Those properties include
the `adminServerName`, `clusterName` and `managedServerNameBase`. If those values contain any
characters that are invalid in a Kubernetes service name, those characters are converted to
valid values in the generated YAML files. For example, an uppercase letter is converted to a
lowercase letter and an underscore `("_")` is converted to a hyphen `("-")`.

The sample demonstrates how to create a WebLogic domain home and associated Kubernetes resources for a domain
that has one cluster only. In addition, the sample provides the capability for users to supply their own scripts
to create the domain home for other use cases. The generated domain YAML file could also be modified to cover more use cases.

#### Verify the results

The create script will verify that the domain was created, and will report failure if there was any error.
However, it may be desirable to manually verify the domain, even if just to gain familiarity with the
various Kubernetes objects that were created by the script.

Note that the example results below use the `default` Kubernetes namespace. If you are using a different
namespace, you need to replace `NAMESPACE` in the example `kubectl` commands with the actual Kubernetes namespace.

##### Generated YAML files with the default inputs

The content of the generated `domain.yaml`:

```
# Copyright 2017, 2019, Oracle Corporation and/or its affiliates. All rights reserved.

# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# This is an example of how to define a Domain resource.
#
apiVersion: "weblogic.oracle/v4"
kind: Domain
metadata:
  name: fmw-domain
  namespace: default
  labels:
    weblogic.resourceVersion: domain-v2
    weblogic.domainUID: fmw-domain
spec:
  # The WebLogic Domain Home
  domainHome: /shared/domains/fmw-domain
  # If the domain home is in the image
  domainHomeInImage: false
  # The WebLogic Server Docker image that the Operator uses to start the domain
  image: “container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3"
  # imagePullPolicy defaults to "Always" if image version is :latest
  imagePullPolicy: "IfNotPresent"
  # Identify which Secret contains the credentials for pulling an image
  #imagePullSecrets:
  #- name:
  # Identify which Secret contains the WebLogic Admin credentials (note that there is an example of
  # how to create that Secret at the end of this file)
  webLogicCredentialsSecret:
    name: fmw-domain-weblogic-credentials
  # Whether to include the server out file into the pod's stdout, default is true
  includeServerOutInPodLog: true
  # Whether to enable log home
  logHomeEnabled: true
  # The in-pod location for domain log, server logs, server out, and Node Manager log files
  logHome: /shared/logs/fmw-domain
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
      value: "-Djava.security.egd=file:/dev/./urandom "
    volumes:
    - name: weblogic-domain-storage-volume
      persistentVolumeClaim:
        claimName: fmw-domain-weblogic-pvc
    volumeMounts:
    - mountPath: /shared
      name: weblogic-domain-storage-volume
  # adminServer is used to configure the desired behavior for starting the administration server.
  adminServer:
    # serverStartState legal values are "RUNNING" or "ADMIN"
    # "RUNNING" means the listed server will be started up to "RUNNING" mode
    # "ADMIN" means the listed server will be start up to "ADMIN" mode
    serverStartState: "RUNNING"
    adminService:
      channels:
    # The Admin Server's NodePort
       - channelName: default
         nodePort: 30731
    # Uncomment to export the T3Channel as a service
    #    - channelName: T3Channel
  # clusters is used to configure the desired behavior for starting member servers of a cluster.  
  # If you use this entry, then the rules will be applied to ALL servers that are members of the named clusters.
  clusters:
  - clusterName: cluster-1
    serverStartState: "RUNNING"
    replicas: 2
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
Name:         fmw-domain
Namespace:    default
Labels:       weblogic.domainUID=fmw-domain
              weblogic.resourceVersion=domain-v2
Annotations:  kubectl.kubernetes.io/last-applied-configuration:
                {"apiVersion":"weblogic.oracle/v4","kind":"Domain","metadata":{"annotations":{},"labels":{"weblogic.domainUID":"fmw-domain","weblogic.reso...
API Version:  weblogic.oracle/v4
Kind:         Domain
Metadata:
  Creation Timestamp:  2019-04-18T00:11:15Z
  Generation:          23
  Resource Version:    5904947
  Self Link:           /apis/weblogic.oracle/v4/namespaces/default/domains/fmw-domain
  UID:                 7b3477e2-616e-11e9-ab7b-000017024fa2
Spec:
  Admin Server:
    Admin Service:
      Annotations:
      Channels:
        Channel Name:  default
        Node Port:     30731
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
      Volume Mounts:
      Volumes:
    Server Service:
      Annotations:
      Labels:
    Server Start State:  RUNNING
  Clusters:
    Cluster Name:  cluster-1
    Cluster Service:
      Annotations:
      Labels:
    Replicas:  4
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
      Volume Mounts:
      Volumes:
    Server Service:
      Annotations:
      Labels:
    Server Start State:           RUNNING
  Domain Home:                    /shared/domains/fmw-domain
  Domain Home In Image:           false
  Image:                          container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3
  Image Pull Policy:              IfNotPresent
  Include Server Out In Pod Log:  true
  Log Home:                       /shared/logs/fmw-domain
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
      Value:  -Djava.security.egd=file:/dev/./urandom
    Init Containers:
    Labels:
    Liveness Probe:
    Node Selector:
    Pod Security Context:
    Readiness Probe:
    Resources:
      Limits:
      Requests:
    Volume Mounts:
      Mount Path:  /shared
      Name:        weblogic-domain-storage-volume
    Volumes:
      Name:  weblogic-domain-storage-volume
      Persistent Volume Claim:
        Claim Name:  fmw-domain-weblogic-pvc
  Server Service:
    Annotations:
    Labels:
  Server Start Policy:  IF_NEEDED
  Web Logic Credentials Secret:
    Name:  fmw-domain-weblogic-credentials
Status:
  Conditions:
    Last Transition Time:  2019-04-18T00:14:34.322Z
    Reason:                ServersReady
    Status:                True
    Type:                  Available
  Modified:                true
  Replicas:                4
  Servers:
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2019-04-18T00:18:46.787Z
      Overall Health:   ok
      Subsystems:
    Node Name:     mark
    Server Name:   managed-server4
    State:         RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2019-04-18T00:18:46.439Z
      Overall Health:   ok
      Subsystems:
    Node Name:     mark
    Server Name:   managed-server3
    State:         RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2019-04-18T00:14:19.227Z
      Overall Health:   ok
      Subsystems:
    Node Name:     mark
    Server Name:   managed-server2
    State:         RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2019-04-18T00:14:17.747Z
      Overall Health:   ok
      Subsystems:
    Node Name:    mark
    Server Name:  managed-server1
    State:        RUNNING
    Health:
      Activation Time:  2019-04-18T00:13:13.836Z
      Overall Health:   ok
      Subsystems:
    Node Name:    mark
    Server Name:  admin-server
    State:        RUNNING
  Start Time:     2019-04-18T00:11:15.306Z
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
$ kubectl get pods
NAME                                     READY   STATUS    RESTARTS   AGE
fmw-domain-admin-server                  1/1     Running   0          15h
fmw-domain-managed-server1               1/1     Running   0          15h
fmw-domain-managed-server2               1/1     Running   0          15h
fmw-domain-managed-server3               1/1     Running   0          15h
fmw-domain-managed-server4               1/1     Running   0          15h

```

#### Verify the services

Use the following command to see the services for the domain:

```
$ kubectl get services -n NAMESPACE
```

Here is an example of the output of this command:
```
$ kubectl get services
NAME                                TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)           AGE
fmw-domain-admin-server             ClusterIP   None             <none>        7001/TCP          15h
fmw-domain-admin-server-external    NodePort    10.101.26.42     <none>        7001:30731/TCP    15h
fmw-domain-cluster-cluster-1        ClusterIP   10.107.55.188    <none>        8001/TCP          15h
fmw-domain-managed-server1          ClusterIP   None             <none>        8001/TCP          15h
fmw-domain-managed-server2          ClusterIP   None             <none>        8001/TCP          15h
fmw-domain-managed-server3          ClusterIP   None             <none>        8001/TCP          15h
fmw-domain-managed-server4          ClusterIP   None             <none>        8001/TCP          15h

```

#### Delete the generated domain home

Sometimes in production, but most likely in testing environments, you might want to remove the domain
home that is generated using the `create-domain.sh` script. Do this by running the generated
`delete domain job` script in the `/<path to output-directory>/weblogic-domains/<domainUID>` directory.

```
$ kubectl create -f delete-domain-job.yaml

```
