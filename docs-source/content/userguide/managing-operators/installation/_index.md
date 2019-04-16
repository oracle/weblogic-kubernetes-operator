---
title: "Install the operator"
date: 2019-02-23T16:47:21-05:00
weight: 1

---

The operator uses Helm to create and deploy the necessary resources and
then run the operator in a Kubernetes cluster.

#### Install the operator Helm chart

Use the `helm install` command to install the operator Helm chart. As part of this, you must specify a "release" name for the operator.

You can override default configuration values in the operator Helm chart by doing one of the following:

- Creating a custom YAML file containing the values to be overridden, and specifying the `--value` option on the Helm command line.
- Overriding individual values directly on the Helm command line, using the `--set` option.

You supply the `–namespace` argument from the `helm install` command line to specify the namespace in which the operator should be installed.  If not specified, then it defaults to `default`.  If the namespace does not already exist, then Helm will automatically create it (and create a default service account in the new namespace), but will not remove it when the release is deleted.  If the namespace already exists, then Helm will re-use it.  These are standard Helm behaviors.

Similarly, you may override the default `serviceAccount` configuration value to specify which service account in the operator's namespace, the operator should use.  If not specified, then it defaults to `default` (for example, the namespace's default service account).  If you want to use a different service account, then you must create the operator's namespace and the service account before installing the operator Helm chart.

For example:
```
$ helm install kubernetes/charts/weblogic-operator \
  --name weblogic-operator --namespace weblogic-operator-namespace \
  --values custom-values.yaml --wait
```
or:
```
$ helm install kubernetes/charts/weblogic-operator \
  --name weblogic-operator --namespace weblogic-operator-namespace \
  --set "javaLoggingLevel=FINE" --wait
```

This creates a Helm release, named `weblogic-operator` in the `weblogic-operator-namespace` namespace, and configures a deployment and supporting resources for the operator.

If `weblogic-operator-namespace` exists, then it will be used.  If it does not exist, then Helm will create it.

You can verify the operator installation by examining the output from the `helm install` command.

{{% notice note %}}
When the operator image is stored in a private registry, see
[WebLogic operator image pull secret]({{<relref "/security/secrets.md#weblogic-operator-image-pull-secret">}})
for more information on specifying the registry credentials.
{{% /notice %}}

#### Alternatively, install the operator Helm chart from GitHub chart repository

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
$ helm install weblogic-operator/weblogic-operator --name weblogic-operator
```

#### Removing the operator

The `helm delete` command is used to remove an operator release and its associated resources from the Kubernetes cluster.  The release name used with the `helm delete` command is the same release name used with the `helm install` command (see [Install the Helm chart](#install-the-operator-helm-chart)).  For example:
```
$ helm delete --purge weblogic-operator
```
{{% notice note %}}
If the operator's namespace did not exist before the Helm chart was installed, then Helm will create it, however, `helm delete` will not remove it.
{{% /notice %}}

After removing the operator deployment, you should also remove the domain custom resource definition:
```
$ kubectl delete customresourcedefinition domains.weblogic.oracle
```
Note that the domain custom resource definition is shared if there are multiple operators in the same cluster.
