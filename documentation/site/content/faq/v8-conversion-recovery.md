---
title: "Recovering from v8 Domain conversion webhook errors"
date: 2026-08-05T00:00:00-05:00
draft: false
weight: 17
description: "Diagnose and recover from 403 and 409 errors during conversion of a v8 Domain resource."
---

> Why does applying a `weblogic.oracle/v8` Domain resource fail with an HTTP 403 or HTTP 409 conversion webhook error, and how do I recover?

Beginning with operator 4.0, the WebLogic Domain resource conversion webhook automatically converts compatible `weblogic.oracle/v8` Domain resources to the current schema. During conversion, the webhook verifies the identity of the live Domain resource and, when applicable, creates or validates the corresponding Cluster resources.

These checks prevent a conversion request from modifying Cluster resources that belong to a different Domain instance or that were left behind by an earlier, incomplete operation.

## Error messages

The Kubernetes API server reports a conversion failure in the command that created, applied, retrieved, or updated the Domain resource. For example:

```
Error from server: error when creating "domain.yaml": conversion webhook for weblogic.oracle/v8, Kind=Domain failed: Exception: jakarta.ws.rs.WebApplicationException: HTTP 403 Conversion Domain 'my-domain' does not match the live Domain
```

Or:

```
Error from server: error when creating "domain.yaml": conversion webhook for weblogic.oracle/v8, Kind=Domain failed: Exception: jakarta.ws.rs.WebApplicationException: HTTP 409 Cluster 'my-domain-cluster-1' already exists in namespace 'my-domain-ns', but conversion Domain 'my-domain' is not live; delete the existing Cluster before recreating the Domain
```

## HTTP 403: Conversion Domain does not match the live Domain

The conversion webhook returns HTTP 403 when it cannot verify that the Domain in the conversion request is the same live Domain resource. This occurs when any of the following is true:

- The conversion request does not contain a Domain UID.
- No live Domain resource with that name exists in the namespace.
- A Domain with the same name exists, but its Kubernetes UID differs from the UID in the conversion request.

This can occur when a Domain was deleted and recreated with the same name, or when Kubernetes completes an older request after that Domain has been deleted or replaced.

### Recovery

1. Check whether the Domain currently exists.

   ```shell
   $ kubectl get domain my-domain -n my-domain-ns
   ```

2. If the Domain exists, record its UID and verify that it is the intended Domain instance.

   ```shell
   $ kubectl get domain my-domain -n my-domain-ns \
       -o jsonpath='{.metadata.uid}{"\n"}'
   ```

3. If the Domain was deleted or recreated, do not retry an operation that was started against the earlier Domain instance. Wait for deletion to complete, check for orphaned Cluster resources as described in the HTTP 409 recovery procedure, and then apply the intended Domain resource again.

4. If the Domain exists and was not replaced, retry the original command after confirming that no other automation is deleting or recreating the Domain.

Do not attempt to set `metadata.uid` in the Domain YAML. Kubernetes assigns and manages this value.

## HTTP 409: Existing Cluster resource prevents recreation of the Domain

The conversion webhook returns HTTP 409 when it finds an existing operator-created Cluster resource for the converted Domain, but the corresponding live Domain no longer exists.

This protects the existing Cluster resource from being silently reused by a newly created Domain with the same name. The new Domain would have a different Kubernetes UID and might have different configuration.

### Recovery

1. Confirm that the Domain is absent.

   ```shell
   $ kubectl get domain my-domain -n my-domain-ns
   ```

   Continue only if this reports `NotFound`.

2. List the Cluster resources that may remain from the deleted Domain.

   ```shell
   $ kubectl get cluster -n my-domain-ns \
       -o custom-columns=NAME:.metadata.name,DOMAIN_UID:.metadata.labels.weblogic\\.domainUID,CREATED_BY_OPERATOR:.metadata.labels.weblogic\\.createdByOperator
   ```

3. Inspect every candidate Cluster resource before deleting it. Confirm that it belongs to the deleted Domain, is not referenced by a live Domain, and has the `weblogic.createdByOperator: "true"` label.

   ```shell
   $ kubectl get cluster my-domain-cluster-1 -n my-domain-ns -o yaml
   ```

4. Delete only the verified, orphaned Cluster resources.

   ```shell
   $ kubectl delete cluster my-domain-cluster-1 -n my-domain-ns
   ```

5. Reapply the v8 Domain resource.

   ```shell
   $ kubectl apply -f domain.yaml
   ```

If the Cluster resource is not labeled `weblogic.createdByOperator=true`, do not delete it as part of this recovery procedure. Determine its owner and configuration first.

## Important notes

- Do not delete Cluster resources while their Domain is live and references them. Doing so changes the running domain configuration.
- Do not recreate a Domain with the same name until Kubernetes has completed deletion of the prior Domain and its operator-created resources, or until any orphaned resources have been explicitly verified and removed.
- The HTTP 409 message is an improved diagnostic for an orphaned-Cluster scenario. It does not mean that the webhook automatically deletes resources.
- The HTTP 403 check remains intentional. It prevents a stale or mismatched conversion request from operating on a different live Domain.

## Collecting diagnostics

Check the conversion webhook logs for the complete exception and conversion request details:

```shell
$ kubectl logs -n YOUR_CONVERSION_WEBHOOK_NS \
    -c weblogic-operator-webhook \
    deployments/weblogic-operator-webhook
```

Also check events in both the Domain namespace and the conversion webhook namespace:

```shell
$ kubectl get events -n my-domain-ns --sort-by=.metadata.creationTimestamp

$ kubectl get events -n YOUR_CONVERSION_WEBHOOK_NS \
    --sort-by=.metadata.creationTimestamp
```

See also:

- [WebLogic Domain resource conversion webhook]({{% relref "/managing-operators/conversion-webhook.md" %}})
- [Troubleshooting the conversion webhook]({{% relref "/managing-operators/troubleshooting.md#troubleshooting-the-conversion-webhook" %}})
- [Domain and Cluster resources]({{% relref "/managing-domains/domain-resource.md" %}})
