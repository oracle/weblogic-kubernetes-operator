---
title: "Schedule pods to particular nodes"
date: 2020-06-30T08:55:00-05:00
draft: false
weight: 15
description: "How do I constrain scheduling WebLogic Server pods to particular nodes."
---

> How do I constrain scheduling WebLogic Server pods to a particular set of nodes?

To do this, in the Domain CR, set a `nodeSelector`: a selector which must match a node's labels for the pod to be scheduled on that node. See `kubectl explain pods.spec.nodeSelector`.

You can set `nodeSelector` labels for WebLogic Server pods, all server pods in a cluster, or all server pods in a domain. `nodeSelector` is a field under the `serverPod` element, which occurs at several points in the Domain CR [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/main/documentation/domains/Domain.md): at the top-level `spec.severPod` for the entire domain, at `spec.adminServer.serverPod` for the Administration Server, at `spec.clusters[*].serverPod` for each cluster, and at `spec.managedServers[*].serverPod` for individual Managed Servers.


```shell
spec:
serverPod:
nodeSelector:
zone:
```
Under that level, you specify labels and values that match the labels on the nodes you want to select.

For example:

```yaml
nodeSelector:
  disktype: ssd
```

For more details, see [Assign Pods to Nodes](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#nodeselector) in the Kubernetes documentation.
