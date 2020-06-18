---
title: "Install the operator"
date: 2019-02-23T16:47:21-05:00
weight: 1

---

The operator uses Helm to create and deploy the necessary resources and
then run the operator in a Kubernetes cluster. This document describes how to install, upgrade,
and remove the operator.

#### Content

 - [Install the operator Helm chart](#install-the-operator-helm-chart)
 - [Alternatively, install the operator Helm chart from the GitHub chart repository](#alternatively-install-the-operator-helm-chart-from-the-github-chart-repository)
 - [Upgrade the operator](#upgrade-the-operator)
 - [Remove the operator](#remove-the-operator)

#### Install the operator Helm chart

Use the `helm install` command to install the operator Helm chart. As part of this, you must specify a "release" name for the operator.

You can override default configuration values in the operator Helm chart by doing one of the following:

- Creating a custom YAML file containing the values to be overridden, and specifying the `--value` option on the Helm command line.
- Overriding individual values directly on the Helm command line, using the `--set` option.

You supply the `–namespace` argument from the `helm install` command line to specify the namespace in which the operator should be installed.  If not specified, then it defaults to `default`.  If the namespace does not already exist, then Helm will automatically create it (and create a default service account in the new namespace), but will not remove it when the release is deleted.  If the namespace already exists, then Helm will re-use it.  These are standard Helm behaviors.

Similarly, you may override the default `serviceAccount` configuration value to specify which service account in the operator's namespace, the operator should use.  If not specified, then it defaults to `default` (for example, the namespace's default service account).  If you want to use a different service account, then you must create the operator's namespace and the service account before installing the operator Helm chart.

For example, using Helm 3.x:

```
$ kubectl create namespace weblogic-operator-namespace
```

```
$ helm install weblogic-operator kubernetes/charts/weblogic-operator \
  --namespace weblogic-operator-namespace \
  --values custom-values.yaml --wait
```
Or:
```
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

```
$ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts
```

Verify that the repository was added correctly:

```
$ helm repo list
NAME           URL
weblogic-operator    https://oracle.github.io/weblogic-kubernetes-operator/charts
```

Update with the latest information about charts from the chart repositories:

```
$ helm repo update
```

Install the operator from the repository:

```
$ helm install weblogic-operator weblogic-operator/weblogic-operator
```

#### Upgrade the operator

To upgrade the operator, use the `helm upgrade` command. When upgrading the operator,
the `helm upgrade` command requires that you supply a new Helm chart and image. For example:

```
$ helm upgrade \
  --reuse-values \
  --set image=oracle/weblogic-kubernetes-operator:2.6.0 \
  --namespace weblogic-operator-namespace \
  --wait \
  weblogic-operator \
  kubernetes/charts/weblogic-operator
```

#### Remove the operator

The `helm delete` command is used to remove an operator release and its associated resources from the Kubernetes cluster.  The release name used with the `helm delete` command is the same release name used with the `helm install` command (see [Install the Helm chart](#install-the-operator-helm-chart)).  For example:

```
$ helm uninstall weblogic-operator
```

{{% notice note %}}
If the operator's namespace did not exist before the Helm chart was installed, then Helm will create it, however, `helm delete` will not remove it.
{{% /notice %}}

After removing the operator deployment, you should also remove the domain custom resource definition:
```
$ kubectl delete customresourcedefinition domains.weblogic.oracle
```
Note that the domain custom resource definition is shared if there are multiple operators in the same cluster.
