---
title: "Installation and upgrade"
date: 2019-02-23T16:47:21-05:00
weight: 3
description: "How to install, upgrade, and uninstall the operator."
---

### Contents

- [Introduction](#introduction)
- [Install the operator](#install-the-operator)
- [Set up domain namespaces](#set-up-domain-namespaces)
- [Update a running operator](#update-a-running-operator)
- [Upgrade the operator](#upgrade-the-operator)
- [Uninstall the operator](#uninstall-the-operator)
- [Installation sample](#installation-sample)

### Introduction

This installation guide describes how to configure, install (deploy), update, upgrade,
and uninstall an instance of the WebLogic Kubernetes Operator.
A single instance is capable of managing multiple domains in multiple namespaces, depending on how it is configured.
A Kubernetes cluster can host multiple operators, but no more than one per namespace.

### Install the operator

{{% notice note %}}
Before installing the operator, ensure that each of its prerequisite requirements is met.
See [Prepare for installation]({{<relref "/userguide/managing-operators/preparation.md">}}).
{{% /notice %}}

After meeting the [prerequisite requirements]({{<relref "/userguide/managing-operators/preparation.md">}}),
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
(for instructions, see [Prepare for installation]({{<relref "/userguide/managing-operators/preparation#prepare-an-operator-namespace-and-service-account">}})).

For example, using Helm 3.x, with the following settings:

|Setting|Value and Notes|
|-|-|
|Helm release name|`sample-weblogic-operator` (you may choose any name)|
|Helm chart repo URL|`https://oracle.github.io/weblogic-kubernetes-operator/charts`|
|Helm chart repo name|`weblogic-operator`|
|`namespace`|`sample-weblogic-operator-ns`|
|`enableClusterRoleBinding`|`true` (gives operator permission to automatically install the Domain CRD and to manage domain resources in any namespace)|
|`domainNamespaceSelectionStrategy`|`LabelSelector` (limits operator to managing namespaces that match the specified label selector)|
|`domainNamespaceLabelSelector`|`weblogic-operator\=enabled` (the label and expected value for the label)|

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
  --set "enableClusterRoleBinding=true" \
  --set "domainNamespaceSelectionStrategy=LabelSelector" \
  --set "domainNamespaceLabelSelector=weblogic-operator\=enabled" \
  --wait
```

Or, instead of using the previous `helm install` command,
create a YAML file named `custom-values.yaml` with the following contents:

```
enableClusterRoleBinding: true
domainNamespaceSelectionStrategy: LabelSelector
domainNamespaceLabelSelector: "weblogic-operator=enabled"
```

And call:

```text
$ helm install sample-weblogic-operator \
  weblogic-operator/weblogic-operator \
  --namespace sample-weblogic-operator-ns \
  --values custom-values.yaml \
  --wait
```

This creates a Helm release named `sample-weblogic-operator`
in the `sample-weblogic-operator-ns` namespace,
configures a deployment and supporting resources for the operator,
and deploys the operator.

You can verify the operator installation by examining the output from the `helm install` command.

To check if the operator is deployed and running,
see [Troubleshooting]({{<relref "/userguide/managing-operators/troubleshooting#check-the-operator-deployment">}}).

**Notes**:
- In this example, you have not set the `kubernetesPlatform`, but this may be required
  for your environment.
  See [Determine the platform setting]({{<relref "/userguide/managing-operators/preparation#determine-the-platform-setting">}}).
- For more information on specifying the registry credentials when the operator image is stored in a private registry, see
  [Customizing operator image name, pull secret, and private registry]({{<relref "/userguide/managing-operators/preparation#customizing-operator-image-name-pull-secret-and-private-registry">}}).
- Do not include a backslash (`\`) before the equals sign (`=`) in a domain namespace label selector
  when specifying the selector in a YAML file.
  A backslash (`\`) is only required when specifying the selector on the command line using `--set`,
  as shown in the previous example.

### Set up domain namespaces

To configure or alter the namespaces that an operator will check for domain resources,
see [Namespace management]({{<relref "/userguide/managing-operators/namespace-management.md">}}).

### Update a running operator

You can update the settings on a running operator by using the `helm upgrade` command.

In most use cases, you should specify `--reuse-values` on the `helm upgrade` command line
to ensure that the operator continues to use the values that you
have already specified (otherwise the operator
will revert to using the default for all values).

Example updates:
- Change the image of a running operator; see [Upgrade the operator](#upgrade-the-operator).
- Change the logging level; see [Operator logging level]({{< relref "/userguide/managing-operators/troubleshooting#operator-logging-level" >}}).
- Change the managed namespaces; see
  [Namespace management]({{<relref "/userguide/managing-operators/namespace-management.md">}}).

### Upgrade the operator

You can upgrade an earlier 3.x operator while the operator's domain resources are deployed and running.
The following instructions will be applicable to upgrade operators
as additional versions are released.

When upgrading the operator:

- Use `helm repo add` to supply a new version of the Helm chart.
- Use the `helm upgrade` command with the `--reuse-values` parameter.
- Supply a new `image` value.

The rationale for supplying a new `image` value is because, even with a new version of the Helm chart,
`--reuse-values` will retain the previous `image` value from when it was installed.  To upgrade,
you must override the `image` value to use the new operator image version.

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

Upgrading an earlier 3.x operator will _not_ automatically roll
any running WebLogic Server instances created by the original operator. It
is not necessary and such instances will continue to run without interruption
during the upgrade.

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
you should also remove the Domain custom resource definition (CRD) if it is no longer needed:
```text
$ kubectl delete customresourcedefinition domains.weblogic.oracle
```
Note that the Domain custom resource definition is shared.
Do not delete the CRD if there are other operators in the same cluster
or you have running domain resources.

### Installation sample

For an example of installing the operator,
setting the namespace that it monitors,
deploying a domain resource to its monitored namespace,
and uninstalling the operator,
see the [Quick Start]({{< relref "/quickstart/_index.md" >}}).
