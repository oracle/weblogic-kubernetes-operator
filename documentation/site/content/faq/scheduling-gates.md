---
title: "Using Pod scheduling gates"
date: 2026-07-09T00:00:00-05:00
draft: false
weight: 16
description: "How does the operator handle Kubernetes Pod scheduling gates?"
---

> How does the operator handle Kubernetes Pod `schedulingGates`?

The operator supports `schedulingGates` under `serverPod` for WebLogic Server Pods. You can configure the field at the domain, Administration Server, cluster, or Managed Server scope. For a Cluster resource, configure it under `spec.serverPod.schedulingGates`.

When `schedulingGates` is configured, the operator includes it only when creating a new Pod. Kubernetes then keeps that Pod in the `SchedulingGated` state until the gate is removed from the Pod. The operator continues to honor normal startup ordering and concurrency limits, including starting the Administration Server before Managed Servers.

Changing or removing `schedulingGates` in the Domain or Cluster resource does not update existing Pods and does not cause the operator to replace them. This matches Kubernetes immutability rules for scheduling gates. To release an existing scheduling-gated Pod, remove the gate from the Pod itself, for example:

```shell
$ kubectl patch pod <pod-name> -n <namespace> --type=json \
    -p='[{"op":"remove","path":"/spec/schedulingGates"}]'
```

A scheduling-gated Pod may remain `Pending` while the gate exists. The operator does not report the Pod as failed for exceeding `maxPendingWaitTimeSeconds` while it is still scheduling-gated.
