# Apache webtier Helm chart

This Helm chart bootstraps an Apache HTTP Server deployment on a [Kubernetes](http://kubernetes.io) cluster using the [Helm](https://helm.sh) package manager.

The chart depends on the Docker image for the Apache HTTP Server with Oracle WebLogic Server Proxy Plugin (supported versions 12.2.1.3.0 and 12.2.1.4.0). See the details in [Apache HTTP Server with Oracle WebLogic Server Proxy Plugin on Docker](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-webtier-apache).

## Prerequisites

You will need to build a Docker image with the Apache webtier in it using the sample provided [here](https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-webtier-apache)
in order to use this load balancer.

## Installing the Chart
To install the chart with the release name `my-release`:
```shell
$ helm install my-release apache-webtier
```
The command deploys the Apache HTTP Server on the Kubernetes cluster with the default configuration. The [configuration](#configuration) section lists the parameters that can be configured during installation.

> **Tip**: List all releases using `helm list`

## Uninstalling the Chart

To uninstall/delete `my-release`:

```shell
$ helm uninstall my-release
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

## Configuration

The following table lists the configurable parameters of the Apache webtier chart and their default values.


| Parameter                          | Description                                                   | Default               |
| -----------------------------------| ------------------------------------------------------------- | ----------------------|
| `image`                            | Apache webtier Docker image                                   | `oracle/apache:12.2.1.3` |
| `imagePullPolicy`                  | Image pull policy for the Apache webtier Docker image         | `IfNotPresent`        |
| `imagePullSecrets`                 | Image pull Secrets required to access the registry containing the Apache webtier Docker image| ``|
| `persistentVolumeClaimName`        | Persistence Volume Claim name Apache webtier                  | ``                    |
| `createRBAC`                       | Boolean indicating if RBAC resources should be created        | `true`                |
| `httpNodePort`                     | Node port to expose for HTTP access                           | `30305`               |
| `httpsNodePort`                    | Node port to expose for HTTPS access                          | `30443`               |
| `virtualHostName`                  | The `VirtualHostName` of the Apache HTTP Server               | ``                    |
| `customCert`                       | The customer supplied certificate                             | ``                    |
| `customKey`                        | The customer supplied private key                             | ``                    |
| `domainUID`                        | Unique ID identifying a domain                                | `domain1`             |
| `clusterName`                      | Cluster name                                                  | `cluster-1`           |
| `adminServerName`                  | Name of the Administration Server                             | `admin-server`        |
| `adminPort`                        | Port number for Administration Server                         | `7001`                |
| `managedServerPort`                | Port number for each Managed Server                           | `8001`                |
| `location`                         | Prepath for all applications deployed on the WebLogic cluster | `/weblogic`           |
| `useNonPriviledgedPorts`           | Configuration of Apache webtier on NonPriviledgedPort         | `false`               |


Specify each parameter using the `--set key=value[,key=value]` argument to `helm install`. For example:

```shell
$ helm install my-release --set persistentVolumeClaimName=webtier-apache-pvc apache-webtier
```

Alternatively, a YAML file that specifies the values for the parameters can be provided while
installing the chart. For example:

```shell
$ helm install my-release --values values.yaml apache-webtier
```
## useNonPriviledgedPorts
By default, the chart will install the Apache webtier on PriviledgedPort (port 80). Set the flag `useNonPriviledgedPorts=true` to enable the Apache webtier to listen on port `8080`


## RBAC
By default, the chart will install the recommended RBAC roles and role bindings.

Set the flag `--authorization-mode=RBAC` on the API server. See the following document for how to enable [RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/).

To determine if your cluster supports RBAC, run the following command:

```shell
$ kubectl api-versions | grep rbac
```

If the output contains "beta", you may install the chart with RBAC enabled.

### Disable RBAC role/rolebinding creation

To disable the creation of RBAC resources (on clusters with RBAC). Do the following:

```shell
$ helm install my-release apache-webtier --set createRBAC=false
```
