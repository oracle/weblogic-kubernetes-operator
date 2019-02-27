# WebLogic sample domain home on a persistent volume

The sample scripts demonstrate the creation of a WebLogic domain home on an existing Kubernetes persistent volume (PV) and persistent volume claim (PVC). The scripts also generate the domain YAML file, which can then be used to start the Kubernetes artifacts of the corresponding domain. Optionally, the scripts start up the domain, and WebLogic Server pods and services.

## Prerequisites

Before you begin, read this guide, [Domain Resource](../../../../../site/domain-resource.md).

The following prerequisites must be handled prior to running the create domain script:
* Make sure the WebLogic operator is running.
* The operator requires WebLogic Server 12.2.1.3.0 with patch 29135930 applied. The existing WebLogic Docker image, `store/oracle/weblogic:12.2.1.3`, was updated on January 17, 2019, and has all the necessary patches applied; a `docker pull` is required if you pulled the image prior to that date. Refer to [WebLogic Docker images](../../../../../site/weblogic-docker-images.md) for details on how to obtain or create the image.
* Create a Kubernetes namespace for the domain unless the intention is to use the default namespace.
* In the same Kubernetes namespace, create the Kubernetes persistent volume (PV) where the domain home will be hosted, and the Kubernetes persistent volume claim (PVC) for the domain. For samples to create a PV and PVC, see [Create sample PV and PVC](../../create-weblogic-domain-pv-pvc/README.md). By default, the `create-domain.sh` script creates a domain with the `domainUID` set to `domain1` and expects the PVC `domain1-weblogic-sample-pvc` to be present. You can create `domain1-weblogic-sample-pvc` using [create-pv-pvc.sh](../../create-weblogic-domain-pv-pvc/create-pv-pvc.sh) with an inputs file that has the `domainUID` set to `domain1`.
* Create the Kubernetes secrets `username` and `password` of the admin account in the same Kubernetes namespace as the domain.

## Use the script to create a domain

Make a copy of the `create-domain-inputs.yaml` file, and run the create script, pointing it at your inputs file and an output directory:

```
$ ./create-domain.sh \
  -i create-domain-inputs.yaml \
  -o /path/to/output-directory
```

The script will perform the following steps:

* Create a directory for the generated Kubernetes YAML files for this domain if it does not already exist.  The pathname is `/path/to/weblogic-operator-output-directory/weblogic-domains/<domainUID>`. If the directory already exists, its contents must be removed before using this script.
* Create a Kubernetes job that will start up a utility WebLogic Server container and run offline WLST scripts, or WebLogic Deploy Tool (WDT) scripts, to create the domain on the shared storage.
* Run and wait for the job to finish.
* Create a Kubernetes domain YAML file, `domain.yaml`, in the directory that is created above. This YAML file can be used to create the Kubernetes resource using the `kubectl create -f` or `kubectl apply -f` command:
```
$ kubectl apply -f /path/to/output-directory/weblogic-domains/<domainUID>/domain.yaml
```

* Create a convenient utility script, `delete-domain-job.yaml`, to clean up the domain home created by the create script.

As a convenience, using the `-e` option, the script can optionally create the domain object, which in turn results in the creation of the corresponding WebLogic Server pods and services as well.

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

If you copy the sample scripts to a different location, make sure that you copy everything in the `<weblogic-kubernetes-operator-project>/kubernetes/samples/scripts` directory together into the target directory, maintaining the original directory hierarchy.

The default domain created by the script has the following characteristics:

* An Administration Server named `admin-server` listening on port `7001`.
* A dynamic cluster named `cluster-1` of size 5.
* Two Managed Servers, named `managed-server1` and `managed-server2`, listening on port `8001`.
* Log files that are located in `/shared/logs/<domainUID>`.
* No applications deployed.
* No data sources or JMS resources.
* A T3 channel.

The domain creation inputs can be customized by editing `create-domain-inputs.yaml`.

