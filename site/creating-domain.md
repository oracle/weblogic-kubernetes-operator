# Creating a WebLogic domain

The WebLogic domain must be installed into the folder that will be mounted as `/shared/domain`. The recommended approach is to use the provided `create-domain-job.sh` script; however, instructions are also provided for manually installing and configuring a WebLogic domain (see [Manually creating a domain](manually-creating-domain.md)).

Note that there is a short video demonstration of the domain creation process available [here](https://youtu.be/Ey7o8ldKv9Y).

## Important considerations and restrictions for WebLogic domains in Kubernetes

When running a WebLogic domain in Kubernetes, there are some additional considerations that must be taken into account to ensure correct functioning:

*	Multicast is not well supported in Kubernetes at the time of writing.  Some networking providers have some support for multicast, but it is generally considered of “beta” quality.  Oracle recommends configuring WebLogic clusters to use unicast.
*	The `ListenAddress` for all servers must be set to the correct DNS name; it should not be set to `0.0.0.0` or left blank.  This is required for cluster discovery of servers to work correctly.
*	If there is a desire to expose a T3 channel outside of the Kubernetes cluster -- for example, to allow WLST or RMI connections from clients outside Kubernetes -- then the recommended approach is to define a dedicated channel (per server) and to expose that channel using a `NodePort` service.  It is required that the channel’s internal and external ports be set to the same value as the chosen `NodePort`; for example, they could all be `32000`.  If all three are not the same, WebLogic Server will reject T3 connection requests.

## Creating a domain namespace

Oracle recommends creating namespaces to host WebLogic domains. It is not required to maintain a one-to-one relationship between WebLogic domains and Kubernetes namespaces, but this may be done if desired. More than one WebLogic domain may be run in a single namespace if desired.

Any namespaces that were listed in the `targetNamespaces` parameter in the operator parameters file when deploying the operator would have been created by the script.  

To create an additional namespace, issue the following command:

```
kubectl create namespace NAMESPACE
```

In this command, replace `NAMESPACE` with the desired namespace.

**Note:** Kubernetes does not allow uppercase characters in the `NAMESPACE`.  Only lowercase characters, numbers, and the hyphen character are allowed.

## Setting up secrets for the Administration Server credentials

The username and password credentials for access to the Administration Server must be stored in a Kubernetes secret in the same namespace that the domain will run in.  The script does not create the secret in order to avoid storing the credentials in a file.  Oracle recommends that this command be executed in a secure shell and appropriate measures be taken to protect the security of the credentials.

Issue the following command to create the secret:

```
kubectl -n NAMESPACE create secret generic SECRET_NAME
  --from-literal=username=ADMIN-USERNAME
  --from-literal=password=ADMIN-PASSWORD
```

In this command, replace the uppercase items with the correct values for the domain.

The `SECRET_NAME` value specified here must be in the format `DOMAINUID-weblogic-credentials` where `DOMAINUID` is replaced with the `domainUID` for the domain.

## Important considerations for persistent volumes

WebLogic domains that are managed by the operator are required to store their configuration and state on a persistent volume.  This volume is mounted read/write on all containers that are running a server in the domain.  There are many different persistent volume providers, but they do not all support multiple read/write mounts.  It is required that the chosen provider supports multiple read/write mounts. Details of available providers are available at [https://kubernetes.io/docs/concepts/storage/persistent-volumes](https://kubernetes.io/docs/concepts/storage/persistent-volumes).  Careful attention should be given to the table in the section titled “Access Modes”.

In a single-node Kubernetes cluster, such as may be used for testing or proof of concept activities, `HostPath` provides the simplest configuration.  In a multinode Kubernetes cluster a `HostPath` that is located on shared storage mounted by all nodes in the Kubernetes cluster is the simplest configuration.  If nodes do not have shared storage, then NFS is probably the most widely available option.  There are other options listed in the referenced table.

## Creating a persistent volume for the domain

The persistent volume for the domain must be created using the appropriate tools before running the script to create the domain.  In the simplest case, namely the `HostPath` provider, this means creating a directory on the Kubernetes master and ensuring that it has the correct permissions:

```
mkdir –m 777 –p /path/to/domain1PersistentVolume
```

For other providers, consult the documentation for the provider for instructions on how to create a persistent volume.

## Customizing the domain parameters file

The domain is created with the provided installation script (`create-domain-job.sh`).  The input to this script is the file `create-domain-job-inputs.yaml`, which needs to be updated to reflect the target environment.  

The following parameters must be provided in the input file:

### CONFIGURATION PARAMETERS FOR THE CREATE DOMAIN JOB

| Parameter	| Definition	| Default |
| --- | --- | --- |
| adminPort	| Port number for the Administration Server.	| 7001 |
| adminServerName	| The name of the Administration Server.	| admin-server |
| createDomainScript	| Script used to create the domain.  This parameter should not be modified. |	/u01/weblogic/create-domain-script.sh |
| domainName	| Name of the WebLogic domain to create.	| base_domain |
| domainUid	| Unique ID that will be used to identify this particular domain. This ID must be unique across all domains in a Kubernetes cluster.	| domain1 |
| managedServerCount	| Number of Managed Server instances to generate for the domain.	| 2 |
| managedServerNameBase	| Base string used to generate Managed Server names.	| managed-server |
| managedServerPort	| Port number for each Managed Server.	| 8001 |
| persistencePath	| Physical path of the persistent volume storage. |	/scratch/k8s_dir/persistentVolume001 |
| persistenceSize	| Total storage allocated by the persistent volume.	| 10Gi |
| persistenceStorageClass	| Name of the storage class to set for the persistent volume and persistent volume claim.	| weblogic |
| persistenceVolumeClaimName	| Name of the Kubernetes persistent volume claim for this domain.	| pv001-claim |
| persistenceVolumeName	| Name of the Kubernetes persistent volume for this domain.	| pv001 |
| productionModeEnabled	| Boolean indicating if production mode is enabled for the domain. | true |
| secretsMountPath |	Path for mounting secrets.  This parameter should not be modified. |	/var/run/secrets-domain1 |
| secretName	| Name of the Kubernetes secret for the Administration Server's username and password. |	domain1-weblogic-credentials |
| T3ChannelPort	| Port for the T3Channel of the NetworkAccessPoint.	| 7002 |
| namespace	| The Kubernetes namespace to create the domain in.	| default |
| loadBalancerAdminPort	| The node port for the load balancer to accept admin requests.	| 30315 |
| loadBalancerWebPort	| The node port for the load balancer to accept user traffic. 	| 30305 |
| enableLoadBalancerAdminPort	| Determines whether the load balancer administration port should be exposed outside the Kubernetes cluster.	| false |
| enablePrometheusIntegration	| Determines whether the Prometheus integration will be enabled.  If set to ‘true’, then the WebLogic Monitoring Exporter will be installed on all servers in the domain and configured to export metrics to Prometheus. |	false |

## Limitations of the create domain script

This Technology Preview release has some limitations in the create domain script that users should be aware of.

*	The script assumes the use of the `HostPath` persistent volume provider.
*	The script creates the specified number of Managed Servers and places them all in one cluster.
*	The script always creates one cluster.

Oracle intends to remove these limitations in a future release.

## Using the script to create a domain

To execute the script and create a domain, issue the following command:

```
./create-domain-job.sh –i create-domain-job-inputs.yaml
```

## What the script does

The script will perform the following steps:

*	Create Kubernetes YAML files based on the provided inputs.
*	Create a persistent volume for the shared state.
*	Create a persistent volume claim for that volume.
*	Create a Kubernetes job that will start up a utility WebLogic Server container and run WLST to create the domain on the shared storage.
*	Wait for the job to finish and then create a domain custom resource for the new domain.

The default domain created the script has the following characteristics:

*	An Administration Server named `admin-server` listening on port `7001`.
*	A single cluster named `cluster-1` containing the specified number of Managed Servers.
*	A Managed Server named `managed-server1` listening on port `8001` (and so on up to the requested number of Managed Servers).
*	Log files are located in `/shared/logs`.
*	No applications deployed.
*	No data sources.
*	No JMS resources.
*	A T3 channel.

## Common problems

This section provides details of common problems that occur during domain creation and how to resolve them.

### Persistent volume provider not configured correctly

Possibly the most common problem experienced during testing was incorrect configuration of the persistent volume provider.  The persistent volume must be accessible to all Kubernetes nodes, and must be able to be mounted as Read/Write/Many.  If this is not the case, the domain creation will fail.

The simplest case is where the `HostPath` provider is used.  This can be either with one Kubernetes node, or with the `HostPath` residing in shared storage available at the same location on every node (for example, on an NFS mount).  In this case, the path used for the persistent volume must have its permission bits set to 777.

## YAML files created by the script

The script will create a Kubernetes YAML file that is then used to create a Kubernetes job that will perform the actual work to create the domain.  The job will start a pod with the standard WebLogic Server Docker image and will execute some BASH and WLST scripts to create the domain.  

Here is the first part of that YAML file:

```
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
apiVersion: batch/v1
kind: Job
metadata:
  name: domain-domain1-job
  namespace: domain1
spec:
  template:
    metadata:
      labels:
        app: domain-domain1-job
        weblogic.domainUID: domain1
    spec:
      restartPolicy: Never
      containers:
        - name: domain-job
          image: store/oracle/weblogic:12.2.1.3
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 7001
          volumeMounts:
          - mountPath: /u01/weblogic
            name: config-map-scripts
          - mountPath: /shared
            name: pv-storage
          - mountPath: /var/run/secrets-domain1
            name: secrets
          command: ["/bin/sh"]
          args: ["/u01/weblogic/create-domain-job.sh"]
          env:
            - name: SHARED_PATH
              value: "/shared"
      volumes:
        - name: config-map-scripts
          configMap:
            name: domain-domain1-scripts
        - name: pv-storage
          persistentVolumeClaim:
            claimName: domain1-pv001-claim
        - name: secrets
          secret:
            secretName: domain1-weblogic-credentials

(many more lines omitted)
```

The lines omitted at the end contain the actual scripts that are executed in this container.  These should generally not be modified, except by developers.  

write more about the file


## Verifying the domain creation

The script will verify that the domain was created, and will report failure if there was any error.  However, it may be desirable to manually verify the domain, even if just to gain familiarity with the various Kubernetes objects that were created by the script.

### Verify the domain custom resource

To confirm that the domain custom resource was created, use this command:

```
kubectl describe domain DOMAINUID -n NAMESPACE
```

Replace `DOMAINUID` with the `domainUID`, and replace `NAMESPACE` with the namespace that the domain was created in.  The output of this command will provide details of the domain, as shown in this example:

```
$ kubectl describe domain domain1 -n domain1
Name:         domain1
Namespace:    domain1
Labels:       <none>
Annotations:  kubectl.kubernetes.io/last-applied-configuration={"apiVersion":"weblogic.oracle/v1","kind":"Domain","metadata":{"annotations":{},"name":"domain1","namespace":"domain1"},"spec":{"adminSecret":{"name":"...
API Version:  weblogic.oracle/v1
Kind:         Domain
Metadata:
  Cluster Name:        
  Creation Timestamp:  2018-01-18T17:27:49Z
  Generation:          0
  Resource Version:    2748848
  Self Link:           /apis/weblogic.oracle/v1/namespaces/domain1/domains/domain1
  UID:                 e79bd64f-fc74-11e7-b3f3-0021f64c21ba
Spec:
  Admin Secret:
    Name:  domain1-weblogic-credentials
  As Env:
  As Name:  admin-server
  As Port:  7001
  Cluster Startup:
    Cluster Name:   cluster-1
    Desired State:  RUNNING
    Env:
      Name:     JAVA_OPTIONS
      Value:    -Dweblogic.StdoutDebugEnabled=false
      Name:     USER_MEM_ARGS
      Value:    -Xms64m -Xmx256m
    Replicas:   2
  Domain Name:  base_domain
  Domain UID:   domain1
  Export T3 Channels:
    T3Channel
  Image:              store/oracle/weblogic:12.2.1.3
  Image Pull Policy:  IfNotPresent
  Replicas:           1
  Server Startup:
    Desired State:  RUNNING
    Env:
      Name:         JAVA_OPTIONS
      Value:        -Dweblogic.StdoutDebugEnabled=false
      Name:         USER_MEM_ARGS
      Value:        -Xms64m -Xmx256m
    Server Name:    admin-server
  Startup Control:  AUTO
Status:
  Available Clusters:
    cluster-1
  Available Servers:
    managed-server2
    managed-server1
    admin-server
  Conditions:
    Last Transition Time:  2018-01-18T17:30:18.818Z
    Reason:                ServersReady
    Status:                True
    Type:                  Available
  Start Time:              2018-01-18T17:27:49.040Z
  Unavailable Clusters:
  Unavailable Servers:
Events:  <none>
```

In the "Status" section of the output, the available servers and clusters are listed.  Note that if this command is issued very soon after the script finishes, there may be no servers available yet, or perhaps only the Administration Server but no Managed Servers.  The operator will start up the Administration Server first and wait for it to become ready before starting Managed Servers.

### Verify pods

The following command can be used to see the pods running the servers:

```
kubectl get pods -n NAMESPACE
```

Here is an example of the output of this command:

```
$ kubectl get pods -n domain1
NAME                      READY     STATUS    RESTARTS   AGE
domain1-admin-server      1/1       Running   0          22h
domain1-managed-server1   1/1       Running   0          22h
domain1-managed-server2   1/1       Running   0          22h
```

### Verify services

The following command can be used to see the services for the domain:

```
kubectl get services -n NAMESPACE
```

Here is an example of the output of this command:

```
$ kubectl get services -n domain1
NAME                                        TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)           AGE
domain1-admin-server                        ClusterIP   10.100.202.99    <none>        7001/TCP          22h
domain1-admin-server-extchannel-t3channel   NodePort    10.98.239.197    <none>        30012:30012/TCP   22h
domain1-managed-server1                     ClusterIP   10.100.184.148   <none>        8001/TCP          22h
domain1-managed-server2                     ClusterIP   10.108.114.41    <none>        8001/TCP          22h
```

### Verify Ingresses

The following command can be used to see the Ingresses for the domain:

```
kubectl describe ing -n domain1
```

Here is an example of the output of this command:

```
$ kubectl describe ing -n domain1
Name:             domain1-cluster-1
Namespace:        domain1
Address:          
Default backend:  default-http-backend:80 (<none>)
Rules:
  Host  Path  Backends
  ----  ----  --------
  *     
        /   domain1-managed-server1:8001 (<none>)
        /   domain1-managed-server2:8001 (<none>)
Annotations:
Events:  <none>
```


## Configuring the domain readiness

Kubernetes has a concept of “readiness” that is used to determine when external requests should be routed to a particular pod.  The domain creation job provided with the operator configures the readiness probe to use the WebLogic Server ReadyApp, which provides a mechanism to check when the server is actually ready to process work, as opposed to simply being in the RUNNING state.  Often applications have some work to do after the server is RUNNING but before they are ready to process new requests.

ReadyApp provides an API that allows an application to register itself, so that its state will be taken into consideration when determining if the server is “ready”, and an API that allows the application to inform ReadyApp when it considers itself to be ready.

Add details of how to use ReadyApp API
