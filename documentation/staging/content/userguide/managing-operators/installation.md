---
title: "Installation and upgrade"
date: 2019-02-23T16:47:21-05:00
weight: 2
description: "How to install, upgrade, and uninstall the operator."
---

### TBD This document is a work in progress

### Contents

- [Prerequisites](#prerequisites)
- [Install the operator](#install-the-operator)
- [Upgrade the operator](#upgrade-the-operator)
- [Uninstall the operator](#uninstall-the-operator)
- [Installation sample](#installation-sample)

**TBD NOTE! This chapter appears to be missing critical necessary details, such as:**
- setting up a service account (probably belongs in prerequisites, see quick start. Also see the security chapter.)
- creating a ns for the operator (mentioned in 'from operator source' but not 'from repo' - probably belongs in prerequisites)
- the full typical command line for helm installing the operator, including referencing its service account (see quick start)
- the options/steps for setting up which namespaces the operator should manage (see helm usage, namespace mgt)
- references to operator specific security issues in the security chapter (if any) (see security chapter)

### Introduction

This installation guide describes how to configure, install (deploy), upgrade,
and uninstall an instance of the WebLogic Kubernetes operator. 
A single instance is capable of managing multiple domains in multiple namespaces depending on how it is configured. 
A Kubernetes cluster can host multiple operators, but no more than one per namespace.

### Prerequisites

Ensure each of these prerequisites are in place before installing an operator:

1. [Check environment](#check-environment)
1. [Install Helm](#install-helm)
1. [Download operator source](#download-operator-source)
1. [Set up operator Helm chart access](#set-up-operator-helm-chart-access)
1. [Inspect the operator Helm chart](#inspect-the-operator-helm-chart)
1. [Manually install the Domain resource custom resource definition (CRD), if need be](#manually-install-the-domain-resource-custom-resource-definition-crd-if-need-be)
1. [Prepare a namespace and service account for the operator, if need be](#prepare-a-namespace-and-service-account-for-the-operator-if-need-be)
1. [Operator image](#operator-image)
1. [Choose a domain namespace selection strategy](#choose-a-domain-namespace-selection-strategy)
1. [Choose a security strategy](#choose-a-security-strategy)

#### Check environment

1. Review the [Operator prerequisites]({{<relref "/userguide/prerequisites/introduction.md">}}) to ensure that your Kubernetes cluster supports the operator.

1. Also review the [Supported platforms]({{< relref "userguide/platforms/environments.md" >}}) for environment and licensing requirements.

1. If your environment doesn't already have a Kubernetes setup, then see [set up Kubernetes]({{< relref "/userguide/kubernetes/k8s-setup.md" >}}).

1. Optionally enable [Istio]({{< relref "/userguide/istio/istio.md" >}}).

{{% notice note %}}
It is important to keep in mind that some supported environments
have additional help or samples that are specific to the operator,
or are subject to limitations, special tuning requirements, or restrictions.
See the [Supported platforms]({{< relref "userguide/platforms/environments.md" >}}) link.
{{% /notice %}}

#### Install Helm

It is required to install Helm in your Kubernetes cluster in order to install an operator.
The operator uses Helm to create necessary resources and then deploy the operator in a Kubernetes cluster.

To check if you already have Helm available, try the `helm version` command.

For detailed instructions on installing Helm, see the [GitHub Helm Repository](https://github.com/helm/helm).

#### Download operator source

Downloading the operator source is required if:

- You plan to provide local file based access to the operator Helm chart 
  (see [Set up operator Helm chart access](#set-up-operator-helm-chart-access)).
- You need to manually install the CRD
  (see [Manually install the Domain resource custom resource definition (CRD), if need be](#manually-install-the-domain-resource-custom-resource-definition-crd-if-need-be)).
- You plan to follow one of the operator [samples]({{<relref "/userguide/samples/_index.md">}}).

Otherwise, you can skip this step.

To download and unpack the operator source into `/tmp/weblogic-kubernetes-operator`:

1. ```text
   $ cd /tmp
   ```
2. ```text
   $ git clone --branch v{{< latestVersion >}} https://github.com/oracle/weblogic-kubernetes-operator.git
   ```

**Note:** We use `/tmp` as an example directory; you can use a different location.

#### Set up operator Helm chart access

Before installing an operator, the operator Helm chart must be made available.
The operator Helm chart includes:
- Pre-configured default values for the configuration of the operator.
- Helm configuration value settings for fine tuning operator behavior.
- Commands for deploying (installing) or undeploying the operator.

You can set up access to the operator Helm chart using either a _file based approach_
or a _GitHub chart repository approach_:

##### _File based approach_

- This approach uses the local file based version of the operator Helm chart
  that is located in the operator
  source `kubernetes/charts/weblogic-operator` directory.
- To set up this option, get the operator source.
  See [Download operator source](download-operator-source).
- To use this option:
  - `cd /tmp/weblogic-kubernetes-operator` 
    (or `cd` to wherever you have downloaded the operator source).
  - Use `kubernetes/charts/weblogic-operator` in your helm
    commands to specify the chart location.

##### _GitHub chart repository approach_

- This approach uses the GitHub chart repository version of the operator Helm chart
  that is located at `https://oracle.github.io/weblogic-kubernetes-operator/charts`
  or in a custom repository that you control.
- To set up your Helm installation so that it can access the
  `https://oracle.github.io/weblogic-kubernetes-operator/charts`
  repository and name the repository reference `weblogic-operator`, use
  the following `helm repo add` command:
  ```text
  $ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update
  ```
- To verify that a Helm chart repository was added correctly, or to list existing repositories:
  ```text
  $ helm repo list
  ```
  ```text
  NAME                 URL
  weblogic-operator    https://oracle.github.io/weblogic-kubernetes-operator/charts
  ```
- To use this option assuming you have named 
  your repository `weblogic-operator`, simply
  use `weblogic-operator/weblogic-operator` in your helm
  commands when specifying the chart location.

#### Inspect the operator Helm chart

You can find out the configuration values that the operator Helm chart supports,
as well as the default values, using the `helm inspect` command.
- Here's an example of using `helm inspect` with the local file based operator Helm chart:
  ```text
  $ cd /tmp/weblogic-kubernetes-operator
  ```
  ```text
  $ helm inspect values kubernetes/charts/weblogic-operator
  ```
- Here's an example of using `helm inspect` with a GitHub chart repository based operator Helm chart:
  ```text
  $ helm inspect values weblogic-operator/weblogic-operator
  ```

The available configuration values are explained by category in
[Operator Helm configuration values]({{<relref "/userguide/managing-operators/using-helm#operator-helm-configuration-values">}}).

Helm commands are explained in more detail in
[Useful Helm operations]({{<relref "/userguide/managing-operators/using-helm#useful-helm-operations">}}).

#### Manually install the Domain resource custom resource definition (CRD), if need be

**What is a Domain CRD?**

The Domain resource type is defined by a Kubernetes CustomResourceDefinition (CRD) resource.
The domain CRD provides Kubernetes with the schema for operator domain resources
and there must be one domain CRD installed in each Kubernetes cluster that hosts the operator.
If you install multiple operators in the same Kubernetes cluster, then they all 
share the same domain CRD.

**When does a Domain CRD need to be manually installed?**

Typically, the operator automatically installs the CRD for the Domain type when the operator first starts._
However, if the operator lacks sufficient permission to install it,
then you may choose to manually install the CRD in advance by using one of the provided YAML files.
Manually installing the CRD in advance allows you to run the operator without giving it privilege
(through Kubernetes roles and bindings) to access or update the CRD or other cluster-scoped resources.
This may be necessary in environments where the operator cannot have cluster-scoped privileges,
such as on OpenShift when running the operator 
with a `dedicated` namespace strategy. The operator's role based access control (RBAC) requirements
are documented [here]({{< relref "/userguide/managing-operators/security/rbac.md" >}}).

**How to manually install a Domain CRD.**

To manually install the CRD, first [download operator source](#download-operator-source),
and, assuming you have installed the operator source into the `/tmp` directory:

```text
$ cd /tmp/weblogic-kubernetes-operator
```

```text
$ kubectl create -f kubernetes/crd/domain-crd.yaml
```

**How to check if a Domain CRD has been installed.**

After the CustomResourceDefinition is installed,
either by the operator or using the `create` command above,
you can verify that the CRD is installed correctly using:

```text
$ kubectl get crd domains.weblogic.oracle
```

#### Prepare a namespace and service account for the operator, if need be

Each operator requires a namespace to run in and a kubernetes service account
within the namespace. Only one operator can run in a given namespace.

When possible, Oracle recommends creating an isolated namespace for
each operator which only hosts the operator, does not host
domains, and does not host Kubernetes resources that are
unrelated to the operator. This simplifies management
and monitoring of an operator.

It is a best practice to directly create a namespace
and service account for the operator instead of relying
on the operator helm chart installation to do this for
you. Here's an example of each:

```text
$ kubectl create namespace sample-weblogic-operator-ns
```

```text
$ kubectl create serviceaccount -n sample-weblogic-operator-ns sample-weblogic-operator-sa
```

In future helm install steps,
you will supply the `--namespace sample-weblogic-operator-ns` 
argument on the command line.
If not specified, then it defaults to `default`.
If the namespace does not already exist, then Helm will automatically create it
(and create a default service account in the new namespace), but will not remove
it when the release is uninstalled. If the namespace already exists,
then Helm will use it. These are standard Helm behaviors.

Similarly, you will specify the `serviceAccount=sample-weblogic-operator-sa`
Helm configuration value to specify the service account in the operator's namespace
that the operator will use. If not specified, then it defaults to `default`
(for example, the namespace's default service account).

#### Operator image

The operator image must be available on all nodes of your Kubernetes cluster.
Production ready operator images for various supported versions of the operator are located in the
[GitHub Container Registry](https://github.com/orgs/oracle/packages/container/package/weblogic-kubernetes-operator).
You can also optionally build your own operator image,
see the [Developer Guide]({{<relref "/developerguide/_index.md">}}).

##### Default operator image

To find the default image that will be used when installing the operator,
see [Inspect the operator Helm chart](#inspect-the-operator-helm-chart) and look for the `image` value.

##### Pulling operator image

In most use cases, Kubernetes will automatically download the image as needed.
If you want to manually place an operator image in a machine's docker image pool,
or test your access to an image, then call `docker pull`. For example:

```text
$ docker pull ghcr.io/oracle/weblogic-kubernetes-operator:{{< latestVersion >}}
```

##### Operator image name, pull secret, and private registry

Sometimes you may want to specify a custom operator image name
or image pull secret. For example, you may want to deploy a different
version than the [default operator image name](#default-operator-image)
or you may want to place an operator image in your own private image registry.
A private image registry usually requires a custom image name for the operator,
and may also require an image pull registry secret in order to allow access.

- To reference a custom image name, use the `image` Helm chart configuration setting
  when installing the operator.
- To create a registry secret, create a Kubernetes Secret of type `docker-registry`
  in the namespace where the operator is to be deployed.
- To reference a registry secret, use the `imagePullSecrets[0].name` Helm chart
  configuration setting when installing the operator.

Here is a truncated example of using the helm install command
to set a custom image name and to reference an image pull secret:
```text
$ cd /tmp/weblogic-kubernetes-operator
```
```text
$ helm install sample-weblogic-operator kubernetes/charts/weblogic-operator \
  --namespace weblogic-operator-ns \
  ... truncated ...
  --set "image=my.io/my-operator-image:1.0" \
  --set "imagePullSecrets[0].name=my-operator-image-pull-secret" \
  ... truncated ...
```

#### Choose a domain namespace selection strategy

TBD add a description of namespace strategies to 'namespace-management.md' (including default behavior of each, if any), and then refer to it here.
note that this is related to security strategy

#### Choose a security strategy

TBD discuss security considerations, and customizing of same - point to 'rbac.md', etc, as needed.
note that this is related to domain namespace selection strategy

### Install the operator

TBD this section needs a rewrite

Use the `helm install` command to install the operator Helm chart that is located in the operator source.
As part of this, you must specify a "release" name for the operator.

You can override default configuration values in the chart by doing one of the following:

- Creating a custom YAML file containing the values to be overridden, and specifying the `--value` option on the Helm command line.
- Overriding individual values directly on the Helm command line, using the `--set` option.

You supply the `–-namespace` argument from the `helm install` command line to specify the namespace in which the operator will be installed. If not specified, then it defaults to `default`.  If the namespace does not already exist, then Helm will automatically create it (and create a default service account in the new namespace), but will not remove it when the release is uninstalled. If the namespace already exists, then Helm will use it. These are standard Helm behaviors.

Similarly, you may override the default `serviceAccount` configuration value to specify a service account in the operator's namespace, the operator will use. If not specified, then it defaults to `default` (for example, the namespace's default service account). If you want to use a different service account, then you must create the operator's namespace and the service account before installing the operator Helm chart.

For example, using Helm 3.x:

```text
$ kubectl create namespace weblogic-operator-namespace
```

```text
$ helm install weblogic-operator kubernetes/charts/weblogic-operator \
  --namespace weblogic-operator-namespace \
  --values custom-values.yaml --wait
```
Or:
```text
$ helm install weblogic-operator kubernetes/charts/weblogic-operator \
  --namespace weblogic-operator-namespace \
  --set "javaLoggingLevel=FINE" --wait
```

**Note**: Before changing the `javaLoggingLevel` setting, consult the [Operator logging level]({{< relref "/userguide/managing-operators/debugging#operator-logging-level" >}}) advice.

This creates a Helm release, named `weblogic-operator` in the `weblogic-operator-namespace` namespace, and configures a deployment and supporting resources for the operator.

You can verify the operator installation by examining the output from the `helm install` command.

{{% notice note %}}
For more information on specifying the registry credentials when the operator image is stored in a private registry, see
[Operator image pull secret]({{<relref "/security/secrets#operator-image-pull-secret">}}).
{{% /notice %}}

### Alternatively, install the operator using a GitHub chart repository Helm chart

TBD combine this section with the previous section.

Add this repository to the Helm installation:

```text
$ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update
```

Verify that the repository was added correctly:

```text
$ helm repo list
```
```
NAME           URL
weblogic-operator    https://oracle.github.io/weblogic-kubernetes-operator/charts
```

Install the operator from the repository:

```text
$ helm install weblogic-operator weblogic-operator/weblogic-operator
```

TBD To check if the operator is deployed and running, see 'debugging.md'.

### Upgrade the operator

{{% notice note %}}
Because operator 3.0.0 introduces _non-backward compatible_ changes, you cannot use `helm upgrade` to upgrade
a 2.6.0 operator to a 3.x operator. Instead, you must delete the 2.6.0 operator and then install the
3.x operator.
{{% /notice %}}

The deletion of the 2.6.0 operator will _not affect_ the Domain CustomResourceDefinition (CRD) and will _not stop_ any
WebLogic Server instances already running.

When the 3.x operator is installed, it will automatically roll any running WebLogic Server instances created by the 2.6.0 operator.
This rolling restart will preserve WebLogic cluster availability guarantees (for clustered members only) similarly to any other rolling restart.

To delete the 2.6.0 operator:

```text
$ helm delete weblogic-operator -n weblogic-operator-namespace
```

Then install the 3.x operator using the [install the operator](#install-the-operator) instructions.

The following instructions will be applicable to upgrade operators within the 3.x release family
as additional versions are released.

To upgrade the operator, use the `helm upgrade` command. Make sure that the
`weblogic-kubernetes-operator` repository on your local machine is at the
operator release to which you are upgrading. When upgrading the operator,
the `helm upgrade` command requires that you supply a new Helm chart and image. For example:

```text
$ helm upgrade \
  --reuse-values \
  --set image=ghcr.io/oracle/weblogic-kubernetes-operator:{{< latestVersion >}} \
  --namespace weblogic-operator-namespace \
  --wait \
  weblogic-operator \
  kubernetes/charts/weblogic-operator
```

### Uninstall the operator

The `helm uninstall` command is used to remove an operator release and its associated resources from the Kubernetes cluster. The release name used with the `helm uninstall` command is the same release name used with the `helm install` command (see [Install the operator](#install-the-operator)). For example:

```text
$ helm uninstall weblogic-operator -n weblogic-operator-namespace
```

{{% notice note %}}
If the operator's namespace did not exist before the Helm chart was installed, then Helm will create it, however, `helm uninstall` will not remove it.
{{% /notice %}}

After removing the operator deployment, you should also remove the Domain custom resource definition (CRD):
```text
$ kubectl delete customresourcedefinition domains.weblogic.oracle
```
Note that the Domain custom resource definition is shared. Do not delete the CRD if there are other operators in the same cluster.

### Installation sample

For an example of installing the operator, setting the namespace that it monitors, deploying a domain resource to its monitored namespace, and uninstalling the operator, see the [Quick Start]({{< relref "/quickstart/_index.md" >}}).
