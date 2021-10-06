---
title: "Namespaces"
date: 2019-09-19T10:41:32-05:00
draft: false
weight: 4
description: "Dynamically change the namespaces that a running operator manages."
---

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
There can be multiple operators in a Kubernetes cluster, and in that case, you must ensure that the namespaces managed by these operators do not overlap.
_At most, a namespace can be managed by one operator._
{{% /notice %}}

This document describes some considerations to be aware of when you manage the namespaces while the operator is running. For example:

* [Check the namespaces that the operator manages](#check-the-namespaces-that-the-operator-manages)
* [Add a namespace for the operator to manage](#add-a-kubernetes-namespace-to-the-operator)
* [Delete a namespace from the operator's domain namespace list](#delete-a-kubernetes-namespace-from-the-operator)
* [Delete and recreate a Kubernetes namespace that the operator manages](#recreate-a-previously-deleted-kubernetes-namespace)

For others, see [Common Mistakes and Solutions]({{< relref "/userguide/managing-operators/common-mistakes.md" >}}).


#### Check the namespaces that the operator manages

Prior to version 3.1.0, the operator supported specifying the namespaces that it would manage only through a list.
Now, the operator supports a list of namespaces, a label selector, or a regular expression matching namespace names.

##### When using a namespace list

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
image: ghcr.io/oracle/weblogic-kubernetes-operator:3.3.2
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

##### When using a label selector

For operators that select namespaces with a selector, simply list namespaces using that selector:

```shell
$ kubectl get ns --selector="weblogic-operator=enabled"
```

##### When using regular expression matching

For operators that select namespaces with a regular expression matching the name, you can use a combination of `kubectl`
and any command-line tool that can process the regular expression, such as `grep`:

```shell
$ kubectl get ns -o go-template='{{range .items}}{{.metadata.name}}{{"\n"}}{{end}}' | grep "^weblogic"
```

#### Add a Kubernetes namespace to the operator

##### When using a namespace list

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

##### When using a label selector or regular expression

For operators configured to select managed namespaces through the use of a label selector or regular expression,
you simply need to create a namespace with the appropriate labels or with a name that matches the expression, respectively.

##### Ensuring the operator has permission to manage the namespace

If you did not choose to enable the value, `enableClusterRoleBinding`, then the operator will not have the necessary
permissions to manage the namespace. You can do this by performing a `helm upgrade` with the values used when installing the
Helm release:

```shell
$ helm upgrade \
    weblogic-operator \
    kubernetes/charts/weblogic-operator \
    --reuse-values
```

####  Delete a Kubernetes namespace from the operator

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

#### Recreate a previously deleted Kubernetes namespace

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
