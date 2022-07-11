---
title: "Scheduling pods to particular nodes"
date: 2020-06-30T08:55:00-05:00
draft: false
weight: 15
description: "How do I constrain scheduling WebLogic Server pods to particular nodes?"
---

> How do I constrain scheduling WebLogic Server pods to a particular set of nodes?

To do this:

First, set a label on the nodes on which the WebLogic Server pods will run. For example:
```shell
$ kubectl label nodes name=weblogic-pods
```

In the Domain CR, set a `nodeSelector`: a selector which must match a node's labels for the pod to be scheduled on that node. See `kubectl explain pods.spec.nodeSelector`.

You can set `nodeSelector` labels for WebLogic Server pods, all server pods in a cluster, or all server pods in a domain. `nodeSelector` is a field under the `serverPod` element, which occurs at several points in the Domain CR [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md):

- At the top-level `spec.severPod` for the entire domain
- At `spec.adminServer.serverPod` for the Administration Server
- At `spec.clusters[*].serverPod` for each cluster
- At `spec.managedServers[*].serverPod` for individual Managed Servers


```yaml
spec:
serverPod:
nodeSelector:
```
Under that level, you specify labels and values that match the labels on the nodes you want to select. For example:

```yaml
nodeSelector:
  name: weblogic-pods
```

For more details, see [Assign Pods to Nodes](https://kubernetes.io/docs/tasks/configure-pod-container/assign-pods-nodes/) in the Kubernetes documentation.
