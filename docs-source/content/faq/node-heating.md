---
title: "Node heating problem"
date: 2020-06-03T08:08:19-04:00
draft: false
weight: 5
description: "The operator creates a Pod for each WebLogic Server instance that is started. The Kubernetes Scheduler then selects a Node for each Pod. Because the default scheduling algorithm gives substantial weight to selecting a Node where the necessary container images have already been pulled, this often results in Kubernetes running many of the Pods for WebLogic Server instances on the same Node while other Nodes are not fairly utilized. This is commonly known as the Node heating problem."
---

The WebLogic Server Kubernetes Operator creates a Pod for each WebLogic Server instance that is started. The [Kubernetes Scheduler](https://kubernetes.io/docs/concepts/scheduling-eviction/kube-scheduler/) then selects a Node for each Pod. Because the default scheduling algorithm gives substantial weight to selecting a Node where the necessary container images have already been pulled, this often results in Kubernetes running many of the Pods for WebLogic Server instances on the same Node while other Nodes are not fairly utilized. This is commonly known as the "Node heating problem."

One solution is to ensure that all necessary container images are available on worker Nodes as part of node provisioning. When the necessary container images are available on each worker Node, the Kubernetes Scheduler will instead select a Node based on other factors such as available CPU and memory or a simple round-robin.

The operator team recommends a different solution that is based on [inter-pod affinity and anti-affinity](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#inter-pod-affinity-and-anti-affinity). This solution has the advantage of both resolving the Node heating problem and of explicitly directing the Kubernetes Scheduler to spread the Pods for WebLogic Server instances from a given cluster or domain more widely across the available Nodes. Inter-pod affinity and anti-affinity are features of the Kubernetes Scheduler that allow the scheduler to choose a Node for a new Pod based on details of the Pods that are already running. For WebLogic Server use cases, the intent will often be for anti-affinity with the Pods for other WebLogic Server instances so that server instances spread over the available Nodes.

To use these features, edit the Domain Custom Resource to add content to the `serverPod` element, in this case at the scope of a cluster, as shown in the following example:

```yaml
clusters:
- clusterName: cluster-1
  serverStartState: "RUNNING"
  serverPod:
    affinity:
      podAntiAffinity:
        preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                  - key: "weblogic.clusterName"
                    operator: In
                    values:
                    - $(CLUSTER_NAME)
              topologyKey: "kubernetes.io/hostname"
```

Because the `serverPod` element here is scoped to a cluster, the content of the `affinity` element will be added to the Pod generated for each WebLogic Server instance that is a member of this WebLogic cluster. This inter-pod anti-affinity statement expresses a preference that the scheduler select a Node for the new Pod avoiding, as much as possible, Nodes that already have Pods with the label "weblogic.clusterName" and the name of this cluster. Note that the `weight` is set to `100`, which is the maximum weight, so that this term will outweigh any possible preference for a Node based on availability of container images.

It is possible to express many other scheduling preferences or constraints. The following example similarly expresses an anti-affinity, but changes the test to have all WebLogic Server instances in the domain prefer to run on Nodes where there is not already a Pod for a running instance:

```yaml
serverPod:
  affinity:
    podAntiAffinity:
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchExpressions:
            - key: "weblogic.domainUID"
              operator: In
              values:
              - $(DOMAIN_UID)
          topologyKey: "kubernetes.io/hostname"
```

Details about how the operator generates Pods for WebLogic Server instances, including details about labels and variable substitution, are available [here]({{< relref "/userguide/managing-domains/domain-resource#pod-generation" >}}).
