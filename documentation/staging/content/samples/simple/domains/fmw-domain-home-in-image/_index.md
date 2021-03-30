---
title: "FMW Infrastructure domain home in image"
date: 2019-04-18T07:32:31-05:00
weight: 6
description: "Sample for creating an FMW Infrastructure domain home inside an image, and the Domain YAML file for deploying the generated WebLogic domain."
---


The sample scripts demonstrate the creation of a FMW Infrastructure domain home in an image using
[WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool) (WIT). 
The sample scripts have the option of putting the WebLogic domain log, server logs, server output files, 
and the Node Manager logs on an existing Kubernetes PersistentVolume (PV) and PersistentVolumeClaim (PVC). 
The scripts also generate the domain YAML file, which can then be used by the scripts or used manually
to start the Kubernetes artifacts of the corresponding domain, including the WebLogic Server pods and services.

#### Prerequisites

Before you begin, read this document, [Domain resource]({{< relref "/userguide/managing-domains/domain-resource/_index.md" >}}).

The following prerequisites must be met prior to running the create domain script:

* Make sure the WebLogic Server Kubernetes Operator is running.
* The operator requires FMW Infrastructure 12.2.1.3.0 with patch 29135930 applied or FMW Infrastructure 12.2.1.4.0. 
  For details on how to obtain or create the image, refer to 
  [FMW Infrastructure domains]({{< relref "/userguide/managing-fmw-domains/fmw-infra/#obtaining-the-fmw-infrastructure-image" >}}).
* Create a Kubernetes Namespace for the domain unless you intend to use the default namespace.
* If `logHomeOnPV` is enabled, create the Kubernetes PersistentVolume where the log home will be hosted, and the Kubernetes PersistentVolumeClaim for the domain in the same Kubernetes Namespace. For samples to create a PV and PVC, see [Create sample PV and PVC]({{< relref "/samples/simple/storage/_index.md" >}}).
* Create the Kubernetes Secrets `username` and `password` of the administrative account in the same Kubernetes
  namespace as the domain.
* Unless you are creating a Restricted-JRF domain, you also need to:
  * Configure access to your database. For details, see [here]({{< relref "/userguide/managing-fmw-domains/fmw-infra/_index.md#configuring-access-to-your-database" >}}).  
  * Create a Kubernetes Secret with the RCU credentials. For details, refer to this [document](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/kubernetes/samples/scripts/create-rcu-credentials/README.md).

#### Use the script to create a domain

The sample for creating domains is in this directory:

```shell
$ cd kubernetes/samples/scripts/create-fmw-infrastructure-domain/domain-home-in-image
```
Make a copy of the `create-domain-inputs.yaml` file, update it with the correct values. 
If `fwmDomainType` is `JRF`, also update the input files with configurations for accessing the RCU database schema,
including `rcuSchemaPrefix`, `rcuSchemaPassword`, `rcuDatabaseURL`, and `rcuCredentialSecrets`.
Run the create script, pointing it at your inputs file and an output directory, along with user name and password for the WebLogic administrator::

```shell
$ ./create-domain.sh \
  -u <username> \
  -p <password> \
  -i create-domain-inputs.yaml \
  -o /<path to output-directory>
```

The script will perform the following steps:

* Create a directory for the generated Kubernetes YAML files for this domain if it does not
  already exist.  The path name is `/<path to output-directory>/weblogic-domains/<domainUID>`.
  If the directory already exists, its contents must be removed before using this script.
* Create a properties file, `domain.properties`, in the directory that is created above. This properties file will be used to create a sample FMW Infrastructure domain.
* Download the latest [WebLogic Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling) (WDT) and [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool) installer ZIP files to your `/tmp/dhii-sample/tools` directory. Both WDT and WIT are required to create your Model in Image container images.
  Visit the GitHub [WebLogic Deploy Tooling Releases](https://github.com/oracle/weblogic-deploy-tooling/releases) and [WebLogic Image Tool Releases](https://github.com/oracle/weblogic-image-tool/releases) web pages to determine the latest release version for each.

* Set up the WebLogic Image Tool in the `/tmp/dhii-sample/tools/imagetool` directory. Set the
  WIT cache store location to the `/tmp/dhii-sample/tools/imagetool-cache` directory and
  put a `wdt_<WDT_VERSION>` entry in the tool's cache, which points to the path of the WDT ZIP file installer.
  For more information about the WIT cache, see the
  [WIT Cache documentation](https://github.com/oracle/weblogic-image-tool/blob/master/site/cache.md).

* Invoke the [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool) to create a new 
  FWM Infrastructure domain using the FMW Infrastructure image specified in the `domainHomeImageBase` 
  parameter from your inputs file, the WDT variables in `domain.properties` file, and the model 
  defined in `wdt_model_configured.yaml` or `wdt_model_restricted_jrf_configured.yaml` depending on 
  the value of `fmwDomainType` in your inputs file.
  The generated image is tagged with the `image` parameter provided in your inputs file.

  {{% notice warning %}}
  Oracle strongly recommends storing the image containing the domain home as private
  in the registry (for example, Oracle Cloud Infrastructure Registry, GitHub Container Registry, and such) because
  this image contains sensitive information about the domain, including keys and
  credentials that are used to access external resources (for example, the data source password).
  For more information, see
  [WebLogic domain in image protection]({{<relref "/security/domain-security/image-protection#weblogic-domain-in-container-image-protection">}}).
  {{% /notice %}}
* Create a Kubernetes domain YAML file, `domain.yaml`, in the directory that is created above. This YAML file can be used to create the Kubernetes resource using the `kubectl create -f` or `kubectl apply -f` command.
```shell
$ kubectl apply -f /<path to output-directory>/weblogic-domains/<domainUID>/domain.yaml
```

* Create a Kubernetes domain YAML file, `domain.yaml`, in the directory that is created above.
  This YAML file can be used to create the Kubernetes resource using the `kubectl create -f`
  or `kubectl apply -f` command:

    ```shell
    $ kubectl apply -f /<path to output-directory>/weblogic-domains/<domainUID>/domain.yaml
    ```

As a convenience, using the `-e` option, the script can optionally create the domain object,
which in turn results in the creation of the corresponding WebLogic Server pods and services as well.

The usage of the create script is as follows:

```shell
$ sh create-domain.sh -h
```
```
usage: create-domain.sh -o dir -i file [-e] [-v] [-h]
  -i Parameter inputs file, must be specified.
  -o Output directory for the generated YAML files, must be specified.
  -u User name used in building the image for WebLogic domain in image.
  -p Password used in building the image for WebLogic domain in image.
  -e Also create the resources in the generated YAML files, optional.
  -v Validate the existence of persistentVolumeClaim, optional.
  -h Help
```

If you copy the sample scripts to a different location, make sure that you copy everything in
the `<weblogic-kubernetes-operator-project>/kubernetes/samples/scripts` directory together
into the target directory, maintaining the original directory hierarchy.

The default domain created by the script has the following characteristics:

* An Administration Server named `admin-server` listening on port `7001`.
* A configured cluster named `cluster-1` of size 3.
* Three Managed Servers, named `managed-server1`, `managed-server2`, and so on, listening on port `8001`.
* Log files that are located in `/shared/logs/<domainUID>`.
* No applications deployed.
* No data sources or JMS resources.
* A T3 channel.

The domain creation inputs can be customized by editing `create-domain-inputs.yaml`.

#### Configuration parameters
The following parameters can be provided in the inputs file.

| Parameter | Definition | Default |
| --- | --- | --- |
| `adminPort` | Port number of the Administration Server inside the Kubernetes cluster. | `7001` |
| `adminNodePort` | Port number of the Administration Server outside the Kubernetes cluster. | `30701` |
| `adminServerName` | Name of the Administration Server. | `admin-server` |
| `clusterName` | Name of the WebLogic cluster instance to generate for the domain. | `cluster-1` |
| `domainHome` | Home directory of the WebLogic domain. If not specified, the value is derived from the `domainUID` as `/shared/domains/<domainUID>`. | `/shared/domains/domain1` |
| `domainPVMountPath` | Mount path of the domain persistent volume. | `/shared` |
| `domainUID` | Unique ID that will be used to identify this particular domain. Used as the name of the generated WebLogic domain as well as the name of the Domain. This ID must be unique across all domains in a Kubernetes cluster. This ID cannot contain any character that is not valid in a Kubernetes Service name. | `domain1` |
| `exposeAdminNodePort` | Boolean indicating if the Administration Server is exposed outside of the Kubernetes cluster. | `false` |
| `exposeAdminT3Channel` | Boolean indicating if the T3 administrative channel is exposed outside the Kubernetes cluster. | `false` |
| `fmwDomainType` | FMW Infrastructure Domain Type. Legal values are `JRF` or `RestrictedJRF`. | `JRF` |
| `httpAccessLogInLogHome` | Boolean indicating if server HTTP access log files should be written to the same directory as `logHome`. Otherwise, server HTTP access log files will be written to the directory specified in the WebLogic domain home configuration. | `true` |
| `image` | FMW Infrastructure image. The operator requires FMW Infrastructure 12.2.1.3.0 with patch 29135930 applied or FMW Infrastructure 12.2.1.4.0. For details on how to obtain or create the image, see [FMW Infrastructure domains]({{< relref "/userguide/managing-fmw-domains/fmw-infra/#obtaining-the-fmw-infrastructure-image" >}}). | `container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4` |
| `imagePullPolicy` | WebLogic Server image pull policy. Legal values are `IfNotPresent`, `Always`, or `Never`. | `IfNotPresent` |
| `imagePullSecretName` | Name of the Kubernetes Secret to access the container registry to pull the WebLogic Server image. The presence of the secret will be validated when this parameter is specified. |  |
| `includeServerOutInPodLog` | Boolean indicating whether to include the server `.out` in the pod's `stdout`. | `true` |
| `initialManagedServerReplicas` | Number of Managed Servers to start initially for the domain. | `2` |
| `javaOptions` | Java options for starting the Administration Server and Managed Servers. A Java option can have references to one or more of the following pre-defined variables to obtain WebLogic domain information: `$(DOMAIN_NAME)`, `$(DOMAIN_HOME)`, `$(ADMIN_NAME)`, `$(ADMIN_PORT)`, and `$(SERVER_NAME)`. | `-Dweblogic.StdoutDebugEnabled=false` |
| `logHome` | The in-pod location for the domain log, server logs, server out, Node Manager log, introspector out, and server HTTP access log files. If not specified, the value is derived from the `domainUID` as `/shared/logs/<domainUID>`. | `/shared/logs/domain1` |
| `managedServerNameBase` | Base string used to generate Managed Server names. | `managed-server` |
| `managedServerPort` | Port number for each Managed Server. | `8001` |
| `namespace` | Kubernetes Namespace in which to create the domain. | `default` |
| `persistentVolumeClaimName` | Name of the persistent volume claim. If not specified, the value is derived from the `domainUID` as `<domainUID>-weblogic-sample-pvc`. | `domain1-weblogic-sample-pvc` |
| `productionModeEnabled` | Boolean indicating if production mode is enabled for the domain. | `true` |
| `serverStartPolicy` | Determines which WebLogic Server instances will be started. Legal values are `NEVER`, `IF_NEEDED`, `ADMIN_ONLY`. | `IF_NEEDED` |
| `t3ChannelPort` | Port for the T3 channel of the network access point. | `30012` |
| `t3PublicAddress` | Public address for the T3 channel.  This should be set to the public address of the Kubernetes cluster.  This would typically be a load balancer address. <p/>For development environments only, in a single server (all-in-one) Kubernetes Deployment, this may be set to the address of the master, or at the very least, it must be set to the address of one of the worker nodes. | If not provided, the script will attempt to set it to the IP address of the Kubernetes cluster. |
| `weblogicCredentialsSecretName` | Name of the Kubernetes Secret for the Administration Server user name and password. If not specified, then the value is derived from the `domainUID` as `<domainUID>-weblogic-credentials`. | `domain1-weblogic-credentials` |
| `serverPodCpuRequest`, `serverPodMemoryRequest`, `serverPodCpuCLimit`, `serverPodMemoryLimit` |  The maximum amount of compute resources allowed, and minimum amount of compute resources required, for each server pod. Please refer to the Kubernetes documentation on `Managing Compute Resources for Containers` for details. | Resource requests and resource limits are not specified. |
| `rcuCredentialsSecret` | The Kubernetes Secret containing the database credentials. | `domain1-rcu-credentials` |
| `rcuDatabaseURL` | The database URL. | `database:1521/service` |
| `rcuSchemaPassword` | Password for the RCU database schema. | Must be provided for `JRF` FMW domain type |
| `rcuSchemaPrefix` | The schema prefix to use in the database, for example `SOA1`.  You may wish to make this the same as the domainUID in order to simplify matching domains to their RCU schemas. | `domain1` |
| `toolsDir` | The directory where WebLogic Deploy Tool and WebLogic Image Tool are installed. The script will install these tools to this directory if they are not already installed. | `/tmp/dhii-sample/tools` |
| `wdtVersion` | Version of the WebLogic Deploy Tool to be installed by the script. This can be a specific version, such as 1.9.10, or `LATEST`.  | `LATEST` |
| `witVersion` | Version of the WebLogic Image Tool to be installed by the script. This can be a specific version, such as 1.9,10, or `LATEST`.  | `LATEST` |

Note that the names of the Kubernetes resources in the generated YAML files may be formed with the
value of some of the properties specified in the inputs YAML file. Those properties include
the `adminServerName`, `clusterName`, and `managedServerNameBase`. If those values contain any
characters that are invalid in a Kubernetes Service name, those characters are converted to
valid values in the generated YAML files. For example, an uppercase letter is converted to a
lowercase letter and an underscore `("_")` is converted to a hyphen `("-")`.

The sample demonstrates how to create a FMW Infrastructure domain home and associated Kubernetes resources for a domain
that has one cluster only. In addition, the sample provides the capability for users to supply their own scripts
to create the domain home for other use cases. The generated domain YAML file could also be modified to cover more use cases.

#### Verify the results

The create script will verify that the domain was created, and will report failure if there was any error.
However, it may be desirable to manually verify the domain, even if just to gain familiarity with the
various Kubernetes objects that were created by the script.

Note that the example results below use the `default` Kubernetes Namespace. If you are using a different
namespace, you need to replace `NAMESPACE` in the example `kubectl` commands with the actual Kubernetes Namespace.

##### Generated YAML files with the default inputs

The content of the generated `domain.yaml`:

```yaml
# Copyright (c) 2017, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# This is an example of how to define a Domain resource.
#
apiVersion: "weblogic.oracle/v8"
kind: Domain
metadata:
  name: fmwdomain
  namespace: default
  labels:
    weblogic.domainUID: fmwdomain
spec:
  # The WebLogic Domain Home
  domainHome: /shared/domains/fmwdomain
  # The domain home source type
  # Set domain home type to PersistentVolume for domain-in-pv, Image for domain-in-image, or FromModel for model-in-image
  domainHomeSourceType: Image

  # The WebLogic Server image that the Operator uses to start the domain
  image: "domain-home-in-image:12.2.1.4"

  # imagePullPolicy defaults to "Always" if image version is :latest
  imagePullPolicy: "IfNotPresent"

  # Identify which Secret contains the credentials for pulling an image
  #imagePullSecrets:
  #- name:

  # Identify which Secret contains the WebLogic Admin credentials (note that there is an example of
  # how to create that Secret at the end of this file)
  webLogicCredentialsSecret:
    name: fmwdomain-weblogic-credentials

  # Whether to include the server out file into the pod's stdout, default is true
  includeServerOutInPodLog: true
  # Whether to enable log home

  logHomeEnabled: true

  # The in-pod location for domain log, server logs, server out, introspector out, and Node Manager log files
  logHome: /shared/logs/fmwdomain

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
    serverPod:
      # an (optional) list of environment variable to be set on the admin servers
      env:
        - name: USER_MEM_ARGS
          value: "-Djava.security.egd=file:/dev/./urandom -Xms512m -Xmx1024m "

  # clusters is used to configure the desired behavior for starting member servers of a cluster.  
  # If you use this entry, then the rules will be applied to ALL servers that are members of the named clusters.
  clusters:
  - clusterName: cluster-1
    serverStartState: "RUNNING"
    serverPod:
      # Instructs Kubernetes scheduler to prefer nodes for new cluster members where there are not
      # already members of the same cluster.
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchExpressions:
                    - key: "weblogic.clusterName"
                      operator: In
                      values:
                        - $(CLUSTER_NAME)
                topologyKey: "kubernetes.io/hostname"
    replicas: 2
  # The number of managed servers to start for unlisted clusters
  # replicas: 1

  # Istio
  # configuration:
  #   istio:
  #     enabled: 
  #     readinessPort: 

```

#### Verify the domain

To confirm that the domain was created, use this command:

```shell
$ kubectl describe domain DOMAINUID -n NAMESPACE
```

Replace `DOMAINUID` with the `domainUID` and `NAMESPACE` with the actual namespace.

Here is an example of the output of this command:

```
Name:         fmwdomain
Namespace:    default
Labels:       weblogic.domainUID=fmwdomain
Annotations:  kubectl.kubernetes.io/last-applied-configuration:
                {"apiVersion":"weblogic.oracle/v8","kind":"Domain","metadata":{"annotations":{},"labels":{"weblogic.domainUID":"fmwdomain"},"name":"fmwdom...
API Version:  weblogic.oracle/v8
Kind:         Domain
Metadata:
  Creation Timestamp:  2021-03-29T18:02:42Z
  Generation:          1
  Resource Version:    30822957
  Self Link:           /apis/weblogic.oracle/v8/namespaces/default/domains/fmwdomain
  UID:                 519a406d-e001-4811-a5be-a887d05e8d50
Spec:
  Admin Server:
    Server Pod:
      Env:
        Name:            USER_MEM_ARGS
        Value:           -Djava.security.egd=file:/dev/./urandom -Xms512m -Xmx1024m 
    Server Start State:  RUNNING
  Clusters:
    Cluster Name:  cluster-1
    Replicas:      2
    Server Pod:
      Affinity:
        Pod Anti Affinity:
          Preferred During Scheduling Ignored During Execution:
            Pod Affinity Term:
              Label Selector:
                Match Expressions:
                  Key:       weblogic.clusterName
                  Operator:  In
                  Values:
                    $(CLUSTER_NAME)
              Topology Key:       kubernetes.io/hostname
            Weight:               100
    Server Start State:           RUNNING
  Data Home:                      
  Domain Home:                    /u01/oracle/user_projects/domains/fmwdomain
  Domain Home Source Type:        Image
  Image:                          domain-home-in-image:12.2.1.4
  Image Pull Policy:              IfNotPresent
  Include Server Out In Pod Log:  true
  Server Pod:
    Env:
      Name:             JAVA_OPTIONS
      Value:            -Dweblogic.StdoutDebugEnabled=false
      Name:             USER_MEM_ARGS
      Value:            -Djava.security.egd=file:/dev/./urandom -Xms256m -Xmx1024m 
  Server Start Policy:  IF_NEEDED
  Web Logic Credentials Secret:
    Name:  domain1-weblogic-credentials
Status:
  Clusters:
    Cluster Name:      cluster-1
    Maximum Replicas:  3
    Minimum Replicas:  0
    Ready Replicas:    1
    Replicas:          2
    Replicas Goal:     2
  Conditions:
    Last Transition Time:        2021-03-29T18:04:00.819Z
    Reason:                      ManagedServersStarting
    Status:                      True
    Type:                        Progressing
  Introspect Job Failure Count:  0
  Replicas:                      2
  Servers:
    Desired State:  RUNNING
    Health:
      Activation Time:  2021-03-29T18:03:59.047Z
      Overall Health:   ok
      Subsystems:
        Subsystem Name:  ServerRuntime
        Symptoms:
    Node Name:      alai-1
    Server Name:    admin-server
    State:          RUNNING
    Cluster Name:   cluster-1
    Desired State:  RUNNING
    Health:
      Activation Time:  2021-03-29T18:09:43.946Z
      Overall Health:   ok
      Subsystems:
        Subsystem Name:  ServerRuntime
        Symptoms:
    Node Name:      alai-1
    Server Name:    managed-server1
    State:          RUNNING
    Cluster Name:   cluster-1
    Desired State:  RUNNING
    Node Name:      alai-1
    Server Name:    managed-server2
    State:          STARTING
    Cluster Name:   cluster-1
    Desired State:  SHUTDOWN
    Server Name:    managed-server3
  Start Time:       2021-03-29T18:02:42.816Z
Events:
  Type    Reason                    Age    From  Message
  ----    ------                    ----   ----  -------
  Normal  DomainCreated             8m55s        Domain resource fmwdomain was created
  Normal  DomainProcessingStarting  8m55s        Creating or updating Kubernetes presence for WebLogic Domain with UID fmwdomain
```

In the `Status` section of the output, the available servers and clusters are listed.
Note that if this command is issued very soon after the script finishes, there may be
no servers available yet, or perhaps only the Administration Server but no Managed Servers.
The operator will start up the Administration Server first and wait for it to become ready
before starting the Managed Servers.

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
NAME                                     READY   STATUS    RESTARTS   AGE
fmwdomain-admin-server                   1/1     Running   0          10m
fmwdomain-managed-server1                1/1     Running   0          9m8s
fmwdomain-managed-server2                1/1     Running   0          9m8s
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
NAME                                TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)           AGE
fmwdomain-admin-server              ClusterIP   None             <none>        7001/TCP          15h
fmwdomain-admin-server-ext          NodePort    10.101.26.42     <none>        7001:30731/TCP    15h
fmwdomain-cluster-cluster-1         ClusterIP   10.107.55.188    <none>        8001/TCP          15h
fmwdomain-managed-server1           ClusterIP   None             <none>        8001/TCP          15h
fmwdomain-managed-server2           ClusterIP   None             <none>        8001/TCP          15h
```

#### Delete the domain

The generated YAML file in the `/<path to output-directory>/weblogic-domains/<domainUID>` directory can be used to delete the Kubernetes resource. Use the following command to delete the domain:

```shell
$ kubectl delete -f domain.yaml
```

#### Delete the generated image

The generated image can be deleted by using `docker rmi` command when the image is no longer needed.
Use the following command to delete an image tagged with `domain-home-in-image:12.2.1.4`:

```shell
$ docker rmi domain-home-in-image:12.2.1.4
```

#### Delete the tools directory

Clean up the directory where WebLogic Deploy Tool and WebLogic Image Tool are installed to by the `create-domain.sh` script if they are no longer needed.

```shell
$ rm -rf /tmp/dhii-sample/tools/
```
