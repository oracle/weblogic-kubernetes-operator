# Weblogic Domain Helm Chart
This chart creates a single Weblogic domain on a [Kubernetes](https://kubernetes.io/) cluster using the 
[Helm](https://github.com/kubernetes/helm) package manager.

## Prerequisites
- Kubernetes 1.7.5+ with Beta APIs enabled

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
Clone the git weblogic-operator repo:

```bash
git clone https://github.com/oracle/weblogic-kubernetes-operator.git
```

Change directory to the cloned git weblogic-operator repo:

```bash
cd weblogic-operator/kubernetes/helm-charts
```

To install the chart with the release `my-release`, namespace `my-namespace` and secret `my-secret' without creating a weblogic domain (such as when a WebLogic domain already exists):

```bash
helm install weblogic-domain --name my-release --namespace my-namespace --set secretName=my-secret --set createWeblogicDomain=false
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
| createWeblogicDomain | Boolean indicating if a weblogic domain should be created | true |
| adminNodePort | NodePort to expose for the admin server | 30701 |
| adminPort | Port number for Admin Server | 7001 |
| adminServerName | Name of the Admin Server | admin-server |
| clusterName | Cluster name | cluster-1 |
| createDomainScript | Script used to create the domain | /u01/weblogic/create-domain-script.sh |
| domainName | Name of the WebLogic domain to create | base_domain |
| domainUid | Unique id identifying a domain. The id must be unique across all domains in a Kubernetes cluster | domain1 |
| exposeAdminT3Channel | Boolean to indicate if the channel should be exposed as a service | false |
| exposeAdminNodePort | Boolean to indicate if the adminNodePort will be exposed | false |
| imagePullSecretName | Name of the Kubernetes secret to access the Docker Store to pull the Weblogic Docker image | |
| loadBalancer | Load balancer to deploy.  Supported values are: traefik, none | traefik |
| loadBalancerAdminPort | Load balancer admin port | 30315 |
| loadBalancerWebPort| Load balancer web port | 30305 |
| managedServerCount | Number of managed servers to generate for the domain | 2 |
| managedServerStartCount | Number of managed severs to initially start for a domain | 2 |
| managedServerNameBase | Base string used to generate managed server names | managed-server |
| managedServerPort | Port number for each managed server | 8001 |
| persistencePath | Physical path of the persistent volume storage | /scratch/k8s_dir/persistentVolume001 |
| persistenceSize | Total storage allocated by the persistent volume | 10Gi |
| productionModeEnabled | Boolean indicating if production mode is enabled for the domain | true |
| secretName | Name of the Kubernetes secret for the Admin Server's username and password | domain1-weblogic-credentials |
| t3ChannelPort | Port for the T3Channel of the NetworkAccessPoint | 30012 |
| t3PublicAddress | Public address for T3Channel of the NetworkAccessPoint. This value should be set to the Kubernetes server address, which you can get by running "kubectl cluster-info".  If this value is not set to that address, WLST will not be able to connect from outside the Kubernetes cluster | kubernetes |
 
Specify parameters to override default values using the `--set key=value[,key=value]` argument to helm install. For example:

```bash
helm install weblogic-domain --name my-release --namespace my-namespace --set managedServerCount=3
```

Alternatively, a YAML file that specifies the values for the parameters can be provided while installing the chart. For example:

```bash
helm install weblogic-domain --name my-release --namespace my-namespace --values values.yaml
```
