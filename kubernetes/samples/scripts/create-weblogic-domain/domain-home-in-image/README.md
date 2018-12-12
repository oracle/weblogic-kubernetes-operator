# WebLogic sample domain home in Docker image

The sample scripts demonstrate the creation of a WebLogic domain home in a Docker image. The scripts also generate the domain YAML file, which can then be used to start the Kubernetes artifacts of the corresponding domain. Optionally, the scripts start up the domain, and WebLogic Server pods and services.

## Prerequisites

The following prerequisites must be handled prior to running the create domain script:
* Make sure the WebLogic operator is running.
* Create a Kubernetes namespace for the domain unless the intention is to use the default namespace.
* Create the Kubernetes secrets `username` and `password` of the admin account in the same Kubernetes namespace as the domain.
* Build the Oracle WebLogic image `oracle/weblogic:12.2.1.3-developer`. Refer to [Oracle WebLogic Server on Docker](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/dockerfiles/12.2.1.3).

## Use the script to create a domain

Make a copy of the `create-domain-inputs.yaml` file, and run the create script, pointing it at your inputs file and an output directory:

```
  ./create-domain.sh \
  -u <username> \
  -p <password> \
  -i create-domain-inputs.yaml \
  -o /path/to/output-directory
```

The script will perform the following steps:

* Create a directory for the generated properties and Kubernetes YAML files for this domain if it does not already exist.  The pathname is `/path/to/weblogic-operator-output-directory/weblogic-domains/<domainUID>`. Note that the script fails if the directory is not empty when the `create-domain.sh` script is executed.
* Create a properties file, `domain.properties`, in the directory that is created above. This properties file will be used to create a sample WebLogic Server domain.
* Clone the weblogic docker-images project via the `git clone https://github.com/oracle/docker-images.git` into the current directory.
* Replace the built-in username and password in the `properties/docker_build/domain_security.properties` file with the `username` and `password` that are supplied in the command line via the `-u` and `-p` options. These credentials need to match the WebLogic domain admin credentials in the secret that is specified via `weblogicCredentialsSecretName` property in the `create-domain-inputs.yaml` file.
* Build a Docker image based on the Docker sample, [Example Image with a WebLogic Server Domain](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-domain-home-in-image). It will create a sample WebLogic Server domain in the Docker image. Also, you can run the Docker sample, [Example Image with a WebLogic Server Domain](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-domain-home-in-image), manually with the generated `domain.properties` to create a domain home image. **Note**: Oracle recommends keeping the domain home image private in the local repository.
* Create a Kubernetes domain YAML file, `domain.yaml`, in the directory that is created above. This YAML file can be used to create the Kubernetes resource using the `kubectl create -f` or `kubectl apply -f` command.

As a convenience, using the `-e` option, the script can optionally create the domain object, which in turn results in the creation of the corresponding WebLogic Server pods and services.

The usage of the create script is as follows:

```
$ sh create-domain.sh -h
usage: create-domain.sh -o dir -i file -u username -p password [-e] [-h]
  -i Parameter inputs file, must be specified.
  -o Ouput directory for the generated properties and YAML files, must be specified.
  -u Username used in building the Docker image for WebLogic domain in image.
  -p Password used in building the Docker image for WebLogic domain in image.
  -e Also create the resources in the generated YAML files, optional.
  -h Help

```

If you copy the sample scripts to a different location, make sure that you copy everything in the `<weblogic-kubernetes-operator-project>/kubernetes/samples/scripts` directory together into the target directory, maintaining the original directory hierarchy.

The default domain created by the script has the following characteristics:

