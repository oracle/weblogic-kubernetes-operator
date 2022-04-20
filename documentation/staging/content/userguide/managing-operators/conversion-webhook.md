---
title: "WebLogic Domain resource conversion webhook"
date: 2019-02-23T16:47:21-05:00
draft: false
weight: 12
description: "Conversion Webhook for upgrading the domain resource schema."
---

### Contents

 - [Introduction](#introduction)
 - [Conversion webhook components](#conversion-webhook-components)
 - [Install the conversion webhook](#install-the-conversion-webhook)
 - [Uninstall the conversion webhook](#uninstall-the-conversion-webhook)
 - [Troubleshooting the conversion webhook](#troubleshooting-the-conversion-webhook)
   - [Connection refused error](#connection-refused-error)
   - [Certificate signed by unknown authority error](#certificate-signed-by-unknown-authority-error)
   - [Conversion failure due to runtime errors](#conversion-failure-due-to-runtime-errors)

#### Introduction
The WebLogic Domain resource conversion webhook automatically and transparently upgrades the domain resource from the 3.x schema to the 4.0 schema by using the [Webhook Conversion](https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definition-versioning/#webhook-conversion) strategy.  The Domain CustomResourceDefinition in Operator version 4.0 has changed significantly from previous Operator releases. The default Kubernetes conversion strategy (None) cannot resolve these changes automatically. For example, we have enhanced the [Auxiliary images]({{<relref "userguide/managing-domains/model-in-image/auxiliary-images">}}) feature in Operator version 4.0, and its configuration has changed as a result. With the `Webhook` conversion strategy, the Kubernetes API server invokes an external REST service which pulls out the configuration of the auxiliary images defined in the WKO 3.x domain resource and converts it to the equivalent configuration in the WKO 4.0.

#### Conversion webhook components
The table below lists different components of the WebLogic Domain resource conversion webhook and their purpose.
| Component Type | Component Name | Purpose |
| --- | --- | -- |
| Deployment | `webLogic-operator-webhook` | Manages the runtime Pod of the WebLogic Domain resource conversion webhook. |
| Service | `webLogic-operator-webhook-svc` | The Kubernetes API server uses this service to reach the conversion webhook runtime defined in the WebLogic Domain CRD. |
| Secret | `webLogic-webhook-secrets` | Contains the CA certificate and key used to secure the communication between the Kubernetes API server and the REST endpoint of WebLogic domain resource conversion webhook. |
| The `spec.conversion` stanza in the Domain CRD | | Used by the Kubernetes API server to call an external service when a Domain conversion is required. |

**Notes:**
- The conversion webhook Deployment `webLogic-operator-webhook` uses the same image as the Operator image, and you should not change this image. You can scale the Deployment by increasing the number of replicas for high availability.
 
- The conversion webhook runtime sets the conversion strategy to `Webhook` at the time of Domain CRD creation. It also adds the webhook client configuration details such as service name, namespace, path, port, and the self-signed CA certificate used for authentication. 

- If the conversion strategy in the existing Domain CRD is `None`, it updates the existing CRD with the `Webhook` conversion strategy and conversion definition.

Here is an example of a webhook configuration in the Domain CRD updated by the WebLogic Domain conversion webhook runtime. This webhook calls `weblogic-operator-webhook-svc` service on port `8084` at the subpath `/webhook`, and verifies the TLS connection against the ServerName `weblogic-operator-webhook-svc.sample-weblogic-operator-ns.svc` using a self-signed CA bundle.
```
spec:
  conversion:
    strategy: Webhook
    webhook:
      clientConfig:
        caBundle: "Ci0tLS0tQk...<base64-encoded PEM bundle>...tLS0K"
        service:
          name: weblogic-operator-webhook-svc
          namespace: sample-weblogic-operator-ns
          path: /webhook
          port: 8084
      conversionReviewVersions:
      - v1
```

#### Install the conversion webhook
Beginning with  Operator version 4.0, when you [install the operator]({{<relref "/userguide/managing-operators/installation#install-the-operator">}}) using the `helm install` command with the operator Helm chart, it also configures a deployment and supporting resources for the conversion webhook and deploys the conversion webhook in the specified namespace. However, the CRD conversion strategy can point to only a single conversion webhook, and the Kubernetes cluster can have only one active conversion webhook. Therefore if the conversion webhook deployment already exists in some other namespace, then a new conversion webhook deployment is not created.

{{% notice note %}}
The cluster-level permissions for listing and reading the Namespaces and Deployments are needed to search all the namespaces for existing conversion webhook deployment.
{{% /notice %}}


If you want to **only** install the conversion webhook (and not the Operator) in the given namespace, set the custom value `webhookOnly` to `true` in the `helm install` command. After meeting the [prerequisite requirements]({{<relref "/userguide/managing-operators/preparation.md">}}), call:
```
$ helm install sample-weblogic-conversion-webhook \
  weblogic-operator/weblogic-operator \
  --namespace sample-weblogic-conversion-webhook-ns \
  --set "webhookOnly=true" \
  --wait
```
The above command creates a Helm release named `sample-weblogic-conversion-webhook` in the `sample-weblogic-conversion-webhook-ns` namespace, configuring a deployment and supporting resources for the conversion webhook and deploying the conversion webhook.

To check if the conversion webhook is deployed and running,
see [Troubleshooting]({{<relref "/userguide/managing-operators/troubleshooting#check-the-conversion-webhook-deployment">}}).

To prevent the removal of conversion webhook during the `helm uninstall` command, set the custom value `preserveWebhook` to `true` during installation using the `helm install` command. 

The following table describes the behavior of different `Helm` commands (with the Operator Helm chart) and the custom values:

| Helm command with the operator Helm chart | Helm chart custom values | Behavior |
| --- | --- | --- |
| Helm install | Default | Operator and conversion webhook installed. |
| Helm install | Default and existing `weblogic-operator-webhook` deployment | Operator installed. |
| Helm install | `webhookOnly=true` | Conversion webhook installed. |
| Helm install | `preserveWebhook=true` | Operator and webhook installed. |
| Helm uninstall | | Operator and webhook deployment uninstalled. |
| Helm uninstall with `preserveWebhook=true` set during `helm install` | | Operator deployment uninstalled (and webhook deployment preserved). |

#### Uninstall the conversion webhook
When you [uninstall the operator]({{<relref "/userguide/managing-operators/installation#uninstall-the-operator">}}) using the `helm uninstall` command, it removes the conversion webhook 
associated with the release and its resources from the Kubernetes cluster. However, if you **only** installed the conversion webhook using `webhookOnly=true` custom value, then run the `helm uninstall` 
command separately to remove the conversion webhook associated with the release and its resources.

For example, assuming the Helm release name for the conversion webhook is `sample-weblogic-conversion-webhook`,
and the conversion webhook namespace is `sample-weblogic-conversion-webhook-ns`:

```text
$ helm uninstall sample-weblogic-conversion-webhook -n sample-weblogic-conversion-webhook-ns
```
This command deletes the conversion webhook Deployment (`weblogic-operator-webhook`), and it also deletes the conversion resources such as service and Secrets.

{{% notice warning %}}
The `helm uninstall` command does not delete the conversion definition stored in the Domain CRD. However, the remaining conversion definition in the CRD will cause problems if the conversion webhook runtime is not available. When this happens, you will see a `connection refused` error when creating a Domain using WKO 3.x/V8 domain resource. To avoid this error, manually patch the Domain CRD to set the conversion strategy to `None` and remove the `Webhook` details using the below command. Alternatively, use the `preserveWebhook=true` custom value when installing the conversion webhook using the `helm install` command to prevent the removal of the conversion webhook. 
{{% /notice %}}

Use the below command to manually patch the Domain CRD to set the conversion strategy to `None` and remove the `Webhook` details:
```
 kubectl patch crd domains.weblogic.oracle --type=merge --patch '{"spec": {"conversion": {"strategy": "None", "webhook": null}}}'
```

When you run the `helm install` command again to install a new WebLogic Domain resource conversion webhook, the conversion webhook runtime updates the CRD conversion strategy to `Webhook`. It also updates the necessary webhook client configuration details.

#### Troubleshooting the conversion webhook
Below are some common mistakes and solutions for the conversion webhook.

##### Connection refused error
When the conversion strategy in Domain CRD is `Webhook` and the conversion webhook runtime specified as part of the conversion definition is not available, you will see the `connection refused` error when creating Domain using WKO 3.x/V8 Domain resource. The POST URL in the error message has the name for the conversion webhook service and the namespace.
{{< img "Connection refused" "images/connection-refused-error.png" >}}

Assuming the conversion webhook is deployed in the `sample-weblogic-operator-ns` namespace, run the below commands to ensure that the Deployment is ready and the webhook service exists in the given namespace.

```
$  kubectl get deployment weblogic-operator-webhook -n sample-weblogic-operator-ns
NAME                        READY   UP-TO-DATE   AVAILABLE   AGE
weblogic-operator-webhook   1/1     1            1           87m

$  kubectl get service weblogic-operator-webhook-svc -n sample-weblogic-operator-ns
NAME                            TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)    AGE
weblogic-operator-webhook-svc   ClusterIP   10.106.89.198   <none>        8084/TCP   88m
```
If the conversion webhook Deployment is not ready, check the logs of the conversion webhook runtime Pod and events in the conversion webhook namespace. Refer to [check the conversion webhook log]({{<relref "/userguide/managing-operators/troubleshooting#check-the-conversion-webhook-log">}}) and [check for the conversion webhook events]({{<relref "/userguide/managing-operators/troubleshooting#check-for-the-conversion-webhook-events">}}). If the service doesn't exist, uninstall the conversion webhook and install it again to create the service.

##### Certificate signed by unknown authority error
If you see below `x509: certificate signed by unknown authority` error, then make sure that the CA certificate specified as part of the conversion definition in Domain CRD is correct. 

```
Error from server: error when retrieving current configuration of:
Resource: "weblogic.oracle/v8, Resource=domains", GroupVersionKind: "weblogic.oracle/v8, Kind=Domain"
Name: "sample-domain1", Namespace: "sample-domain1-ns"
from server for: "domain-v8.yaml": conversion webhook for weblogic.oracle/v9, Kind=Domain failed: Post "https://weblogic-operator-webhook-svc.sample-weblogic-operator-ns.svc:8084/webhook?timeout=30s": x509: certificate signed by unknown authority (possibly because of "crypto/rsa: verification error" while trying to verify candidate authority certificate "weblogic-webhook")
```

This error can happen when the conversion definition in Domain CRD has a stale CA certificate generated by the previous conversion webhook Deployments. Run the below patch command to remove the `Webhook` conversion definition from the Domain CRD. The conversion webhook runtime will automatically update the Domain CRD with the latest conversion definition.

```
 kubectl patch crd domains.weblogic.oracle --type=merge --patch '{"spec": {"conversion": {"strategy": "None", "webhook": null}}}'
```

##### Conversion failure due to runtime errors
If you see a `WebLogic Domain custom resource conversion webhook failed` error when creating a Domain using WKO 3.x (V8 schema) domain resource, check the conversion webhook runtime Pod logs and events generated in the conversion webhook namespace. See [Check the conversion webhook log]({{<relref "/userguide/managing-operators/troubleshooting#check-the-conversion-webhook-log">}}) and [Check for the events]({{<relref "/userguide/managing-operators/troubleshooting#check-for-the-conversion-webhook-events">}}). Assuming the conversion webhook is deployed in the `sample-weblogic-operator-ns` namespace, run the below commands to check for logs and events.

```
$ kubectl logs -n sample-weblogic-operator-ns -c weblogic-operator-webhook deployments/weblogic-operator-webhook

$ kubectl get events -n sample-weblogic-operator-ns
```
