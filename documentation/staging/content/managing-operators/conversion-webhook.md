---
title: "Domain upgrade"
date: 2019-02-23T16:47:21-05:00
draft: false
weight: 4
description: "Conversion webhook for upgrading the domain resource schema."
---

{{< table_of_contents >}}

### Introduction

{{% notice tip %}}
The conversion webhook that is described in this document
transparently handles a `weblogic.oracle/v8` schema domain resource at runtime,
but if you want to use the new fields introduced in the latest `weblogic.oracle/v9` schema
with a Domain that is currently `weblogic.oracle/v8`,
then you will need to update its Domain resource file
and potentially create new Cluster resource files.
To simplify this conversion, see
[manual upgrade command line tool]({{< relref "managing-domains/upgrade-domain-resource#upgrade-the-weblogicoraclev8-schema-domain-resource-manually" >}}).
{{% /notice %}}

The WebLogic Domain resource conversion webhook is a singleton Deployment in your Kubernetes cluster that automatically and transparently upgrades domain resources with `weblogic.oracle/v8` schema to `weblogic.oracle/v9` schema. It does this by internally using a [Kubernetes Webhook Conversion](https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definition-versioning/#webhook-conversion) strategy.  The Domain CustomResourceDefinition in your Kubernetes cluster, which defines the schema of operator managed Domains, has changed significantly in operator version 4.0 from previous operator releases, therefore we have updated the API version to `weblogic.oracle/v9`. For example, we have enhanced [Auxiliary images]({{<relref "managing-domains/model-in-image/auxiliary-images">}}) in operator version 4.0, and its configuration has changed as a result. With the `Webhook` conversion strategy, the Kubernetes API server internally invokes an external REST service which pulls out the configuration of the auxiliary images defined in the `weblogic.oracle/v8` schema domain resource and converts it to the equivalent `weblogic.oracle/v9` schema configuration in operator 4.0. The webhook is automatically installed by default when an operator is installed, and uninstalled when any operator is uninstalled, but you can optionally install and uninstall it independently. For details, see [Install the conversion webhook](#install-the-conversion-webhook) and [Uninstall the conversion webhook](#uninstall-the-conversion-webhook).

### Conversion webhook components
The following table lists the components of the WebLogic Domain resource conversion webhook and their purpose.
| Component Type | Component Name | Purpose |
| --- | --- | -- |
| Deployment | `webLogic-operator-webhook` | Manages the runtime Pod of the WebLogic Domain resource conversion webhook. |
| Service | `webLogic-operator-webhook-svc` | The Kubernetes API server uses this service to reach the conversion webhook runtime defined in the WebLogic Domain CRD. |
| Secret | `webLogic-webhook-secrets` | Contains the CA certificate and key used to secure the communication between the Kubernetes API server and the REST endpoint of WebLogic domain resource conversion webhook. |
| The `spec.conversion` stanza in the Domain CRD | | Used internally by the Kubernetes API server to call an external service when a Domain conversion is required. |

**Notes:**
- The conversion webhook Deployment `webLogic-operator-webhook` uses and requires the same image as the operator image. You can scale the Deployment by increasing the number of replicas, although this is rarely required.

- The conversion webhook Deployment sets the `spec.conversion.strategy` field of the Domain CRD to `Webhook`. It also adds webhook client configuration details such as service name, namespace, path, port, and the self-signed CA certificate used for authentication.

### Install the conversion webhook

Beginning with  operator version 4.0, when you [install the operator]({{<relref "/managing-operators/installation#install-the-operator">}}) using the `helm install` command with the operator Helm chart, by default, it also configures a deployment and supporting resources for the conversion webhook and deploys the conversion webhook in the specified namespace. Note that a webhook is a singleton deployment in the cluster. Therefore, if the webhook is already installed in the cluster and the operator installation version is the same or older, then the operator installation will skip the webhook installation. However, if the operator version is newer, then a new webhook Deployment is created and all webhook traffic for the CRD is redirected to the new webhook.

{{% notice note %}}
**Security Considerations:**
The `helm install` step requires cluster-level permissions for listing and reading all Namespaces and Deployments to search for existing conversion webhook deployments. If you cannot grant the cluster-level permissions and have multiple operators deployed, then install the conversion webhook separately and set the Helm configuration value `operatorOnly` to `true` in the `helm install` command to prevent multiple conversion webhook deployments. In addition, the webhook uses a service account that is usually the same service account as an operator running in the same namespace. This service account requires permissions to create and read events in the conversion webhook namespace. For more information, see [RBAC]({{<relref "/managing-operators/rbac.md" >}}).
{{% /notice %}}


If you want to install _only_ the conversion webhook (and not the operator) in the given namespace, set the Helm configuration value `webhookOnly` to `true` in the `helm install` command. After meeting the [prerequisite requirements]({{<relref "/managing-operators/preparation.md">}}), call:
```
$ helm install sample-weblogic-conversion-webhook \
  weblogic-operator/weblogic-operator \
  --namespace sample-weblogic-conversion-webhook-ns \
  --set "webhookOnly=true" \
  --wait
```
The previous command creates a Helm release named `sample-weblogic-conversion-webhook`
in the `sample-weblogic-conversion-webhook-ns` namespace,
configures a deployment and supporting resources for the conversion webhook,
and deploys the conversion webhook. This command uses the `default` service account for the `sample-weblogic-conversion-webhook-ns` namespace.

To check if the conversion webhook is deployed and running,
see [Troubleshooting]({{<relref "/managing-operators/troubleshooting#check-the-conversion-webhook-deployment">}}).

To prevent the removal of the conversion webhook during the `helm uninstall` command of an operator, set the Helm configuration value `preserveWebhook` to `true` during the `helm install` of the operator release.

The following table describes the behavior of different operator `Helm` chart commands for various Helm configuration values.

| Helm command with the operator Helm chart | Helm configuration values | Behavior |
| --- | --- | --- |
| Helm install | None specified | Operator and conversion webhook installed. |
| Helm install | `webhookOnly=true` | Only conversion webhook installed. |
| Helm install | `operatorOnly=true` | Only operator installed. |
| Helm install | `preserveWebhook=true` | Operator and webhook installed and a future uninstall will not remove the webhook. |
| Helm uninstall | | Operator and webhook deployment uninstalled. |
| Helm uninstall with `preserveWebhook=true` set during `helm install` | | Operator deployment uninstalled and webhook deployment preserved. |

**Note:**
A webhook install is skipped if there's already a webhook deployment at the same or newer version. The `helm install` step requires cluster-level permissions to search for existing conversion webhook deployments in all namespaces.

### Uninstall the conversion webhook

{{% notice warning %}}
If you are deploying Domains using `weblogic.oracle/v8` or older schema and either you have multiple operators deployed or you want to create Domains without the operator deployed, then you may not want to uninstall the webhook. If the conversion webhook runtime is not available under these conditions, then you will see a `conversion webhook not found` error. To avoid this error, reinstall the webhook and, in the future, use the `preserveWebhook=true` Helm configuration value when installing an operator.
{{% /notice %}}

When you [uninstall the operator]({{<relref "/managing-operators/installation#uninstall-the-operator">}}) using the `helm uninstall` command, it removes the conversion webhook
associated with the release and its resources from the Kubernetes cluster. However, if you _only_ installed the conversion webhook using the `webhookOnly=true` Helm configuration value, then run the `helm uninstall`
command separately to remove the conversion webhook associated with the release and its resources.

For example, assuming the Helm release name for the conversion webhook is `sample-weblogic-conversion-webhook`,
and the conversion webhook namespace is `sample-weblogic-conversion-webhook-ns`:

```text
$ helm uninstall sample-weblogic-conversion-webhook -n sample-weblogic-conversion-webhook-ns
```
This command deletes the conversion webhook Deployment (`weblogic-operator-webhook`) and it also deletes the conversion resources, such as service and Secrets.

### Troubleshooting the conversion webhook
See [Troubleshooting the conversion webhook]({{<relref "/managing-operators/troubleshooting#troubleshooting-the-conversion-webhook">}}).
