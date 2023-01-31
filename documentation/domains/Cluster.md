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
| `timeoutSeconds` | integer | For graceful shutdown only, number of seconds to wait before aborting in-flight work and shutting down the server. Defaults to 30 seconds. |
| `waitForAllSessions` | Boolean | For graceful shutdown only, set to true to wait for all HTTP sessions during in-flight work handling; false to wait for non-persisted HTTP sessions only. Defaults to false. |