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
- [Check the conversion webhook deployment](#check-the-conversion-webhook-deployment)
- [Check common operator issues](#check-common-operator-issues)
- [Check for operator events](#check-for-operator-events)
- [Check for conversion webhook events](#check-for-conversion-webhook-events)
- [Check the operator log](#check-the-operator-log)
- [Check the conversion webhook log](#check-the-conversion-webhook-log)
- [Operator ConfigMap](#operator-configmap)
- [Force the operator to restart](#force-the-operator-to-restart)
- [Operator and conversion webhook logging level](#operator-and-conversion-webhook-logging-level)
- [Troubleshooting the conversion webhook](#troubleshooting-the-conversion-webhook)
  - [Ensure the conversion webhook is deployed and running](#ensure-the-conversion-webhook-is-deployed-and-running)
  - [Check for runtime errors during conversion](#check-for-runtime-errors-during-conversion)
- [See also](#see-also)

### Troubleshooting a particular domain resource

After you have an installed and running operator, it is rarely but sometimes necessary to debug the operator itself.
If you are having problems with a particular domain resource, then first see [Domain debugging]({{<relref "/managing-domains/debugging.md">}}).

### Check Helm status

An operator runtime is installed into a Kubernetes cluster and maintained using a Helm release.
For information about how to list your installed Helm releases and get each release's configuration,
see [Useful Helm operations]({{<relref "/managing-operators/using-helm#useful-helm-operations">}}).

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
refer to the operator [Prepare for installation]({{< relref "/managing-operators/preparation#how-to-manually-install-the-domain-resource-custom-resource-definition-crd" >}}) documentation.

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

### Check the conversion webhook deployment

Verify that the conversion webhook is deployed and running by listing all deployments with the `weblogic.webhookName` label.

```text
$ kubectl get deployment --all-namespaces=true -l weblogic.webhookName
```

Check the conversion webhook deployment's detailed status:

```text
$ kubectl -n WH_NAMESPACE get deployments/weblogic-operator-webhook -o yaml
```

And/or:

```text
$ kubectl -n WH_NAMESPACE describe deployments/weblogic-operator-webhook
```

Each conversion webhook deployment will have a corresponding Kubernetes pod
with a name that has a prefix that matches the deployment name,
plus a unique suffix that changes every time the deployment restarts.

To find conversion webhook pods and check their high-level status:

```text
$ kubectl get pods --all-namespaces=true -l weblogic.webhookName
```

To check the details for a given pod:

```text
$ kubectl -n WH_NAMESPACE get pod weblogic-operator-webhook-UNIQUESUFFIX -o yaml
$ kubectl -n WH_NAMESPACE describe pod weblogic-operator-webhook-UNIQUESUFFIX
```
A pod `describe` usefully includes any events that might be associated with the conversion webhook.
For information about installing and uninstalling the webhook, see
[WebLogic Domain resource conversion webhook]({{< relref "/managing-operators/conversion-webhook.md" >}}).

### Check common operator issues

- See [Common mistakes and solutions]({{< relref "/managing-operators/common-mistakes.md" >}}).
- Check the [FAQs]({{<relref "/faq/_index.md">}}).

### Check for operator events

To check for Kubernetes events that may have been logged to the operator's namespace:

```text
$ kubectl -n OP_NAMESPACE get events --sort-by='.lastTimestamp'
```

### Check for conversion webhook events

To check for Kubernetes events that may have been logged to the conversion webhook's namespace:

```text
$ kubectl -n WH_NAMESPACE get events --sort-by='.lastTimestamp'
```
### Check the operator log

Look for `SEVERE` and `ERROR` level messages in your operator logs. For example:


- Find your operator.
  ```shell
  $ kubectl get deployment --all-namespaces=true -l weblogic.operatorName
  ```
  ```
  NAMESPACE                     NAME                DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
  sample-weblogic-operator-ns   weblogic-operator   1         1         1            1           20h

  ```
- Use `grep` on the operator log; look for `SEVERE` and `WARNING` level messages.

  ```shell
  $ kubectl logs deployment/weblogic-operator -n sample-weblogic-operator-ns  \
    | egrep -e "level...(SEVERE|WARNING)"
  ```
  ```json
  {"timestamp":"03-18-2020T20:42:21.702+0000","thread":11,"fiber":"","domainUID":"","level":"WARNING","class":"oracle.kubernetes.operator.helpers.HealthCheckHelper","method":"createAndValidateKubernetesVersion","timeInMillis":1584564141702,"message":"Kubernetes minimum version check failed. Supported versions are 1.13.5+,1.14.8+,1.15.7+, but found version v1.12.3","exception":"","code":"","headers":{},"body":""}
  ```

- You can filter out operator log messages specific to your `domainUID` by piping the previous logs command through `grep "domainUID...MY_DOMAINUID"`. For example, assuming your operator is running in namespace `sample-weblogic-operator-ns` and your domain UID is `sample-domain1`:

  ```shell
  $ kubectl logs deployment/weblogic-operator -n sample-weblogic-operator-ns  \
    | egrep -e "level...(SEVERE|WARNING)" \
    | grep "domainUID...sample-domain1"
  ```

### Check the conversion webhook log

To check the conversion webhook deployment's log (especially look for `SEVERE` and `ERROR` level messages):

```text
$ kubectl logs -n YOUR_CONVERSION_WEBHOOK_NS -c weblogic-operator-webhook deployments/weblogic-operator-webhook
```

### Operator ConfigMap

An operator's settings are automatically maintained by Helm in a Kubernetes ConfigMap named `weblogic-operator-cm` in the same namespace as the operator. To view the contents of this ConfigMap, call `kubectl -n sample-weblogic-operator-ns get cm weblogic-operator-cm -o yaml`.

### Force the operator to restart

{{% notice note %}}
An operator is designed to robustly handle thousands of domains even in the event of failures,
so it should not normally be necessary to force an operator to restart, even after an upgrade.
Accordingly, if you encounter a problem that you think requires an operator restart to resolve,
then please make sure that the operator development team is aware of the issue
(see [Get Help]({{< relref "/introduction/get-help.md" >}})).
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

### Operator and conversion webhook logging level

{{% notice warning %}}
It should rarely be necessary to change the operator and conversion webhook to use a finer-grained logging level,
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
[javaLoggingLevel]({{< relref "/managing-operators/using-helm#javalogginglevel" >}}) documentation.

### Troubleshooting the conversion webhook
The following are some common mistakes and solutions for the conversion webhook.

#### Ensure the conversion webhook is deployed and running
Verify that the conversion webhook is deployed and running by following the steps in [check the conversion webhook deployment](#check-the-conversion-webhook-deployment).
If it is not deployed, then you will see the following `conversion webhook not found`
error when creating a Domain with `weblogic.oracle/v8` schema Domain resource.

```
Error from server: error when creating "k8s-domain.yaml": conversion webhook for weblogic.oracle/v9, Kind=Domain failed: Post "https://weblogic-operator-webhook-svc.sample-weblogic-operator-ns.svc:8084/webhook?timeout=30s": service "weblogic-operator-webhook-svc" not found
```

The conversion webhook can be deployed standalone or as part of an operator installation. Note that if the conversion webhook was installed as part
of an operator installation, then it is implicitly removed by default when the operator is uninstalled.  If the conversion webhook is not deployed or running,
then reinstall it by following the steps in
[Installing the conversion webhook]({{<relref "/managing-operators/conversion-webhook#install-the-conversion-webhook" >}}).

If the conversion webhook Deployment is deployed but is not in the ready status, then you will see a `connection refused` error when creating a Domain using the `weblogic.oracle/v8` schema Domain resource.

The POST URL in the error message has the name of the conversion webhook service and the namespace. For example, if the POST URL is `https://weblogic-operator-webhook-svc.sample-weblogic-operator-ns.svc:8084/webhook?timeout=30s`, then the service name is `weblogic-operator-webhook-svc` and the namespace is `sample-weblogic-operator-ns`. In this case, run the following commands to ensure that the Deployment is running and the webhook service exists in the `sample-weblogic-operator-ns` namespace.

```
$  kubectl get deployment weblogic-operator-webhook -n sample-weblogic-operator-ns
NAME                        READY   UP-TO-DATE   AVAILABLE   AGE
weblogic-operator-webhook   1/1     1            1           87m

$  kubectl get service weblogic-operator-webhook-svc -n sample-weblogic-operator-ns
NAME                            TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)    AGE
weblogic-operator-webhook-svc   ClusterIP   10.106.89.198   <none>        8084/TCP   88m
```
If the conversion webhook Deployment status is not ready, then [check the conversion webhook log]({{<relref "/managing-operators/troubleshooting#check-the-conversion-webhook-log">}}) and the [conversion webhook events]({{<relref "/managing-operators/troubleshooting#check-for-conversion-webhook-events">}}) in the conversion webhook namespace. If the conversion webhook service doesn't exist, make sure that the conversion webhook was installed correctly and reinstall the conversion webhook to see if it resolves the issue.

#### Check for runtime errors during conversion
If you see a `WebLogic Domain custom resource conversion webhook failed` error when creating a Domain with a `weblogic.oracle/v8` schema domain resource, then [check the conversion webhook runtime Pod logs]({{<relref "/managing-operators/troubleshooting#check-the-conversion-webhook-log">}}) and [check for the generated events]({{<relref "/managing-operators/troubleshooting#check-for-conversion-webhook-events">}}) in the conversion webhook namespace. Assuming that the conversion webhook is deployed in the `sample-weblogic-operator-ns` namespace, run the following commands to check for logs and events.

```
$ kubectl logs -n sample-weblogic-operator-ns -c weblogic-operator-webhook deployments/weblogic-operator-webhook

$ kubectl get events -n sample-weblogic-operator-ns
```

### See also

If you have set up either of the following, then these documents may be helpful in debugging:
- [Operator REST HTTPS interface]({{<relref "/managing-operators/the-rest-api#configure-the-operators-external-rest-https-interface">}})
- [Elastic Stack (Elasticsearch, Logstash, and Kibana)]({{<relref "/samples/elastic-stack/operator/_index.md#elastic-stack-per-operator-configuration">}}) integration
