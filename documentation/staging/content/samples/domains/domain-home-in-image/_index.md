---
title: "Domain home in image"
date: 2019-02-23T17:32:31-05:00
weight: 3
description: "Sample for creating a WebLogic domain home inside an image, and the domain resource YAML file for deploying the generated WebLogic domain."
---

The sample scripts demonstrate the creation of a WebLogic domain home in an image using [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/) (WIT). The sample scripts have the option of putting the WebLogic domain log, server logs, server output files, and the Node Manager logs on an existing Kubernetes PersistentVolume (PV) and PersistentVolumeClaim (PVC). The scripts also generate the domain resource YAML file, which can then be used by the scripts or used manually to start the Kubernetes artifacts of the corresponding domain, including the WebLogic Server pods and services.

#### Prerequisites

Before you begin, read this document, [Domain resource]({{< relref "/userguide/managing-domains/domain-resource/_index.md" >}}).

The following prerequisites must be met prior to running the create domain script:

* The WebLogic Image Tool requires that `JAVA_HOME` is set to a Java JDK version 8 or later.
* The operator requires an image with either Oracle WebLogic Server 12.2.1.3.0 with patch 29135930 applied, or Oracle WebLogic Server 12.2.1.4.0, or Oracle WebLogic Server 14.1.1.0.0. The existing WebLogic Server image, `container-registry.oracle.com/middleware/weblogic:12.2.1.3`, has all the necessary patches applied. For details on how to obtain or create the image, see [WebLogic Server images]({{< relref "/userguide/base-images/_index.md#create-or-obtain-weblogic-server-images" >}}).
* Create a Kubernetes Namespace for the domain unless you intend to use the default namespace.
* If `logHomeOnPV` is enabled, create the Kubernetes PersistentVolume where the log home will be hosted, and the Kubernetes PersistentVolumeClaim for the domain in the same Kubernetes Namespace. For samples to create a PV and PVC, see [Create sample PV and PVC]({{< relref "/samples/storage/_index.md" >}}).
* Create a Kubernetes Secret for the WebLogic administrator credentials that contains the fields `username` and `password`, and make sure that the secret name matches the value specified for `weblogicCredentialsSecretName`; see [Configuration parameters](#configuration-parameters) below. For example:

```shell
$ cd ./kubernetes/samples/scripts/create-weblogic-domain-credentials
```
```shell
$ create-weblogic-credentials.sh
   -u <username>
   -p <password>
   -d domain1
   -n default
   -s domain1-weblogic-credentials
```
**NOTE**: Using this example, you would configure `weblogicCredentialsSecretName` to be `domain1-weblogic-credentials`.

#### Use the script to create a domain

{{% notice note %}}
The `create-domain.sh` script generates a new container image on each run with a new domain home and a different internal `domain secret` in it.  To prevent having disparate images with different domain secrets in the same domain, we strongly recommend that a new domain uses a `domainUID` that is different from any of the active domains, or that you delete the existing Domain using the following command and wait until all the WebLogic Server instance Pods are terminated before you create a Domain with the same `domainUID`:
`$ kubectl delete domain [domainUID] -n [domainNamespace]`
{{% /notice %}}

The sample for creating domains is in this directory:

```shell
$ cd kubernetes/samples/scripts/create-weblogic-domain/domain-home-in-image
```

Make a copy of the `create-domain-inputs.yaml` file, update it with the correct values,
and run the create script, pointing it at your inputs file and an output directory,
along with user name and password for the WebLogic administrator:

```shell
$ ./create-domain.sh \
  -u <username> \
  -p <password> \
  -i create-domain-inputs.yaml \
  -o /<path to output-directory>
```
{{% notice note %}} The `create-domain.sh` script and its inputs file are for demonstration purposes _only_; its contents and the domain resource file that it generates for you might change without notice. In production, we strongly recommend that you use the WebLogic Image Tool and WebLogic Deploy Tooling (when applicable), and directly work with domain resource files instead.
{{% /notice%}}

The script will perform the following steps:

* Create a directory for the generated properties and Kubernetes YAML files for this domain if it does not already exist.  The pathname is `/<path to output-directory>/weblogic-domains/<domainUID>`. If the directory already exists, its contents will be removed.

* Create a properties file, `domain.properties`, in the directory that is created above.
  This properties file will be used to create a sample WebLogic Server domain.
  The `domain.properties` file will be removed upon successful completion of the script.

* Download the latest [WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling) (WDT) and [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool/) installer ZIP files to your `/tmp/dhii-sample/tools` directory.
  WIT is required to create your Domain in Image container images, and WDT is required if using `wdt` mode.
  Visit the GitHub [WebLogic Deploy Tooling Releases](https://github.com/oracle/weblogic-deploy-tooling/releases) and [WebLogic Image Tool Releases](https://github.com/oracle/weblogic-image-tool/releases) web pages to determine the latest release version for each.

* Set up the WebLogic Image Tool in the `<toolsDir>/imagetool` directory, where `<toolsDir>` is the
  directory specified in the `toolsDir` parameter in the inputs YAML file. Set the
  WIT cache store location to the `<tools>/imagetool-cache` directory and
  put a `wdt_<WDT_VERSION>` entry in the tool's cache, which points to the path of the WDT ZIP file installer.
  For more information about the WIT cache, see the
  [WIT Cache documentation](https://oracle.github.io/weblogic-image-tool/userguide/tools/cache/).

* If the optional `-n` option and an encryption key is provided, invoke the WDT
  [Encrypt Model Tool](https://oracle.github.io/weblogic-deploy-tooling/userguide/tools/encrypt/)
  in a container running the image specified in `domainHomeImageBase` parameter in your inputs file
  to encrypt the password properties in `domain.properties` file. Note that this password encryption
  step is skipped if the value of the `mode` parameter in the inputs YAML file is `wlst` because
  the feature is provided by WDT.

* Invoke the [WebLogic Image Tool](https://oracle.github.io/weblogic-image-tool) to create a
  new WebLogic Server domain based on the WebLogic image specified in the `domainHomeImageBase` parameter
  from your inputs file. The new WebLogic Server domain is created using one of the
  following options based on the value of the `mode` parameter in the inputs YAML file:
  * If the value of the `mode` parameter is `wdt`, the WDT model specified in the `createDomainWdtModel`
    parameter and the WDT variables in `domain.properties` file are used by the WebLogic Image Tool to create
    the new WebLogic Server domain.
  * If the value of the `mode` parameter is `wlst`, the offline WLST
    script specified in the `createDomainWlstScript` parameter is run to create the new WebLogic Server domain.

* The generated image is tagged with the `image` parameter provided in your inputs file.

  {{% notice warning %}}
  Oracle strongly recommends storing the image containing the domain home as private
  in the registry (for example, Oracle Cloud Infrastructure Registry, GitHub Container Registry, and such) because
  this image contains sensitive information about the domain, including keys and
  credentials that are used to access external resources (for example, the data source password).
  For more information, see
  [WebLogic domain in image protection]({{<relref "/security/domain-security/image-protection#weblogic-domain-in-container-image-protection">}}).
  {{% /notice %}}
* Create a Kubernetes domain resource YAML file, `domain.yaml`, in the directory that is created above. This YAML file can be used to create the Kubernetes resource using the `kubectl create -f` or `kubectl apply -f` command.
```shell
$ kubectl apply -f /<path to output-directory>/weblogic-domains/<domainUID>/domain.yaml
```

As a convenience, using the `-e` option, the script can optionally create the domain object, which in turn results in the creation of the corresponding WebLogic Server pods and services. This option should be used in a single node Kubernetes cluster only.

For a multi-node Kubernetes cluster, make sure that the generated image is available on all nodes before creating the domain resource YAML file using the `kubectl apply -f` command.

The usage of the create script is as follows:

```shell
$ sh create-domain.sh -h
```
```text
usage: create-domain.sh -o dir -i file -u username -p password [-n encryption-key] [-e] [-v] [-h]
  -i Parameter inputs file, must be specified.
  -o Output directory for the generated properties and YAML files, must be specified.
  -u WebLogic administrator user name for the WebLogic domain.
  -p WebLogic administrator Password for the WebLogic domain.
  -e Also create the resources in the generated YAML files, optional.
  -v Validate the existence of persistentVolumeClaim, optional.
  -n Encryption key for encrypting passwords in the WDT model and properties files, optional.
  -h Help
```

If you copy the sample scripts to a different location, make sure that you copy everything in the `<weblogic-kubernetes-operator-project>/kubernetes/samples/scripts` directory together into the target directory, maintaining the original directory hierarchy.

The default domain created by the script has the following characteristics:

* An Administration Server named `admin-server` listening on port `7001`.
* A dynamic cluster named `cluster-1` of size 5.
* Two Managed Servers, named `managed-server1` and `managed-server2`, listening on port `8001`.
* No applications deployed.
* A T3 channel.

If you run the sample from a machine that is remote to the Kubernetes cluster, and you need to push the new image to a registry that is local to the cluster, you need to do the following (also, see the `image` property in the [Configuration parameters](#configuration-parameters) table.):

* Set the `image` property in the inputs file to the target image name (including the registry hostname, port, and the tag, if needed).
* If you want Kubernetes to pull the image from a private registry, create a Kubernetes Secret to hold your credentials and set the `imagePullSecretName` property in the inputs file to the name of the secret.
{{% notice note %}}
The Kubernetes Secret must be in the same namespace where the domain will be running.
For more information, see [WebLogic domain in image protection]({{<relref "/security/domain-security/image-protection#weblogic-domain-in-container-image-protection">}}).
{{% /notice %}}
* Run the `create-domain.sh` script without the `-e` option.
* Push the `image` to the target registry.
* Run the following command to create the domain:

```shell
$ kubectl apply -f /<path to output-directory>/weblogic-domains/<domainUID>/domain.yaml
```

The domain creation inputs can be customized by editing `create-domain-inputs.yaml`.

#### Configuration parameters
The following parameters can be provided in the inputs file.

| Parameter | Definition | Default |
| --- | --- | --- |
| `sslEnabled` | Boolean indicating whether to enable SSL for each WebLogic Server instance. | `false` |
| `adminPort` | Port number of the Administration Server inside the Kubernetes cluster. | `7001` |
| `adminServerSSLPort` | SSL port number of the Administration Server inside the Kubernetes cluster. | `7002` |
| `adminNodePort` | Port number of the Administration Server outside the Kubernetes cluster. | `30701` |
| `adminServerName` | Name of the Administration Server. | `admin-server` |
| `clusterName` | Name of the WebLogic cluster instance to generate for the domain. | `cluster-1` |
| `configuredManagedServerCount` | Number of Managed Server instances to generate for the domain. | `5` |
| `createDomainWdtModel` | WDT model YAML file that the create domain script uses to create a WebLogic domain when using wdt `mode`. This value is ignored when the `mode` is set to `wlst`. | `wdt/wdt_model_dynamic.yaml` |
| `createDomainWlstScript` | WLST script that the create domain script uses to create a WebLogic domain when using wlst `mode`. This value is ignored when the `mode` is set to `wdt` (which is the default `mode`). | `wlst/create-wls-domain.py` |
| `domainHome` | Domain home directory of the WebLogic domain to be created in the generated WebLogic Server image. | `/u01/oracle/user_projects/domains/<domainUID>` |
| `domainHomeImageBase` | Base WebLogic binary image used to build the WebLogic domain image. The operator requires either Oracle WebLogic Server 12.2.1.3.0 with patch 29135930 applied, or Oracle WebLogic Server 12.2.1.4.0, or Oracle WebLogic Server 14.1.1.0.0. The existing WebLogic Server image, `container-registry.oracle.com/middleware/weblogic:12.2.1.3`, has all the necessary patches applied. For details on how to obtain or create the image, see [WebLogic Server images]({{< relref "/userguide/managing-domains/domain-in-image/base-images/_index.md#creating-or-obtaining-weblogic-server-images" >}}). | `container-registry.oracle.com/middleware/weblogic:12.2.1.3` |
| `domainPVMountPath` | Mount path of the domain persistent volume. This parameter is required if `logHomeOnPV` is true. Otherwise, it is ignored. | `/shared` |
| `domainUID` | Unique ID that will be used to identify this particular domain. Used as the name of the generated WebLogic domain as well as the name of the Domain. This ID must be unique across all domains in a Kubernetes cluster. This ID cannot contain any character that is not valid in a Kubernetes Service name. | `domain1` |
| `exposeAdminNodePort` | Boolean indicating if the Administration Server is exposed outside of the Kubernetes cluster. | `false` |
| `exposeAdminT3Channel` | Boolean indicating if the T3 administrative channel is exposed outside the Kubernetes cluster. | `false` |
| `httpAccessLogInLogHome` | Boolean indicating if server HTTP access log files should be written to the same directory as `logHome` if `logHomeOnPV` is true. Otherwise, server HTTP access log files will be written to the directory specified in the WebLogic domain home configuration. | `true` |
| `image` | WebLogic Server image that the operator uses to start the domain. The create domain scripts generate a WebLogic Server image with a domain home in it. By default, the scripts tag the generated WebLogic Server image as  `domain-home-in-image`, and use it plus the tag that is obtained from the `domainHomeImageBase` to set the `image` element in the generated domain resource YAML file. If this property is set, the create domain scripts will use the value specified, instead of the default value, to tag the generated image and set the `image` in the domain resource YAML file. A unique value is required for each domain that is created using the scripts. If you are running the sample scripts from a machine that is remote to the Kubernetes cluster where the domain is going to be running, you need to set this property to the image name that is intended to be used in a registry local to that Kubernetes cluster. You also need to push the `image` to that registry before starting the domain using the `kubectl create -f` or `kubectl apply -f` command. | `domain-home-in-image:<tag from domainHomeImageBase>`|
| `imagePullPolicy` | WebLogic Server image pull policy. Legal values are `IfNotPresent`, `Always`, or `Never`. | `IfNotPresent` |
| `imagePullSecretName` | Name of the Kubernetes Secret to access the container registry to pull the WebLogic Server image. The presence of the secret will be validated when this parameter is specified. |  |
| `includeServerOutInPodLog` | Boolean indicating whether to include the server `.out` int the pod's stdout. | `true` |
| `initialManagedServerReplicas` | Number of Managed Servers to initially start for the domain. | `2` |
| `javaOptions` | Java options for starting the Administration Server and Managed Servers. A Java option can have references to one or more of the following pre-defined variables to obtain WebLogic domain information: `$(DOMAIN_NAME)`, `$(DOMAIN_HOME)`, `$(ADMIN_NAME)`, `$(ADMIN_PORT)`, and `$(SERVER_NAME)`. If `sslEnabled` is set to `true` and the WebLogic demo certificate is used, add `-Dweblogic.security.SSL.ignoreHostnameVerification=true` to allow the managed servers to connect to the Administration Server while booting up.  The WebLogic generated demo certificate in this environment typically contains a host name that is different from the runtime container's host name.  | `-Dweblogic.StdoutDebugEnabled=false` |
| `logHomeOnPV` | Specifies whether the log home is stored on the persistent volume. If set to true, then you must specify the `logHome`, `persistentVolumeClaimName`, and `domainPVMountPath` parameters.| `false` |
| `logHome` | The in-pod location for domain log, server logs, server out, Node Manager log, introspector out, and server HTTP access log files. If not specified, the value is derived from the `domainUID` as `/shared/logs/<domainUID>`. This parameter is required if `logHomeOnPV` is true. Otherwise, it is ignored. | `/shared/logs/domain1` |
| `managedServerNameBase` | Base string used to generate Managed Server names. | `managed-server` |
| `managedServerPort` | Port number for each Managed Server. | `8001` |
| `managedServerSSLPort` | SSL port number for each Managed Server. | `8002` |
| `mode` | Whether to use the WDT model specified in `createDomainWdtModel` or the offline WLST script specified in `createDomainWlstScript` to create a WebLogic domain. Legal values are `wdt` or `wlst`. | `wdt` |
| `namespace` | Kubernetes Namespace in which to create the domain. | `default` |
| `persistentVolumeClaimName` | Name of the persistent volume claim. If not specified, the value is derived from the `domainUID` as `<domainUID>-weblogic-sample-pvc`. This parameter is required if `logHomeOnPV` is true. Otherwise, it is ignored. | `domain1-weblogic-sample-pvc` |
| `productionModeEnabled` | Boolean indicating if production mode is enabled for the domain. | `true` |
| `serverStartPolicy` | Determines which WebLogic Server instances will be started. Legal values are `NEVER`, `IF_NEEDED`, `ADMIN_ONLY`. | `IF_NEEDED` |
| `t3ChannelPort` | Port for the T3 channel of the network access point. | `30012` |
| `t3PublicAddress` | Public address for the T3 channel.  This should be set to the public address of the Kubernetes cluster.  This would typically be a load balancer address. <p/>For development environments only, in a single server (all-in-one) Kubernetes Deployment, this may be set to the address of the master, or at the very least, it must be set to the address of one of the worker nodes. |  If not provided, the script will attempt to set it to the IP address of the Kubernetes cluster. |
| `weblogicCredentialsSecretName` | Name of the Kubernetes Secret for the Administration Server user name and password. | `domain1-weblogic-credentials` |
| `serverPodCpuRequest`, `serverPodMemoryRequest`, `serverPodCpuCLimit`, `serverPodMemoryLimit` |  The maximum amount of compute resources allowed, and minimum amount of compute resources required, for each server pod. Please refer to the Kubernetes documentation on `Managing Compute Resources for Containers` for details. | Resource requests and resource limits are not specified. |
| `toolsDir` | The directory where WebLogic Deploy Tool and WebLogic Image Tool are installed. The script will install these tools to this directory if they are not already installed. | `/tmp/dhii-sample/tools` |
| `wdtVersion` | Version of the WebLogic Deploy Tool to be installed by the script. This can be a specific version, such as 1.9.10, or `LATEST`.  | `LATEST` |
| `witVersion` | Version of the WebLogic Image Tool to be installed by the script. This can be a specific version, such as 1.9.10, or `LATEST`.  | `LATEST` |

Note that the names of the Kubernetes resources in the generated YAML files may be formed with the value of some of the properties specified in the inputs YAML file. Those properties include the `adminServerName`, `clusterName`, and `managedServerNameBase`. If those values contain any characters that are invalid in a Kubernetes Service name, those characters are converted to valid values in the generated YAML files. For example, an uppercase letter is converted to a lowercase letter and an underscore `("_")` is converted to a hyphen `("-")`.

The sample demonstrates how to create a WebLogic domain home and associated Kubernetes resources for a domain that has only one cluster. In addition, the sample provides the capability for users to supply their own scripts to create the domain home for other use cases. Also, the generated domain resource YAML file can be modified to cover more use cases.

#### Verify the results

The create script will verify that the domain was created, and will report failure if there was any error.  However, it may be desirable to manually verify the domain, even if just to gain familiarity with the various Kubernetes objects that were created by the script.

Note that the example results below use the `default` Kubernetes Namespace. If you are using a different namespace, you need to replace `NAMESPACE` in the example `kubectl` commands with the actual Kubernetes Namespace.

##### Generated YAML files with the default inputs

The content of the generated `domain.yaml`:

```yaml
# Copyright (c) 2017, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This is an example of how to define a Domain resource.
#
apiVersion: "weblogic.oracle/v9"
kind: Domain
metadata:
  name: domain1
  namespace: default
  labels:
    weblogic.domainUID: domain1
spec:
  # The WebLogic Domain Home
  domainHome: /u01/oracle/user_projects/domains/domain1
  # Set domain home type to PersistentVolume for domain-in-pv, Image for domain-in-image, or FromModel for model-in-image
  domainHomeSourceType: Image
  # The WebLogic Server image that the operator uses to start the domain
  image: "domain-home-in-image:12.2.1.4"
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
  # The in-pod location for domain log, server logs, server out, introspector out, and Node Manager log files
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
      value: "-Djava.security.egd=file:/dev/./urandom "
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

#### Verify the domain

To confirm that the domain was created, use this command:

```shell
$ kubectl describe domain DOMAINUID -n NAMESPACE
```

Replace `DOMAINUID` with the `domainUID` and `NAMESPACE` with the actual namespace.

Here is an example of the output of this command:

```shell
$ kubectl describe domain domain1
```
```
Name:         domain1
Namespace:    default
Labels:       weblogic.domainUID=domain1
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
  Image:                          domain-home-in-image:12.2.1.4
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

#### Verify the pods

Use the following command to see the pods running the servers:

```shell
$ kubectl get pods -n NAMESPACE
```

Here is an example of the output of this command:

```shell
$ kubectl get pods
```
```
NAME                                         READY     STATUS    RESTARTS   AGE
domain1-admin-server                         1/1       Running   0          30m
domain1-managed-server1                      1/1       Running   0          29m
domain1-managed-server2                      1/1       Running   0          29m
```

#### Verify the services

Use the following command to see the services for the domain:

```shell
$ kubectl get services -n NAMESPACE
```

Here is an example of the output of this command:
```shell
$ kubectl get services
```
```
NAME                                        TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)           AGE
domain1-admin-server                        ClusterIP   None             <none>        7001/TCP          32m
domain1-cluster-cluster-1                   ClusterIP   10.99.151.142    <none>        8001/TCP          31m
domain1-managed-server1                     ClusterIP   None             <none>        8001/TCP          31m
domain1-managed-server2                     ClusterIP   None             <none>        8001/TCP          22m
```

#### Delete the domain

The generated YAML file in the `/<path to output-directory>/weblogic-domains/<domainUID>` directory can be used to delete the Kubernetes resource. Use the following command to delete the domain:

```shell
$ kubectl delete -f domain.yaml
```

#### Delete the generated image.

When no longer needed, delete the generated image.
If the image is in a local repository, use the following command to delete an image tagged with `domain-home-in-image:12.2.1.4`:

```shell
$ docker rmi domain-home-in-image:12.2.1.4
```

#### Delete the tools directory.

When no longer needed, delete the directory where WebLogic Deploy Tool and WebLogic Image Tool are installed.
By default, they are installed under `/tmp/dhii-sample/tools` directory.

```shell
$ rm -rf /tmp/dhii-sample/tools/
```
