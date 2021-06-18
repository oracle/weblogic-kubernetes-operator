---
title: "Install the operator"
date: 2019-02-23T16:47:21-05:00
weight: 1

---

The operator uses Helm to create the necessary resources and
then deploy the operator in a Kubernetes cluster. This document describes how to install, upgrade,
and uninstall the operator.

#### Contents

 - [Install the operator Helm chart](#install-the-operator-helm-chart)
 - [Alternatively, install the operator Helm chart from the GitHub chart repository](#alternatively-install-the-operator-helm-chart-from-the-github-chart-repository)
 - [Upgrade the operator](#upgrade-the-operator)
 - [Uninstall the operator](#uninstall-the-operator)

#### Install the operator Helm chart

Use the `helm install` command to install the operator Helm chart. As part of this, you must specify a "release" name for the operator.

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

Then install the 3.x operator using the [installation](#install-the-operator-helm-chart) instructions above.

The following instructions will be applicable to upgrade operators within the 3.x release family
as additional versions are released.

To upgrade the operator, use the `helm upgrade` command. Make sure that the
`weblogic-kubernetes-operator` repository on your local machine is at the
operator release to which you are upgrading. When upgrading the operator,
the `helm upgrade` command requires that you supply a new Helm chart and image. For example:

```shell
$ helm upgrade \
  --reuse-values \
  --set image=ghcr.io/oracle/weblogic-kubernetes-operator:3.2.4 \
  --namespace weblogic-operator-namespace \
  --wait \
  weblogic-operator \
  kubernetes/charts/weblogic-operator
```

#### Uninstall the operator

The `helm uninstall` command is used to remove an operator release and its associated resources from the Kubernetes cluster. The release name used with the `helm uninstall` command is the same release name used with the `helm install` command (see [Install the Helm chart](#install-the-operator-helm-chart)). For example:

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
