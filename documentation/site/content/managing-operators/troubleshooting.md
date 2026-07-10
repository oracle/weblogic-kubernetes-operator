---
title: "Troubleshooting"
date: 2019-02-23T16:47:21-05:00
weight: 12
description: "General advice for debugging and monitoring the operator."
---

{{< table_of_contents >}}

### Troubleshooting a particular domain resource

After you have an installed and running operator, it is rarely but sometimes necessary to debug the operator itself.
If you are having problems with a particular domain resource, then first see [Domain debugging]({{% relref "/managing-domains/debugging.md" %}}).

### Check Helm status

An operator runtime is installed into a Kubernetes cluster and maintained using a Helm release.
For information about how to list your installed Helm releases and get each release's configuration,
see [Useful Helm operations]({{% relref "/managing-operators/using-helm#useful-helm-operations" %}}).

### Ensure the operator CRDs are installed

When you install and run an operator, the installation should have deployed a domain custom resource
and a cluster custom resource to the cluster.
To check, verify that the following command lists a CRD with the name `domains.weblogic.oracle`
and another CRD with the name `clusters.weblogic.oracle`:

```text
$ kubectl get crd
```

The command output should look something like the following:

```text
NAME                                    CREATED AT
clusters.weblogic.oracle                2022-10-15T03:45:27Z
domains.weblogic.oracle                 2022-10-15T03:45:27Z
```

When a domain or cluster CRD is not installed, the operator runtimes will not be able to monitor domains or clusters, and commands like `kubectl get domains` will fail.

Typically, the operator automatically installs each CRD when the operator first starts. However,
if a CRD was not installed, for example, if the operator lacked sufficient permission to install it, then
refer to the operator [Prepare for installation]({{% relref "/managing-operators/preparation#how-to-manually-install-the-domain-resource-custom-resource-definition-crd" %}}) documentation.

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

All operators in a Kubernetes cluster share a single conversion webhook deployment.
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
[WebLogic Domain resource conversion webhook]({{% relref "/managing-operators/conversion-webhook.md" %}}).

### Check common operator issues

- See [Common mistakes and solutions]({{% relref "/managing-operators/common-mistakes.md" %}}).
- Check the [FAQs]({{% relref "/faq/_index.md" %}}).

### Helm 4 server-side apply field ownership conflicts

Helm 4 uses Kubernetes Server-Side Apply by default when installing a new Helm
release. For an upgrade or rollback, Helm 4 uses the previous release's apply
method by default. When installing or upgrading the WebLogic Kubernetes Operator
Helm chart with Server-Side Apply, the Kubernetes API server may reject a chart
resource if another field manager owns one or more of the same resource fields.
This can occur, for example, when a previous installation left resources behind
or another tool modifies resources that the operator Helm release manages.

The Helm command may fail with an error similar to:

```text
Error: INSTALLATION FAILED: conflict occurred while applying object NAMESPACE/RESOURCE_NAME API_VERSION, Kind=RESOURCE_KIND:
Apply failed with 1 conflict: conflict with "FIELD_MANAGER" using API_VERSION: FIELD_PATH
```

For an upgrade, the error begins with `UPGRADE FAILED` instead of
`INSTALLATION FAILED`.

The conflicting manager name may differ, for example, `before-first-apply`,
`kubectl-client-side-apply`, or another tool that uses Kubernetes apply semantics.
This error is a consequence of Helm 4 Server-Side Apply and Kubernetes field
ownership; it does not indicate a change to the operator Helm chart. The
Kubernetes API server rejects the conflicting operation before the Helm
installation or upgrade completes.

Kubernetes Server-Side Apply tracks ownership of individual resource fields in
`metadata.managedFields`. If Helm 4 applies a field that is already owned by
another field manager, Kubernetes rejects the update unless conflicts are forced.
For more information, see
[Helm 4 Overview](https://helm.sh/docs/overview/) and
[helm install](https://helm.sh/docs/helm/helm_install/) and
[helm upgrade](https://helm.sh/docs/helm/helm_upgrade/).

Helm 4 selects the apply method as follows:

| Workflow | Default apply method | Guidance |
|-|-|-|
| Install a new release with Helm 4 | Server-side apply | If a field ownership conflict occurs, inspect the conflicting resource and use `--server-side=false` if you want Helm to use client-side apply. |
| Upgrade with Helm 4 a release that was installed by Helm 3 | Client-side apply | Helm 4 detects that the earlier release used client-side apply. Normally no apply-method option is required; you can specify `--server-side=false` explicitly. |
| Upgrade with Helm 4 a release that was installed by Helm 4 | The previous release's apply method | If the release used Server-Side Apply and a conflict occurs, inspect the conflicting resource before choosing whether to use client-side apply or transfer field ownership. |

To inspect the field managers for the resource named in the error, use the
following command. Omit `--namespace RESOURCE_NAMESPACE` for a cluster-scoped
resource.

```shell
$ kubectl get RESOURCE_TYPE RESOURCE_NAME \
  --namespace RESOURCE_NAMESPACE \
  --show-managed-fields \
  -o yaml
```

To use client-side apply, as Helm 3 does, disable Server-Side Apply when
installing the operator:

```shell
$ helm install OPERATOR_RELEASE_NAME weblogic-operator/weblogic-operator \
  --namespace OPERATOR_NAMESPACE \
  --server-side=false \
  --wait
```

Use the same operator Helm configuration values and other options that your
installation otherwise requires.

If you need to explicitly use client-side apply for an operator upgrade,
including an upgrade of a release installed by Helm 3, add
`--server-side=false` to the normal
[operator upgrade command]({{% relref "/managing-operators/installation#upgrade-the-operator" %}}).

Do not use `--force-conflicts` without first confirming that the operator Helm
release should take ownership of every conflicting field. Forcing a conflict can
overwrite a value managed by another tool and transfer its field ownership to
Helm. If you intentionally switch an operator release to Server-Side Apply and
want Helm to take ownership of the conflicting fields, add `--server-side=true`
and `--force-conflicts` to the normal operator upgrade command. For a fresh
install, some Helm 4 installation paths may not honor `--force-conflicts`; see
[Helm issue 32069](https://github.com/helm/helm/issues/32069).

If disabling Server-Side Apply reveals a separate error that an existing
resource cannot be imported into the release, identify which previous Helm
release or workflow owns the resource before changing or deleting it. In a
cluster with multiple operator installations, do not remove shared resources
that another operator still requires.

### Check for operator events

To check for Kubernetes events that may have been logged to the operator's namespace:

```text
$ kubectl -n OP_NAMESPACE get events --sort-by='.lastTimestamp'
```

### Check the operator metrics endpoint

The operator exposes a Prometheus-compatible metrics endpoint on port `8083` at path `/metrics`.
The operator deployment is annotated for scraping using `prometheus.io/scrape: 'true'` and
`prometheus.io/port: '8083'`.

To check the metrics endpoint from the operator namespace:

```text
$ kubectl -n OP_NAMESPACE port-forward deployment/weblogic-operator 8083:8083
```

Then, in a different terminal:

```text
$ curl http://localhost:8083/metrics
```

The operator metrics output includes standard JVM and process metrics from the Prometheus Java client.
It also includes operator-specific metrics that identify the namespaces and domains that are actively managed
by the operator at the time of the scrape:

- `wko_managed_namespace_count`
  The number of namespaces actively managed by the operator.
- `wko_managed_namespace_info{namespace="..."}` 
  A presence metric with value `1` for each namespace actively managed by the operator.
- `wko_managed_domain_count`
  The total number of domains actively managed by the operator across all active namespaces.
- `wko_managed_domain_info{namespace="...",domain_uid="..."}`
  A presence metric with value `1` for each domain actively managed by the operator.

These metrics reflect the operator's current runtime state. For example, if the operator uses
the `RegExp` namespace selection strategy, then the metrics report the namespaces and domains that are
currently being managed after the regular expression has been resolved, not the configured regular expression itself.

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

### Domain on PV hostPath PersistentVolume denied after upgrade

Beginning with operator version 4.3.9, operator-created PersistentVolumes that specify a `hostPath`
source under `domain.spec.configuration.initializeDomainOnPV.persistentVolume` require the operator
Helm chart value `domainOnPV.localDeveloperMode=true`. This mode is intended only for local development
clusters and must not be used in production or shared multi-tenant clusters. If multiple Domain
resources use the same `hostPath` and the same domain home location, their domain creation jobs can
race and overwrite the same files.

If you upgrade the operator and then apply a Domain on PV resource that uses an operator-created
`hostPath` PersistentVolume, the validating webhook may deny the request with an error similar to
the following:

```text
admission webhook "weblogic.validating.webhook" denied the request:
Persistent volume sample-domain1-pv-rwm1 is invalid, the 'spec.hostPath' source is not allowed in
'spec.configuration.initializeDomainOnPV.persistentVolume' unless the operator Helm chart value
'domainOnPV.localDeveloperMode' is enabled.
```

To enable this setting for a local development cluster, update the Helm release:

```text
$ helm upgrade OPERATOR_RELEASE_NAME weblogic-operator/weblogic-operator \
  --namespace OP_NAMESPACE \
  --reuse-values \
  --set domainOnPV.localDeveloperMode=true \
  --wait
```

The Helm upgrade updates the operator and webhook ConfigMaps, but if this is the only change, Kubernetes
may not restart the already running operator or validating webhook Pods because their Deployment pod
templates did not change. Restart the operator and webhook deployments so that they use the updated
ConfigMap values:

```text
$ kubectl -n OP_NAMESPACE rollout restart deployment/weblogic-operator
$ kubectl -n OP_NAMESPACE rollout status deployment/weblogic-operator
$ kubectl -n OP_NAMESPACE rollout restart deployment/weblogic-operator-webhook
$ kubectl -n OP_NAMESPACE rollout status deployment/weblogic-operator-webhook
```

You can verify the live setting with:

```text
$ kubectl -n OP_NAMESPACE get cm weblogic-operator-cm \
  -o jsonpath='{.data.domainOnPVLocalDeveloperMode}{"\n"}'
$ kubectl -n OP_NAMESPACE get cm weblogic-webhook-cm \
  -o jsonpath='{.data.domainOnPVLocalDeveloperMode}{"\n"}'
```

### Force the operator to restart

{{% notice note %}}
An operator is designed to robustly handle thousands of domains even in the event of failures,
so it should not normally be necessary to force an operator to restart, even after an upgrade.
Accordingly, if you encounter a problem that you think requires an operator restart to resolve,
then please make sure that the operator development team is aware of the issue
(see [Get Help]({{% relref "/introduction/get-help.md" %}})).
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
[javaLoggingLevel]({{% relref "/managing-operators/using-helm#javalogginglevel" %}}) documentation.

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
[Installing the conversion webhook]({{% relref "/managing-operators/conversion-webhook#install-the-conversion-webhook" %}}).

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
If the conversion webhook Deployment status is not ready, then [check the conversion webhook log]({{% relref "/managing-operators/troubleshooting#check-the-conversion-webhook-log" %}}) and the [conversion webhook events]({{% relref "/managing-operators/troubleshooting#check-for-conversion-webhook-events" %}}) in the conversion webhook namespace. If the conversion webhook service doesn't exist, make sure that the conversion webhook was installed correctly and reinstall the conversion webhook to see if it resolves the issue.

#### X509: Certificate signed by unknown authority error from the webhook
The following `x509: certificate signed by unknown authority` error from the conversion webhook can be due to the incorrect proxy configuration of the Kubernetes API server in your environment or incorrect self-signed certificate in the conversion webhook configuration in the Domain CRD.

```
Error from server (InternalError): error when creating "./weblogic-domains/sample-domain1/domain.yaml": Internal error occurred: conversion webhook for weblogic.oracle/v8, Kind=Domain failed: Post "https://weblogic-operator-webhook-svc.sample-weblogic-operator-ns.svc:8084/webhook?timeout=30s": x509: certificate signed by unknown authority
```
- If your environment uses a PROXY server, then ensure that the NO_PROXY settings of the Kubernetes API server include the `.svc` value. The Kubernetes API server makes a REST request to the conversion webhook REST endpoint using the host name `weblogic-operator-webhook-svc.${NAMESPACE}.svc` in the POST URL. If the REST request is routed through a PROXY server, then you will see an "x509: certificate signed by unknown authority" error. Because this REST request is internal to your Kubernetes cluster, ensure that it doesn't get routed through a PROXY server by adding `.svc` to the `NO_PROXY` settings.
- If, for some reason your Domain CRD conversion webhook configuration has an incorrect self-signed certificate, then you can patch the Domain CRD to remove the existing conversion webhook configuration. The operator will re-create the conversion webhook configuration with the correct self-signed certificate in the Domain CRD. Use the following `patch` command to remove the conversion webhook configuration in the Domain CRD to see if it resolves the error.

    ```
    kubectl patch crd domains.weblogic.oracle --type=merge --patch '{"spec": {"conversion": {"strategy": "None", "webhook": null}}}'
    ```

#### Webhook errors in older operator versions
When you install operator version 4.x or upgrade to operator 4.x, a conversion webhook configuration is added to your Domain CRD. If you downgrade or switch back to the operator version 3.x, the conversion webhook configuration is not removed from the CRD. This is to support environments with multiple operator installations potentially with different versions. For environments having a single operator installation, use the following `patch` command to manually remove the conversion webhook configuration from Domain CRD.

```
kubectl patch crd domains.weblogic.oracle --type=merge --patch '{"spec": {"conversion": {"strategy": "None", "webhook": null}}}'
```

#### Webhook errors in operator dedicated Mode
If the operator is running in the `Dedicated` mode, the operator's service account will not have the permission to read or update the CRD. If you need to convert the domain resources with `weblogic.oracle/v8` schema to `weblogic.oracle/v9` schema using the conversion webhook in `Dedicated` mode, then you can manually add the conversion webhook configuration to the Domain CRD. Use the following `patch` command to add the conversion webhook configuration to the Domain CRD.

**NOTE**: Substitute `YOUR_OPERATOR_NS` in the below command with the namespace where the operator is installed.

```
export OPERATOR_NS=YOUR_OPERATOR_NS
```
```
kubectl patch crd domains.weblogic.oracle --type=merge --patch '{"spec": {"conversion": {"strategy": "Webhook", "webhook": {"clientConfig": { "caBundle": "'$(kubectl get secret weblogic-webhook-secrets -n ${OPERATOR_NS} -o=jsonpath="{.data.webhookCert}"| base64 --decode)'", "service": {"name": "weblogic-operator-webhook-svc", "namespace": "'${OPERATOR_NS}'", "path": "/webhook", "port": 8084}}, "conversionReviewVersions": ["v1"]}}}}'
```

#### Check for runtime errors during conversion
If you see a `WebLogic Domain custom resource conversion webhook failed` error when creating a Domain with a `weblogic.oracle/v8` schema domain resource, then [check the conversion webhook runtime Pod logs]({{% relref "/managing-operators/troubleshooting#check-the-conversion-webhook-log" %}}) and [check for the generated events]({{% relref "/managing-operators/troubleshooting#check-for-conversion-webhook-events" %}}) in the conversion webhook namespace. Assuming that the conversion webhook is deployed in the `sample-weblogic-operator-ns` namespace, run the following commands to check for logs and events.

```
$ kubectl logs -n sample-weblogic-operator-ns -c weblogic-operator-webhook deployments/weblogic-operator-webhook

$ kubectl get events -n sample-weblogic-operator-ns
```

### See also

If you have set up either of the following, then these documents may be helpful in debugging:
- [Operator REST HTTPS interface]({{% relref "/managing-operators/the-rest-api#configure-the-operators-external-rest-https-interface" %}})
- [Elastic Stack (Elasticsearch, Logstash, and Kibana)]({{% relref "/samples/elastic-stack/operator/_index.md#elastic-stack-per-operator-configuration" %}}) integration
