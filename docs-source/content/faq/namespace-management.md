---
title: "Managing Domain Namespaces"
date: 2019-09-16
draft: false
---

Each WebLogic operator deployment manages a list of Kubernetes namespaces, which is 
configured via the domainNamespaces setting. In each namespace that the operator manages, 
there are a couple of Kubernetes resources that have to be present before any WebLogic 
domain custom resource can be successfully deployed in that namespace. Those resources are
either created as part of the Operator helm chart installation, or created by the operator at runtime.

There are a couple of things that you need to be aware if you need to do the following when the WebLogic  operator is running.
* Add a namespace to the operator's managed namespace list,
* Delete a namespace from the operator's managed namespace list, or 
* Delete and recreate a Kubernetes namespace that the WebLogic operator manages.

#### Adding a Kubernetes namespace for the operator to manage
When you want a WebLogic operator deployment to manage a namespace, you need to add a namespace to the domainNamespaces list of the operator. Note that the namespace has to be already created, say using the "kubectl create" command.

By adding the namespace to the domainNamespaces list, it tells the operator deployment or runtime 
to initialize the necessary Kubernetes resources for the namespace so that the operator is ready to host WebLogic domain resources.

Given that the operator is running and managing the "default" namespace, the following example helm command adds namespace "ns1" to the domainNamespaces list, where "weblogic-operator" is the release name and "kubernetes/charts/weblogic-operator" is the location of the helm charts of the operator.

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
Changes to the domainNamespaces setting is checked by the operator periodically. The operator becomes 
ready to host domain resources only after the required configmap (namely weblogic-domain-cm) is initialized.
{{% /notice %}}
 
You can verify if the operator is ready to host domains resource in a namespace by checking the existence of the required configmap resource.

```
$ kubetctl get cm -n <namespace>
```

For example:

```
bash-4.2$ kubectl get cm -n ns1

NAME                 DATA      AGE

weblogic-domain-cm   14        12m
```

####  Deleting a Kubernetes namespace from the Operator
When a namespace is not supposed to be managed by an operator any more, it needs to be removed from 
the operator's domainNamespaces list so that the corresponding Kubernetes resources that are 
associated with the namespace can be cleaned up. 

Given that the operator is running and managing the "default" and "ns1" namespaces, the following example helm command removes namespace "ns1" from the domainNamespaces list, where "weblogic-operator" is the release name and "kubernetes/charts/weblogic-operator" is the location of the helm charts of the operator.

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

When you need to delete a namespace (and the resources in it) and then recreate the namespace for any reason, 
remember to remove the namespace from the Operator's domainNamespaces list 
after you deleted the namespace, and add it back to the domainNamespaces list after you recreated the namespace.

{{% notice note %}}
Make sure that you wait sufficient time between deleting and recreating the namespace because it takes some time for the resources in a namespace to go way after the namespace is deleted.
In addition, as we mentioned above, changes to the domainNamespaces setting is checked by the operator periodically, and the operator becomes ready to host domain resources only after the required configmap (namely weblogic-domain-cm) is initialized.
{{% /notice %}}

If a domain custom resource is created in a newly recreated namespace before the namespace is ready, you may see that the introspector job pod fails to start. You may see a warning events like the following one when you check the description of the introspector pod. Note that the domain name is "domain1" in the following example output.

```
Events:
  Type     Reason                 Age               From               Message
  ----     ------                 ----              ----               -------
  Normal   Scheduled              1m                default-scheduler  Successfully assigned domain1-introspect-domain-job-57hjq to slc16ffk
  Normal   SuccessfulMountVolume  1m                kubelet, slc16ffk  MountVolume.SetUp succeeded for volume "weblogic-domain-cm-volume"
  Normal   SuccessfulMountVolume  1m                kubelet, slc16ffk  MountVolume.SetUp succeeded for volume "default-token-445xw"
  Warning  FailedMount            20s (x8 over 1m)  kubelet, slc16ffk  MountVolume.SetUp failed for volume "weblogic-credentials-volume" : secrets "domain1-weblogic-credentials" not found

```

If you run into situations where a new domain that is created after its namespace is deleted and recreated does not come up successfully, you can try to restart the operator pod using the following two approaches.

* Killing the operator pod, and let Kubernetes restart it,
* Scaling the Operator deployment to 0 and then back to 1 by changing the value of the "replicas". Here are an example command to do the scaling, where "weblogic-operator" is the release name and the operator itself is running in the namespace "weblogic-operator-namespace".

```
$ kubectl scale deployment.apps/weblogic-operator -n weblogic-operator-namespace --replicas=0
```

```
$ kubectl scale deployment.apps/weblogic-operator -n weblogic-operator-namespace --replicas=1
```

{{% notice note %}}
Restarting an operator pod will interrupt all domain resources managed by the operator deployment, instead of just the domain resources in the namespace that is to be re-initialized. Therefore, this can only be used as a last resort.
{{% /notice %}}
