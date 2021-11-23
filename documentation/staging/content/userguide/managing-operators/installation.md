---
title: "Installation and upgrade"
date: 2019-02-23T16:47:21-05:00
weight: 2
description: "How to install, upgrade, and uninstall the operator."
---


#### Contents

- [Prerequisites](#prerequisites)
   - [Check environment](#check-environment)
   - [Install Helm](#install-helm)
   - [Download operator source](#download-operator-source)
   - [Operator's Helm chart configuration](#operators-helm-chart-configuration)
   - [Operator image](#operator-image)
- [Manually install the Domain resource custom resource definition (CRD), if need be](#manually-install-the-domain-resource-custom-resource-definition-crd-if-need-be)
- [Install the operator Helm chart from operator source](#install-the-operator-helm-chart-from-operator-source)
- [Alternatively, install the operator Helm chart from the GitHub chart repository](#alternatively-install-the-operator-helm-chart-from-the-github-chart-repository)
- [Upgrade the operator](#upgrade-the-operator)
- [Uninstall the operator](#uninstall-the-operator)
- [Installation sample](#installation-sample)

#### TBD NOTE! This chapter appears to be missing critical necessary details, such as:

- setting up a service account (probably belongs in prerequisites, see quick start. Also see the security chapter.)
- creating a ns for the operator (mentioned in 'from operator source' but not 'from repo' - probably belongs in prerequisites)
- the full typical command line for helm installing the operator, including referencing its service account (see quick start)
- the options/steps for setting up which namespaces the operator should manage (see helm usage, namespace mgt)
- references to operator specific security issues in the security chapter (if any) (see security chapter)

#### Prerequisites

The operator uses Helm to create the necessary resources and then deploy the operator in a Kubernetes cluster.

##### Check environment

Review the [Operator prerequisites]({{<relref "/userguide/prerequisites/introduction.md">}}) to ensure that your Kubernetes cluster supports the operator.

##### Install Helm

Helm manages releases (installations) of your charts. For detailed instructions on installing Helm, see https://github.com/helm/helm.

##### Download operator source

For example:
```
$ cd /tmp
```
```
$ git clone --branch v3.3.2 https://github.com/oracle/weblogic-kubernetes-operator.git
```

This will download and unpack the operator source into `/tmp/weblogic-kubernetes-operator`.

We use `/tmp` as an example directory; you can use a different location.

##### Operator's Helm chart configuration

The operator Helm chart is pre-configured with default values for the configuration of the operator.
The operator’s Helm chart is located in the operator source `kubernetes/charts/weblogic-operator` directory.

You can find out the configuration values that the Helm chart supports, as well as the default values, using this command at the root of your operator source directory:
```shell
$ helm inspect values kubernetes/charts/weblogic-operator
```

The available configuration values are explained by category in
[Operator Helm configuration values]({{<relref "/userguide/managing-operators/using-helm#operator-helm-configuration-values">}}).

Helm commands are explained in more detail in
[Useful Helm operations]({{<relref "/userguide/managing-operators/using-helm#useful-helm-operations">}}).

##### Operator image

Get the operator image from the [GitHub Container Registry](https://github.com/orgs/oracle/packages/container/package/weblogic-kubernetes-operator).

#### Manually install the Domain resource custom resource definition (CRD), if need be

The Domain type is defined by a Kubernetes CustomResourceDefinition (CRD). Typically, the operator installs the CRD for the Domain type when the operator first starts. However, if the operator lacks sufficient permission to install it, you may choose to install the CRD in advance by using one of the provided YAML files. Installing the CRD in advance allows you to run the operator without giving it privilege (through Kubernetes roles and bindings) to access or update the CRD or other cluster-scoped resources. This may be necessary in environments where the operator cannot have cluster-scoped privileges, such as OpenShift Dedicated. The operator's role based access control (RBAC) requirements are documented [here]({{< relref "/userguide/managing-operators/security/rbac.md" >}}).

```shell
$ kubectl create -f kubernetes/crd/domain-crd.yaml
```

After the CustomResourceDefinition is installed, either by the operator or using one of the `create` commands above, you can verify that the CRD is installed correctly using:

```shell
$ kubectl get crd domains.weblogic.oracle
```

#### Install the operator Helm chart from operator source

Use the `helm install` command to install the operator Helm chart that is located in the operator source.
As part of this, you must specify a "release" name for the operator.

You can override default configuration values in the chart by doing one of the following:

- Creating a custom YAML file containing the values to be overridden, and specifying the `--value` option on the Helm command line.
- Overriding individual values directly on the Helm command line, using the `--set` option.

You supply the `–-namespace` argument from the `helm install` command line to specify the namespace in which the operator will be installed. If not specified, then it defaults to `default`.  If the namespace does not already exist, then Helm will automatically create it (and create a default service account in the new namespace), but will not remove it when the release is uninstalled. If the namespace already exists, then Helm will use it. These are standard Helm behaviors.

Similarly, you may override the default `serviceAccount` configuration value to specify a service account in the operator's namespace, the operator will use. If not specified, then it defaults to `default` (for example, the namespace's default service account). If you want to use a different service account, then you must create the operator's namespace and the service account before installing the operator Helm chart.

For example, using Helm 3.x:

```shell
$ kubectl create namespace weblogic-operator-namespace
```

```shell
$ helm install weblogic-operator kubernetes/charts/weblogic-operator \
  --namespace weblogic-operator-namespace \
  --values custom-values.yaml --wait
```
Or:
```shell
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

#### Alternatively, install the operator Helm chart from the GitHub chart repository

Add this repository to the Helm installation:

```shell
$ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update
```

Verify that the repository was added correctly:

```shell
$ helm repo list
```
```
NAME           URL
weblogic-operator    https://oracle.github.io/weblogic-kubernetes-operator/charts
```

Install the operator from the repository:

```shell
$ helm install weblogic-operator weblogic-operator/weblogic-operator
```

#### Upgrade the operator

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

```shell
$ helm delete weblogic-operator -n weblogic-operator-namespace
```

Then install the 3.x operator using the [installation](#install-the-operator-helm-chart-from-operator-source) instructions.

The following instructions will be applicable to upgrade operators within the 3.x release family
as additional versions are released.

To upgrade the operator, use the `helm upgrade` command. Make sure that the
`weblogic-kubernetes-operator` repository on your local machine is at the
operator release to which you are upgrading. When upgrading the operator,
the `helm upgrade` command requires that you supply a new Helm chart and image. For example:

```shell
$ helm upgrade \
  --reuse-values \
  --set image=ghcr.io/oracle/weblogic-kubernetes-operator:{{< latestVersion >}} \
  --namespace weblogic-operator-namespace \
  --wait \
  weblogic-operator \
  kubernetes/charts/weblogic-operator
```

#### Uninstall the operator

The `helm uninstall` command is used to remove an operator release and its associated resources from the Kubernetes cluster. The release name used with the `helm uninstall` command is the same release name used with the `helm install` command (see [Install the Helm chart](#install-the-operator-helm-chart-from-operator-source)). For example:

```shell
$ helm uninstall weblogic-operator -n weblogic-operator-namespace
```

{{% notice note %}}
If the operator's namespace did not exist before the Helm chart was installed, then Helm will create it, however, `helm uninstall` will not remove it.
{{% /notice %}}

After removing the operator deployment, you should also remove the Domain custom resource definition (CRD):
```shell
$ kubectl delete customresourcedefinition domains.weblogic.oracle
```
Note that the Domain custom resource definition is shared. Do not delete the CRD if there are other operators in the same cluster.

#### Installation sample

For an example of installing the operator, setting the namespace that it monitors, deploying a domain resource to its monitored namespace, and uninstalling the operator, see the [Quick Start]({{< relref "/quickstart/_index.md" >}}).
