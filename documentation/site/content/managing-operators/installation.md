---
title: "Installation and upgrade"
date: 2019-02-23T16:47:21-05:00
weight: 3
description: "How to install, upgrade, and uninstall the operator."
---

{{< table_of_contents >}}

### Introduction

This installation guide describes how to configure, install (deploy), update, upgrade,
and uninstall an instance of the WebLogic Kubernetes Operator.
A single instance is capable of managing multiple domains in multiple namespaces, depending on how it is configured.
A Kubernetes cluster can host multiple operators, but no more than one per namespace.

### Install the operator

{{% notice note %}}
Before installing the operator, ensure that each of its prerequisite requirements is met.
See [Prepare for installation]({{<relref "/managing-operators/preparation.md">}}).
{{% /notice %}}

{{% notice note %}}
By default, installing the operator also configures a deployment and supporting resources for the
[conversion webhook]({{<relref "/managing-operators/conversion-webhook">}})
and deploys the conversion webhook. The conversion webhook deployment is required for operator version 4.x.
When a conversion webhook is already installed, skip the conversion webhook installation by setting
the Helm configuration value `operatorOnly` to `true` in the `helm install` command.
For more details, see [install the conversion webhook]({{<relref "/managing-operators/conversion-webhook#install-the-conversion-webhook">}}).
{{% /notice %}}

After meeting the [prerequisite requirements]({{<relref "/managing-operators/preparation.md">}}),
install the operator using the `helm install` command with the operator Helm chart according
to the following instructions.

Minimally you should specify:
- A Helm "release" name for the operator
- The Helm chart location
- The operator namespace
- The platform (if required)
- The namespace selection settings

A typical Helm release name is `weblogic-operator`.
The operator samples and documentation
often use `sample-weblogic-operator`.

You can override default configuration values in the Helm chart by doing one of the following:

- Creating a custom YAML file containing the values to be overridden,
  and specifying the `--value` option on the Helm command line.
- Overriding individual values directly on the Helm command line,
  using the `--set` option.

You supply the `--namespace` argument from the `helm install` command line
to specify the namespace in which the operator will be installed.
If not specified, then it defaults to `default`.
If the namespace does not already exist, then Helm will automatically create it
(and Kubernetes will create a `default` service account in the new namespace),
but note that Helm will not remove the namespace or service account when the release is uninstalled.
If the namespace already exists, then Helm will use it. These are standard Helm behaviors.

Similarly, you may override the default `serviceAccount` configuration value
to specify a service account in the operator's namespace that the operator will use.
For common use cases, the namespace `default` service account is sufficient.
If you want to use a different service account (recommended),
then you must create the operator's namespace
and the service account before installing the operator Helm chart
(for instructions, see [Prepare for installation]({{<relref "/managing-operators/preparation#prepare-an-operator-namespace-and-service-account">}})).

For example, using Helm 3.x, with the following settings:

|Setting|Value and Notes|
|-|-|
|Helm release name|`sample-weblogic-operator` (you may choose any name)|
|Helm chart repo URL|`https://oracle.github.io/weblogic-kubernetes-operator/charts`|
|Helm chart repo name|`weblogic-operator`|
|`namespace`|`sample-weblogic-operator-ns`|

```text
$ kubectl create namespace sample-weblogic-operator-ns
```
Access the operator Helm chart using this format: `helm repo add <helm-chart-repo-name> <helm-chart-repo-url>`.
Each version of the Helm chart defaults to using an operator image from the matching version.
```text
$ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update  
```
- To get information about the operator Helm chart, use the `helm show` command with this format: `helm show <helm-chart-repo-name>/weblogic-operator`.
  For example, with an operator Helm chart where the repository is named `weblogic-operator`:

  ```text
  $ helm show chart weblogic-operator/weblogic-operator
  $ helm show values weblogic-operator/weblogic-operator
  ```

- To list the versions of the operator that you can install from the Helm chart repository:

  ```text
  $ helm search repo weblogic-operator/weblogic-operator --versions
  ```

- For a specified version of the Helm chart and operator, use the `--version <value>` option with `helm install`
  to choose the version that you want, with the `latest` value being the default.

Install the operator using this format: `helm install <helm-release-name> <helm-chart-repo-name>/weblogic-operator ...`
```text
$ helm install sample-weblogic-operator \
  weblogic-operator/weblogic-operator \
  --namespace sample-weblogic-operator-ns \
  --wait
```

This creates a Helm release named `sample-weblogic-operator`
in the `sample-weblogic-operator-ns` namespace,
configures a deployment and supporting resources for the operator,
and deploys the operator.

You can verify the operator installation by examining the output from the `helm install` command.

To check if the operator is deployed and running,
see [Troubleshooting]({{<relref "/managing-operators/troubleshooting#check-the-operator-deployment">}}).

**NOTES**:
- In this example, you have not set the `kubernetesPlatform`, but this may be required
  for your environment.
  See [Determine the platform setting]({{<relref "/managing-operators/preparation#determine-the-platform-setting">}}).
- For more information on specifying the registry credentials when the operator image is stored in a private registry, see
  [Customizing operator image name, pull secret, and private registry]({{<relref "/managing-operators/preparation#customizing-operator-image-name-pull-secret-and-private-registry">}}).
