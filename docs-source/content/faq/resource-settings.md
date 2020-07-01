---
title: "Pod Memory and CPU Resources"
date: 2020-06-30T08:55:00-05:00
draft: false
weight: 25
---

### Contents

 - [Introduction](#introduction)
 - [Setting resource requests and limits in a domain resource](#setting-resource-requests-and-limits-in-a-domain-resource)
 - [Determining pod Quality Of Service](#determining-pod-quality-of-service)
 - [Java heap size and memory resource considerations](#java-heap-size-and-memory-resource-considerations)
   - [Importance of setting heap size and memory resources](#importance-of-setting-heap-size-and-memory-resources)
   - [Default heap sizes](#default-heap-sizes)
   - [Configuring heap size](#configuring-heap-size)
 - [CPU resource considerations](#cpu-resource-considerations)
 - [Operator sample heap and resource configuration](#operator-sample-heap-and-resource-configuration)
 - [Configuring CPU affinity](#configuring-cpu-affinity)
 - [Measuring JVM heap, pod CPU, and pod memory](#measuring-jvm-heap-pod-cpu-and-pod-memory)
 - [References](#references)

### Introduction

An operator creates a pod for each WebLogic Server instance and each pod will have a container. You can tune pod container memory and/or CPU usage by configuring Kubernetes resource requests and limits, and you can tune a WebLogic JVM heap usage using the `USER_MEM_ARGS` environment variable in your domain resource. A resource request sets the minimum amount of a resource that a container requires. A resource limit is the maximum amount of resource a container is given and prevents a container from using more than its share of a resource. Additionally, resource requests and limits determine a pod's Quality of Service.

This FAQ discusses tuning these parameters so WebLogic servers can run efficiently.

### Setting resource requests and limits in a domain resource

You can set Kubernetes memory and CPU requests and limits in a [domain resource]({{< relref "/userguide/managing-domains/domain-resource" >}}) using its `spec.serverPod.resources` stanza, and you can override the setting for individual WebLogic servers or clusters using the `serverPod.resources` element in `spec.adminServer`, `spec.clusters`, and/or `spec.managedServers`. For example: 

```
  spec:
    serverPod:
      requests:
        cpu: "250m"
        memory: "768Mi"
      limits:
        cpu: "2"
        memory: "2Gi"
```

Limits and requests for CPU resources are measured in cpu units. One cpu, in Kubernetes, is equivalent to 1 vCPU/Core for cloud providers and 1 hyperthread on bare-metal Intel processors. An `m` suffix in a CPU attribute indicates 'milli-CPU', so `250m` is 25% of a CPU. 

Memory can be expressed in various units, where one `Mi` is one IEC unit mega-byte (1024^2), and one `Gi` is one IEC unit giga-byte (1024^3).

See also [Managing Resources for Containers](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/), [Assign Memory Resources to Containers and Pods](https://kubernetes.io/docs/tasks/configure-pod-container/assign-memory-resource) and [Assign CPU Resources to Containers and Pods](https://kubernetes.io/docs/tasks/configure-pod-container/assign-cpu-resource) in Kubernetes documentation.

### Determining pod Quality Of Service

A pod's Quality of Service (QoS) is based on whether it's configured with resource requests and limits:

- **Best Effort QoS** (lowest priority): If you don't configure requests and limits for a pod, then the pod is given a `best-effort` QoS. In cases where a node runs out of non-shareable resources, a Kubernetes `kubelet` default out-of-resource eviction policy evicts running pods with the `best-effort` QoS first.

- **Burstable QoS** (medium priority): If you configure both resource requests and limits for a pod, and set the requests to be less than their respective limits, then the pod will be given a `burstable` QoS. Similarly, if you only configure resource requests (without limits) for a pod, then the pod QoS is also `burstable`. If a node runs out of non-shareable resources, the node's `kubelet` will evict `burstable` pods only when there are no more running `best-effort` pods.

- **Guaranteed QoS** (highest priority): If you set a pod's requests and the limits to equal values, then the pod will have a `guaranteed` QoS. These settings indicates that your pod will consume a fixed amount of memory and CPU. With this configuration, if a node runs out of shareable resources, then a Kubernetes node's `kubelet` will evict `best-effort` and `burstable` QoS pods before terminating `guaranteed` QoS pods.

{{% notice note %}} 
For most use cases, Oracle recommends configuring WebLogic pods with memory and CPU requests and limits, and furthermore setting requests equal to their respective limits in order to ensure a `guaranteed` QoS.
{{% /notice %}}

{{% notice note %}} 
In newer version of Kubernetes, it is possible to fine tune scheduling and eviction policies using [Pod Priority Preemption](https://kubernetes.io/docs/concepts/configuration/pod-priority-preemption/) in combination with the `serverPod.priorityClassName` domain resource attribute. Note that Kubernetes already ships with two PriorityClasses: `system-cluster-critical` and `system-node-critical`. These are common classes and are used to [ensure that critical components are always scheduled first](https://kubernetes.io/docs/tasks/administer-cluster/guaranteed-scheduling-critical-addon-pods/).
{{% /notice %}}

### Java heap size and memory resource considerations

{{% notice note %}} 
For most use cases, Oracle recommends configuring Java heap sizes for WebLogic pods instead of relying on defaults.
{{% /notice %}}

#### Importance of setting heap size and memory resources

It's extremely important to set correct heap sizes, memory requests, and memory limits for WebLogic JVMs and Pods. 

A WebLogic JVM heap must be sufficiently sized to run its applications and services, but should not be sized too large so as not to waste memory resources.

A pod memory limit must be sufficiently sized to accommodate the configured heap and native memory requirements, but  not too big to waste memory resources. If a JVM's memory usage (sum of heap and native memory) exceeds its pod's limit, then the JVM process will be abruptly killed due to an out-of-memory error and the WebLogic container will consequently automatically restart due to a liveness probe failure.  

Oracle recommends setting minimum and maximum heap (or heap percentages) and at least a container memory request.

{{% notice warning %}}
If resource requests and resource limits are set too high, then your pods may not be scheduled due to lack of node resources, will unnecessarily use up CPU shared resources that could be used by other pods, or may prevent other pods from running.
{{% /notice %}}

#### Default heap sizes

With the latest Java versions, Java 8 update 191 and onwards or Java 11, then if you don't configure a heap size (no '-Xms' or '-Xms') the default heap size is dynamically determined:
- If you configure the memory limit for a container, then the JVM default maximum heap size will be 25% (1/4th) of container memory limit and the default minimum heap size will be 1.56% (1/64th) of the limit value. 

  The default JVM heap settings in this case are often too conservative because the WebLogic JVM is the only major process running in the container.

- If no memory limit is configured, then the JVM default maximum heap size will be  25% (1/4th) of the its node's machine RAM and the default minimum heap size will be 1.56% (1/64th) of the RAM.

  The default JVM heap settings in this case can have undesirable behavior, including using unnecessary amounts of memory to the point where it might affect other pods that run on the same node.

#### Configuring heap size

If you specify pod memory limits, Oracle recommends configuring WebLogic Server heap sizes as a percentage. The JVM will interpret the percentage as a fraction of the limit. This is done using the JVM `-XX:MinRAMPercentage` and `-XX:MaxRAMPercentage` options in the `USER_MEM_ARGS` [domain resource environment variable]({{< relref "/userguide/managing-domains/domain-resource#jvm-memory-and-java-option-environment-variables" >}}).  For example:

```
  spec:
    resources:
      env:
      - name: USER_MEM_ARGS
        value: "--XX:MinRAMPercentage=25.0 --XX:MaxRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"
```

Additionally there's also a node-manager process that's running in the same container as the WebLogic Server which has its own heap and native memory requirements. Its heap is tuned by using `-Xms` and `-Xmx` in the `NODEMGR_MEM_ARGS` environment variable. Oracle recommends setting the node manager heap memory to fixed sizes, instead of percentages, where [the default tuning]({{< relref "/userguide/managing-domains/domain-resource#jvm-memory-and-java-option-environment-variables" >}}) is usually sufficient.

{{% notice note %}}
Notice that the `NODEMGR_MEM_ARGS` and `USER_MEM_ARGS` environment variables both set `-Djava.security.egd=file:/dev/./urandom` by default so we have also included them in the above example for specifying a `USER_MEM_ARGS` value. This helps speed up Node Manager and WebLogic Server startup on systems with low entropy. 
{{% /notice %}}

In some cases, you might only want to configure memory resource requests but not configure memory resource limits. In such scenarios, you can use the traditional fixed heap size settings (`-Xms` and `-Xmx`) in your WebLogic Server `USER_MEM_ARGS` instead of the percentage settings (`-XX:MinRAMPercentage` and `-XX:MaxRAMPercentage`).

### CPU resource considerations

It's important to set both a CPU request and a limit for WebLogic Server pods. This ensures that all WebLogic server pods have enough CPU resources, and, as discussed earlier, if the request and limit are set to the same value, then they get a `guaranteed` QoS. A `guaranteed` QoS ensures the pods are handled with a higher priority during scheduling and so are the least likely to be evicted.

If a CPU request and limit are _not_ configured for a WebLogic Server pod:
- The pod can end up using all CPU resources available on its node and starve other containers from using shareable CPU cycles. 

- The WebLogic server JVM may choose an unsuitable garbage collection (GC) strategy.

- A WebLogic Server self-tuning work-manager may incorrectly optimize the number of threads it allocates for the default thread pool. 

It's also important to keep in mind that if you set a value of CPU core count that's larger than core count of your biggest node, then the pod will never be scheduled. Let's say you have a pod that needs 4 cores but you have a kubernetes cluster that's comprised of 2 core VMs. In this case, your pod will never be scheduled and will have `Pending` status. For example:

```
$ kubectl get pod sample-domain1-managed-server1 -n sample-domain1-ns
NAME                              READY   STATUS    RESTARTS   AGE
sample-domain1-managed-server1    0/1     Pending   0          65s

$ kubectl describe pod sample-domain1-managed-server1 -n sample-domain1-ns
Events:
  Type     Reason            Age                From               Message
  ----     ------            ----               ----               -------
  Warning  FailedScheduling  16s (x3 over 26s)  default-scheduler  0/2 nodes are available: 2 Insufficient cpu.
```

### Operator sample heap and resource configuration

The operator samples configure non-default minimum and maximum heap sizes for WebLogic server JVMs of at least 256MB and 512MB respectively. You can edit a sample's template or domain resource `resources.env` `USER_MEM_ARGS` to have different values. See [Configuring heap size](#configuring-heap-size).

Similarly, the operator samples configure CPU and memory resource requests to at least `250m` and `768Mi` respectively.

There's no memory or CPU limit configured by default in samples and so the default QoS for sample WebLogic server pod's is `Burstable`. 

If you wish to set resource requests or limits differently on a sample domain resource or domain resource template, see [Setting resource requests and limits in a domain resource](#setting-resource-requests-and-limits-in-a-domain-resource). Or for samples that generate their domain resource using an 'inputs' file, see the `serverPodMemoryRequest`, `serverPodMemoryLimit`, `serverPodCpuRequest`, and `serverPodCpuLimit` parameters in the sample's `create-domain.sh` input file.

### Configuring CPU affinity

A Kubernetes hosted WebLogic server may exhibit high lock contention in comparison to an on-premise deployment. This lock contention may be due to lack of CPU cache affinity and/or scheduling latency when workloads move between different CPU cores.  

In an on-premise deployment, CPU cache affinity, and therefore reduced lock contention, can be achieved by binding WLS java process to particular CPU core(s) (using the `taskset` command).

In a Kubernetes deployment, similar cache affinity can be achieved by doing the following:
- Ensuring a pod's CPU resource request and limit are set and equal (to ensure a `guaranteed` QoS).
- Configuring the `kubelet` CPU manager policy to be `static` (the default is `none`). See [Control CPU Management Policies on the Node](https://kubernetes.io/docs/tasks/administer-cluster/cpu-management-policies). 
Note that some Kubernetes environments may not allow changing the CPU management policy.

### Measuring JVM heap, pod CPU, and pod memory
You can monitor JVM heap, pod CPU and pod memory using Prometheus and Grafana using steps similar to the [Monitor a SOA Domain]({{< relref "/samples/simple/elastic-stack/soa-domain/weblogic-monitoring-exporter-setup" >}}) sample. Also see [Tools for Monitoring Resources](https://kubernetes.io/docs/tasks/debug-application-cluster/resource-usage-monitoring/) in the Kubernetes documentation.

### References:
1. [Managing Resources for Containers](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/) in the Kubernetes documentation.
1. [Assign Memory Resources to Containers and Pods](https://kubernetes.io/docs/tasks/configure-pod-container/assign-memory-resource) in the Kubernetes documentation.
1. [Assign CPU Resources to Containers and Pods](https://kubernetes.io/docs/tasks/configure-pod-container/assign-cpu-resource/) in the Kubernetes documentation.
1. [Pod Priority Preemption](https://kubernetes.io/docs/concepts/configuration/pod-priority-preemption/) in the Kubernetes documentation.
1. [GCP Kubernetes best practices: Resource requests and limits](https://cloud.google.com/blog/products/gcp/kubernetes-best-practices-resource-requests-and-limits)
1. [Tools for Monitoring Resources](https://kubernetes.io/docs/tasks/debug-application-cluster/resource-usage-monitoring/) in the Kubernetes documentation.
1. [Blog -- Docker support in Java 8](https://blog.softwaremill.com/docker-support-in-new-java-8-finally-fd595df0ca54). (Discusses Java container support in general.)
1. [Blog -- Kubernetes Patterns : Capacity Planning](https://www.magalix.com/blog/kubernetes-patterns-capacity-planning)

