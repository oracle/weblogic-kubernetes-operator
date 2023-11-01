### Cluster

A Cluster resource describes the lifecycle options for all of the Managed Server members of a WebLogic cluster, including Java options, environment variables, additional Pod content, and the ability to explicitly start, stop, or restart cluster members. It must describe a cluster that already exists in the WebLogic domain configuration. See also `domain.spec.clusters`.

| Name | Type | Description |
| --- | --- | --- |
| `apiVersion` | string | The API version defines the versioned schema of this cluster. |
| `kind` | string | The type of the REST resource. Must be "Cluster". |
| `metadata` | [Object Meta](k8s1.13.5.md#object-meta) | The resource metadata. Must include the `name` and `namespace. |
| `spec` | [Cluster Spec](#cluster-spec) | The specification of the operation of the WebLogic cluster. Required. |
| `status` | [Cluster Status](#cluster-status) | The current status of the operation of the WebLogic cluster. Updated automatically by the operator. |

### Cluster Spec

The specification of the operation of the WebLogic cluster. Required.

| Name | Type | Description |
| --- | --- | --- |
| `clusterName` | string | The name of the cluster. This value must match the name of a WebLogic cluster already defined in the WebLogic domain configuration. Required. |
| `clusterService` | [Cluster Service](#cluster-service) | Customization affecting Kubernetes Service generated for this WebLogic cluster. |
| `maxConcurrentShutdown` | integer | The maximum number of WebLogic Server instances that will shut down in parallel for this cluster when it is being partially shut down by lowering its replica count. A value of 0 means there is no limit. Defaults to `spec.maxClusterConcurrentShutdown`, which defaults to 1. |
| `maxConcurrentStartup` | integer | The maximum number of Managed Servers instances that the operator will start in parallel for this cluster in response to a change in the `replicas` count. If more Managed Server instances must be started, the operator will wait until a Managed Server Pod is in the `Ready` state before starting the next Managed Server instance. A value of 0 means all Managed Server instances will start in parallel. Defaults to `domain.spec.maxClusterConcurrentStartup`, which defaults to 0. |
| `maxUnavailable` | integer | The maximum number of cluster members that can be temporarily unavailable. Defaults to `domain.spec.maxClusterUnavailable`, which defaults to 1. |
| `replicas` | integer | The number of cluster member Managed Server instances to start for this WebLogic cluster. The operator will sort cluster member Managed Server names from the WebLogic domain configuration by normalizing any numbers in the Managed Server name and then sorting alphabetically. This is done so that server names such as "managed-server10" come after "managed-server9". The operator will then start Managed Server instances from the sorted list, up to the `replicas` count, unless specific Managed Servers are specified as starting in their entry under the `managedServers` field. In that case, the specified Managed Server instances will be started and then additional cluster members will be started, up to the `replicas` count, by finding further cluster members in the sorted list that are not already started. If cluster members are started because of their related entries under `managedServers`, then this cluster may have more cluster members running than its `replicas` count. Defaults to `domain.spec.replicas`, which defaults 1. |
| `restartVersion` | string | Changes to this field cause the operator to restart WebLogic Server instances. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#restarting-servers. |
| `serverPod` | [Server Pod](#server-pod) | Customization affecting the generation of Pods for WebLogic Server instances. |
| `serverService` | [Server Service](#server-service) | Customization affecting the generation of ClusterIP Services for WebLogic Server instances. |
| `serverStartPolicy` | string | The strategy for deciding whether to start a WebLogic Server instance. Legal values are `Never`, or `IfNeeded`. Defaults to `IfNeeded`. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers. |

### Cluster Status

| Name | Type | Description |
| --- | --- | --- |
| `clusterName` | string | WebLogic cluster name. |
| `conditions` | Array of [Cluster Condition](#cluster-condition) | Current service state of the cluster. |
| `labelSelector` | string | Label selector that can be used to discover Pods associated with WebLogic managed servers belonging to this cluster. Must be set to work with HorizontalPodAutoscaler. |
| `maximumReplicas` | integer | The maximum number of cluster members. |
| `minimumReplicas` | integer | The minimum number of cluster members. |
| `observedGeneration` | integer | The Cluster resource generation observed by the WebLogic operator. If the Cluster resource exists, then this value will match the 'cluster.metadata.generation'  when the 'cluster.status' correctly reflects the latest cluster resource changes. |
| `readyReplicas` | integer | The number of ready cluster members. |
| `replicas` | integer | The number of currently running cluster members. |
| `replicasGoal` | integer | The requested number of cluster members. Cluster members will be started by the operator if this value is larger than zero. |

### Cluster Service

| Name | Type | Description |
| --- | --- | --- |
| `annotations` | Map | The annotations to be added to generated resources. |
| `labels` | Map | The labels to be added to generated resources. The label names must not start with "weblogic.". |
| `sessionAffinity` | string | Advanced setting to enable client IP based session affinity. Must be ClientIP or None. Defaults to None. More info: https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-resource/#cluster-spec-elements |

### Server Pod

| Name | Type | Description |
| --- | --- | --- |
| `affinity` | [Affinity](k8s1.13.5.md#affinity) | The Pod's scheduling constraints. More info: https://oracle.github.io/weblogic-kubernetes-operator/faq/node-heating/.  See `kubectl explain pods.spec.affinity`. |
| `annotations` | Map | The annotations to be added to generated resources. |
| `containers` | Array of [Container](k8s1.13.5.md#container) | Additional containers to be included in the server Pod. See `kubectl explain pods.spec.containers`. |
| `containerSecurityContext` | [Security Context](k8s1.13.5.md#security-context) | Container-level security attributes. Will override any matching Pod-level attributes. See `kubectl explain pods.spec.containers.securityContext`. Beginning with operator version 4.0.5, if no value is specified for this field, the operator will use default content for container-level `securityContext`. More info: https://oracle.github.io/weblogic-kubernetes-operator/security/domain-security/pod-and-container/. |
| `env` | Array of [Env Var](k8s1.13.5.md#env-var) | A list of environment variables to set in the container running a WebLogic Server instance. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-resource/#jvm-memory-and-java-option-environment-variables. See `kubectl explain pods.spec.containers.env`. |
| `envFrom` | Array of [Env From Source](k8s1.13.5.md#env-from-source) | List of sources to populate environment variables in the container running a WebLogic Server instance. The sources include either a config map or a secret. The operator will not expand the dependent variables in the 'envFrom' source. More details: https://kubernetes.io/docs/tasks/inject-data-application/define-environment-variable-container/#define-an-environment-variable-for-a-container. Also see: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-resource/#jvm-memory-and-java-option-environment-variables. |
| `hostAliases` | Array of [Host Alias](k8s1.13.5.md#host-alias) | HostAliases is an optional list of hosts and IPs that will be injected into the pod's hosts file if specified. This is only valid for non-hostNetwork pods. |
| `initContainers` | Array of [Container](k8s1.13.5.md#container) | Initialization containers to be included in the server Pod. See `kubectl explain pods.spec.initContainers`. |
| `labels` | Map | The labels to be added to generated resources. The label names must not start with "weblogic.". |
| `livenessProbe` | [Probe Tuning](#probe-tuning) | Settings for the liveness probe associated with a WebLogic Server instance. |
| `maxPendingWaitTimeSeconds` | integer | The maximum time in seconds that the operator waits for a WebLogic Server pod to reach the running state before it considers the pod failed. Defaults to 5 minutes. |
| `maxReadyWaitTimeSeconds` | integer | The maximum time in seconds that the operator waits for a WebLogic Server pod to reach the ready state before it considers the pod failed. Defaults to 1800 seconds. |
| `nodeName` | string | NodeName is a request to schedule this Pod onto a specific Node. If it is non-empty, the scheduler simply schedules this pod onto that node, assuming that it fits the resource requirements. See `kubectl explain pods.spec.nodeName`. |
| `nodeSelector` | Map | Selector which must match a Node's labels for the Pod to be scheduled on that Node. See `kubectl explain pods.spec.nodeSelector`. |
| `podSecurityContext` | [Pod Security Context](k8s1.13.5.md#pod-security-context) | Pod-level security attributes. See `kubectl explain pods.spec.securityContext`. Beginning with operator version 4.0.5, if no value is specified for this field, the operator will use default content for the pod-level `securityContext`. More info: https://oracle.github.io/weblogic-kubernetes-operator/security/domain-security/pod-and-container/. |
| `priorityClassName` | string | If specified, indicates the Pod's priority. "system-node-critical" and "system-cluster-critical" are two special keywords which indicate the highest priorities with the former being the highest priority. Any other name must be defined by creating a PriorityClass object with that name. If not specified, the pod priority will be the default or zero, if there is no default. See `kubectl explain pods.spec.priorityClassName`. |
| `readinessGates` | Array of [Pod Readiness Gate](k8s1.13.5.md#pod-readiness-gate) | If specified, all readiness gates will be evaluated for Pod readiness. A Pod is ready when all its containers are ready AND all conditions specified in the readiness gates have a status equal to "True". More info: https://github.com/kubernetes/community/blob/master/keps/sig-network/0007-pod-ready%2B%2B.md. |
| `readinessProbe` | [Probe Tuning](#probe-tuning) | Settings for the readiness probe associated with a WebLogic Server instance. |
| `resources` | [Resource Requirements](k8s1.13.5.md#resource-requirements) | Memory and CPU minimum requirements and limits for the WebLogic Server instance. See `kubectl explain pods.spec.containers.resources`. |
| `restartPolicy` | string | Restart policy for all containers within the Pod. One of Always, OnFailure, Never. Default to Always. More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#restart-policy. See `kubectl explain pods.spec.restartPolicy`. |
| `runtimeClassName` | string | RuntimeClassName refers to a RuntimeClass object in the node.k8s.io group, which should be used to run this Pod. If no RuntimeClass resource matches the named class, the Pod will not be run. If unset or empty, the "legacy" RuntimeClass will be used, which is an implicit class with an empty definition that uses the default runtime handler. More info: https://github.com/kubernetes/community/blob/master/keps/sig-node/0014-runtime-class.md This is an alpha feature and may change in the future. See `kubectl explain pods.spec.runtimeClassName`. |
| `schedulerName` | string | If specified, the Pod will be dispatched by the specified scheduler. If not specified, the Pod will be dispatched by the default scheduler. See `kubectl explain pods.spec.schedulerName`. |
| `serviceAccountName` | string | Name of the ServiceAccount to be used to run this Pod. If it is not set, default ServiceAccount will be used. The ServiceAccount has to exist at the time the Pod is created. See `kubectl explain pods.spec.serviceAccountName`. |
| `shutdown` | [Shutdown](#shutdown) | Configures how the operator should shut down the server instance. |
| `tolerations` | Array of [Toleration](k8s1.13.5.md#toleration) | If specified, the Pod's tolerations. See `kubectl explain pods.spec.tolerations`. |
| `topologySpreadConstraints` | Array of [V 1 Topology Spread Constraint](#v-1-topology-spread-constraint) | TopologySpreadConstraints describes how a group of pods ought to spread across topology domains. Scheduler will schedule pods in a way which abides by the constraints. All topologySpreadConstraints are ANDed. |
| `volumeMounts` | Array of [Volume Mount](k8s1.13.5.md#volume-mount) | Additional volume mounts for the container running a WebLogic Server instance. See `kubectl explain pods.spec.containers.volumeMounts`. |
| `volumes` | Array of [Volume](k8s1.13.5.md#volume) | Additional volumes to be created in the server Pod. See `kubectl explain pods.spec.volumes`. |

### Server Service

| Name | Type | Description |
| --- | --- | --- |
| `annotations` | Map | The annotations to be added to generated resources. |
| `labels` | Map | The labels to be added to generated resources. The label names must not start with "weblogic.". |
| `precreateService` | Boolean | If true, the operator will create ClusterIP Services even for WebLogic Server instances without running Pods. |

### Cluster Condition

| Name | Type | Description |
| --- | --- | --- |
| `lastTransitionTime` | DateTime | Last time the condition transitioned from one status to another. |
| `message` | string | Human-readable message indicating details about last transition. |
| `status` | string | The status of the condition. Can be True, False. |
| `type` | string | The type of the condition. Valid types are Completed, Available, Failed, and Rolling. |

### Probe Tuning

| Name | Type | Description |
| --- | --- | --- |
| `failureThreshold` | integer | Number of times the check is performed before giving up. Giving up in case of liveness probe means restarting the container. In case of readiness probe, the Pod will be marked Unready. Defaults to 1. |
| `initialDelaySeconds` | integer | The number of seconds before the first check is performed. |
| `periodSeconds` | integer | The number of seconds between checks. |
| `successThreshold` | integer | Minimum number of times the check needs to pass for the probe to be considered successful after having failed. Defaults to 1. Must be 1 for liveness Probe. |
| `timeoutSeconds` | integer | The number of seconds with no response that indicates a failure. |

### Shutdown

| Name | Type | Description |
| --- | --- | --- |
| `ignoreSessions` | Boolean | For graceful shutdown only, indicates to ignore pending HTTP sessions during in-flight work handling. Defaults to false. |
| `shutdownType` | string | Specifies how the operator will shut down server instances. Legal values are `Graceful` and `Forced`. Defaults to `Graceful`. |
| `skipWaitingCohEndangeredState` | Boolean | For graceful shutdown only, set to true to skip waiting for Coherence Cache Cluster service MBean HAStatus in safe state before shutdown. By default, the operator will wait until it is safe to shutdown the Coherence Cache Cluster. Defaults to false. |
| `timeoutSeconds` | integer | For graceful shutdown only, number of seconds to wait before aborting in-flight work and shutting down the server. Defaults to 30 seconds. |
| `waitForAllSessions` | Boolean | For graceful shutdown only, set to true to wait for all HTTP sessions during in-flight work handling; false to wait for non-persisted HTTP sessions only. Defaults to false. |

### V 1 Topology Spread Constraint

TopologySpreadConstraint specifies how to spread matching pods among the given topology.

| Name | Type | Description |
| --- | --- | --- |
| `labelSelector` | [Label Selector](k8s1.13.5.md#label-selector) | A label selector is a label query over a set of resources. The result of matchLabels and matchExpressions are ANDed. An empty label selector matches all objects. A null label selector matches no objects. |
| `matchLabelKeys` | Array of string | MatchLabelKeys is a set of pod label keys to select the pods over which spreading will be calculated. The keys are used to lookup values from the incoming pod labels, those key-value labels are ANDed with labelSelector to select the group of existing pods over which spreading will be calculated for the incoming pod. The same key is forbidden to exist in both MatchLabelKeys and LabelSelector. MatchLabelKeys cannot be set when LabelSelector isn't set. Keys that don't exist in the incoming pod labels will be ignored. A null or empty list means only match against labelSelector.  This is a beta field and requires the MatchLabelKeysInPodTopologySpread feature gate to be enabled (enabled by default). |
| `maxSkew` | integer | MaxSkew describes the degree to which pods may be unevenly distributed. When `whenUnsatisfiable=DoNotSchedule`, it is the maximum permitted difference between the number of matching pods in the target topology and the global minimum. The global minimum is the minimum number of matching pods in an eligible domain or zero if the number of eligible domains is less than MinDomains. For example, in a 3-zone cluster, MaxSkew is set to 1, and pods with the same labelSelector spread as 2/2/1: In this case, the global minimum is 1. | zone1 | zone2 | zone3 | |  P P  |  P P  |   P   | - if MaxSkew is 1, incoming pod can only be scheduled to zone3 to become 2/2/2; scheduling it onto zone1(zone2) would make the ActualSkew(3-1) on zone1(zone2) violate MaxSkew(1). - if MaxSkew is 2, incoming pod can be scheduled onto any zone. When `whenUnsatisfiable=ScheduleAnyway`, it is used to give higher precedence to topologies that satisfy it. It's a required field. Default value is 1 and 0 is not allowed. |
| `minDomains` | integer | MinDomains indicates a minimum number of eligible domains. When the number of eligible domains with matching topology keys is less than minDomains, Pod Topology Spread treats "global minimum" as 0, and then the calculation of Skew is performed. And when the number of eligible domains with matching topology keys equals or greater than minDomains, this value has no effect on scheduling. As a result, when the number of eligible domains is less than minDomains, scheduler won't schedule more than maxSkew Pods to those domains. If value is nil, the constraint behaves as if MinDomains is equal to 1. Valid values are integers greater than 0. When value is not nil, WhenUnsatisfiable must be DoNotSchedule.  For example, in a 3-zone cluster, MaxSkew is set to 2, MinDomains is set to 5 and pods with the same labelSelector spread as 2/2/2: | zone1 | zone2 | zone3 | |  P P  |  P P  |  P P  | The number of domains is less than 5(MinDomains), so "global minimum" is treated as 0. In this situation, new pod with the same labelSelector cannot be scheduled, because computed skew will be 3(3 - 0) if new Pod is scheduled to any of the three zones, it will violate MaxSkew.  This is a beta field and requires the MinDomainsInPodTopologySpread feature gate to be enabled (enabled by default). |
| `nodeAffinityPolicy` | string | NodeAffinityPolicy indicates how we will treat Pod's nodeAffinity/nodeSelector when calculating pod topology spread skew. Options are: - Honor: only nodes matching nodeAffinity/nodeSelector are included in the calculations. - Ignore: nodeAffinity/nodeSelector are ignored. All nodes are included in the calculations.  If this value is nil, the behavior is equivalent to the Honor policy. This is a beta-level feature default enabled by the NodeInclusionPolicyInPodTopologySpread feature flag. |
| `nodeTaintsPolicy` | string | NodeTaintsPolicy indicates how we will treat node taints when calculating pod topology spread skew. Options are: - Honor: nodes without taints, along with tainted nodes for which the incoming pod has a toleration, are included. - Ignore: node taints are ignored. All nodes are included.  If this value is nil, the behavior is equivalent to the Ignore policy. This is a beta-level feature default enabled by the NodeInclusionPolicyInPodTopologySpread feature flag. |
| `topologyKey` | string | TopologyKey is the key of node labels. Nodes that have a label with this key and identical values are considered to be in the same topology. We consider each <key, value> as a "bucket", and try to put balanced number of pods into each bucket. We define a domain as a particular instance of a topology. Also, we define an eligible domain as a domain whose nodes meet the requirements of nodeAffinityPolicy and nodeTaintsPolicy. e.g. If TopologyKey is "kubernetes.io/hostname", each Node is a domain of that topology. And, if TopologyKey is "topology.kubernetes.io/zone", each zone is a domain of that topology. It's a required field. |
| `whenUnsatisfiable` | string | WhenUnsatisfiable indicates how to deal with a pod if it doesn't satisfy the spread constraint. - DoNotSchedule (default) tells the scheduler not to schedule it. - ScheduleAnyway tells the scheduler to schedule the pod in any location,   but giving higher precedence to topologies that would help reduce the   skew. A constraint is considered "Unsatisfiable" for an incoming pod if and only if every possible node assignment for that pod would violate "MaxSkew" on some topology. For example, in a 3-zone cluster, MaxSkew is set to 1, and pods with the same labelSelector spread as 3/1/1: | zone1 | zone2 | zone3 | | P P P |   P   |   P   | If WhenUnsatisfiable is set to DoNotSchedule, incoming pod can only be scheduled to zone2(zone3) to become 3/2/1(3/1/2) as ActualSkew(2-1) on zone2(zone3) satisfies MaxSkew(1). In other words, the cluster can still be imbalanced, but scheduler won't make it *more* imbalanced. It's a required field. |