---
title: "Docker image protection"
date: 2019-03-08T19:00:49-05:00
weight: 1
description: "WebLogic domain in Docker image protection"
---

#### WebLogic domain in Docker image protection

{{% notice warning %}}
Oracle strongly recommends storing the Docker images that contain a
WebLogic domain home as private in the Docker registry.
In addition to any local registry, public Docker registries include
[Docker Hub](https://hub.docker.com/) and the
[Oracle Cloud Infrastructure Registry](https://cloud.oracle.com/containers/registry) (OCIR).
{{% /notice %}}

The WebLogic domain home that is part of a Docker image contains sensitive
information about the domain including keys and credentials that are used to
access external resources (for example, data source password). In addition, the Docker image
may be used to create a running server that further exposes the WebLogic domain
outside of the Kubernetes cluster.

There are two main options to pull images from a private registry:

1. Specify the image pull secret on the WebLogic `Domain` resource.
2. Set up the `ServiceAccount` in the domain namespace with an image pull secret.


##### 1. Use `imagePullSecrets` with the `Domain` resource.

In order to access a Docker image that is protected by a private registry, the
`imagePullSecrets` should be specified on the Kubernetes `Domain` resource definition:
``` yaml
apiVersion: "weblogic.oracle/v2"
kind: Domain
metadata:
  name: domain1
  namespace: domain1-ns
  labels:
    weblogic.resourceVersion: domain-v2
    weblogic.domainUID: domain1
spec:
  domainHomeInImage: true
  image: "my-domain-home-in-image"
  imagePullPolicy: "IfNotPresent"
  imagePullSecrets:
  - name: "my-registry-pull-secret"
  webLogicCredentialsSecret:
    name: "domain1-weblogic-credentials"
```
To create the Kubernetes secret called `my-registry-pull-secret` in
the namespace where the domain will be running, `domain1-ns`, the following
command can be used:
```bash
$ kubectl create secret docker-registry my-registry-pull-secret \
  -n domain1-ns \
  --docker-server=<registry-server> \
  --docker-username=<name> \
  --docker-password=<password> \
  --docker-email=<email>
```

For more information about creating Kubernetes secrets for accessing
the registry, see the Kubernetes documentation about
[pulling an image from a private registry](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/).

##### 2. Set up the Kubernetes `ServiceAccount` with `imagePullSecrets`.

An additional option for accessing a Docker image protected by a private registry
is to set up the Kubernetes `ServiceAccount` in the namespace running the
WebLogic domain with a set of image pull secrets thus avoiding the need to
set `imagePullSecrets` for each `Domain` resource being created (as each resource
instance represents a WebLogic domain that the operator is managing).

The Kubernetes secret would be created in the same manner as shown above and then the
`ServiceAccount` would be updated to include this image pull secret:
```bash
$ kubectl patch serviceaccount default -n domain1-ns \
  -p '{"imagePullSecrets": [{"name": "my-registry-pull-secret"}]}'
```

For more information about updating a Kubernetes `ServiceAccount`
for accessing the registry, see the Kubernetes documentation about
[configuring service accounts](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/#add-imagepullsecrets-to-a-service-account).
