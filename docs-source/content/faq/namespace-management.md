---
title: "Managing domain namespaces"
date: 2019-09-19T10:41:32-05:00
draft: false
weight: 1
---

Each operator deployment manages a number of Kubernetes Namespaces. For more information, see [Operator Helm configuration values]({{< relref "/userguide/managing-operators/using-the-operator/using-helm#operator-helm-configuration-values" >}}). A number of Kubernetes resources
must be present in a namespace before any WebLogic domain custom resources can be successfully
deployed into it.
Those Kubernetes resources are created either as part of the installation
of the operator's Helm chart, or created by the operator at runtime.

This FAQ describes some considerations to be aware of when you manage the namespaces while the operator is running. For example:

* [Check the namespaces that the operator manages](#check-the-namespaces-that-the-operator-manages)
* [Add a namespace for the operator to manage](#add-a-kubernetes-namespace-to-the-operator)
* [Delete a namespace from the operator's domain namespace list](#delete-a-kubernetes-namespace-from-the-operator)
* [Delete and recreate a Kubernetes Namespace that the operator manages](#recreate-a-previously-deleted-kubernetes-namespace)

For others, see [Common Mistakes and Solutions]({{< relref "/userguide/managing-operators/using-the-operator/using-helm#common-mistakes-and-solutions" >}}).

{{% notice note %}}
There can be multiple operators in a Kubernetes cluster, and in that case, you must ensure that their respective lists of `domainNamespaces` do not overlap.
{{% /notice %}}

#### Check the namespaces that the operator manages
You can find the list of the namespaces that the operator manages using the `helm get values` command.
For example, the following command shows all the values of the operator release `weblogic-operator`; the `domainNamespaces` list contains `default` and `ns1`.

```
$ helm get values weblogic-operator
domainNamespaces:
- default
- ns1
elasticSearchHost: elasticsearch.default.svc.cluster.local
elasticSearchPort: 9200
elkIntegrationEnabled: false
externalDebugHttpPort: 30999
externalRestEnabled: false
externalRestHttpsPort: 31001
image: oracle/weblogic-kubernetes-operator:2.6.0
imagePullPolicy: IfNotPresent
internalDebugHttpPort: 30999
istioEnabled: false
javaLoggingLevel: INFO
logStashImage: logstash:6.6.0
remoteDebugNodePortEnabled: false
serviceAccount: default
suspendOnDebugStartup: false

```

If you don't know the release name of the operator, you can use `helm list` to list all the releases for a specified namespace or all namespaces:

```
$ helm list --namespace <namespace>
$ helm list --all-namespaces
```

#### Add a Kubernetes Namespace to the operator
If you want an operator deployment to manage a namespace, you need to add the namespace to the operator's `domainNamespaces` list. Note that the namespace has to already exist, for example, using the `kubectl create` command.

Adding a namespace to the `domainNamespaces` list tells the operator deployment or runtime
to initialize the necessary Kubernetes resources for the namespace so that the operator is ready to host WebLogic domain resources in that namespace.

When the operator is running and managing the `default` namespace, the following example Helm command adds the namespace `ns1` to the `domainNamespaces` list, where `weblogic-operator` is the release name of the operator, and `kubernetes/charts/weblogic-operator` is the location of the operator's Helm charts.

```
$ helm upgrade \
  --reuse-values \
  --set "domainNamespaces={default,ns1}" \
  --wait \
  --force \
  weblogic-operator \
  kubernetes/charts/weblogic-operator
```

{{% notice note %}}
Changes to the `domainNamespaces` list might not be picked up by the operator right away because the operator
monitors the changes to the setting periodically. The operator becomes ready to host domain resources in
a namespace only after the required `configmap` (namely `weblogic-domain-cm`) is initialized in the namespace.
{{% /notice %}}

You can verify that the operator is ready to host domain resources in a namespace by confirming the existence of the required `configmap` resource.

```
$ kubetctl get cm -n <namespace>
```

For example, the following example shows that the domain `configmap` resource exists in the namespace `ns1`.

```
bash-4.2$ kubectl get cm -n ns1

NAME                 DATA      AGE

weblogic-domain-cm   14        12m
```

####  Delete a Kubernetes Namespace from the operator
When you no longer want a namespace to be managed by the operator, you need to remove it from
the operator's `domainNamespaces` list, so that the corresponding Kubernetes resources that are
associated with the namespace can be cleaned up.

While the operator is running and managing the `default` and `ns1` namespaces, the following example Helm
command removes the namespace `ns1` from the `domainNamespaces` list, where `weblogic-operator` is the release
name of the operator, and `kubernetes/charts/weblogic-operator` is the location of the operator Helm charts.

```
$ helm upgrade \
  --reuse-values \
  --set "domainNamespaces={default}" \
  --wait \
  --force \
  weblogic-operator \
  kubernetes/charts/weblogic-operator

```

#### Recreate a previously deleted Kubernetes Namespace

If you need to delete a namespace (and the resources in it) and then recreate it,
remember to remove the namespace from the operator's `domainNamespaces` list
after you delete the namespace, and add it back to the `domainNamespaces` list after you recreate the namespace
using the `helm upgrade` commands that were illustrated previously.

{{% notice note %}}
Make sure that you wait a sufficient period of time between deleting and recreating the
namespace because it takes time for the resources in a namespace to go away after the namespace is deleted.
In addition, as mentioned above, changes to the `domainNamespaces` setting is monitored by the operator
periodically, and the operator becomes ready to host domain resources only after the required domain
`configmap` (namely `weblogic-domain-cm`) is initialized in the namespace.
{{% /notice %}}

If a domain custom resource is created before the namespace is ready, you might see that the introspector job pod
fails to start, with a warning like the following, when you review the description of the introspector pod.
Note that `domain1` is the name of the domain in the following example output.

```
Events:
  Type     Reason                 Age               From               Message
  ----     ------                 ----              ----               -------
  Normal   Scheduled              1m                default-scheduler  Successfully assigned domain1-introspect-domain-job-bz6rw to slc16ffk

  Normal   SuccessfulMountVolume  1m                kubelet, slc16ffk  MountVolume.SetUp succeeded for volume "weblogic-credentials-volume"

  Normal   SuccessfulMountVolume  1m                kubelet, slc16ffk  MountVolume.SetUp succeeded for volume "default-token-jzblm"

  Warning  FailedMount            27s (x8 over 1m)  kubelet, slc16ffk  MountVolume.SetUp failed for volume "weblogic-domain-cm-volume" : configmaps "weblogic-domain-cm" not found

```

If you still run into problems after you perform the `helm upgrade` to re-initialize a namespace
that is deleted and recreated, you can restart the operator pod as shown
in the following examples, where the operator itself is running in the
namespace `weblogic-operator-namespace` with the release name, `weblogic-operator`.

* Kill the operator pod, and let Kubernetes restart it.

```
$ kubectl delete pod/weblogic-operator-65b95bc5b5-jw4hh -n weblogic-operator-namespace
```

* Scale the operator deployment to `0` and then back to `1` by changing the value of the `replicas`.

```
$ kubectl scale deployment.apps/weblogic-operator -n weblogic-operator-namespace --replicas=0
```

```
$ kubectl scale deployment.apps/weblogic-operator -n weblogic-operator-namespace --replicas=1
```

Note that restarting the operator pod makes the operator temporarily unavailable for managing its namespaces.
For example, a domain that is created while the operator is restarting will not be started until the
operator pod is fully up again.
