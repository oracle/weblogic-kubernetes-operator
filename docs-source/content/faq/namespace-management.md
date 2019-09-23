---
title: "Managing Domain Namespaces"
date: 2019-09-19
draft: false
---

Each WebLogic operator deployment manages a number of Kubernetes namespaces (for more information about setting domain namespaces, see [Operator Helm configuration values]({{<relref "/userguide/managing-operators/using-the-operator/using-helm.md#operator-helm-configuration-values">}}). A couple of Kubernetes resources
have to be present in a namespace before any WebLogic domain custom resources can be successfully 
deployed into it.
Those Kubernetes resources are either created as part of the installation
of the operator's helm chart, or created by the operator at runtime.

This documentation describes a couple of things that you need to be aware when you manage the namespaces while the WebLogic operator is running. For example,
* [Check the namespaces that an operator manages](#checking-the-namespaces-that-an-operator-manages)
* [Add a namespace for an operator to manage](#adding-a-kubernetes-namespace-to-an-operator)
* [Delete a namespace from an operator's domain namespace list](#deleting-a-kubernetes-namespace-from-an-operator)
* [Delete and recreate a Kubernetes namespace that an operator manages](#recreating-a-previously-deleted-kubernetes-namespace).

See more in [Common Mistakes and Solutions]({{<relref "/userguide/managing-operators/using-the-operator/using-helm.md#common-mistakes-and-solutions">}}).

#### Checking the namespaces that an operator manages
You can find out the list of the namespaces that an operator manages using the `helm get values` command.
For example, the following command shows all values of the operator release `weblogic-operator`, and the `domainNamespaces" list contains `default` and `ns1`. 
.
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
image: oracle/weblogic-kubernetes-operator:2.3.1
imagePullPolicy: IfNotPresent
internalDebugHttpPort: 30999
istioEnabled: false
javaLoggingLevel: INFO
logStashImage: logstash:6.6.0
remoteDebugNodePortEnabled: false
serviceAccount: default
suspendOnDebugStartup: false

```

If you don't know the release name of the operator, you can use `helm ls` to list all releases.

#### Adding a Kubernetes namespace to an operator
If you want a WebLogic operator deployment to manage a namespace, you need to add the namespace to the operator's `domainNamespaces` list. Note that the namespace has to be pre-created, say using the `kubectl create` command.

By adding a namespace to the `domainNamespaces` list, it tells the operator deployment or runtime 
to initialize the necessary Kubernetes resources for the namespace so that the operator is ready to host WebLogic domain resources in that namespace.

When the operator is running and managing the `default` namespace, the following example helm command adds namespace `ns1` to the `domainNamespaces` list, where `weblogic-operator` is the release name of the operator, and `kubernetes/charts/weblogic-operator` is the location of the operator's helm charts.

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
Changes to the `domainNamespaces` list may not be picked up by the operator right away because the operator 
checks the changes to the setting periodically. The operator becomes ready to host domain resources in 
a namespace only after the required `configmap` (namely `weblogic-domain-cm`) is initialized in the namespace.
{{% /notice %}}
 
You can verify if the operator is ready to host domain resources in a namespace by checking the existence of the required `configmap` resource.

```
$ kubetctl get cm -n <namespace>
```

For example, the following example shows that the domain `configmap` resource exists in namespace `ns1`.

```
bash-4.2$ kubectl get cm -n ns1

NAME                 DATA      AGE

weblogic-domain-cm   14        12m
```

####  Deleting a Kubernetes namespace from an operator
When a namespace is not supposed to be managed by an operator any more, it needs to be removed from 
the operator's `domainNamespaces` list so that the corresponding Kubernetes resources that are 
associated with the namespace can be cleaned up. 

While the operator is running and managing the `default` and `ns1` namespaces, the following example helm 
command removes namespace `ns1` from the `domainNamespaces` list, where `weblogic-operator` is the release 
name of the operator, and `kubernetes/charts/weblogic-operator` is the location of the helm charts of the operator.

```
$ helm upgrade \
  --reuse-values \
  --set "domainNamespaces={default}" \
  --wait \
  --force \
  weblogic-operator \
  kubernetes/charts/weblogic-operator

```

#### Recreating a previously deleted Kubernetes namespace

If you need to delete a namespace (and the resources in it) and then recreate it,
remember to remove the namespace from the operator's `domainNamespaces` list 
after you deleted the namespace, and add it back to the `domainNamespaces` list after you recreated the namespace
using the `helm upgrade` commands that have been illustrated above.

{{% notice note %}}
Make sure that you wait a sufficient period of time between deleting and recreating the 
namespace because it takes time for the resources in a namespace to go way after the namespace is deleted.
In addition, as we have mentioned above, changes to the `domainNamespaces` setting is checked by the operator 
periodically, and the operator becomes ready to host domain resources only after the required domain 
`configmap` (namely `weblogic-domain-cm`) is initialized in the namespace.
{{% /notice %}}

If a domain custom resource is created before the namespace is ready, you may see that the introspector job pod 
fails to start with a warning event like the following when you check the description of the introspector pod. 
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

If you run into situations where a new domain that is created after its namespace is deleted and recreated 
does not come up successfully, you can restart the operator pod as shown in the following examples, where 
the operator itself is running in the namespace `weblogic-operator-namespace` with a release name
of `weblogic-operator`.

* Killing the operator pod, and let Kubernetes restart it

```
$ kubectl delete pod/weblogic-operator-65b95bc5b5-jw4hh -n weblogic-operator-namespace
```

* Scaling the operator deployment to `0` and then back to `1` by changing the value of the `replicas`. 

```
$ kubectl scale deployment.apps/weblogic-operator -n weblogic-operator-namespace --replicas=0
```

```
$ kubectl scale deployment.apps/weblogic-operator -n weblogic-operator-namespace --replicas=1
```

{{% notice note %}}
Restarting an operator pod interrupts not just the domain resources in the namespace that is to be re-initialized, 
but all domain resources managed by the operator deployment. Therefore, this can only be used as the last resort.
{{% /notice %}}:
