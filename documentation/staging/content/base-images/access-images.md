---
title: "Access domain images"
date: 2019-02-23T16:45:55-05:00
weight: 4
description: "Set up Kubernetes to access domain images."
---

In most operator samples, it is assumed that the Kubernetes cluster has a single worker node
and any images that are needed by that node have either been created on that node or
externally pulled to the node from a registry (using `docker pull`).
This is fine for most demonstration purposes,
and if this assumption is correct, then no additional steps
are needed to ensure that Kubernetes has access to the image.
_Otherwise, additional steps are typically required to ensure that a Kubernetes cluster has access to domain images._

For example, it is typical in production deployments
for the Kubernetes cluster to be remote and have multiple worker nodes,
and to store domain images in a central repository that requires authentication.

Here are two typical scenarios for supplying domain images to such deployments:

- [Option 1](#option-1-store-images-in-a-central-registry-and-set-up-image-pull-secrets-on-each-domain-resource): Store images in a central registry and set up image pull secrets on each domain resource

- [Option 2](#option-2-store-images-in-a-central-registry-and-set-up-a-kubernetes-service-account-with-image-pull-secrets-in-each-domain-namespace): Store images in a central registry and set up a Kubernetes service account with image pull secrets in each domain namespace

#### Option 1: Store images in a central registry and set up image pull secrets on each domain resource

The most commonly used option is to store the image in a central registry
and set up image pull secrets for a domain resource:

- A Kubernetes `docker-registry` secret containing the registry credentials must be created
  in the same namespace as domain resources with a `domain.spec.image` attribute that reference the image.
  For example, to create a secret with OCR credentials:

  ```shell
  $ kubectl create secret docker-registry SECRET_NAME \
    -n NAMESPACE_WHERE_YOU_DEPLOY_DOMAINS \
    --docker-server=container-registry.oracle.com \
    --docker-username=YOUR_USERNAME \
    --docker-password=YOUR_PASSWORD \
    --docker-email=YOUR_EMAIL
  ```

- The name of the secret must be added to these domain resources using
  the `domain.spec.imagePullSecrets` field. For example:

  ```text
  ...
  spec:
  ...
    imagePullSecrets:
    - name: SECRET_NAME
  ...
  ```

- If you are using the Oracle Container Registry, then
  you must use the web interface to accept the Oracle Standard Terms and Restrictions
  for the Oracle software images that you intend to deploy.
  You need to do this only once for a particular image.
  See [Obtain images from the Oracle Container Registry]({{< relref "/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}}).

For more information about creating Kubernetes Secrets for accessing
the registry, see the Kubernetes documentation,
[Pull an image from a private registry](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/).

#### Option 2: Store images in a central registry and set up a Kubernetes service account with image pull secrets in each domain namespace

An option for accessing an image that is stored in a private registry
is to set up the Kubernetes `ServiceAccount` in the namespace running the
WebLogic domain with a set of image pull secrets thus avoiding the need to
set `imagePullSecrets` for each `Domain` resource being created (because each resource
instance represents a WebLogic domain that the operator is managing):

- Create a Kubernetes `docker-registry` secret as shown
  in [Option 1](#option-1-store-images-in-a-central-registry-and-set-up-image-pull-secrets-on-each-domain-resource).

- Modify the `ServiceAccount` that is in the same namespace
  as your domain resources to include this image pull secret:

  ```shell
  $ kubectl patch serviceaccount default -n domain1-ns \
  -p '{"imagePullSecrets": [{"name": "my-registry-pull-secret"}]}'
  ```

  Note that this patch command entirely replaces the current list of
  image pull secrets (if any). To include multiple secrets, use
  the following format:
  `-p '{"imagePullSecrets": [{"name": "my-registry-pull-secret"}, {"name": "my-registry-pull-secret2"}]}'`.

For more information about updating a Kubernetes `ServiceAccount`
for accessing the registry, see the Kubernetes documentation,
[Configure service accounts for pods](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/#add-image-pull-secrets-to-a-service-account).