* An Administration Server named `admin-server` listening on port `7001`.
* A dynamic cluster named `cluster-1` of size 2.
* Two Managed Servers, named `managed-server1` and `managed-server2`, listening on port `8001`.
* No applications deployed.
* A Derby data source.
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
| `clusterType` | Type of the WebLogic Cluster. Legal values are `CONFIGURED` or `DYNAMIC`. | `DYNAMIC` |
| `configuredManagedServerCount` | Number of Managed Server instances to generate for the domain. | `2` |
| `domainUID` | Unique ID that will be used to identify this particular domain. Used as the name of the generated WebLogic domain as well as the name of the Kubernetes domain resource. This ID must be unique across all domains in a Kubernetes cluster. This ID cannot contain any character that is not valid in a Kubernetes service name. | `domain1` |
| `exposeAdminNodePort` | Boolean indicating if the Administration Server is exposed outside of the Kubernetes cluster. | `false` |
| `exposeAdminT3Channel` | Boolean indicating if the T3 administrative channel is exposed outside the Kubernetes cluster. | `false` |
| `includeServerOutInPodLog` | Boolean indicating whether to include server .out to the pod's stdout. | `true` |
| `initialManagedServerReplicas` | Number of Managed Servers to initially start for the domain. | `2` |
| `javaOptions` | Java options for starting the Administration and Managed Servers. A Java option can have references to one or more of the following pre-defined variables to obtain WebLogic domain information: `$(DOMAIN_NAME)`, `$(DOMAIN_HOME)`, `$(ADMIN_NAME)`, `$(ADMIN_PORT)`, and `$(SERVER_NAME)`. | `-Dweblogic.StdoutDebugEnabled=false` |
| `managedServerNameBase` | Base string used to generate Managed Server names. | `managed-server` |
| `managedServerPort` | Port number for each Managed Server. | `8001` |
| `namespace` | Kubernetes namespace in which to create the domain. | `default` |
| `productionModeEnabled` | Boolean indicating if production mode is enabled for the domain. | `true` |
| `serverStartPolicy` | Determines which WebLogic Servers will be started up. Legal values are `NEVER`, `ALWAYS`, `IF_NEEDED`, `ADMIN_ONLY`. | `IF_NEEDED` |
| `t3ChannelPort` | Port for the T3 channel of the NetworkAccessPoint. | `30012` |
| `t3PublicAddress` | Public address for the T3 channel. | `kubernetes` |
| `weblogicCredentialsSecretName` | Name of the Kubernetes secret for the Administration Server's username and password. | `domain1-weblogic-credentials` |

Note that the names of the Kubernetes resources in the generated YAML files may be formed with the value of some of the properties specified in the `create-inputs.yaml` file. Those properties include the `adminServerName`, `clusterName` and `managedServerNameBase`. If those values contain any characters that are invalid in a Kubernetes service name, those characters are converted to valid values in the generated YAML files. For example, an uppercase letter is converted to a lowercase letter and an underscore `("_")` is converted to a hyphen `("-")`.

The sample demonstrates how to create a WebLogic domain home and associated Kubernetes resources for a domain that has only one cluster. In addition, the sample provides the capability for users to supply their own scripts to create the domain home for other use cases. Also, the generated domain YAML file can be modified to cover more use cases.

## Verify the results

The create script will verify that the domain was created, and will report failure if there was any error.  However, it may be desirable to manually verify the domain, even if just to gain familiarity with the various Kubernetes objects that were created by the script.

Note that the example results below use the `default` Kubernetes namespace. If you are using a different namespace, you need to replace `NAMESPACE` in the example `kubectl` commands with the actual Kubernetes namespace.

### Generated YAML files with the default inputs

The content of the generated `domain.yaml`:

```
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
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
  # The Operator currently does not support other images
  image: "12213-domain-home-in-image:latest"
  # imagePullPolicy defaults to "Never"
  imagePullPolicy: "Never"
  # Identify which Secret contains the WebLogic Admin credentials (note that there is an example of
  # how to create that Secret at the end of this file)
  adminSecret:
    name: domain1-weblogic-credentials
  # Whether to include the server out file into the pod's stdout, default is true
  includeServerOutInPodLog: true
  # serverStartPolicy legal values are "NEVER", "ALWAYS", "IF_NEEDED", or "ADMIN_ONLY"
  # This determines which WebLogic Servers the Operator will start up when it discovers this Domain
  # - "ALWAYS" will start up all defined servers
  # - "ADMIN_ONLY" will start up only the administration server (no managed servers will be started)
  # - "IF_NEEDED" will start all non-clustered servers, including the administration server and clustered servers up to the replica count
  serverStartPolicy: "IF_NEEDED"
  # an (optional) list of environment variable to be set on the servers
  env:
  - name: JAVA_OPTIONS
    value: "-Dweblogic.StdoutDebugEnabled=false"
  - name: USER_MEM_ARGS
    value: "-Xms64m -Xmx256m "
  # adminServer is used to configure the desired behavior for starting the administration server.
  adminServer:
  # serverStartState legal values are "RUNNING" or "ADMIN"
  # "RUNNING" means the listed server will be started up to "RUNNING" mode
  # "ADMIN" means the listed server will be start up to "ADMIN" mode
    serverStartState: "RUNNING"
    # The Admin Server's NodePort
    # nodePort: 30701
    # Uncomment to export the T3Channel as a service
    # exportedNetworkAccessPoints:
    #   T3Channel: {}
  # clusters is used to configure the desired behavior for starting member servers of a cluster.  
  # If you use this entry, then the rules will be applied to ALL servers that are members of the named clusters.
  clusters:
    cluster-1:
      desiredState: "RUNNING"
      replicas: 2
  # The number of managed servers to start for unlisted clusters
  # replicas: 1

```

