---
title: "Container image protection"
date: 2019-03-08T19:00:49-05:00
weight: 1
description: "WebLogic domain in image protection"
---

#### WebLogic domain in image protection

{{% notice warning %}}
Oracle strongly recommends storing the container images that contain a
WebLogic domain home as private in the container registry.
In addition to any local registry, public container registries include
[GItHub Container Registry](https://ghcr.io/) and the
[Oracle Cloud Infrastructure Registry](https://cloud.oracle.com/containers/registry) (OCIR).
{{% /notice %}}

The WebLogic domain home that is part of an image contains sensitive
information about the domain including keys and credentials that are used to
access external resources (for example, the data source password). In addition, the image
may be used to create a running server that further exposes the WebLogic domain
outside of the Kubernetes cluster.

There are two main options to pull images from a private registry:

1. Specify the image pull secret on the WebLogic `Domain` resource.
2. Set up the `ServiceAccount` in the domain namespace with an image pull secret.


##### 1. Use `imagePullSecrets` with the `Domain` resource.

In order to access an image that is protected by a private registry, the
`imagePullSecrets` should be specified in the Kubernetes `Domain` resource definition:
``` yaml
apiVersion: "weblogic.oracle/v2"
kind: Domain
metadata:
  name: domain1
  namespace: domain1-ns
  labels:
    weblogic.domainUID: domain1
spec:
  domainHomeSourceType: Image
  image: "my-domain-home-in-image"
  imagePullPolicy: "IfNotPresent"
  imagePullSecrets:
  - name: "my-registry-pull-secret"
  webLogicCredentialsSecret:
    name: "domain1-weblogic-credentials"
```
To create the Kubernetes Secret, `my-registry-pull-secret`, in
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

For more information about creating Kubernetes Secrets for accessing
the registry, see the Kubernetes documentation about
[pulling an image from a private registry](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/).

##### 2. Set up the Kubernetes `ServiceAccount` with `imagePullSecrets`.

An additional option for accessing an image protected by a private registry
is to set up the Kubernetes `ServiceAccount` in the namespace running the
WebLogic domain with a set of image pull secrets thus avoiding the need to
set `imagePullSecrets` for each `Domain` resource being created (because each resource
instance represents a WebLogic domain that the operator is managing).

The Kubernetes Secret would be created in the same manner as shown above and then the
`ServiceAccount` would be updated to include this image pull secret:
```bash
$ kubectl patch serviceaccount default -n domain1-ns \
  -p '{"imagePullSecrets": [{"name": "my-registry-pull-secret"}]}'
```

For more information about updating a Kubernetes `ServiceAccount`
for accessing the registry, see the Kubernetes documentation about
[configuring service accounts](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/#add-imagepullsecrets-to-a-service-account).