- Do not include a backslash (`\`) before the equals sign (`=`) in a domain namespace label selector
  when specifying the selector in a YAML file.
  A backslash (`\`) is only required when specifying the selector on the command line using `--set`,
  as shown in the previous example.

### Install the WebLogic domain resource conversion webhook

By default, the WebLogic domain resource conversion webhook is automatically installed the first time an operator is installed in a cluster and removed the first time an operator is uninstalled.

**NOTE**: If you are using multiple operators, or want to be able to create or alter domains even when no operators are running, then you will need to fine tune this life cycle.
For conversion webhook installation details, see [Install the conversion webhook]({{<relref "/managing-operators/conversion-webhook#install-the-conversion-webhook" >}}).

### Set up domain namespaces

To configure or alter the namespaces that an operator will check for domain resources,
see [Namespace management]({{<relref "/managing-operators/namespace-management.md">}}).

### Update a running operator

You can update the settings on a running operator by using the `helm upgrade` command.

In most use cases, you should specify `--reuse-values` on the `helm upgrade` command line
to ensure that the operator continues to use the values that you
have already specified (otherwise the operator
will revert to using the default for all values).

Example updates:
- Change the image of a running operator; see [Upgrade the operator](#upgrade-the-operator).
- Change the logging level; see [Operator logging level]({{< relref "/managing-operators/troubleshooting#operator-and-conversion-webhook-logging-level" >}}).
- Change the managed namespaces; see
  [Namespace management]({{<relref "/managing-operators/namespace-management.md">}}).

### Upgrade the operator

You can upgrade a 3.x operator while the operator's domain resources are deployed and running.
The following instructions will be applicable to upgrade operators
as additional versions are released.

When upgrading the operator:

- Use `helm repo add` to supply a new version of the Helm chart.
- Use the `helm upgrade` command with the `--reuse-values` parameter.
- Supply a new `image` value.

The rationale for supplying a new `image` value is because, even with a new version of the Helm chart,
`--reuse-values` will retain the previous `image` value from when it was installed.  To upgrade,
you must override the `image` value to use the new operator image version.

**NOTE**: When upgrading a 3.x operator to 4.x, note that the default value of `domainNamespaceSelectionStrategy`
changed from `List` to `LabelSelector`, so you need to label the namespaces that the operator is supposed
to watch, rather than just providing the list of namespaces. For detailed information,
see [Namespace management]({{<relref "/managing-operators/namespace-management#check-the-namespaces-that-a-running-operator-manages">}}).

For example:

```text
$ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update
$ helm upgrade sample-weblogic-operator \
  weblogic-operator/weblogic-operator \
  --reuse-values \
  --set image=ghcr.io/oracle/weblogic-kubernetes-operator:{{< latestVersion >}} \
  --namespace sample-weblogic-operator-ns \
  --wait
```

Upgrading a 3.x operator will _not_ automatically roll
any running WebLogic Server instances created by the original operator. It
is not necessary and such instances will continue to run without interruption
during the upgrade.

When you upgrade a 3.x operator to 4.0, it will also create a
WebLogic Domain resource conversion webhook deployment and its associated resources in the same namespace. If the conversion
webhook deployment already exists in some other namespace, then a new conversion webhook deployment is not created.
The webhook automatically and transparently upgrades the existing Domains from the 3.x schema to the 4.0 schema.
For more information, see
[WebLogic Domain resource conversion webhook]({{< relref "/managing-operators/conversion-webhook.md" >}}).

### Uninstall the operator

{{% notice note %}}
If you uninstall an operator, then any domains that it is managing will continue running;
however, any changes to a domain resource that was managed by the operator
will not be detected or automatically handled, and, if you
want to clean up such a domain, then you will need to manually delete
all of the domain's resources (domain, pods, services, and such).
{{% /notice %}}

The `helm uninstall` command is used to remove an operator release
and its associated resources from the Kubernetes cluster.
The Helm release name and namespace used with the `helm uninstall` command
must be the same release name used with the `helm install`
command (see [Install the operator](#install-the-operator)).

For example, assuming the Helm release name is `sample-weblogic-operator`
and the operator namespace is `sample-weblogic-operator-ns`:

```text
$ helm uninstall sample-weblogic-operator -n sample-weblogic-operator-ns
```

{{% notice note %}}
If the operator's namespace or service account did not exist before the Helm chart was installed,
then Helm will create them during `helm install`; however, `helm uninstall` will not remove them.
{{% /notice %}}

After removing the operator deployment,
you should also remove the Domain and Cluster custom resource definitions (CRD) if they are no longer needed:
```text
$ kubectl delete customresourcedefinition domains.weblogic.oracle
$ kubectl delete customresourcedefinition clusters.weblogic.oracle
```
Note that the custom resource definitions are shared.
Do not delete them if there are other operators in the same cluster
or you have running domain resources.

Beginning with operator version 4.0, uninstalling an operator also removes the conversion webhook
 deployment and its associated resources by default.
Therefore, if you have multiple operators running, then, by default, an uninstall
of one operator will affect the other operators. The uninstall will not delete the conversion definition
in the domain CRD so you will be unable to create domains using `weblogic.oracle/v8` schema.
If you want to prevent the uninstall of an operator  
from having these side effects, then use one of the following two options:
- [Install the conversion webhook]({{< relref "/managing-operators/conversion-webhook#install-the-conversion-webhook" >}})
 in a separate namespace using `webhookOnly=true` Helm configuration value.
- Use the `preserveWebhook=true` Helm configuration value during operator installation with the `helm install` command.

For more information, see
[uninstall the conversion webhook]({{<relref "/managing-operators/conversion-webhook#uninstall-the-conversion-webhook" >}})
for the conversion webhook uninstallation details.

### Installation sample

For an example of installing the operator,
setting the namespace that it monitors,
deploying a domain resource to its monitored namespace,
and uninstalling the operator,
see the [Quick Start]({{< relref "/quickstart/_index.md" >}}).
