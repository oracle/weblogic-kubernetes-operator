# Apache webtier
[Apache HTTP Server] - Load Balancer for WebLogic domain on Kubernetes

## Install

```console
$ helm install apache-webtier
```

## Introduction

This chart bootstraps an [Apache HTTP Server] deployment on a [Kubernetes](http://kubernetes.io) cluster using the [Helm](https://helm.sh) package manager.


## Prerequisites

- Kubernetes 1.8+

```console
$ docker pull wlsldi-v2.docker.oraclecorp.com/weblogic-webtier-apache-12.2.1.3.0:latest
$ docker tag wlsldi-v2.docker.oraclecorp.com/weblogic-webtier-apache-12.2.1.3.0:latest store/oracle/apache:12.2.1.3
```

## Installing the Chart
To install the chart with the release name `my-release`:
```console
$ helm install --name my-release apache-webtier
```
The command deploys Apache HTTP Server on the Kubernetes cluster in the default configuration. The [configuration](#configuration) section lists the parameters that can be configured during installation.

> **Tip**: List all releases using `helm list`

## Uninstalling the Chart

To uninstall/delete the `my-release`:

```console
$ helm delete --purge my-release
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

## Configuration

The following table lists the configurable parameters of the Apache webtier chart and their default values.


| Parameter                          | Description                                                   | Default               |
| -----------------------------------| ------------------------------------------------------------- | ----------------------|
| `imageRegistry`                    | Docker registry used to pull image                            | `store/oracle`        |
| `image`                            | Apache webtier docker image                                   | `apache`              |
| `imageTag`                         | Image tag for the apache webtier docker image                 | `12.2.1.3`            |
| `imagePullPolicy`                  | Image pull policy for the apache webiter docker image         | `IfNotPresent`        |
| `volumePath`                       | Docker volume path for apache webtier                         | ``                    |
| `createRBAC`                       | Boolean indicating if RBAC resources should be created        | `true`                |
| `httpNodePort`                     | NodePort to expose for http access                            | `30305`               |
| `httpsNodePort`                    | NodePort to expose for https access                           | `30443`               |
| `virtualHostName`                  | The VirtualHostName of the Apache HTTP server                 | ``                    |
| `customCert`                       | The customer supplied certificate                             | ``                    |
| `customKey`                        | The customer supplied private key                             | ``                    |
| `domainUID`                        | Unique ID identifying a domain                                | `domain1`             |
| `clusterName`                      | Cluster name                                                  | `cluster-1`           |
| `adminServerName`                  | Name of the admin server                                      | `admin-server`        |
| `adminPort`                        | Port number for admin server                                  | `7001`                |
| `managedServerPort`                | Port number for each managed server                           | `8001`                |
| `location`                         | Prepath for all application deployed on WebLogic cluster      | `/weblogic`           |

Specify each parameter using the `--set key=value[,key=value]` argument to `helm install`. For example:

```console
$ helm install --name my-release --set volumePath=/scratch/my-config apache-webtier
```

Alternatively, a YAML file that specifies the values for the parameters can be provided while
installing the chart. For example:

```console
$ helm install --name my-release --values values.yaml apache-webtier
```

## RBAC
By default the chart will install the recommended RBAC roles and rolebindings.

You need to have the flag `--authorization-mode=RBAC` on the api server. See the following document for how to enable [RBAC](https://kubernetes.io/docs/admin/authorization/rbac/).

To determine if your cluster supports RBAC, run the following command:

```console
$ kubectl api-versions | grep rbac
```

If the output contains "beta", you may install the chart with RBAC enabled (see below).

### Disable RBAC role/rolebinding creation

To disable the creation of RBAC resources (On clusters with RBAC). Do the following:

```console
$ helm install --name my-release apache-webtier --set createRBAC=false
```