### Configuration parameters
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
| `image` | WebLogic Docker image. The operator requires WebLogic Server 12.2.1.3.0 with patch 29135930 applied. The existing WebLogic Docker image, `store/oracle/weblogic:12.2.1.3`, was updated on January 17, 2019, and has all the necessary patches applied; a `docker pull` is required if you pulled the image prior to that date. Refer to [WebLogic Docker images](../../../../../site/weblogic-docker-images.md) for details on how to obtain or create the image. | `store/oracle/weblogic:12.2.1.3` |
| `imagePullPolicy` | WebLogic Docker image pull policy. Legal values are `IfNotPresent`, `Always`, or `Never` | `IfNotPresent` |
| `imagePullSecretName` | Name of the Kubernetes secret to access the Docker Store to pull the WebLogic Server Docker image. The presence of the secret will be validated when this parameter is specified |  |
| `includeServerOutInPodLog` | Boolean indicating whether to include the server .out to the pod's stdout. | `true` |
| `initialManagedServerReplicas` | Number of Managed Servers to initially start for the domain. | `2` |
| `javaOptions` | Java options for starting the Administration and Managed Servers. A Java option can have references to one or more of the following pre-defined variables to obtain WebLogic domain information: `$(DOMAIN_NAME)`, `$(DOMAIN_HOME)`, `$(ADMIN_NAME)`, `$(ADMIN_PORT)`, and `$(SERVER_NAME)`. | `-Dweblogic.StdoutDebugEnabled=false` |
| `logHome` | The in-pod location for domain log, server logs, server out, and Node Manager log files. If not specified, the value is derived from the `domainUID` as `/shared/logs/<domainUID>`. | `/shared/logs/domain1` |
| `managedServerNameBase` | Base string used to generate Managed Server names. | `managed-server` |
| `managedServerPort` | Port number for each Managed Server. | `8001` |
| `namespace` | Kubernetes namespace in which to create the domain. | `default` |
| `persistentVolumeClaimName` | Name of the persistent volume claim. If not specified, the value is derived from the `domainUID` as `<domainUID>-weblogic-sample-pvc` | `domain1-weblogic-sample-pvc` |
| `productionModeEnabled` | Boolean indicating if production mode is enabled for the domain. | `true` |
| `serverStartPolicy` | Determines which WebLogic Servers will be started up. Legal values are `NEVER`, `IF_NEEDED`, `ADMIN_ONLY`. | `IF_NEEDED` |
| `t3ChannelPort` | Port for the T3 channel of the NetworkAccessPoint. | `30012` |
| `t3PublicAddress` | Public address for the T3 channel.  This should be set to the public address of the Kubernetes cluster.  This would normally be a load balancer address. <p/>For development environments only: In a single server (all-in-one) Kubernetes deployment, this may be set to the address of the master, or at the very least, it must be set to the address of one of the worker nodes. | If not provided, the script will attempt to set it to the IP address of the kubernetes cluster |
| `weblogicCredentialsSecretName` | Name of the Kubernetes secret for the Administration Server's username and password. If not specified, the value is derived from the `domainUID` as `<domainUID>-weblogic-credentials`. | `domain1-weblogic-credentials` |
| `weblogicImagePullSecretName` | Name of the Kubernetes secret for the Docker Store, used to pull the WebLogic Server image. | `docker-store-secret` |

Note that the names of the Kubernetes resources in the generated YAML files may be formed with the value of some of the properties specified in the `create-inputs.yaml` file. Those properties include the `adminServerName`, `clusterName` and `managedServerNameBase`. If those values contain any characters that are invalid in a Kubernetes service name, those characters are converted to valid values in the generated YAML files. For example, an uppercase letter is converted to a lowercase letter and an underscore `("_")` is converted to a hyphen `("-")`.

The sample demonstrates how to create a WebLogic domain home and associated Kubernetes resources for a domain that only has one cluster. In addition, the sample provides the capability for users to supply their own scripts to create the domain home for other use cases. The generated domain YAML file could also be modified to cover more use cases.

## Verify the results

The create script will verify that the domain was created, and will report failure if there was any error.  However, it may be desirable to manually verify the domain, even if just to gain familiarity with the various Kubernetes objects that were created by the script.

Note that the example results below use the `default` Kubernetes namespace. If you are using a different namespace, you need to replace `NAMESPACE` in the example `kubectl` commands with the actual Kubernetes namespace.

### Generated YAML files with the default inputs

The content of the generated `domain.yaml`:

```
# Copyright 2017, 2019, Oracle Corporation and/or its affiliates. All rights reserved.

# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# This is an example of how to define a Domain resource.
#
apiVersion: "weblogic.oracle/v2"
kind: Domain
metadata:
  name: domain1
  namespace: default
  labels:
    weblogic.resourceVersion: domain-v2
    weblogic.domainUID: domain1
spec:
  # The WebLogic Domain Home
  domainHome: /shared/domains/domain1
  # If the domain home is in the image
  domainHomeInImage: false
  # The WebLogic Server Docker image that the operator uses to start the domain
  image: "store/oracle/weblogic:12.2.1.3"
  # imagePullPolicy defaults to "Always" if image version is :latest
  imagePullPolicy: "IfNotPresent"
  # Identify which Secret contains the credentials for pulling an image
  #imagePullSecrets:
  #- name:
  # Identify which Secret contains the WebLogic Admin credentials (note that there is an example of
  # how to create that Secret at the end of this file)
  webLogicCredentialsSecret:
    name: domain1-weblogic-credentials
  # Whether to include the server out file into the pod's stdout, default is true
  includeServerOutInPodLog: true
  # Whether to enable log home
  logHomeEnabled: true
  # The in-pod name location for domain log, server logs, server out, and Node Manager log files
  logHome: /shared/logs/domain1
  # serverStartPolicy legal values are "NEVER", "IF_NEEDED", or "ADMIN_ONLY"
  # This determines which WebLogic Servers the operator will start up when it discovers this Domain
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
      value: "-Djava.security.egd=file:/dev/./urandom -Xms64m -Xmx256m "
    volumes:
    - name: weblogic-domain-storage-volume
      persistentVolumeClaim:
        claimName: domain1-weblogic-sample-pvc
    volumeMounts:
    - mountPath: /shared
      name: weblogic-domain-storage-volume
  # adminServer is used to configure the desired behavior for starting the administration server.
  adminServer:
    # serverStartState legal values are "RUNNING" or "ADMIN"
    # "RUNNING" means the listed server will be started up to "RUNNING" mode
    # "ADMIN" means the listed server will be start up to "ADMIN" mode
    serverStartState: "RUNNING"
    # adminService:
    #   channels:
    # The Admin Server's NodePort
    #    - channelName: default
    #      nodePort: 30701
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

### Verify the domain

To confirm that the domain was created, use this command:

```
$ kubectl describe domain DOMAINUID -n NAMESPACE
```

Replace `DOMAINUID` with the `domainUID` and `NAMESPACE` with the actual namespace.

Here is an example of the output of this command:

```
$ kubectl describe domain domain1
Name:         domain1
Namespace:    default
Labels:       weblogic.domainUID=domain1
              weblogic.resourceVersion=domain-v2
