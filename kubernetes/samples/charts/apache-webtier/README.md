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
$ helm delete my-release
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

## Configuration

The following table lists the configurable parameters of the Apache webiter chart and their default values.


| Parameter                          | Description                                                   | Default               |
| -----------------------------------| ------------------------------------------------------------- | ----------------------|
| `image.registry`                   | Docker registry used to pull oracle images                    | `wlsldi-v2.docker.oraclecorp.com` |
| `image.repository`                 | WebLogic Apache webtier image                                 | `weblogic-webtier-apache-12.2.1.3.0` |
| `image.tag`                        | Tag of the image                                              | `latest`              |
| `image.pullPolicy`                 | Image pull policy                        `                    | `IfNotPresent`        |
| `persistence.enabled`              | Enable persistent storage for Apache webtier configuration    | `true`                |
| `persistence.hostPath`             | Physical path of the persistent storage                       | `/scratch/apache-webtier-config` |
| `persistence.mountPath`            | Mount point                                                   | `/config`             |
| `rbac.create`                      | If `true`, create and use RBAC resources                      | `true`                |
| `serviceAccount.create`            | If `true`, create a new service account                       | `true`                |
| `serviceAccount.name`              | Service account to be used. If not set and `serviceAccount.create` is `true`, a name is generated using the fullname template | `apache-webtier` |
| `service.nodePorts.http`           | Apache webiter web port                                       | `30305`               |

Specify each parameter using the `--set key=value[,key=value]` argument to `helm install`. For example:

```console
$ helm install --name my-release --set persistence.hostPath=/scratch/my-config apache-webtier
```

Alternatively, a YAML file that specifies the values for the parameters can be provided while
installing the chart. For example:

```console
$ helm install --name my-release --values values.yaml apache-webtier
```

## RBAC
By default the chart will not install the recommended RBAC roles and rolebindings.

You need to have the flag `--authorization-mode=RBAC` on the api server. See the following document for how to enable [RBAC](https://kubernetes.io/docs/admin/authorization/rbac/).

To determine if your cluster supports RBAC, run the following command:

```console
$ kubectl api-versions | grep rbac
```

If the output contains "beta", you may install the chart with RBAC enabled (see below).

### Enable RBAC role/rolebinding creation

To enable the creation of RBAC resources (On clusters with RBAC). Do the following:

```console
$ helm install --name my-release apache-webtier --set rbac.create=true
```
