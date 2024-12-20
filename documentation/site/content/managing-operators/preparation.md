---
title: "Prepare for installation"
date: 2021-12-05T16:47:21-05:00
weight: 2
description: "Consult these preparation steps, strategy choices, and prequisites prior to installing an operator."
---

#### Introduction

A single operator instance is capable of managing multiple domains in multiple namespaces, depending on how it is configured.
A Kubernetes cluster can host multiple operators, but no more than one per namespace.

Before installing an operator, ensure that each of these prerequisite requirements is met:

1. [Check environment](#check-environment)
1. [Set up the operator Helm chart access](#set-up-the-operator-helm-chart-access)
1. [Inspect the operator Helm chart](#inspect-the-operator-helm-chart)
1. [Prepare an operator namespace and service account](#prepare-an-operator-namespace-and-service-account)
1. [Prepare operator image](#prepare-operator-image)
   - [Locating an operator image](#locating-an-operator-image)
   - [Default operator image](#default-operator-image)
   - [Pulling operator image](#pulling-operator-image)
   - [Customizing operator image name, pull secret, and private registry](#customizing-operator-image-name-pull-secret-and-private-registry)
1. [Determine the platform setting](#determine-the-platform-setting)
1. [Choose a security strategy](#choose-a-security-strategy)
   - [Any namespace with cluster role binding enabled](#any-namespace-with-cluster-role-binding-enabled)
   - [Any namespace with cluster role binding disabled](#any-namespace-with-cluster-role-binding-disabled)
   - [Local namespace only with cluster role binding disabled](#local-namespace-only-with-cluster-role-binding-disabled)
1. [Choose a domain namespace selection strategy](#choose-a-domain-namespace-selection-strategy)
1. [Choose a Helm release name](#choose-a-helm-release-name)
1. [Be aware of advanced operator configuration options](#be-aware-of-advanced-operator-configuration-options)
1. Special use cases:
   - [How to download the Helm chart if Internet access is not available](#how-to-download-the-helm-chart-if-internet-access-is-not-available)
   - [How to manually install the Domain and Cluster custom resource definitions (CRD)](#how-to-manually-install-the-domain-and-cluster-custom-resource-definitions-crd)

#### Check environment

1. Review the [Operator prerequisites]({{<relref "/introduction/prerequisites/introduction.md">}}) to ensure that your Kubernetes cluster supports the operator.

1. It is important to keep in mind that some supported environments
   have additional help or samples that are specific to the operator,
   or are subject to limitations, special tuning requirements,
   special licensing requirements, or restrictions.
   See [Supported environments]({{< relref "/introduction/platforms/environments.md" >}}) for details.

1. If your environment doesn't already have a Kubernetes setup, then see [set up Kubernetes]({{< relref "/managing-operators/k8s-setup.md" >}}).

1. If it is not already installed, then install Helm.
   To install an operator, it is required to install Helm in your Kubernetes cluster.
   The operator uses Helm to create the necessary resources and then deploy the operator in a Kubernetes cluster.

   To check if you already have Helm available, try the `helm version` command.

   For detailed instructions on installing Helm, see the [GitHub Helm Repository](https://github.com/helm/helm).

1. Optionally, enable [Istio]({{< relref "/managing-domains/accessing-the-domain/istio/istio.md" >}}).


#### Set up the operator Helm chart access

Before installing an operator, the operator Helm chart must be made available.
The operator Helm chart includes:
- Pre-configured default values for the configuration of the operator.
- Helm configuration value settings for fine-tuning operator behavior.
- Commands for deploying (installing) or undeploying the operator.

You can set up access to the operator Helm chart using the chart repository.

- Use the operator Helm chart repository
  that is located at `https://oracle.github.io/weblogic-kubernetes-operator/charts`
  or in a custom repository that you control.
- To set up your Helm installation so that it can access the
  `https://oracle.github.io/weblogic-kubernetes-operator/charts`
  repository and name the repository reference `weblogic-operator`, use
  the following command, `helm repo add <helm-chart-repo-name> <helm-chart-repo-url>`:
  ```shell
  $ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update
  ```
- To verify that a Helm chart repository was added correctly, or to list existing repositories:
  ```shell
  $ helm repo list
  ```
  ```text
  NAME                 URL
  weblogic-operator    https://oracle.github.io/weblogic-kubernetes-operator/charts
  ```
- For example, assuming you have named
  your repository `weblogic-operator`, simply
  use `weblogic-operator/weblogic-operator` in your Helm
  commands when specifying the chart location.

    - To list the versions of the operator that you can install from the Helm chart repository:

      ```shell
      $ helm search repo weblogic-operator/weblogic-operator --versions
      ```

    - For a specified version of the Helm chart and operator, with the `helm pull` and `helm install` commands, use the `--version <value>` option
      to choose the version that you want, with the `latest` value being the default.

#### Inspect the operator Helm chart

You can find out the configuration values that the operator Helm chart supports,
as well as the default values, using the `helm show` command.

  ```shell
  $ helm show values weblogic-operator/weblogic-operator
  ```


- Alternatively, you can view most of the configuration values
and their defaults in the operator source in the
`./kubernetes/charts/weblogic-operator/values.yaml` file.

- The available configuration values are explained by category in the
[Operator Helm configuration values]({{<relref "/managing-operators/using-helm#operator-helm-configuration-values">}})
section of the operator Configuration Reference.

- Helm commands are explained in more detail here, see
[Useful Helm operations]({{<relref "/managing-operators/using-helm#useful-helm-operations">}}).

#### Prepare an operator namespace and service account

Each operator requires a namespace to run in and a Kubernetes service account
within the namespace. The service account will be used
to host the operator's security permissions. Only one operator can run
in a given namespace.

To simplifies management and monitoring of an operator, Oracle recommends:
- When possible, create an isolated namespace
  for each operator which hosts only the operator, does not host
  domains, and does not host Kubernetes resources that are
  unrelated to the operator. Sometimes this is not possible,
  in which case the operator can be configured to
  manage domains in its own namespace. For more information, see
  [Choose a security strategy](#choose-a-security-strategy) and
  [Choose a domain namespace selection strategy](#choose-a-domain-namespace-selection-strategy).
- Creating a dedicated service account for each operator
  instead of relying on the `default` service account that
  Kubernetes creates when a new namespace is created.
- Directly creating a namespace
  and service account instead of relying
  on the operator Helm chart installation to create
  these resources for you.

Here's an example of each:

```shell
$ kubectl create namespace sample-weblogic-operator-ns
```

```shell
$ kubectl create serviceaccount -n sample-weblogic-operator-ns sample-weblogic-operator-sa
```

In operator installation steps,
you will specify the namespace using the `--namespace MY-NAMESPACE`
operator Helm chart configuration setting on the Helm install command line.
If not specified, then it defaults to `default`.
If the namespace does not already exist, then Helm will automatically create it
(and Kubernetes will create a `default` service account in the new namespace).
If you later uninstall the operator, then Helm will not remove the specified namespace.
These are standard Helm behaviors.

Similarly, you will specify the `serviceAccount=MY-SERVICE-ACCOUNT`
operator Helm chart configuration setting on the Helm install command
line to specify the service account in the operator's namespace
that the operator will use. If not specified, then it defaults to `default`. This
service account will not be automatically removed when you uninstall an operator.

#### Prepare operator image

The operator image must be available to all nodes of your Kubernetes cluster.

##### Locating an operator image

Production-ready operator images for various supported versions of the operator are
publicly located in the operator
[GitHub Container Registry](https://github.com/orgs/oracle/packages/container/package/weblogic-kubernetes-operator).
Operator GitHub container registry images can be directly referenced using an image name similar to
`ghcr.io/oracle/weblogic-kubernetes-operator:N.N.N` where `N.N.N` refers to the operator version
and `ghcr.io` is the DNS name of the GitHub container registry.
You can also optionally build your own operator image,
see the [Developer Guide]({{<relref "/developerguide/_index.md">}}).

##### Default operator image

Each Helm chart version defaults to using an operator image from the matching version.
To find the default image name that will be used when installing the operator,
see [Inspect the operator Helm chart](#inspect-the-operator-helm-chart) and look for the `image` value.
The value will look something like this, `ghcr.io/oracle/weblogic-kubernetes-operator:N.N.N`.

##### Pulling operator image

In most use cases, Kubernetes will automatically download (pull) the operator image, as needed,
to the machines on its cluster nodes.

If you want to manually place an operator image
in a particular machine's container image pool, or test access to an image,
then call `docker pull`. For example:

```shell
$ docker pull ghcr.io/oracle/weblogic-kubernetes-operator:{{< latestVersion >}}
```

Note that if the image registry you are using is a private registry that
requires an image pull credential,
then you will need to call `docker login my-registry-dns-name.com`
before calling `docker pull`.

##### Customizing operator image name, pull secret, and private registry

Sometimes, you may want to specify a custom operator image name
or an image pull secret. For example, you may want to deploy a different
version than the [default operator image name](#default-operator-image)
or you may want to place an operator image in your own private image registry.

A private image registry requires using a custom image name for the operator
where the first part of the name up to the first slash (`/`) character
is the DNS location of the registry and the remaining part refers
to the image location within the registry. A private image registry
may also require an image pull registry secret to
provide security credentials.

- To reference a custom image name, specify the `image=` operator Helm chart configuration setting
  when installing the operator,
  for example `--set "image=my-image-registry.io/my-operator-image:1.0"`.
- To create an image pull registry secret, create a Kubernetes Secret of type `docker-registry`
  in the namespace where the operator is to be deployed, as described
  in [Specifying `imagePullSecrets` on a Pod](https://kubernetes.io/docs/concepts/containers/images/#specifying-imagepullsecrets-on-a-pod).
- To reference an image pull registry secret from an operator installation, there are two options:
  - Use the `imagePullSecrets` operator Helm chart
    configuration setting when installing the operator.
    For examples, see [imagePullSecrets]({{<relref "/managing-operators/using-helm#imagepullsecrets">}}).
  - Or, add the pull secret name to the service account you will
    use for the operator. See [Prepare an operator namespace and service account](#prepare-an-operator-namespace-and-service-account)
    and [Add image pull secret to service account](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/#add-image-pull-secret-to-service-account).

#### Determine the platform setting

It is important to set the correct value
for the `kubernetesPlatform` Helm chart configuration setting when installing the operator.

In particular, beginning with operator version 3.3.2,
specify the operator `kubernetesPlatform` Helm chart setting
with the value `OpenShift` when using the `OpenShift` Kubernetes platform,
for example `--set "kubernetesPlatform=OpenShift"`.
This accommodates OpenShift security requirements.

For more information,
see [kubernetesPlatform]({{<relref "/managing-operators/using-helm#kubernetesplatform">}}).

#### Choose a security strategy

There are three commonly used security strategies for deploying an operator:

1. [Any namespace with cluster role binding enabled](#any-namespace-with-cluster-role-binding-enabled)
1. [Any namespace with cluster role binding disabled](#any-namespace-with-cluster-role-binding-disabled)
1. [Local namespace only with cluster role binding disabled](#local-namespace-only-with-cluster-role-binding-disabled)

For a detailed description of the operator's security-related resources,
see the operator's role-based access control (RBAC) requirements,
which are documented [here]({{< relref "/managing-operators/rbac.md" >}}).

##### Any namespace with cluster role binding enabled

If you want to give the operator permission to access any namespace,
then, for most use cases, set the `enableClusterRoleBinding` operator Helm chart
configuration setting to `true` when installing the operator.

For example `--set "enableClusterRoleBinding=true"`.
The default for this setting is `true`.

This is the most popular security strategy.

##### Any namespace with cluster role binding disabled

If your operator Helm `enableClusterRoleBinding` configuration value is `false`,
then an operator is still capable of managing multiple namespaces
but a running operator will _not_ have privilege to manage a newly added namespace
that matches its namespace selection criteria until you upgrade
the operator's Helm release.
See [Ensuring the operator has permission to manage a namespace]({{< relref "/managing-operators/namespace-management#ensuring-the-operator-has-permission-to-manage-a-namespace" >}}).

**NOTE**: You will need to manually install the Domain and Cluster CRDs
because `enableClusterRoleBinding` is not set to `true`
and installation of the CRD requires cluster role binding privileges.
See [How to manually install the Domain and Cluster custom resource definitions (CRD)](#how-to-manually-install-the-domain-and-cluster-custom-resource-definitions-crd).

##### Local namespace only with cluster role binding disabled

If you want to limit the operator so that it can access only resources in its local namespace, then:

- Choose the `Dedicated` namespace selection strategy.
  See [Choose a domain namespace selection strategy](#choose-a-domain-namespace-selection-strategy).
- Install only the operator deployment and omit installing the webhook deployment  using `operatorOnly=true`. This is because the webhook deployment must modify the Domain CRD to register the schema conversion webhook endpoint or register the validating webhook endpoint, both of which involve cluster-level resources.
- You will need to manually install the Domain and Cluster CRDs
  because `enableClusterRoleBinding` is not set to `true`
  and installing the CRD requires cluster role binding privileges.
  See [How to manually install the Domain and Cluster custom resource definitions (CRD)](#how-to-manually-install-the-domain-and-cluster-custom-resource-definitions-crd).

This may be necessary in environments where the operator cannot have cluster-scoped privileges,
such as may happen on OpenShift platforms or when running the operator with a `Dedicated` namespace strategy.

Many customers do not have administrative privileges to their Kubernetes cluster because either a third-party or an infrastructure team is responsible for managing the cluster. In these cases, the customer, such as an applications team, will only have privilege in a single namespace.
As described above, the CRD documents must be installed in advance if the operator will not have sufficient privilege at the Kubernetes cluster-level to manage the lifecycle of these CRD documents. Therefore, the third-party or infrastructure team must complete the `kubectl create` of the CRD documents prior to the application team's installation of the operator.

At a minimum, the infrastructure team must install the CRD documents and create the namespace for the operator:

```shell
$ kubectl create -f https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/refs/heads/release/4.2/kubernetes/crd/domain-crd.yaml
$ kubectl create -f https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/refs/heads/release/4.2/kubernetes/crd/cluster-crd.yaml
$ kubectl create ns weblogic-operator
```

This would then allow the applications team to install a namespace-dedicated version of the operator without any webhooks or other cluster-level resources:

```shell
helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts
helm install weblogic-operator weblogic-operator/weblogic-operator --namespace weblogic-operator --set enableClusterRoleBinding=false --set domainNamespaceSelectionStrategy=Dedicated --set operatorOnly=true
```

{{% notice note %}}
Since this combination of options omits installing the webhook deployment, customers must use the `v9` schema version for Domain resources and manually uprade any `v8` resources from the 3.x version of the operator.
{{% /notice %}}

#### Choose a domain namespace selection strategy

Before installing your operator,
choose the value for its `domainNamespaceSelectionStrategy` Helm chart configuration setting and its related setting (if any).
See [Choose a domain namespace section strategy]({{<relref "/managing-operators/namespace-management#choose-a-domain-namespace-selection-strategy">}}).

See [Choose a security strategy](#choose-a-security-strategy).

For a description of common namespace management issues,
see [Common mistakes and solutions]({{<relref "/managing-operators/common-mistakes.md">}}).
For reference, see [WebLogic domain management]({{<relref "/managing-operators/using-helm#weblogic-domain-management">}}).

#### Choose a Helm release name

The operator requires Helm for installation,
and Helm requires that each installed operator
be assigned a release name. Helm release names
can be the same if they are deployed to
different namespaces, but must
be unique within a particular namespace.

A typical Helm release name is simply `weblogic-operator`.
The operator samples and documentation
often use `sample-weblogic-operator`.

#### Be aware of advanced operator configuration options

Review the settings in the [Configuration Reference]({{<relref "/managing-operators/using-helm.md">}}) for
less commonly used advanced or fine tuning Helm chart configuration options
that might apply to your particular use case.
These include node selectors,
node affinity,
Elastic Stack integration,
the operator REST API,
setting operator pod labels,
setting operator pod annotations,
and Istio.

### Special use cases

If applicable, please review the following special use cases.

#### How to download the Helm chart if Internet access is not available

At a high level, you use `helm pull` to download a released version of the Helm chart and move it to the machine with no Internet access,
so that then you can run `helm install` to install the operator.  

The steps are:
1. On a machine with Internet access, to download the chart to the current directory, run:
```shell
$ helm pull weblogic-operator --repo https://oracle.github.io/weblogic-kubernetes-operator/charts --destination .
```
For a specified version of the Helm chart, with `helm pull` and `helm install`, use the `--version <value>` option
to choose the version that you want, with the `latest` value being the default. To list the versions of the
operator that you can install from the Helm chart repository, run:
```shell
$ helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update
$ helm search repo weblogic-operator/weblogic-operator --versions
```
2. Move the resulting `weblogic-operator-<version>.tgz` file to the machine without Internet access on which you want to install the WebLogic Kubernetes Operator.
3. Run `$ tar zxf weblogic-operator-<version>.tgz`. This puts the Helm chart files in a local directory `./weblogic-operator`.
4. To create the namespace where the operator will be installed, run `$ kubectl create namespace weblogic-operator`.
{{% notice note %}}
Creating a dedicated namespace for the operator is the most common approach, but is not always correct or sufficient. For details,
see the prerequisite steps starting with Step 3. [Inspect the operator Helm chart](#inspect-the-operator-helm-chart).
Be sure to follow all the previously detailed prerequisite steps, ending at Step 10. [Be aware of advanced operator configuration options](#be-aware-of-advanced-operator-configuration-options).
{{% /notice %}}
5. To install the operator, run `$ helm install weblogic-operator ./weblogic-operator --namespace weblogic-operator`.


#### How to manually install the Domain and Cluster custom resource definitions (CRD)

The Domain and Cluster resource types are defined by Kubernetes CustomResourceDefinition (CRD) resources.
The Domain and Cluster CRDs provide Kubernetes with the schema for WebLogic-related resources
and these two CRDs must be installed in each Kubernetes cluster that hosts the operator.
If you install multiple operators in the same Kubernetes cluster, then they all
share the same CRDs.

**When do the Domain and Cluster CRDs need to be manually installed?**

Typically, the operator's webhook deployment automatically installs the CRDs when it first starts.
However, if the webhook lacks sufficient permission to install the CRDs,
then you must choose to manually install the CRD documents in advance by using the provided YAML files.
Manually installing the CRDs in advance allows you to run the operator without giving it privilege
(through Kubernetes roles and bindings) to access or update the CRD documents or other cluster-scoped resources.
This may be necessary in environments where the operator cannot have cluster-scoped security privileges,
such when running the operator with a `Dedicated` namespace strategy.
See [Choose a security strategy](#choose-a-security-strategy).

**How to manually install the Domain and Cluster CRDs.**

To manually install the CRDs, perform the following steps:

```shell
$ kubectl create -f https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/refs/heads/release/4.2/kubernetes/crd/domain-crd.yaml
$ kubectl create -f https://raw.githubusercontent.com/oracle/weblogic-kubernetes-operator/refs/heads/release/4.2/kubernetes/crd/cluster-crd.yaml
```

**How to check if a Domain and Cluster CRDs have been installed.**

You can verify that the Domain CRD is installed correctly using:

```shell
$ kubectl get crd domains.weblogic.oracle
```

Or, by calling:

```shell
$ kubectl explain domain.spec
```

The `kubectl explain` call should succeed
and list the domain resource's `domain.spec` attributes
that are defined in the Domain CRD.

Similarly, you can verify that the Cluster CRD is installed correctly using:

```shell
$ kubectl get crd clusters.weblogic.oracle
```

Or, by calling:

```shell
$ kubectl explain cluster.spec
```

The `kubectl explain` call should succeed
and list the cluster resource's `cluster.spec` attributes
that are defined in the Cluster CRD.
