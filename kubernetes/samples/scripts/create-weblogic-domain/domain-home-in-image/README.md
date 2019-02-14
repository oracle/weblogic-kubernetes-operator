# WebLogic sample domain home in Docker image

The sample scripts demonstrate the creation of a WebLogic domain home in a Docker image using one of the domain home in image samples in the [WebLogic Server Domain Docker image samples GitHub project](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples). The sample scripts have an option of putting the WebLogic domain log, server logs, server output files, and the Node Manager logs on an existing Kubernetes persistent volume (PV) and persistent volume claim (PVC). The scripts also generate the domain YAML file, which can then be used by the scripts or used manually to start the Kubernetes artifacts of the corresponding domain, including the WebLogic Server pods and services.

## Prerequisites

Before you begin, read this guide, [Domain Resource](../../../../../site/domain-resource.md).

The following prerequisites must be handled prior to running the create domain script:
* The WDT sample requires that `JAVA_HOME` is set to a Java JDK version 1.8 or later.
* The operator requires WebLogic Server 12.2.1.3.0 with patch 29135930 applied. The existing WebLogic Docker image, `store/oracle/weblogic:12.2.1.3`, was updated on January 17, 2019, and has all the necessary patches applied; a `docker pull` is required if you pulled the image prior to that date. Refer to [WebLogic Docker images](../../../../../site/weblogic-docker-images.md) for details on how to obtain or create the image.
* Create a Kubernetes namespace for the domain unless the intention is to use the default namespace.
* If `logHomeOnPV` is enabled, create the Kubernetes persistent volume where the log home will be hosted, and the Kubernetes persistent volume claim for the domain in the same Kubernates namespace. For samples to create a PV and PVC, see [Create sample PV and PVC](../../create-weblogic-domain-pv-pvc/README.md).
* Create a Kubernetes secret for the WebLogic administrator credentials that contains the fields `username` and `password`, and make sure that the secret name matches the value specified for `weblogicCredentialsSecretName` (see Configuration table below). For example:

```
$ cd ./kubernetes/samples/scripts/create-weblogic-domain-credentials
$ create-weblogic-credentials.sh
   -u weblogic
   -p welcome1
   -d domain1
   -n default
   -s domain1-weblogic-credentials
```
**NOTE**: Then make sure to configure `weblogicCredentialsSecretName` to be `domain1-weblogic-credentials`.

## Use the script to create a domain

**Note**: The `create-domain.sh` script generates a new Docker image on each run with a new domain home and a different internal `domain secret` in it.  To prevent having disparate images with different domain secrets in the same domain, we strongly recommend that a new domain uses a `domainUID` that is different from any of the active domains, or that you delete the existing domain resource using the following command and wait until all the server pods are terminated before you create a domain with the same `domainUID`:

```
$kubectl delete domain [domainUID] -n [domainNamespace]
```

Make a copy of the `create-domain-inputs.yaml` file, and run the create script, pointing it at your inputs file and an output directory:

```
$ ./create-domain.sh \
  -u <username> \
  -p <password> \
  -i create-domain-inputs.yaml \
  -o /path/to/output-directory
```

The script will perform the following steps:

* Create a directory for the generated properties and Kubernetes YAML files for this domain if it does not already exist.  The pathname is `/path/to/weblogic-operator-output-directory/weblogic-domains/<domainUID>`. If the directory already exists, its contents will be removed.
* Create a properties file, `domain.properties`, in the directory that is created above. This properties file will be used to create a sample WebLogic Server domain.
* Clone the WebLogic docker-images project into the directory that is derived from the `domainHomeImageBuildPath` property using `git clone https://github.com/oracle/docker-images.git`. By default, the script always cleans up the directory and clones it again on every run. You need to specify the `-k` option if you want to use a previously cloned project. Note that if the specified `domainHomeImageBuildPath` is empty, the script will still clone the project even if the `-k` option is specified.
* Replace the built-in user name and password in the `properties/docker-build/domain_security.properties` file with the `username` and `password` that are supplied on the command line using the `-u` and `-p` options. These credentials need to match the WebLogic domain admin credentials in the secret that is specified via the `weblogicCredentialsSecretName` property in the `create-domain-inputs.yaml` file.
* Build a Docker image based on the Docker sample, [Example Image with a WebLogic Server Domain using the Oracle WebLogic Scripting Tooling (WLST)](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-domain-home-in-image) or [Example Image with a WebLogic Server Domain using the Oracle WebLogic Deploy Tooling (WDT)](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-domain-home-in-image-wdt). It will create a sample WebLogic Server domain in the Docker image. **Note**: Oracle recommends keeping the domain home image private in the local repository.
* Create a tag that refers to the generated Docker image.
* Create a Kubernetes domain YAML file, `domain.yaml`, in the directory that is created above. This YAML file can be used to create the Kubernetes resource using the `kubectl create -f` or `kubectl apply -f` command.
```
$ kubectl apply -f /path/to/output-directory/weblogic-domains/<domainUID>/domain.yaml
```

As a convenience, using the `-e` option, the script can optionally create the domain object, which in turn results in the creation of the corresponding WebLogic Server pods and services. This option should be used in a single node Kubernetes cluster only.

For a multi-node Kubernetes cluster, make sure that the generated image is available on all nodes before creating the domain resource using the `kubectl apply -f` command.

The usage of the create script is as follows:

```
$ sh create-domain.sh -h
usage: create-domain.sh -o dir -i file -u username -p password [-k] [-e] [-h]
  -i Parameter inputs file, must be specified.
  -o Ouput directory for the generated properties and YAML files, must be specified.
  -u User name used in building the Docker image for WebLogic domain in image.
  -p Password used in building the Docker image for WebLogic domain in image.
  -e Also create the resources in the generated YAML files, optional.
  -v Validate the existence of persistentVolumeClaim, optional.
  -k Keep what has been previously cloned from https://github.com/oracle/docker-images.git, optional.
     If not specified, this script will always remove the existing project and clone again.
  -h Help

```

If you copy the sample scripts to a different location, make sure that you copy everything in the `<weblogic-kubernetes-operator-project>/kubernetes/samples/scripts` directory together into the target directory, maintaining the original directory hierarchy.

The default domain created by the script has the following characteristics:

* An Administration Server named `admin-server` listening on port `7001`.
* A dynamic cluster named `cluster-1` of size 5.
* Two Managed Servers, named `managed-server1` and `managed-server2`, listening on port `8001`.
* No applications deployed.
* A T3 channel.

If you run the sample from a machine that is remote to the Kubernetes cluster, and you need to push the new image to a registry that is local to the cluster, you need to do the following (also see the `image` property in the Configuration parameters table in the next section):
* Set the `image` property in the inputs file to the target image name (including the registry hostname/port and the tag, if needed).
* Run the `create-domain.sh` script without the `-e` option.
* Push the `image` to the target registry.
* Run the following command to create the domain:

```
$ kubectl apply -f /path/to/output-directory/weblogic-domains/<domainUID>/domain.yaml
```

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
| `domainHomeImageBase` | Base WebLogic binary image used to build the WebLogic domain image. The operator requires WebLogic Server 12.2.1.3.0 with patch 29135930 applied. The existing WebLogic Docker image, `store/oracle/weblogic:12.2.1.3`, was updated on January 17, 2019, and has all the necessary patches applied; a `docker pull` is required if you pulled the image prior to that date. Refer to [WebLogic Docker images](../../../../../site/weblogic-docker-images.md) for details on how to obtain or create the image. | `store/oracle/weblogic:12.2.1.3` |
| `domainHomeImageBuildPath` | Location of the WebLogic "domain home in image" Docker image in `https://github.com/oracle/docker-images.git` project. If not specified, use "./docker-images/OracleWebLogic/samples/12213-domain-home-in-image". Another possible value is "./docker-images/OracleWebLogic/samples/12213-domain-home-in-image-wdt" which uses WDT, instead of WLST, to generate the domain configuration. | `./docker-images/OracleWebLogic/samples/12213-domain-home-in-image` |
| `domainPVMountPath` | Mount path of the domain persistent volume. This parameter is required if `logHomeOnPV` is true. Otherwise, it is ignored. | `/shared` |
| `domainUID` | Unique ID that will be used to identify this particular domain. Used as the name of the generated WebLogic domain as well as the name of the Kubernetes domain resource. This ID must be unique across all domains in a Kubernetes cluster. This ID cannot contain any character that is not valid in a Kubernetes service name. | `domain1` |
| `exposeAdminNodePort` | Boolean indicating if the Administration Server is exposed outside of the Kubernetes cluster. | `false` |
| `exposeAdminT3Channel` | Boolean indicating if the T3 administrative channel is exposed outside the Kubernetes cluster. | `false` |
| `image` | WebLogic Server Docker image that the operator uses to start the domain. The create domain scripts generate a WebLogic Server Docker image with a domain home in it. By default, the scripts tag the generated WebLogic server Docker image as either `domain-home-in-image` or `domain-home-in-image-wdt` based on the `domainHomeImageBuildPath` property, and use it plus the tag that is obtained from the `domainHomeImageBase` to set the `image` element in the generated domain YAML file. If this property is set, the create domain scripts will use the value specified, instead of the default value, to tag the generated image and set the `image` in the domain YAML file. A unique value is required for each domain that is created using the scripts. If you are running the sample scripts from a machine that is remote to the Kubernetes cluster where the domain is going to be running, you need to set this property to the image name that is intended to be used in a registry local to that Kubernetes cluster. You also need to push the `image` to that registry before starting the domain using the `kubectl create -f` or `kubectl apply -f` command. | |
| `imagePullPolicy` | WebLogic Docker image pull policy. Legal values are `IfNotPresent`, `Always`, or `Never`. | `IfNotPresent` |
| `imagePullSecretName` | Name of the Kubernetes secret to access the Docker Store to pull the WebLogic Server Docker image. The presence of the secret will be validated when this parameter is specified. |  |
| `includeServerOutInPodLog` | Boolean indicating whether to include `server.out` to the pod's stdout. | `true` |
| `initialManagedServerReplicas` | Number of Managed Servers to initially start for the domain. | `2` |
| `javaOptions` | Java options for starting the Administration and Managed Servers. A Java option can have references to one or more of the following pre-defined variables to obtain WebLogic domain information: `$(DOMAIN_NAME)`, `$(DOMAIN_HOME)`, `$(ADMIN_NAME)`, `$(ADMIN_PORT)`, and `$(SERVER_NAME)`. | `-Dweblogic.StdoutDebugEnabled=false` |
| `logHomeOnPV` | Specifies whether the log home is stored on the persistent volume. If set to true, then you must specify the `logHome`, `persistentVolumeClaimName` and `domainPVMountPath` parameters.| `false` |
| `logHome` | The in-pod location for domain log, server logs, server out, and Node Manager log files. If not specified, the value is derived from the `domainUID` as `/shared/logs/<domainUID>`. This parameter is required if `logHomeOnPV` is true. Otherwise, it is ignored. | `/shared/logs/domain1` |
| `managedServerNameBase` | Base string used to generate Managed Server names. | `managed-server` |
| `managedServerPort` | Port number for each Managed Server. | `8001` |
| `namespace` | Kubernetes namespace in which to create the domain. | `default` |
| `persistentVolumeClaimName` | Name of the persistent volume claim. If not specified, the value is derived from the `domainUID` as `<domainUID>-weblogic-sample-pvc`. This parameter is required if `logHomeOnPV` is true. Otherwise, it is ignored. | `domain1-weblogic-sample-pvc` |
| `productionModeEnabled` | Boolean indicating if production mode is enabled for the domain. | `true` |
| `serverStartPolicy` | Determines which WebLogic Servers will be started up. Legal values are `NEVER`, `IF_NEEDED`, `ADMIN_ONLY`. | `IF_NEEDED` |
| `t3ChannelPort` | Port for the T3 channel of the NetworkAccessPoint. | `30012` |
| `t3PublicAddress` | Public address for the T3 channel.  This should be set to the public address of the Kubernetes cluster.  This would normally be a load balancer address. <p/>For development environments only: In a single server (all-in-one) Kubernetes deployment, this may be set to the address of the master, or at the very least, it must be set to the address of one of the worker nodes. | `kubernetes` |
| `weblogicCredentialsSecretName` | Name of the Kubernetes secret for the Administration Server's user name and password. | `domain1-weblogic-credentials` |

