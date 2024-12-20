---
title: "Namespace management"
date: 2019-09-19T10:41:32-05:00
draft: false
weight: 6
description: "Configure or dynamically change the namespaces that a running operator manages."
---

{{< table_of_contents >}}

### Overview

An operator deployment must be configured to manage Kubernetes namespaces,
and a number of Kubernetes resources
must be present in a namespace before any WebLogic Server instances can be successfully
started by operator. These Kubernetes resources are created either as part of the installation
of the operator's Helm chart, or are created by the operator.

An operator can manage all WebLogic domains in all namespaces in a Kubernetes cluster,
or only manage domains in a specific subset of the namespaces,
or manage only the domains that are located in the same namespace as the operator.
You can change the namespaces that an operator deployment manages while the operator is still running.
You can also create and prepare a namespace for the operator to manage while the operator is still running.

* For considerations for configuring which namespaces an operator manages,
see [Choose a domain namespace selection strategy](#choose-a-domain-namespace-selection-strategy),
* For some considerations to be aware of when
you manage the namespaces while the operator is running,
see [Altering namespaces for a running operator](#altering-namespaces-for-a-running-operator).
* For namespace management information that is external to this document,
see [WebLogic domain management]({{<relref "/managing-operators/using-helm.md#weblogic-domain-management">}})
and [Common mistakes and solutions]({{< relref "/managing-operators/common-mistakes.md" >}}).

{{% notice warning %}}
There can be multiple operators in a Kubernetes cluster,
and in that case,
you must ensure that the namespaces managed by these operators do not overlap.
_At most, a namespace can be managed by one operator._
{{% /notice %}}

### Choose a domain namespace selection strategy

An operator can manage domain resources in multiple namespaces,
including its own namespace,
but two operators cannot manage domains that are in the same namespace.
The operator installation Helm chart
[domainNamespaceSelectionStrategy]({{<relref "/managing-operators/using-helm#domainnamespaceselectionstrategy">}})
configuration setting controls which namespaces an operator manages.

| Strategy        | Description                                                                                                                                                                                                                                     | Example                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `LabelSelector` | This is the default. The operator will manage namespaces with Kubernetes labels that match the label selector defined by your Helm chart configuration `domainNamespaceLabelSelector` attribute, which defaults to `weblogic-operator=enabled`. | With the Helm chart defaults, the operator will manage namespaces that have a label named `weblogic-operator` when this label has the value `enabled`. You can label the namespace `sample-domain1-ns` using the command `kubectl label namespace sample-domain1-ns weblogic-operator=enabled`. You can define a different label selector for your operator installation using the `domainNamespaceLabelSelector`, such as `--set "domainNamespaceLabelSelector=environment\=prod"`. For detailed syntax requirements, see [domainNamespaceLabelSelector]({{<relref "/managing-operators/using-helm#domainnamespacelabelselector">}}) in the Configuration Reference.                   |
| `RegExp`        | The operator will manage namespaces that match the regular expression set by your `domainNamespaceRegExp` Helm chart configuration attribute.                                                                                                   | If you want an operator to manage namespaces that start with the string `prod`, then use `--set "domainNamespaceSelectionStrategy=RegExp"` for your operator Helm configuration setting, and set the `domainNamespaceRegExp` Helm chart configuration attribute using `--set "domainNamespaceRegExp=^prod"`.                                                                                                                                                                                                                                                                                                                                                                            |
| `Dedicated`     | The operator will manage only domains that are in the same namespace as the operator.                                                                                                                                                           | If the operator is deployed to namespace `my-operator-ns` and was installed using `--set "domainNamespaceSelectionStrategy=Dedicated"` in its Helm chart configuration, then the operator will manage only domains in the `my-operator-ns` namespace. Customers often choose this strategy if a third-party or infrastructure team manages the Kubernetes cluster and the team installing the operator, such as an applications team, will have privilege only within one namespace. For more details on this use case, see [Local namespace only with cluster role binding disabled]({{<relref "/managing-operators/preparation#any-namespace-with-cluster-role-binding-disabled">}}). |
| `List`          | The operator will manage the namespaces included in the `domainNamespaces` operator installation Helm chart configuration value, which is a list that defaults to `{default}`.                                                                  | If you want to manage namespaces `default` and `ns1`, then in your operator installation Helm chart configuration, use `--set "domainNamespaceSelectionStrategy=List"` and `--set "domainNamespaces={default,ns1}"`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |

For detailed reference information about each setting,
see [WebLogic domain management]({{<relref "/managing-operators/using-helm#weblogic-domain-management">}}).

**NOTES**:

- Your security strategy may determine which namespace strategy you should choose,
  see [Choose a security strategy]({{<relref "/managing-operators/preparation#choose-a-security-strategy">}}).
- As has been noted previously,
  two operators cannot manage domains that are in the same namespace.
  If two operators are configured to manage the same namespace,
  then their behavior is undefined
  but it is likely that the second operator installation will
  deploy a `FAILED` Helm release and generate an error similar to
  `Error: release op2 failed: rolebindings.rbac.authorization.k8s.io "weblogic-operator-rolebinding-namespace" already exists`.
- For more information about ensuring an operator has permission to manage a namespace,
  see [Ensuring the operator has permission to manage a namespace](#ensuring-the-operator-has-permission-to-manage-a-namespace).
- For more information about listing, adding, deleting, or recreating the namespaces
  that an already running operator manages,
  see [Altering namespaces for a running operator](#altering-namespaces-for-a-running-operator).
- For more information about common namespace management issues,
  see [Common mistakes and solutions]({{<relref "/managing-operators/common-mistakes.md">}}).

### Ensuring the operator has permission to manage a namespace

If your operator Helm `enableClusterRoleBinding` configuration value is `true`, then
the operator has permission to manage any namespace and can automatically
manage a namespace that is added after the operator was last installed or upgraded.

If your operator Helm `enableClusterRoleBinding` configuration value is `false`, then:
- The operator Helm chart will create RoleBindings in each namespace that matches
  your domain namespace selection criteria during a call to `helm install` or `helm upgrade`.
  These RoleBindings give the operator's service account the necessary privileges in the namespace.
- The Helm chart will only create these RoleBindings in namespaces that match
  the operator's domain namespace selection criteria
  at the time the chart is installed or upgraded.
- If you later create namespaces
  that match a `List`, `LabelSelector`, or `RegExp` selector,
  then the operator will _not_ have privilege in these namespaces until you upgrade the Helm release.
  You can resolve this issue by performing
  a `helm upgrade` on an already installed operator Helm release.

  For example, with an operator release name `weblogic-operator`:
  ```shell
  $ helm upgrade weblogic-operator/weblogic-operator --reuse-values
  ```
  **NOTE**: If you still run into problems after you perform the `helm upgrade` to re-initialize a namespace
  that is deleted and recreated, then you can
  try [forcing the operator to restart]({{< relref "/managing-operators/troubleshooting#force-the-operator-to-restart" >}}).

For a detailed description of the operator's security related resources,
see the operator's role-based access control (RBAC) requirements
which are documented [here]({{< relref "/managing-operators/rbac.md" >}}).

### Check the namespaces that a running operator manages

Prior to version 3.1.0, the operator supported specifying the namespaces that it would manage only through a list.
Now, the operator supports a list of namespaces, a label selector, or a regular expression matching namespace names.

**When using a namespace list**

For operators that specify namespaces by a list, you can find the list of the namespaces using the `helm get values` command.
For example, the following command shows all the values of the operator release `weblogic-operator`; the `domainNamespaces` list contains `default` and `ns1`:

```shell
$ helm get values weblogic-operator
```
```
domainNamespaces:
- default
- ns1
elasticSearchHost: elasticsearch.default.svc.cluster.local
elasticSearchPort: 9200
elkIntegrationEnabled: false
externalDebugHttpPort: 30999
externalRestEnabled: false
externalRestHttpsPort: 31001
image: ghcr.io/oracle/weblogic-kubernetes-operator:{{< latestVersion >}}
imagePullPolicy: IfNotPresent
internalDebugHttpPort: 30999
javaLoggingLevel: INFO
logStashImage: logstash:6.6.0
remoteDebugNodePortEnabled: false
serviceAccount: default
suspendOnDebugStartup: false
```

If you don't know the release name of the operator, you can use `helm list` to list all the releases for a specified namespace or all namespaces:

```shell
$ helm list --namespace <namespace>
```
```shell
$ helm list --all-namespaces
```

**When using a label selector**

For operators that select namespaces with a selector, simply list namespaces using that selector:

```shell
$ kubectl get ns --selector="weblogic-operator=enabled"
```

You can see the labels on all namespaces by calling:

```shell
$ kubectl get ns --show-labels
```

**When using regular expression matching**

For operators that select namespaces with a regular expression matching the name, you can use a combination of `kubectl`
and any command-line tool that can process the regular expression, such as `grep`:

```shell
$ kubectl get ns -o go-template='{{range .items}}{{.metadata.name}}{{"\n"}}{{end}}' | grep "^weblogic"
```

### Altering namespaces for a running operator

This section describes the steps for adding, deleting, or recreated namespaces that are managed by a running operator.

#### Add a Kubernetes namespace to a running operator

The following are steps for adding namespaces that are managed by a running operator.

**When using a namespace list**

When the operator is configured to manage a list of namespaces and you want the operator to manage an additional namespace,
you need to add the namespace to the operator's `domainNamespaces` list. Note that this namespace has to already exist, for example,
using the `kubectl create` command.

Adding a namespace to the `domainNamespaces` list tells the operator to initialize the necessary
Kubernetes resources so that the operator is ready to manage WebLogic Server instances in that namespace.

When the operator is managing the `default` namespace, the following example Helm command adds the namespace `ns1` to the `domainNamespaces` list, where `weblogic-operator` is the release name of the operator:

```shell
$ helm upgrade \
  weblogic-operator/weblogic-operator \
  --reuse-values \
  --set "domainNamespaces={default,ns1}" \
  --wait
```

You can verify that the operator has initialized a namespace by confirming the existence of the required `configmap` resource.

```shell
$ kubetctl get cm -n <namespace>
```

For example, the following example shows that the domain `configmap` resource exists in the namespace `ns1`.

```shell
$ kubectl get cm -n ns1
```
```
NAME                 DATA      AGE

weblogic-scripts-cm   14        12m
```

**When using a label selector or regular expression**

For operators configured to select managed namespaces through the use of a label selector or regular expression,
you  need to create a namespace with the appropriate labels or with a name that matches the expression, respectively.

{{% notice warning %}}
If your operator Helm `enableClusterRoleBinding` configuration value is `false`,
then a running operator will _not_ have privilege to manage the newly added namespace until you upgrade
the operator's Helm release.
See [Ensuring the operator has permission to manage a namespace](#ensuring-the-operator-has-permission-to-manage-a-namespace).
{{% /notice %}}

#### Delete a Kubernetes namespace from a running operator

The following are steps for deleting namespaces that are managed by a running operator.

**When using a namespace list**

When the operator is configured to manage a list of namespaces and you no longer want a namespace to be managed by the operator, you need to:
- First, remove the namespace from the operator's `domainNamespaces` list.
- Then, delete the namespace.

While the operator is running and managing the `default` and `ns1` namespaces, the following example Helm
command removes the namespace `ns1` from the `domainNamespaces` list, where `weblogic-operator` is the release
name of the operator:

```shell
$ helm upgrade \
  weblogic-operator/weblogic-operator \
  --reuse-values \
  --set "domainNamespaces={default}" \
  --wait
```

**When using a label selector or regular expression**

For operators configured to select managed namespaces through the use of a label selector or regular expression,
you  need to delete the namespace. For the label selector option, you can also adjust the labels on the namespace
so that the namespace no longer matches the selector.

#### Recreate a previously deleted Kubernetes namespace with a running operator

The following are steps for recreating previously deleted namespaces that are managed by a running operator.

**When using a namespace list**

If you deleted a namespace (and the resources in it) and then want to recreate it:
- First, add back (recreate) the namespace, for example, using the `kubectl create` command.
- Then, add the namespace back to the `domainNamespaces` list using the `helm upgrade` command illustrated [previously](#add-a-kubernetes-namespace-to-a-running-operator).

If a domain custom resource is created before the namespace is ready, you might see that the introspector job pod
fails to start, with a warning like the following, when you review the description of the introspector pod.
Note that `domain1` is the name of the domain in the following example output.

```
Events:
  Type     Reason                 Age               From               Message
  ----     ------                 ----              ----               -------
  Normal   Scheduled              1m                default-scheduler  Successfully assigned domain1-introspector-bz6rw to slc16ffk
  Normal   SuccessfulMountVolume  1m                kubelet, slc16ffk  MountVolume.SetUp succeeded for volume "weblogic-credentials-volume"
  Normal   SuccessfulMountVolume  1m                kubelet, slc16ffk  MountVolume.SetUp succeeded for volume "default-token-jzblm"
  Warning  FailedMount            27s (x8 over 1m)  kubelet, slc16ffk  MountVolume.SetUp failed for volume "weblogic-scripts-cm-volume" : configmaps "weblogic-scripts-cm" not found
```

If you still run into problems after you perform the `helm upgrade` to re-initialize a namespace
that was deleted and recreated, then you can
try [forcing the operator to restart]({{< relref "/managing-operators/troubleshooting#force-the-operator-to-restart" >}}).

**When using a label selector or regular expression**

If your operator Helm `enableClusterRoleBinding` configuration value is `false`,
then a running operator will _not_ have privilege to manage the newly recreated namespace until you upgrade
the operator's Helm release.
See [Ensuring the operator has permission to manage a namespace](#ensuring-the-operator-has-permission-to-manage-a-namespace).
