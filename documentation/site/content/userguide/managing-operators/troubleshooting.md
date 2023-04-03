---
title: "Troubleshooting"
date: 2019-02-23T16:47:21-05:00
weight: 11
description: "General advice for debugging and monitoring the operator."
---

### Contents

- [Troubleshooting a particular domain resource](#troubleshooting-a-particular-domain-resource)
- [Check Helm status](#check-helm-status)
- [Ensure the operator CRD is installed](#ensure-the-operator-crd-is-installed)
- [Check the operator deployment](#check-the-operator-deployment)
- [Check common issues](#check-common-issues)
- [Check for events](#check-for-events)
- [Check the operator log](#check-the-operator-log)
- [Operator ConfigMap](#operator-configmap)
- [Force the operator to restart](#force-the-operator-to-restart)
- [Operator logging level](#operator-logging-level)
- [See also](#see-also)

### Troubleshooting a particular domain resource

After you have an installed and running operator, it is rarely but sometimes necessary to debug the operator itself.
If you are having problems with a particular domain resource, then first see [Domain debugging]({{<relref "/userguide/managing-domains/model-in-image/debugging.md">}}).

### Check Helm status

An operator runtime is installed into a Kubernetes cluster and maintained using a Helm release.
For information about how to list your installed Helm releases and get each release's configuration,
see [Useful Helm operations]({{<relref "/userguide/managing-operators/using-helm#useful-helm-operations">}}).

### Ensure the operator CRD is installed

When you install and run an operator, the installation should have deployed a domain custom resource to the cluster.
To check, verify that the following command lists a CRD with the name `domains.weblogic.oracle`:

```text
$ kubectl get crd
```

The command output should look something like the following:

```text
NAME                                   CREATED AT
domains.weblogic.oracle                2021-09-27T18:46:38Z
```

When the domain CRD is not installed, the operator runtimes will not be able to monitor domains, and commands like `kubectl get domains` will fail.

Typically, the operator automatically installs the CRD for the Domain type when the operator first starts. However,
if the domain CRD was not installed, for example, if the operator lacked sufficient permission to install it, then
refer to the operator [Prepare for installation]({{< relref "/userguide/managing-operators/preparation#how-to-manually-install-the-domain-resource-custom-resource-definition-crd" >}}) documentation.

### Check the operator deployment

Verify that the operator's deployment is deployed and running by listing all deployments with the `weblogic.operatorName` label.

```text
$ kubectl get deployment --all-namespaces=true -l weblogic.operatorName
```

Check the operator deployment's detailed status:

```text
$ kubectl -n OP_NAMESPACE get deployments/weblogic-operator -o yaml
```

And/or:

```text
$ kubectl -n OP_NAMESPACE describe deployments/weblogic-operator
```

Each operator deployment will have a corresponding Kubernetes pod
with a name that has a prefix that matches the deployment name,
plus a unique suffix that changes every time the deployment restarts.

To find operator pods and check their high-level status:

```text
$ kubectl get pods --all-namespaces=true -l weblogic.operatorName
```

To check the details for a given pod:

```text
$ kubectl -n OP_NAMESPACE get pod weblogic-operator-UNIQUESUFFIX -o yaml
$ kubectl -n OP_NAMESPACE describe pod weblogic-operator-UNIQUESUFFIX
```
A pod `describe` usefully includes any events that might be associated with the operator.

### Check common issues

- See [Common mistakes and solutions]({{< relref "/userguide/managing-operators/common-mistakes.md" >}}).
- Check the [FAQs]({{<relref "/faq/_index.md">}}).

### Check for events

To check for Kubernetes events that may have been logged to the operator's namespace:

```text
$ kubectl -n OP_NAMESPACE get events --sort-by='.lastTimestamp'
```

### Check the operator log

To check the operator deployment's log (especially look for `SEVERE` and `ERROR` level messages):

```text
$ kubectl logs -n YOUR_OPERATOR_NS -c weblogic-operator deployments/weblogic-operator
```

### Operator ConfigMap

An operator's settings are automatically maintained by Helm in a Kubernetes ConfigMap named `weblogic-operator-cm` in the same namespace as the operator. To view the contents of this ConfigMap, call `kubectl -n sample-weblogic-operator-ns get cm weblogic-operator-cm -o yaml`.

### Force the operator to restart

{{% notice note %}}
An operator is designed to robustly handle thousands of domains even in the event of failures,
so it should not normally be necessary to force an operator to restart, even after an upgrade.
Accordingly, if you encounter a problem that you think requires an operator restart to resolve,
then please make sure that the operator development team is aware of the issue
(see [Get Help]({{< relref "/userguide/introduction/get-help.md" >}})).
{{% /notice %}}

When you restart an operator:

* The operator is temporarily unavailable for managing its namespaces.
  * For example,  a domain that is created while the operator
    is restarting will not be started until the
    operator pod is fully up again.
* This will not shut down your current domains or affect their resources.
* The restarted operator will rediscover existing domains and manage them.

There are several approaches for restarting an operator:

* Most simply, use the `helm upgrade` command: `helm upgrade <release-name> --reuse-values --recreate-pods`

   ```text
   $ helm upgrade weblogic-operator --reuse-values --recreate-pods
   ```

* Delete the operator pod, and let Kubernetes restart it.

  a. First, find the operator pod you wish to delete:

     ```text
     $ kubectl get pods --all-namespaces=true -l weblogic.operatorName
     ```

  b. Second, delete the pod. For example:

     ```text
     $ kubectl delete pod/weblogic-operator-65b95bc5b5-jw4hh -n OP_NAMESPACE
     ```

* Scale the operator deployment to `0`, and then back to `1`, by changing the value of the `replicas`.

  a. First, find the namespace of the operator deployment you wish to restart:

     ```text
     $ kubectl get deployment --all-namespaces=true -l weblogic.operatorName
     ```

  b. Second, scale the deployment down to zero replicas:

     ```text
     $ kubectl scale deployment.apps/weblogic-operator -n OP_NAMESPACE --replicas=0
     ```
  c. Finally, scale the deployment back up to one replica:

     ```text
     $ kubectl scale deployment.apps/weblogic-operator -n OP_NAMESPACE --replicas=1

     ```

### Operator logging level

{{% notice warning %}}
It should rarely be necessary to change the operator to use a finer-grained logging level,
but, in rare situations, the operator support team may direct you to do so.
If you change the logging level, then be aware that FINE or finer-grained logging levels
can be extremely verbose and quickly use up gigabytes of disk space in the span of hours, or,
at the finest levels, during heavy activity, in even minutes.
_Consequently, the logging level should only be increased for as long as is needed
to help get debugging data for a particular problem._
{{% /notice %}}

To set the operator `javaLoggingLevel` to `FINE` (default is `INFO`)
assuming the operator Helm release is named `sample-weblogic-operator`
its namespace is `sample-weblogic-operator-ns`,
and you have locally downloaded the operator src to `/tmp/weblogic-kubernetes-operator`:

```
$ cd /tmp/weblogic-kubernetes-operator
```

```
$ helm upgrade \
  sample-weblogic-operator \
  weblogic-operator/weblogic-operator \
  --namespace sample-weblogic-operator-ns \
  --reuse-values \
  --set "javaLoggingLevel=FINE" \
  --wait
```

To set the operator `javaLoggingLevel` back to `INFO`:

```
$ helm upgrade \
  sample-weblogic-operator \
  weblogic-operator/weblogic-operator \
  --namespace sample-weblogic-operator-ns \
  --reuse-values \
  --set "javaLoggingLevel=INFO" \
  --wait
```

For more information, see the
[javaLoggingLevel]({{< relref "/userguide/managing-operators/using-helm#javalogginglevel" >}}) documentation.

### See also

If you have set up either of the following, then these documents may be helpful in debugging:
- [Operator REST HTTPS interface]({{<relref "/userguide/managing-operators/the-rest-api#configure-the-operators-external-rest-https-interface">}})
- [Elastic Stack (Elasticsearch, Logstash, and Kibana)]({{<relref "/samples/elastic-stack/operator/_index.md#elastic-stack-per-operator-configuration">}}) integration