Note that the names of the Kubernetes resources in the generated YAML files may be formed with the value of some of the properties specified in the `create-inputs.yaml` file. Those properties include the `adminServerName`, `clusterName` and `managedServerNameBase`. If those values contain any characters that are invalid in a Kubernetes service name, those characters are converted to valid values in the generated YAML files. For example, an uppercase letter is converted to a lowercase letter and an underscore `("_")` is converted to a hyphen `("-")`.

The sample demonstrates how to create a WebLogic domain home and associated Kubernetes resources for a domain that has only one cluster. In addition, the sample provides the capability for users to supply their own scripts to create the domain home for other use cases. Also, the generated domain YAML file can be modified to cover more use cases.

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
  domainHome: /u01/oracle/user_projects/domains/domain1
  # If the domain home is in the image
  domainHomeInImage: true
  # The WebLogic Server Docker image that the operator uses to start the domain
  image: "domain-home-in-image:12.2.1.3"
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
  # logHomeEnabled: false
  # The in-pod location for domain log, server logs, server out, and Node Manager log files
  # logHome: /shared/logs/domain1
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
    # volumes:
    # - name: weblogic-domain-storage-volume
    #   persistentVolumeClaim:
    #     claimName: domain1-weblogic-sample-pvc
    # volumeMounts:
    # - mountPath: /shared
    #   name: weblogic-domain-storage-volume
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
###

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
Annotations:  <none>
API Version:  weblogic.oracle/v2
Kind:         Domain
Metadata:
  Cluster Name:        
  Creation Timestamp:  2019-01-10T14:29:37Z
  Generation:          1
  Resource Version:    3698533
  Self Link:           /apis/weblogic.oracle/v2/namespaces/default/domains/domain1
  UID:                 28655979-14e4-11e9-b751-fa163e855ac8
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
  Domain Home:                    /u01/oracle/user_projects/domains/domain1
  Domain Home In Image:           true
  Image:                          domain-home-in-image:12.2.1.3
  Image Pull Policy:              IfNotPresent
  Include Server Out In Pod Log:  true
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
    Volumes:
  Server Service:
    Annotations:
    Labels:
  Server Start Policy:  IF_NEEDED
  Web Logic Credentials Secret:
    Name:  domain1-weblogic-credentials
Status:
  Conditions:
    Last Transition Time:  2019-01-10T14:31:10.681Z
    Reason:                ServersReady
    Status:                True
    Type:                  Available
  Servers:
    Health:
      Activation Time:  2019-01-10T14:30:47.432Z
      Overall Health:   ok
      Subsystems:
    Node Name:     slc16ffk
    Server Name:   admin-server
    State:         RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2019-01-10T14:32:01.467Z
      Overall Health:   ok
      Subsystems:
    Node Name:     slc16ffk
    Server Name:   managed-server1
    State:         RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2019-01-10T14:32:04.532Z
      Overall Health:   ok
      Subsystems:
    Node Name:    slc16ffk
    Server Name:  managed-server2
    State:        RUNNING
  Start Time:     2019-01-10T14:29:37.455Z
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
domain1-admin-server                         1/1       Running   0          30m
domain1-managed-server1                      1/1       Running   0          29m
domain1-managed-server2                      1/1       Running   0          29m
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
domain1-admin-server                        ClusterIP   None             <none>        7001/TCP          32m
domain1-cluster-cluster-1                   ClusterIP   10.99.151.142    <none>        8001/TCP          31m
domain1-managed-server1                     ClusterIP   None             <none>        8001/TCP          31m
domain1-managed-server2                     ClusterIP   None             <none>        8001/TCP          22m
```

## Delete the domain

The generated YAML file in the `/path/to/weblogic-operator-output-directory/weblogic-domains/<domainUID>` directory can be used to delete the Kubernetes resource. Use the following command to delete the domain:

```
$ kubectl delete -f domain.yaml

```
