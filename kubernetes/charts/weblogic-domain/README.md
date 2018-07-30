# Weblogic Domain Helm Chart
This chart creates a single Weblogic domain on a [Kubernetes](https://kubernetes.io/) cluster using the 
[Helm](https://github.com/kubernetes/helm) package manager.

## Prerequisites
- [Requirements for the Oracle WebLogic Server Kubernetes Operator](https://github.com/oracle/weblogic-kubernetes-operator/blob/master/README.md)

- Install [Helm](https://github.com/kubernetes/helm#install)

- Initialize Helm by installing Tiller, the server portion of Helm, to your Kubernetes cluster

```bash
helm init
```
- Install of the weblogic-operator helm chart

## Creating a Namespace 
We recommend that you create a namespace to run your WebLogic Domain(s) in.
You may wish to maintain a one to one relationship between WebLogic Domains
and Kubernetes namespaces, but this is not required.  You may run more than
one WebLogic Domain in a single namespace if you wish to. 

To create a namespace named `my-namespace`:

```bash
kubectl create namespace my-namespace
```

## Setting a Secret for the Admin Server
You must setup the Admin Server `username` and `password` credentials as a Kubernetes secret.

To create a secret named `my-secret` in the namespace `my-namespace`:

```bash
kubectl create secret generic my-secret --from-literal=username=ADMIN-USERNAME --from-literal=password=ADMIN-PASSWORD --namespace my-namespace
```
In this command, you must replace the uppercase items with your
own values.  The secret name will be needed when installing the helm chart.

## Installing the Chart

Change directory to the cloned git weblogic-kubernetes-operator repo:

```bash
cd kubernetes/charts
```

To install the chart with the release `my-release`, namespace `my-namespace` and secret `my-secret' without creating a weblogic domain (such as when a WebLogic domain already exists):

```bash
helm install weblogic-domain --name my-release --namespace my-namespace --set weblogicCredentialsSecretName=my-secret --set createWebLogicDomain=false
```

The command deploys weblogic-domain on the Kubernetes cluster in the default configuration. The [configuration](#configuration) section lists
the parameters that can be configured during installation.

> Note: if you do not pass the --name flag, a release name will be auto-generated. You can view releases by running helm list (or helm ls, for short).


## Uninstalling the Chart
To uninstall/delete the `my-release` deployment:

```bash
helm delete --purge my-release
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

## Configuration 
|  Key                           |  Description                      |  Default              |
| -------------------------------|-----------------------------------|-----------------------|
| createWebLogicDomain | Boolean indicating if a weblogic domain should be created | true |
| adminPort | Port number for Admin Server | 7001 |
| adminServerName | Name of the Admin Server | admin-server |
| domainName | Name of the WebLogic domain to create | base_domain |
| domainUID | Unique id identifying a domain. The id must be unique across all domains in a Kubernetes cluster | domain1 (needs to uncomment) |
| clusterType | Type of WebLogic Cluster. Legal values are "CONFIGURED" or "DYNAMIC" | DYNAMIC |
| startupControl | Determines which WebLogic Servers the Operator will start up. Legal values are "NONE", "ALL", "ADMIN", "SPECIFIED", or "AUTO" | "AUTO" |
| clusterName | Cluster name | cluster-1 |
| configuredManagedServerCount | Number of managed servers to generate for the domain | 2 |
| initialManagedServerReplicas | Number of managed severs to initially start for a domain | 2 |
| managedServerNameBase | Base string used to generate managed server names | managed-server |
| managedServerPort | Port number for each managed server | 8001 |
| weblogicImage | WebLogic Docker image | store/oracle/weblogic:12.2.1.3 |
| weblogicDomainStorageType | Persistent volume type for the domain's storage. The value must be 'HOST_PATH' or 'NFS' | HOST_PAT |
| weblogicDomainStorageNFSServer | The server name or ip address of the NFS server to use for the domain's storage | nfsServer (need to uncomment as necessary) |
| weblogicDomainStoragePath | Physical path of the persistent volume storage | /scratch/k8s_dir/domain1 (need to uncomment) |
| weblogicDomainStorageReclaimPolicy | Reclaim policy of the domain's persistent storage. The valid values are: 'Retain', 'Delete', and 'Recycle' | Retain |
| weblogicDomainStorageSize | Total storage allocated to the domain's persistent volume | 10Gi |
| productionModeEnabled | Boolean indicating if production mode is enabled for the domain | true |
| weblogicCredentialsSecretName | Name of the Kubernetes secret for the Admin Server's username and password | domain1-weblogic-credentials |
| weblogicImagePullSecretName | Name of the Kubernetes secret to access the Docker Store to pull the Weblogic Docker image | |
| t3ChannelPort | Port for the T3Channel of the NetworkAccessPoint | 30012 |
| t3PublicAddress | Public address for T3Channel of the NetworkAccessPoint. This value should be set to the Kubernetes server address, which you can get by running "kubectl cluster-info".  If this value is not set to that address, WLST will not be able to connect from outside the Kubernetes cluster | kubernetes |
| exposeAdminT3Channel | Boolean to indicate if the channel should be exposed as a service | false |
| adminNodePort | NodePort to expose for the admin server | 30701 |
| exposeAdminNodePort | Boolean to indicate if the adminNodePort will be exposed | false |
| loadBalancer | Load balancer to deploy.  Supported values are: APACHE, TRAEFIX, VOYAGER, NONE | TRAEFIK |
| loadBalancerVolumePath | Docker volume path for APACHE. | |
| loadBalancerExposeAdminPort| If the admin port is going to be exposed for APACHE | false |
| loadBalancerWebPort| Load balancer web port | 30305 |
| loadBalancerDashboardPort | Load balancer dashboard port | 30315 |
| javaOptions | Java option for WebLogic Server | -Dweblogic.StdoutDebugEnabled=false |
 
Specify parameters to override default values using the `--set key=value[,key=value]` argument to helm install. For example:

```bash
helm install weblogic-domain --name my-release --namespace my-namespace --set configuredManagedServerCount=3
```

Alternatively, a YAML file that specifies the values for the parameters can be provided while installing the chart. For example:

```bash
helm install weblogic-domain --name my-release --namespace my-namespace --values values.yaml
```
