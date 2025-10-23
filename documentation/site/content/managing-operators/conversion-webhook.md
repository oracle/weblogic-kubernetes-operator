---
title: "Upgrade operator from version 3.x to 4.x"
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

The Domain CustomResourceDefinition (CRD) in your Kubernetes cluster, which defines the schema of operator managed Domains, has changed significantly in operator version 4.0 from previous operator releases, therefore, we have updated the API version to `weblogic.oracle/v9`. For example, we have enhanced [Auxiliary images]({{<relref "/managing-domains/model-in-image/auxiliary-images">}}) in operator version 4.0, and its configuration has changed. Operator 4.0 uses the [Kubernetes Webhook Conversion](https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definition-versioning/#webhook-conversion) strategy and a WebLogic Domain resource conversion webhook to automatically and transparently upgrade domain resources with `weblogic.oracle/v8` schema to `weblogic.oracle/v9` schema. With the Webhook conversion strategy, the Kubernetes API server internally invokes an external REST service that pulls out the configuration of the auxiliary images defined in the `weblogic.oracle/v8` schema domain resource and converts it to the equivalent `weblogic.oracle/v9` schema configuration. The WebLogic Domain resource conversion webhook is a singleton Deployment in your Kubernetes cluster. It is installed by default when an operator is installed, and uninstalled when any operator is uninstalled, but you can optionally install and uninstall it independently. For details, see [Install the conversion webhook](#install-the-conversion-webhook) and [Uninstall the conversion webhook](#uninstall-the-conversion-webhook). For manually upgrading the domain resources with `weblogic.oracle/v8` schema to `weblogic.oracle/v9` schema, see [Upgrade Domain resource]({{<relref "/managing-domains/upgrade-domain-resource/_index.md">}}).

### Conversion webhook components
The following table lists the components of the WebLogic Domain resource conversion webhook and their purpose.
| Component Type | Component Name | Purpose |
| --- | --- | -- |
| Deployment | `webLogic-operator-webhook` | Manages the runtime Pod of the WebLogic Domain resource conversion webhook. |
| Service | `webLogic-operator-webhook-svc` | The Kubernetes API server uses this service to reach the conversion webhook runtime defined in the WebLogic Domain CRD. |
| Secret | `webLogic-webhook-secrets` | Contains the CA certificate and key used to secure the communication between the Kubernetes API server and the REST endpoint of WebLogic domain resource conversion webhook. |
| The `spec.conversion` stanza in the Domain CRD | | Used internally by the Kubernetes API server to call an external service when a Domain conversion is required. |

**NOTES**:
- The conversion webhook Deployment `webLogic-operator-webhook` uses and requires the same image as the operator image. You can scale the Deployment by increasing the number of replicas, although this is rarely required.

- The conversion webhook Deployment sets the `spec.conversion.strategy` field of the Domain CRD to `Webhook`. It also adds webhook client configuration details such as service name, namespace, path, port, and the self-signed CA certificate used for authentication.

### Install the conversion webhook

Beginning with  operator version 4.0, when you [install the operator]({{<relref "/managing-operators/installation#install-the-operator">}}) using the `helm install` command with the operator Helm chart, by default, it also configures a deployment and supporting resources for the conversion webhook and deploys the conversion webhook in the specified namespace. Note that a webhook is a singleton deployment in the cluster. Therefore, if the webhook is already installed in the cluster and the operator installation version is the same or older, then the operator installation will skip the webhook installation. However, if the operator version is newer, then a new webhook Deployment is created and all webhook traffic for the CRD is redirected to the new webhook.

{{% notice note %}}
**Security Considerations:**
The `helm install` step requires cluster-level permissions for listing and reading all Namespaces and Deployments to search for existing conversion webhook deployments. If you cannot grant the cluster-level permissions and have multiple operators deployed, then install the conversion webhook separately and set the Helm configuration value `operatorOnly` to `true` in the `helm install` command to prevent multiple conversion webhook deployments. In addition, the webhook uses a service account that is usually the same service account as an operator running in the same namespace. This service account requires permissions to create and read events in the conversion webhook namespace. For more information, see [RBAC]({{<relref "/managing-operators/rbac.md" >}}).
{{% /notice %}}

{{% notice note %}}
Operator version 4.x requires a conversion webhook. The `operatorOnly` Helm configuration value is an advanced setting and should be used only when a conversion webhook is already installed.
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

**NOTE**:
A webhook install is skipped if there's already a webhook deployment at the same or newer version. The `helm install` step requires cluster-level permissions to search for existing conversion webhook deployments in all namespaces.

### Upgrade the conversion webhook

We support having exactly one installation of the webhook and having one or more installations of the operator. To have more than one installation of the operator, you would need to install a Helm release with just the webhook and then separately install multiple Helm releases with just the operator. The conversion webhook should be updated to at least match the version of the most recent operator in the cluster.

To upgrade the conversion webhook only, you must have _first_ installed a Helm release with the webhook only, use the `--set webhookOnly=true` option, then you can update that release.

The following example installs the conversion webhook only (at the specified version), and then upgrades it (to a later, specified version).
```
kubectl create namespace <your-namespace>

helm repo add weblogic-helm-repository https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update

helm install <your-release-name> weblogic-helm-repository/weblogic-operator --namespace <your-namespace> --set webhookOnly=true --version <selected-version>
```
The first two steps create the namespace and configure Helm with the chart repository.

The final step installs the webhook specifying that the install should be for the webhook only and at a specific version of the product.

To upgrade to a later version of the webhook:
```
helm upgrade <your-release-name> weblogic-helm-repository/weblogic-operator --namespace <your-namespace> --version <selected-new-version>
```

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
