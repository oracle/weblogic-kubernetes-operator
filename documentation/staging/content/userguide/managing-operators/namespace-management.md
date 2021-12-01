---
title: "Namespaces"
date: 2019-09-19T10:41:32-05:00
draft: false
weight: 4
description: "Dynamically change the namespaces that a running operator manages."
---

### Contents

TBD generate contents
TBD check all relref into namespace-management to see if they reflect title changes

### Overview

An operator deployment must be configured to manage Kubernetes namespaces,
and a number of Kubernetes resources
must be present in a namespace before any WebLogic Server instances can be successfully
started by operator. These Kubernetes resources are created either as part of the installation
of a release of the operator's Helm chart, or are created by the operator.

An operator can manage all WebLogic domains in all namespaces in a Kubernetes cluster,
or only manage domains in a specific subset of the namespaces,
or manage only the domains that are located in the same namespace as the operator.
You can change the namespaces that an operator deployment manages while the operator is still running.
You can also create and prepare a namespace for the operator to manage while the operator is still running.

{{% notice note %}}
There can be multiple operators in a Kubernetes cluster,
and in that case,
you must ensure that the namespaces managed by these operators do not overlap.
_At most, a namespace can be managed by one operator._
{{% /notice %}}

This document describes considerations for configuring which namespaces that an operator manages,
see [Choose a domain namespace selection strategy](#choose-a-namespace-selection-strategy),
and this document describes some considerations to be aware of when
you manage the namespaces while the operator is running,
see [Altering namespaces for a running operator](#altering-namespaces-for-a-running-operator), 

For namespace management information that is external to this document,
see [WebLogic domain management]({{<relref "/userguide/managing-operators/using-helm.md#weblogic-domain-management">}}) 
in the operator configuration guide,
and [Common Mistakes and Solutions]({{< relref "/userguide/managing-operators/common-mistakes.md" >}}).

### Choose a domain namespace selection strategy

TBD this section needs review

An operator can manage domain resources in multiple namespaces,
including its own namespace,
but two operators cannot manage domains that are in the same namespace.
The operator install Helm chart
[`domainNamespaceSelectionStrategy`]({{<relref "/userguide/managing-operators/using-helm#domainnamespaceselectionstrategy">}})
configuration setting controls which namespaces that an operator manages:

|Strategy|Description|Example|
|-|-|-|
|`Dedicated`|The operator will only manage domains that are in the same namespace as the operator.|If the operator is deployed to namespace `my-operator-ns` and it was installed using `--set "domainNamespaceSelectionStrategy=Dedicated"` in its Helm chart configuration, then the operator will only manage domains in the `my-operator-ns` namespace.|
|`List`|This is the default. The operator will manage the namespaces included in the `domainNamespaces` operator install Helm chart configuration value which is a list that in defaults to "{default}".|If you want to manage namespaces `default` and `ns1`, then use `--set "domainNamespaceSelectionStrategy=List"` and `--set "domainNamespaces={default,ns1}"` in your operator install Helm chart configuration.|
|`LabelSelector`|The operator will manage namespaces with Kubernetes labels that match the label selector defined by your Helm chart configuration `domainNamespaceLabelSelector` attribute.|If your operator Helm chart configuration has `--set "domainNamespaceSelectionStrategy=LabelSelector"` and you define the label selector in your operator install using `--set "domainNamespaceLabelSelector=weblogic-operator\=my-operator"`, then the operator will manage namespaces with label name `weblogic-operator` when this label has value `my-operator`, and you can label each such a namespace using the command `kubectl label namespace some-namespace-name weblogic-operator=my-operator`.|
|`RegExp`|The operator will manage namespaces that match the regular expression set by your `domainNamespaceRegExp` Helm chart configuration attribute.|If you want an operator to manage namespaces that start with the string "prod", then use `--set "domainNamespaceSelectionStrategy=RegExp"` for your operator Helm configuration setting, and set the `domainNamespaceRegExp` Helm chart configuration attribute using `--set "domainNamespaceRegExp=^prod"`.|

**Notes:**

- Your security strategy may determine which namespace strategy you should choose,
  see [Choose a security strategy]({{<relref "/userguide/managing-operators/installation.md#choose-a-security-strategy">}})
  in the operator installation guide.
- As has already been noted earlier,
  two operators cannot manage domains that are in the same namespace.
  If two operators are configured to manage the same namespace,
  then behavior is undefined
  but it is likely that the second operator install will
  deploy a `FAILED` Helm release and generate an error similar to
  `Error: release op2 failed: rolebindings.rbac.authorization.k8s.io "weblogic-operator-rolebinding-namespace" already exists`.
- For a discussion about listing, adding, deleting, or recreating the namespaces
  that an already running operator manages,
  see [Altering namespaces for a running operator](#altering-namespaces-for-a-running-operator).
- For a discussion about common namespace management issues,
  see [Common Mistakes]({{<relref "/userguide/managing-operators/common-mistakes.md">}}).
- For reference, see [WebLogic domain management]({{<relref "/userguide/managing-operators/using-helm.md#weblogic-domain-management">}})
  in the operator configuration guide.
- If the deprecated `dedicated` operator Helm chart configuration setting is set to `true`,
  then the `domainNamespaceSelectionStrategy` setting will be ignored
  and the operator will force the selection strategy to be `Dedicated`.A

### Ensuring the operator has permission to manage a namespace

If you did not choose to enable the value, `enableClusterRoleBinding`, 
then the operator will not have the necessary
permissions to manage a namespace unless the 
operator has been deployed (installed) to the same namespace.

You can change this value dynamically by performing
a `helm upgrade` with the values used when installing the
Helm release:

```shell
$ helm upgrade \
    weblogic-operator \
    kubernetes/charts/weblogic-operator \
    --reuse-values
```

TBD This won't upgrade to a cluster-role, since enable defaults to false... Right?


### Altering namespaces for a running operator

#### Check the namespaces that a running operator manages

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

**When using regular expression matching**

For operators that select namespaces with a regular expression matching the name, you can use a combination of `kubectl`
and any command-line tool that can process the regular expression, such as `grep`:

```shell
$ kubectl get ns -o go-template='{{range .items}}{{.metadata.name}}{{"\n"}}{{end}}' | grep "^weblogic"
```

#### Add a Kubernetes namespace to a running operator

**When using a namespace list**

When the operator is configured to manage a list of namespaces and you want the operator to manage an additional namespace,
you need to add the namespace to the operator's `domainNamespaces` list. Note that this namespace has to already exist, for example,
using the `kubectl create` command.

Adding a namespace to the `domainNamespaces` list tells the operator to initialize the necessary
Kubernetes resources so that the operator is ready to manage WebLogic Server instances in that namespace.

When the operator is managing the `default` namespace, the following example Helm command adds the namespace `ns1` to the `domainNamespaces` list, where `weblogic-operator` is the release name of the operator, and `kubernetes/charts/weblogic-operator` is the location of the operator's Helm charts:

```shell
$ helm upgrade \
  weblogic-operator \
  kubernetes/charts/weblogic-operator \
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
you simply need to create a namespace with the appropriate labels or with a name that matches the expression, respectively.

####  Delete a Kubernetes namespace from a running operator

##### When using a namespace list

When the operator is configured to manage a list of namespaces and you no longer want a namespace to be managed by the operator, you need to remove it from
the operator's `domainNamespaces` list, so that the resources that are
associated with the namespace can be cleaned up.

While the operator is running and managing the `default` and `ns1` namespaces, the following example Helm
command removes the namespace `ns1` from the `domainNamespaces` list, where `weblogic-operator` is the release
name of the operator, and `kubernetes/charts/weblogic-operator` is the location of the operator Helm charts:

```shell
$ helm upgrade \
    --reuse-values \
    --set "domainNamespaces={default}" \
    --wait \
    --force \
    weblogic-operator \
    kubernetes/charts/weblogic-operator
```

##### When using a label selector or regular expression

For operators configured to select managed namespaces through the use of a label selector or regular expression,
you simply need to delete the namespace. For the label selector option, you can also adjust the labels on the namespace
so that the namespace no longer matches the selector.

#### Recreate a previously deleted Kubernetes namespace with a running operator

When the operator is configured to manage a list of namespaces and if you need to delete a namespace (and the resources in it) and then recreate it,
remember to remove the namespace from the operator's `domainNamespaces` list
after you delete the namespace, and add it back to the `domainNamespaces` list after you recreate the namespace
using the `helm upgrade` commands that were illustrated previously.

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
that is deleted and recreated, then you can
try [forcing the operator to restart]({{< relref "/userguide/managing-operators/debugging#force-the-operator-to-restart" >}}).