### Verify the domain

To confirm that the domain was created, use this command:

```
kubectl describe domain DOMAINUID -n NAMESPACE
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
  Creation Timestamp:  2018-12-11T01:33:27Z
  Generation:          1
  Resource Version:    46624
  Self Link:           /apis/weblogic.oracle/v2/namespaces/default/domains/domain1
  UID:                 c1f7be60-fce4-11e8-bc6c-0021f6985fb7
Spec:
  Admin Secret:
    Name:  domain1-weblogic-credentials
  Admin Server:
    Exported Network Access Points:
    Node Port Annotations:
    Node Port Labels:
    Server Pod:
      Container Security Context:
      Env:
      Liveness Probe:
      Node Selector:
      Pod Annotations:
      Pod Labels:
      Pod Security Context:
      Readiness Probe:
      Resources:
        Limits:
        Requests:
      Service Annotations:
      Service Labels:
      Volume Mounts:
      Volumes:
    Server Start State:  RUNNING
  Clusters:
    Cluster - 1:
      Replicas:  2
      Server Pod:
        Container Security Context:
        Env:
        Liveness Probe:
        Node Selector:
        Pod Annotations:
        Pod Labels:
        Pod Security Context:
        Readiness Probe:
        Resources:
          Limits:
          Requests:
        Service Annotations:
        Service Labels:
        Volume Mounts:
        Volumes:
      Server Start State:         RUNNING
  Domain Home:                    /u01/oracle/user_projects/domains/domain1
  Domain Home In Image:           true
  Image:                          12213-domain-home-in-image:latest
  Image Pull Policy:              Never
  Include Server Out In Pod Log:  true
  Managed Servers:
  Server Pod:
    Container Security Context:
    Env:
    Liveness Probe:
    Node Selector:
    Pod Annotations:
    Pod Labels:
    Pod Security Context:
    Readiness Probe:
    Resources:
      Limits:
      Requests:
    Service Annotations:
    Service Labels:
    Volume Mounts:
    Volumes:
  Server Start Policy:  IF_NEEDED
Status:
  Conditions:
    Last Transition Time:  2018-12-11T01:35:23.652Z
    Reason:                ServersReady
    Status:                True
    Type:                  Available
  Servers:
    Health:
      Activation Time:  2018-12-11T01:34:59.546Z
      Overall Health:   ok
      Subsystems:
    Node Name:     xxxxxxxx
    Server Name:   admin-server
    State:         RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2018-12-11T01:36:46.132Z
      Overall Health:   ok
      Subsystems:
    Node Name:     xxxxxxxx
    Server Name:   managed-server1
    State:         RUNNING
    Cluster Name:  cluster-1
    Health:
      Activation Time:  2018-12-11T01:36:47.865Z
      Overall Health:   ok
      Subsystems:
    Node Name:     xxxxxxxx
    Server Name:   managed-server2
    State:        RUNNING
  Start Time:     2018-12-11T01:33:27.339Z
Events:            <none>
```

In the `Status` section of the output, the available servers and clusters are listed.  Note that if this command is issued very soon after the script finishes, there may be no servers available yet, or perhaps only the Administration Server but no Managed Servers.  The operator will start up the Administration Server first and wait for it to become ready before starting the Managed Servers.

### Verify the pods

Use the following command to see the pods running the servers:

```
kubectl get pods -n NAMESPACE
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
kubectl get services -n NAMESPACE
```

Here is an example of the output of this command:
```
$ kubectl get services
NAME                                        TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)           AGE
domain1-admin-server                        NodePort    10.96.206.134    <none>        7001:30701/TCP    23m
domain1-admin-server-extchannel-t3channel   NodePort    10.107.164.241   <none>        30012:30012/TCP   22m
domain1-cluster-cluster-1                   ClusterIP   10.109.133.168   <none>        8001/TCP          22m
domain1-managed-server1                     ClusterIP   None             <none>        8001/TCP          22m
domain1-managed-server2                     ClusterIP   None             <none>        8001/TCP          22m
```

## Delete the domain

The generated YAML file in the `/path/to/weblogic-operator-output-directory/weblogic-domains/<domainUID>` directory can be used to delete the Kubernetes resource. Use the following command to delete the domain:

```
$ kubectl delete -f domain.yaml

```
