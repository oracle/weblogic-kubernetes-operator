# Considerations for Pod Resource (Memory and CPU) Requests and Limits
The operator creates a pod for each running WebLogic Server instance and each pod will have a container. It.s important that containers have enough resources in order for applications to run efficiently and expeditiously. 

If a pod is scheduled on a node with limited resources, it.s possible for the node to run out of memory or CPU resources, and for applications to stop working properly or have degraded performance. It.s also possible for a rouge application to use all available memory and/or CPU, which makes other containers running on the same system unresponsive. The same problem can happen if an application has memory leak or bad configuration. 

A pod.s resource requests and limit parameters can be used to solve these problems. Setting resource limits prevents an application from using more than it.s share of resource. Thus, limiting resources improves reliability and stability of applications.  It also allows users to plan for the hardware capacity. Additionally, pod.s priority and the Quality of Service (QoS) that pod receives is affected by whether resource requests and limits are specified or not.

## Pod Quality Of Service (QoS) and Prioritization
Pod.s Quality of Service (QoS) and priority is determined based on whether pod.s resource requests and limits are configured or not and how they.re configured.

Best Effort QoS: If you don.t configure requests and limits, pod receives .best-effort. QoS and pod has the lowest priority. In cases where node runs out of non-shareable resources, kubelet.s out-of-resource eviction policy evicts/kills the pods with best-effort QoS first.

Burstable QoS: If you configure both resource requests and limits, and set the requests to be less than the limit, pod.s QoS will be .Burstable.. Similarly when you only configure the resource requests (without limits), the pod QoS is .Burstable.. When the node runs out of non-shareable resources, kubelet will kill .Burstable. Pods only when there are no more .best-effort. pods running. The Burstable pod receives medium priority.

Guaranteed QoS:  If you set the requests and the limits to equal values, pod will have .Guranteed. QoS and pod will be considered as of the top most priority. These settings indicates that your pod will consume a fixed amount of memory and CPU. With this configuration, if a node runs out of shareable resources, Kubernetes will kill the best-effort and the burstable Pods first before terminating these Guaranteed QoS Pods. These are the highest priority Pods.

## Java heap size and pod memory request/limit considerations
It.s extremely important to set correct heap size for JVM-based applications.  If available memory on node or memory allocated to container is not sufficient for specified JVM heap arguments (and additional off-heap memory), it is possible for WL process to run out of memory. In order to avoid this, you will need to make sure that configured heap sizes are not too big and that the pod is scheduled on the node with sufficient memory.
With the latest Java version, it.s possible to rely on the default JVM heap settings which are safe but quite conservative. If you configure the memory limit for a container but don.t configure heap sizes (-Xms and -Xmx), JVM will configure max heap size to 25% (1/4th) of container memory limit by default. The minimum heap size is configured to 1.56% (1/64th) of limit value.

### Default heap size and resource request values for sample WebLogic Server Pods:
The samples configure default min and max heap size for WebLogic server java process to 256MB and 512MB respectively. This can be changed using USER_MEM_ARGS environment variable. The default min and max heap size for node-manager process is 64MB and 100MB. This can be changed by using NODEMGR_MEM_ARGS environment variable. 

The default memory request in samples for WebLogic server pod is 768MB and default CPU request is 250m. This can be changed during domain creation in resources section.

There.s no memory or CPU limit configured by default in samples and default QoS for WebLogic server pod is Burstable. If your use-case and workload requires higher QoS and priority, this can be achieved by setting memory and CPU limits. You.ll need to run tests and experiment with different memory/CPU limits to determine optimal limit values.

### Configure min/max heap size in percentages using "-XX:MinRAMPercentage" and "-XX:MaxRAMPercentage"
If you specify pod memory limit, it's recommended to configure heap size as a percentage of the total RAM (memory) specified in the pod memory limit. These parameters allow you to fine-tune the heap size . the meaning of those settings is explained in this excellent answer on StackOverflow. Please note . they set the percentage, not the fixed values. Thanks to it changing container memory settings will not break anything. 
When configuring memory limits, it.s important to make sure that the limit is sufficiently big to accommodate the configured heap (and off-heap) requirements, but it's not too big to waste memory resource. Since pod memory will never go above the limit, if JVM's memory usage (sum of heap and native memory) goes above the limit, JVM process will be killed due to out-of-memory error and WebLogic container will be restarted due to liveness probe failure.   Additionally there's also a node-manager process that.s running in same container and it has it's own heap and off-heap requirements. You can also fine tune the node manager heap size in percentages by setting "-XX:MinRAMPercentage" and "-XX:MaxRAMPercentage" using .NODEMGR_JAVA_OPTIONS. environment variable. 

### Using "-Xms" and "-Xmx" parameters when not configuring limits 
In some cases, it.s difficult to come up with a hard limit for the container and you might only want to configure memory requests but not configure memory limits. In such scenarios, you can use traditional approach to set min/max heap size using .-Xms. and .-Xmx..

### CPU requests and limits 
It.s important that the containers running WebLogic applications have enough CPU resources, otherwise applications performance can suffer. You also don't want to set CPU requests and limit too high if your application don't need or use it. Since CPU is a shared resource, if the amount of CPU that you reserve is more than required by your application, the CPU cycles will go unused and be wasted. If no CPU request and limit is configured, it can end up using all CPU resources available on node. This can starve other containers from using shareable CPU cycles. 

One other thing to keep in mind is that if pod CPU limit is not configured, it might lead to incorrect garbage collection (GC) strategy selection. WebLogic self-tuning work-manager uses pod CPU limit to configure the  number of threads in a default thread pool. If you don.t specify container CPU limit, the performance might be affected due to incorrect number of GC threads or wrong WebLogic server thread pool size. 

## Beware of setting resource limits too high
It.s important to keep in mind that if you set a value of CPU core count that.s larger than core count of the biggest node, then the pod will never be scheduled. Let.s say you have a pod that needs 4 cores but you have a kubernetes cluster that.s comprised of 2 core VMs. In this case, your pod will never be scheduled.  WebLogic applications are normally designed to take advantage of multiple cores and should be given CPU requests as such. CPUs are considered as a compressible resource. If your apps are hitting CPU limits, kubernetes will start to throttle your container. This means your CPU will be artificially restricted, giving your app potentially worse performance. However it won.t be terminated or evicted. 
Just like CPU, if you put a memory request that.s larger than amount of memory on your nodes, the pod will never be scheduled.
## CPU Affinity and lock contention in k8s
We observed much higher lock contention in k8s env when running some workloads in kubernetes as compared to traditional env. The lock contention seem to be caused by the lack of CPU cache affinity and/or scheduling latency when the workload moves to different CPU cores.  

In traditional (non-k8s) environment, often tests are run with CPU affinity by binding WLS java process to particular CPU core(s) (using taskset command). This results in reduced lock contention and better performance. 

In k8s environment. when CPU manager policy is configured to be "static" and QOS is "Guaranteed" for WLS pods, we see reduced lock contention and better performance. The default CPU manager policy is "none" (default). Please refer to controlling CPU management policies for more details.

## References:
1) https://cloud.google.com/blog/products/gcp/kubernetes-best-practices-resource-requests-and-limits
2) https://blog.softwaremill.com/docker-support-in-new-java-8-finally-fd595df0ca54
3) https://kubernetes.io/docs/concepts/configuration/pod-priority-preemption/
4) https://www.magalix.com/blog/kubernetes-patterns-capacity-planning 