Annotations:  kubectl.kubernetes.io/last-applied-configuration={"apiVersion":"weblogic.oracle/v2","kind":"Domain","metadata":{"annotations":{},"labels":{"weblogic.domainUID":"domain1","weblogic.resourceVersion":"do...
API Version:  weblogic.oracle/v2
Kind:         Domain
Metadata:
  Cluster Name:        
  Creation Timestamp:  2019-01-10T14:50:52Z
  Generation:          1
  Resource Version:    3700284
  Self Link:           /apis/weblogic.oracle/v2/namespaces/default/domains/domain1
  UID:                 2023ae0a-14e7-11e9-b751-fa163e855ac8
Spec:
  Admin Server:
    Server Pod:
      Annotations:
      Container Security Context:
      Env:
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
    Replicas:  2
    Server Pod:
      Annotations:
      Container Security Context:
      Env:
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
  Domain Home:                    /shared/domains/domain1
  Domain Home In Image:           false
  Image:                          store/oracle/weblogic:12.2.1.3
  Image Pull Policy:              IfNotPresent
  Include Server Out In Pod Log:  true
  Log Home:                       /shared/logs/domain1
  Log Home Enabled:               true
  Managed Servers:
  Server Pod:
    Annotations:
    Container Security Context:
    Env:
      Name:   JAVA_OPTIONS
      Value:  -Dweblogic.StdoutDebugEnabled=false
      Name:   USER_MEM_ARGS
      Value:  -Xms64m -Xmx256m
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
        Claim Name:  domain1-weblogic-sample-pvc
  Server Service:
    Annotations:
    Labels:
  Server Start Policy:  IF_NEEDED
  Web Logic Credentials Secret:
    Name:  domain1-weblogic-credentials
Status:
  Conditions:
    Last Transition Time:  2019-01-10T14:52:33.878Z
    Reason:                ServersReady
    Status:                True
    Type:                  Available
  Servers:
    Health:
      Activation Time:  2019-01-10T14:52:07.351Z
      Overall Health:   ok
      Subsystems:
    Node Name:     slc16ffk
    Server Name:   admin-server
    State:         RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2019-01-10T14:53:30.352Z
      Overall Health:   ok
      Subsystems:
    Node Name:     slc16ffk
    Server Name:   managed-server1
    State:         RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2019-01-10T14:53:26.503Z
      Overall Health:   ok
      Subsystems:
    Node Name:    slc16ffk
    Server Name:  managed-server2
    State:        RUNNING
  Start Time:     2019-01-10T14:50:52.104Z
Events:           <none>
```

In the `Status` section of the output, the available servers and clusters are listed.  Note that if this command is issued very soon after the script finishes, there may be no servers available yet, or perhaps only the Administration Server but no Managed Servers.  The operator will start up the Administration Server first and wait for it to become ready before starting the Managed Servers.

### Verify the pods

Use the following command to see the pods running the servers:

```
$ kubectl get pods -n NAMESPACE
```

Here is an example of the output of this command:

```
$ kubectl get pods
NAME                                         READY     STATUS    RESTARTS   AGE
domain1-admin-server                         1/1       Running   0          1m
domain1-managed-server1                      1/1       Running   0          8m
domain1-managed-server2                      1/1       Running   0          8m
```

### Verify the services

Use the following command to see the services for the domain:

```
$ kubectl get services -n NAMESPACE
```

Here is an example of the output of this command:
```
$ kubectl get services
NAME                                        TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)           AGE
domain1-admin-server                        ClusterIP   10.96.206.134    <none>        7001/TCP          23m
domain1-admin-server-external               NodePort    10.107.164.241   <none>        30012:30012/TCP   22m
domain1-cluster-cluster-1                   ClusterIP   10.109.133.168   <none>        8001/TCP          22m
domain1-managed-server1                     ClusterIP   None             <none>        8001/TCP          22m
domain1-managed-server2                     ClusterIP   None             <none>        8001/TCP          22m
```

## Delete the generated domain home

Sometimes in production, but most likely in testing environments, you might want to remove the domain home that is generated using the `create-domain.sh` script. Do this by running the generated `delete domain job` script in the `/path/to/weblogic-operator-output-directory/weblogic-domains/<domainUID>` directory.

```
$ kubectl create -f delete-domain-job.yaml

```
