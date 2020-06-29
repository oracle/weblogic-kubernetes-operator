### Domain

| Name | Type | Description |
| --- | --- | --- |
| `apiVersion` | string | The API version defines the versioned schema of this Domain. Required. |
| `kind` | string | The type of the REST resource. Must be "Domain". Required. |
| `metadata` | [Object Meta](k8s1.13.5.md#object-meta) | The resource metadata. Must include the `name` and `namespace`. Required. |
| `spec` | [Domain Spec](#domain-spec) | The specification of the operation of the WebLogic domain. Required. |
| `status` | [Domain Status](#domain-status) | The current status of the operation of the WebLogic domain. Updated automatically by the operator. |

### Domain Spec

The specification of the operation of the WebLogic domain. Required.

| Name | Type | Description |
| --- | --- | --- |
| `adminServer` | [Admin Server](#admin-server) | Lifecycle options for the Administration Server, including Java options, environment variables, additional Pod content, and which channels or network access points should be exposed using a NodePort Service. |
| `allowReplicasBelowMinDynClusterSize` | Boolean | Whether to allow the number of running cluster member Managed Server instances to drop below the minimum dynamic cluster size configured in the WebLogic domain configuration, if this is not specified for a specific cluster under the `clusters` field. Defaults to true. |
| `clusters` | array of [Cluster](#cluster) | Lifecycle options for all of the Managed Server members of a WebLogic cluster, including Java options, environment variables, additional Pod content, and the ability to explicitly start, stop, or restart cluster members. The `clusterName` field of each entry must match a cluster that already exists in the WebLogic domain configuration. |
| `configOverrides` | string | Deprecated. Use `configuration.overridesConfigMap` instead. Ignored if `configuration.overridesConfigMap` is specified. The name of the ConfigMap for optional WebLogic configuration overrides. |
| `configOverrideSecrets` | array of string | Deprecated. Use `configuration.secrets` instead. Ignored if `configuration.secrets` is specified. A list of names of the Secrets for optional WebLogic configuration overrides. |
| `configuration` | [Configuration](#configuration) | Models and overrides affecting the WebLogic domain configuration. |
| `dataHome` | string | An optional directory in a server's container for data storage of default and custom file stores. If `dataHome` is not specified or its value is either not set or empty, then the data storage directories are determined from the WebLogic domain configuration. |
| `domainHome` | string | The directory containing the WebLogic domain configuration inside the container. Defaults to /shared/domains/domains/<domainUID> if domainHomeSourceType is PersistentVolume. Defaults to /u01/oracle/user_projects/domains/ if domainHomeSourceType is Image. Defaults to /u01/domains/<domainUID> if domainHomeSourceType is FromModel. |
| `domainHomeInImage` | Boolean | Deprecated. Use `domainHomeSourceType` instead. Ignored if `domainHomeSourceType` is specified. True indicates that the domain home file system is present in the container image specified by the image field. False indicates that the domain home file system is located on a persistent volume. Defaults to unset. |
| `domainHomeSourceType` | string | Domain home file system source type: Legal values: Image, PersistentVolume, FromModel. Image indicates that the domain home file system is present in the container image specified by the `image` field. PersistentVolume indicates that the domain home file system is located on a persistent volume. FromModel indicates that the domain home file system will be created and managed by the operator based on a WDT domain model. If this field is specified, it overrides the value of `domainHomeInImage`. If both fields are unspecified, then `domainHomeSourceType` defaults to Image. |
| `domainUID` | string | Domain unique identifier. It is recommended that this value be unique to assist in future work to identify related domains in active-passive scenarios across data centers; however, it is only required that this value be unique within the namespace, similarly to the names of Kubernetes resources. This value is distinct and need not match the domain name from the WebLogic domain configuration. Defaults to the value of `metadata.name`. |
| `httpAccessLogInLogHome` | Boolean | Specifies whether the server HTTP access log files will be written to the same directory specified in `logHome`. Otherwise, server HTTP access log files will be written to the directory configured in the WebLogic domain configuration. Defaults to true. |
| `image` | string | The WebLogic container image; required when `domainHomeSourceType` is Image or FromModel; otherwise, defaults to container-registry.oracle.com/middleware/weblogic:12.2.1.4. |
| `imagePullPolicy` | string | The image pull policy for the WebLogic container image. Legal values are Always, Never and IfNotPresent. Defaults to Always if image ends in :latest; IfNotPresent, otherwise. |
| `imagePullSecrets` | array of [Local Object Reference](k8s1.13.5.md#local-object-reference) | A list of image pull Secrets for the WebLogic container image. |
| `includeServerOutInPodLog` | Boolean | Specifies whether the server .out file will be included in the Pod's log. Defaults to true. |
| `introspectVersion` | string | Changes to this field cause the operator to repeat its introspection of the WebLogic domain configuration. Repeating introspection is required for the operator to recognize changes to the domain configuration, such as adding a new WebLogic cluster or Managed Server instance, to regenerate configuration overrides, or to regenerate the WebLogic domain home when the `domainHomeSourceType` is FromModel. Introspection occurs automatically, without requiring change to this field, when servers are first started or restarted after a full domain shutdown. For the FromModel `domainHomeSourceType`, introspection also occurs when a running server must be restarted because of changes to any of the fields listed here: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#properties-that-cause-servers-to-be-restarted. See also `overridesConfigurationStrategy`. |
| `logHome` | string | The directory in a server's container in which to store the domain, Node Manager, server logs, server *.out, and optionally HTTP access log files if `httpAccessLogInLogHome` is true. Ignored if `logHomeEnabled` is false. |
| `logHomeEnabled` | Boolean | Specifies whether the log home folder is enabled. Defaults to true if `domainHomeSourceType` is PersistentVolume; false, otherwise. |
| `managedServers` | array of [Managed Server](#managed-server) | Lifecycle options for individual Managed Servers, including Java options, environment variables, additional Pod content, and the ability to explicitly start, stop, or restart a named server instance. The `serverName` field of each entry must match a Managed Server that already exists in the WebLogic domain configuration or that matches a dynamic cluster member based on the server template. |
| `maxClusterConcurrentStartup` | number | The maximum number of cluster member Managed Server instances that the operator will start in parallel for a given cluster, if `maxConcurrentStartup` is not specified for a specific cluster under the `clusters` field. A value of 0 means there is no configured limit. Defaults to 0. |
| `replicas` | number | The default number of cluster member Managed Server instances to start for each WebLogic cluster in the domain configuration, unless `replicas` is specified for that cluster under the `clusters` field. For each cluster, the operator will sort cluster member Managed Server names from the WebLogic domain configuration by normalizing any numbers in the Managed Server name and then sorting alphabetically. This is done so that server names such as "managed-server10" come after "managed-server9". The operator will then start Managed Servers from the sorted list, up to the `replicas` count, unless specific Managed Servers are specified as starting in their entry under the `managedServers` field. In that case, the specified Managed Servers will be started and then additional cluster members will be started, up to the `replicas` count, by finding further cluster members in the sorted list that are not already started. If cluster members are started because of their entries under `managedServers`, then a cluster may have more cluster members running than its `replicas` count. Defaults to 0. |
| `restartVersion` | string | Changes to this field cause the operator to restart WebLogic Server instances. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#restarting-servers. |
| `serverPod` | [Server Pod](#server-pod) | Customization affecting the generation of Pods for WebLogic Server instances. |
| `serverService` | [Server Service](#server-service) | Customization affecting the generation of Kubernetes Services for WebLogic Server instances. |
| `serverStartPolicy` | string | The strategy for deciding whether to start a WebLogic Server instance. Legal values are ADMIN_ONLY, NEVER, or IF_NEEDED. Defaults to IF_NEEDED. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers. |
| `serverStartState` | string | The WebLogic runtime state in which the server is to be started. Use ADMIN if the server should start in the admin state. Defaults to RUNNING. |
| `webLogicCredentialsSecret` | [Secret Reference](k8s1.13.5.md#secret-reference) | Reference to a Kubernetes Secret that contains the user name and password needed to boot a WebLogic Server under the `username` and `password` fields. |

### Domain Status

The current status of the operation of the WebLogic domain. Updated automatically by the operator.

| Name | Type | Description |
| --- | --- | --- |
| `clusters` | array of [Cluster Status](#cluster-status) | Status of WebLogic clusters in this domain. |
| `conditions` | array of [Domain Condition](#domain-condition) | Current service state of the domain. |
| `message` | string | A human readable message indicating details about why the domain is in this condition. |
| `reason` | string | A brief CamelCase message indicating details about why the domain is in this state. |
| `replicas` | number | The number of running cluster member Managed Servers in the WebLogic cluster if there is exactly one cluster defined in the domain configuration and where the `replicas` field is set at the `spec` level rather than for the specific cluster under `clusters`. This field is provided to support use of Kubernetes scaling for this limited use case. |
| `servers` | array of [Server Status](#server-status) | Status of WebLogic Servers in this domain. |
| `startTime` | DateTime | RFC 3339 date and time at which the operator started the domain. This will be when the operator begins processing and will precede when the various servers or clusters are available. |

### Admin Server

| Name | Type | Description |
| --- | --- | --- |
| `adminService` | [Admin Service](#admin-service) | Customization affecting the generation of the Kubernetes Service for the Administration Server. These settings can also specify the creation of a second NodePort Service to expose specific channels or network access points outside the Kubernetes cluster. |
| `restartVersion` | string | Changes to this field cause the operator to restart WebLogic Server instances. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#restarting-servers. |
| `serverPod` | [Server Pod](#server-pod) | Customization affecting the generation of Pods for WebLogic Server instances. |
| `serverService` | [Server Service](#server-service) | Customization affecting the generation of Kubernetes Services for WebLogic Server instances. |
| `serverStartPolicy` | string | The strategy for deciding whether to start a WebLogic Server instance. Legal values are ALWAYS, NEVER, or IF_NEEDED. Defaults to IF_NEEDED. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers. |
| `serverStartState` | string | The WebLogic runtime state in which the server is to be started. Use ADMIN if the server should start in the admin state. Defaults to RUNNING. |

### Cluster

| Name | Type | Description |
| --- | --- | --- |
| `allowReplicasBelowMinDynClusterSize` | Boolean | Specifies whether the number of running cluster members is allowed to drop below the minimum dynamic cluster size configured in the WebLogic domain configuration. Otherwise, the operator will ensure that the number of running cluster members is not less than the minimum dynamic cluster setting. This setting applies to dynamic clusters only. Defaults to true. |
| `clusterName` | string | The name of the cluster. This value must match the name of a WebLogic cluster already defined in the WebLogic domain configuration. Required. |
| `clusterService` | [Kubernetes Resource](#kubernetes-resource) | Customization affecting Kubernetes Service generated for this WebLogic cluster. |
| `maxConcurrentStartup` | number | The maximum number of Managed Servers instances that the operator will start in parallel for this cluster in response to a change in the `replicas` count. If more Managed Server instances must be started, the operator will wait until a Managed Server Pod is in the `Ready` state before starting the next Managed Server instance. A value of 0 means all Managed Server instances will start in parallel. Defaults to 0. |
| `maxUnavailable` | number | The maximum number of cluster members that can be temporarily unavailable. Defaults to 1. |
| `replicas` | number | The number of cluster member Managed Server instances to start for this WebLogic cluster. The operator will sort cluster member Managed Server names from the WebLogic domain configuration by normalizing any numbers in the Managed Server name and then sorting alphabetically. This is done so that server names such as "managed-server10" come after "managed-server9". The operator will then start Managed Server instances from the sorted list, up to the `replicas` count, unless specific Managed Servers are specified as starting in their entry under the `managedServers` field. In that case, the specified Managed Server instances will be started and then additional cluster members will be started, up to the `replicas` count, by finding further cluster members in the sorted list that are not already started. If cluster members are started because of their related entries under `managedServers`, then this cluster may have more cluster members running than its `replicas` count. Defaults to 0. |
| `restartVersion` | string | Changes to this field cause the operator to restart WebLogic Server instances. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#restarting-servers. |
| `serverPod` | [Server Pod](#server-pod) | Customization affecting the generation of Pods for WebLogic Server instances. |
| `serverService` | [Server Service](#server-service) | Customization affecting the generation of Kubernetes Services for WebLogic Server instances. |
| `serverStartPolicy` | string | The strategy for deciding whether to start a WebLogic Server instance. Legal values are NEVER, or IF_NEEDED. Defaults to IF_NEEDED. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers. |
| `serverStartState` | string | The WebLogic runtime state in which the server is to be started. Use ADMIN if the server should start in the admin state. Defaults to RUNNING. |

### Configuration

| Name | Type | Description |
| --- | --- | --- |
| `introspectorJobActiveDeadlineSeconds` | number | The introspector job timeout value in seconds. If this field is specified, then the operator's ConfigMap `data.introspectorJobActiveDeadlineSeconds` value is ignored. Defaults to 120 seconds. |
| `istio` | [Istio](#istio) | The Istio service mesh integration settings. |
| `model` | [Model](#model) | Model in image model files and properties. |
| `opss` | [Opss](#opss) | Settings for OPSS security. |
| `overrideDistributionStrategy` | string | Determines how updated configuration overrides are distributed to already running WebLogic Servers following introspection when the domainHomeSourceType is PersistentVolume or Image.  Configuration overrides are generated during introspection from Secrets, the `overrideConfigMap` field, and WebLogic domain topology. Legal values are DYNAMIC, which means that the operator will distribute updated configuration overrides dynamically to running servers, and ON_RESTART, which means that servers will use updated configuration overrides only after the server's next restart. The selection of ON_RESTART will not cause servers to restart when there are updated configuration overrides available. See also `introspectVersion`. Defaults to DYNAMIC. |
| `overridesConfigMap` | string | The name of the ConfigMap for WebLogic configuration overrides. If this field is specified, then the value of spec.configOverrides is ignored. |
| `secrets` | array of string | A list of names of the Secrets for WebLogic configuration overrides or model. If this field is specified, then the value of spec.configOverrideSecrets is ignored. |

### Managed Server

| Name | Type | Description |
| --- | --- | --- |
| `restartVersion` | string | Changes to this field cause the operator to restart WebLogic Server instances. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#restarting-servers. |
| `serverName` | string | The name of the Managed Server. This name must match the name of a Managed Server instance or of a dynamic cluster member name from a server template already defined in the WebLogic domain configuration. Required. |
| `serverPod` | [Server Pod](#server-pod) | Customization affecting the generation of Pods for WebLogic Server instances. |
| `serverService` | [Server Service](#server-service) | Customization affecting the generation of Kubernetes Services for WebLogic Server instances. |
| `serverStartPolicy` | string | The strategy for deciding whether to start a WebLogic Server instance. Legal values are ALWAYS, NEVER, or IF_NEEDED. Defaults to IF_NEEDED. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers. |
| `serverStartState` | string | The WebLogic runtime state in which the server is to be started. Use ADMIN if the server should start in the admin state. Defaults to RUNNING. |

### Server Pod

| Name | Type | Description |
| --- | --- | --- |
| `affinity` | [Affinity](k8s1.13.5.md#affinity) | If specified, the Pod's scheduling constraints. |
| `annotations` | Map | The annotations to be added to generated resources. |
| `containers` | array of [Container](k8s1.13.5.md#container) | Additional containers to be included in the server Pod. |
| `containerSecurityContext` | [Security Context](k8s1.13.5.md#security-context) | Container-level security attributes. Will override any matching Pod-level attributes. |
| `env` | array of [Env Var](k8s1.13.5.md#env-var) | A list of environment variables to set in the container running a WebLogic Server instance. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-resource/#jvm-memory-and-java-option-environment-variables. |
| `initContainers` | array of [Container](k8s1.13.5.md#container) | Initialization containers to be included in the server Pod. |
| `labels` | Map | The labels to be added to generated resources. The label names must not start with "weblogic.". |
| `livenessProbe` | [Probe Tuning](#probe-tuning) | Settings for the liveness probe associated with a WebLogic Server instance. |
| `nodeName` | string | NodeName is a request to schedule this Pod onto a specific Node. If it is non-empty, the scheduler simply schedules this pod onto that node, assuming that it fits the resource requirements. |
| `nodeSelector` | Map | Selector which must match a Node's labels for the Pod to be scheduled on that Node. |
| `podSecurityContext` | [Pod Security Context](k8s1.13.5.md#pod-security-context) | Pod-level security attributes. |
| `priorityClassName` | string | If specified, indicates the Pod's priority. "system-node-critical" and "system-cluster-critical" are two special keywords which indicate the highest priorities with the former being the highest priority. Any other name must be defined by creating a PriorityClass object with that name. If not specified, the pod priority will be the default or zero, if there is no default. |
| `readinessGates` | array of [Pod Readiness Gate](k8s1.13.5.md#pod-readiness-gate) | If specified, all readiness gates will be evaluated for Pod readiness. A Pod is ready when all its containers are ready AND all conditions specified in the readiness gates have a status equal to "True". More info: https://github.com/kubernetes/community/blob/master/keps/sig-network/0007-pod-ready%2B%2B.md. |
| `readinessProbe` | [Probe Tuning](#probe-tuning) | Settings for the readiness probe associated with a WebLogic Server instance. |
| `resources` | [Resource Requirements](k8s1.13.5.md#resource-requirements) | Memory and CPU minimum requirements and limits for the WebLogic Server instance. |
| `restartPolicy` | string | Restart policy for all containers within the Pod. One of Always, OnFailure, Never. Default to Always. More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#restart-policy. |
| `runtimeClassName` | string | RuntimeClassName refers to a RuntimeClass object in the node.k8s.io group, which should be used to run this Pod. If no RuntimeClass resource matches the named class, the Pod will not be run. If unset or empty, the "legacy" RuntimeClass will be used, which is an implicit class with an empty definition that uses the default runtime handler. More info: https://github.com/kubernetes/community/blob/master/keps/sig-node/0014-runtime-class.md This is an alpha feature and may change in the future. |
| `schedulerName` | string | If specified, the Pod will be dispatched by the specified scheduler. If not specified, the Pod will be dispatched by the default scheduler. |
| `serviceAccountName` | string | Name of the ServiceAccount to be used to run this Pod. If it is not set, default ServiceAccount will be used. The ServiceAccount has to exist at the time the Pod is created. |
| `shutdown` | [Shutdown](#shutdown) | Configures how the operator should shut down the server instance. |
| `tolerations` | array of [Toleration](k8s1.13.5.md#toleration) | If specified, the Pod's tolerations. |
| `volumeMounts` | array of [Volume Mount](k8s1.13.5.md#volume-mount) | Additional volume mounts for the server Pod. |
| `volumes` | array of [Volume](k8s1.13.5.md#volume) | Additional volumes to be created in the server Pod. |

### Server Service

| Name | Type | Description |
| --- | --- | --- |
| `annotations` | Map | The annotations to be added to generated resources. |
| `labels` | Map | The labels to be added to generated resources. The label names must not start with "weblogic.". |
| `precreateService` | Boolean | If true, the operator will create Services even for Managed Server instances without running Pods. |

### Cluster Status

| Name | Type | Description |
| --- | --- | --- |
| `clusterName` | string | WebLogic cluster name. |
| `maximumReplicas` | number | The maximum number of cluster members. |
| `minimumReplicas` | number | The minimum number of cluster members. |
| `readyReplicas` | number | The number of ready cluster members. |
| `replicas` | number | The number of currently running cluster members. |
| `replicasGoal` | number | The requested number of cluster members. Cluster members will be started by the operator if this value is larger than zero. |

### Domain Condition

| Name | Type | Description |
| --- | --- | --- |
| `lastProbeTime` | DateTime | Last time we probed the condition. |
| `lastTransitionTime` | DateTime | Last time the condition transitioned from one status to another. |
| `message` | string | Human-readable message indicating details about last transition. |
| `reason` | string | Unique, one-word, CamelCase reason for the condition's last transition. |
| `status` | string | The status of the condition. Can be True, False, Unknown. |
| `type` | string | The type of the condition. Valid types are Progressing, Available, and Failed. |

### Server Status

| Name | Type | Description |
| --- | --- | --- |
| `clusterName` | string | WebLogic cluster name, if the server is a member of a cluster. |
| `desiredState` | string | Desired state of this WebLogic Server instance. Values are RUNNING, ADMIN, or SHUTDOWN. |
| `health` | [Server Health](#server-health) | Current status and health of a specific WebLogic Server instance. |
| `nodeName` | string | Name of Node that is hosting the Pod containing this WebLogic Server instance. |
| `serverName` | string | WebLogic Server instance name. |
| `state` | string | Current state of this WebLogic Server instance. |

### Admin Service

| Name | Type | Description |
| --- | --- | --- |
| `annotations` | Map | Annotations to associate with the Administration Server's Service(s). |
| `channels` | array of [Channel](#channel) | Specifies which of the Administration Server's WebLogic channels should be exposed outside the Kubernetes cluster via a NodePort Service, along with the port for each channel. If not specified, the Administration Server's NodePort Service will not be created. |
| `labels` | Map | Labels to associate with the Administration Server's Service(s). |

### Kubernetes Resource

| Name | Type | Description |
| --- | --- | --- |
| `annotations` | Map | The annotations to be added to generated resources. |
| `labels` | Map | The labels to be added to generated resources. The label names must not start with "weblogic.". |

### Istio

| Name | Type | Description |
| --- | --- | --- |
| `enabled` | Boolean | True, if this domain is deployed under an Istio service mesh. Defaults to true when the `istio` field is specified. |
| `readinessPort` | number | The operator will create a WebLogic network access point with this port for use by the readiness probe. Defaults to 8888. |

### Model

| Name | Type | Description |
| --- | --- | --- |
| `configMap` | string | Name of a ConfigMap containing the WebLogic Deploy Tooling model. |
| `domainType` | string | WebLogic Deploy Tooling domain type. Legal values: WLS, RestrictedJRF, JRF. Defaults to WLS. |
| `runtimeEncryptionSecret` | string | Runtime encryption secret. Required when `domainHomeSourceType` is set to FromModel. |

### Opss

| Name | Type | Description |
| --- | --- | --- |
| `walletFileSecret` | string | Name of a Secret containing the OPSS key wallet file. |
| `walletPasswordSecret` | string | Name of a Secret containing the OPSS key passphrase. |

### Probe Tuning

| Name | Type | Description |
| --- | --- | --- |
| `initialDelaySeconds` | number | The number of seconds before the first check is performed. |
| `periodSeconds` | number | The number of seconds between checks. |
| `timeoutSeconds` | number | The number of seconds with no response that indicates a failure. |

### Shutdown

| Name | Type | Description |
| --- | --- | --- |
| `ignoreSessions` | Boolean | For graceful shutdown only, indicates to ignore pending HTTP sessions during in-flight work handling. Defaults to false. |
| `shutdownType` | string | Specifies how the operator will shut down server instances. Defaults to graceful shutdown. |
| `timeoutSeconds` | number | For graceful shutdown only, number of seconds to wait before aborting in-flight work and shutting down the server. Defaults to 30 seconds. |

### Server Health

| Name | Type | Description |
| --- | --- | --- |
| `activationTime` | DateTime | RFC 3339 date and time at which the server started. |
| `overallHealth` | string | Server health of this WebLogic Server instance. If the value is "Not available", the operator has failed to read the health. If the value is "Not available (possibly overloaded)", the operator has failed to read the health of the server possibly due to the server is in the overloaded state. |
| `subsystems` | array of [Subsystem Health](#subsystem-health) | Status of unhealthy subsystems, if any. |

### Channel

| Name | Type | Description |
| --- | --- | --- |
| `channelName` | string | Name of the channel. The "default" value refers to the Administration Server's default channel, which is configured using the ServerMBean's ListenPort. The "default-secure" value refers to the Administration Server's default secure channel, which is configured using the ServerMBean's SSLMBean's ListenPort. The "default-admin" value refers to the Administration Server's default administrative channel, which is configured using the DomainMBean's AdministrationPort. Otherwise, provide the name of one of the Administration Server's network access points, which is configured using the ServerMBean's NetworkAccessMBeans. The "default", "default-secure", and "default-admin" channels may not be specified here when using Istio. |
| `nodePort` | number | Specifies the port number used to access the WebLogic channel outside of the Kubernetes cluster. If not specified, defaults to the port defined by the WebLogic channel. |

### Subsystem Health

| Name | Type | Description |
| --- | --- | --- |
| `health` | string | Server health of this WebLogic Server instance. |
| `subsystemName` | string | Name of subsystem providing symptom information. |
| `symptoms` | array of string | Symptoms provided by the reporting subsystem. |