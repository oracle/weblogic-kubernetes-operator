### Domain

Domain represents a WebLogic domain and how it will be realized in the Kubernetes cluster.

| Name | Type | Description |
| --- | --- | --- |
| `apiVersion` | string | The API version for the Domain. |
| `kind` | string | The type of resource. Must be 'Domain'. |
| `metadata` | [Object Meta](k8s1.13.5.md#object-meta) | The domain meta-data. Must include the name and namespace. |
| `spec` | [Domain Spec](#domain-spec) | The specification of the domain. Required. |
| `status` | [Domain Status](#domain-status) | The current status of the domain. Updated by the operator. |

### Domain Spec

DomainSpec is a description of a domain.

| Name | Type | Description |
| --- | --- | --- |
| `adminServer` | [Admin Server](#admin-server) | Configuration for the Administration Server. |
| `clusters` | array of [Cluster](#cluster) | Configuration for the clusters. |
| `configOverrides` | string | The name of the config map for optional WebLogic configuration overrides. |
| `configOverrideSecrets` | array of string | A list of names of the secrets for optional WebLogic configuration overrides. |
| `domainHome` | string | The folder for the WebLogic Domain. Not required. Defaults to /shared/domains/domains/domainUID if domainHomeInImage is false. Defaults to /u01/oracle/user_projects/domains/ if domainHomeInImage is true. |
| `domainHomeInImage` | Boolean | True if this domain's home is defined in the Docker image for the domain. Defaults to true. |
| `domainUID` | string | Domain unique identifier. Must be unique across the Kubernetes cluster. Not required. Defaults to the value of metadata.name. |
| `experimental` | [Experimental](#experimental) | Experimental feature configurations. |
| `image` | string | The WebLogic Docker image; required when domainHomeInImage is true; otherwise, defaults to container-registry.oracle.com/middleware/weblogic:12.2.1.3. |
| `imagePullPolicy` | string | The image pull policy for the WebLogic Docker image. Legal values are Always, Never and IfNotPresent. Defaults to Always if image ends in :latest, IfNotPresent otherwise. |
| `imagePullSecrets` | array of [Local Object Reference](k8s1.13.5.md#local-object-reference) | A list of image pull secrets for the WebLogic Docker image. |
| `includeServerOutInPodLog` | Boolean | If true (the default), the server .out file will be included in the pod's stdout. |
| `logHome` | string | The in-pod name of the directory in which to store the domain, node manager, server logs, and server  *.out files |
| `logHomeEnabled` | Boolean | Specified whether the log home folder is enabled. Not required. Defaults to true if domainHomeInImage is false. Defaults to false if domainHomeInImage is true.  |
| `managedServers` | array of [Managed Server](#managed-server) | Configuration for individual Managed Servers. |
| `replicas` | number | The number of managed servers to run in any cluster that does not specify a replica count. |
| `restartVersion` | string | If present, every time this value is updated the operator will restart the required servers. |
| `serverPod` | [Server Pod](#server-pod) | Configuration affecting server pods. |
| `serverService` | [Server Service](#server-service) | Customization affecting ClusterIP Kubernetes services for WebLogic Server instances. |
| `serverStartPolicy` | string | The strategy for deciding whether to start a server. Legal values are ADMIN_ONLY, NEVER, or IF_NEEDED. |
| `serverStartState` | string | The state in which the server is to be started. Use ADMIN if server should start in the admin state. Defaults to RUNNING. |
| `webLogicCredentialsSecret` | [Secret Reference](k8s1.13.5.md#secret-reference) | The name of a pre-created Kubernetes secret, in the domain's namespace, that holds the username and password needed to boot WebLogic Server under the 'username' and 'password' fields. |

### Domain Status

DomainStatus represents information about the status of a domain. Status may trail the actual state of a system.

| Name | Type | Description |
| --- | --- | --- |
| `clusters` | array of [Cluster Status](#cluster-status) | Status of WebLogic clusters in this domain. |
| `conditions` | array of [Domain Condition](#domain-condition) | Current service state of domain. |
| `message` | string | A human readable message indicating details about why the domain is in this condition. |
| `reason` | string | A brief CamelCase message indicating details about why the domain is in this state. |
| `replicas` | number | The number of running Managed Servers in the WebLogic cluster if there is only one cluster in the domain and where the cluster does not explicitly configure its replicas in a cluster specification. |
| `servers` | array of [Server Status](#server-status) | Status of WebLogic Servers in this domain. |
| `startTime` | DateTime | RFC 3339 date and time at which the operator started the domain. This will be when the operator begins processing and will precede when the various servers or clusters are available. |

### Admin Server

AdminServer represents the operator configuration for the Administration Server.

| Name | Type | Description |
| --- | --- | --- |
| `adminService` | [Admin Service](#admin-service) | Configures which of the Administration Server's WebLogic admin channels should be exposed outside the Kubernetes cluster via a node port service. |
| `restartVersion` | string | If present, every time this value is updated the operator will restart the required servers. |
| `serverPod` | [Server Pod](#server-pod) | Configuration affecting server pods. |
| `serverService` | [Server Service](#server-service) | Customization affecting ClusterIP Kubernetes services for WebLogic Server instances. |
| `serverStartPolicy` | string | The strategy for deciding whether to start a server. Legal values are ALWAYS, NEVER, or IF_NEEDED. |
| `serverStartState` | string | The state in which the server is to be started. Use ADMIN if server should start in the admin state. Defaults to RUNNING. |

### Cluster

An element representing a cluster in the domain configuration.

| Name | Type | Description |
| --- | --- | --- |
| `clusterName` | string | The name of this cluster. Required |
| `clusterService` | [Kubernetes Resource](#kubernetes-resource) | Customization affecting ClusterIP Kubernetes services for the WebLogic cluster. |
| `maxUnavailable` | number | The maximum number of cluster members that can be temporarily unavailable. Defaults to 1. |
| `replicas` | number | The number of cluster members to run. |
| `restartVersion` | string | If present, every time this value is updated the operator will restart the required servers. |
| `serverPod` | [Server Pod](#server-pod) | Configuration affecting server pods. |
| `serverService` | [Server Service](#server-service) | Customization affecting ClusterIP Kubernetes services for WebLogic Server instances. |
| `serverStartPolicy` | string | The strategy for deciding whether to start a server. Legal values are NEVER, or IF_NEEDED. |
| `serverStartState` | string | The state in which the server is to be started. Use ADMIN if server should start in the admin state. Defaults to RUNNING. |

### Experimental

| Name | Type | Description |
| --- | --- | --- |
| `istio` | [Istio](#istio) | Istio service mesh integration configuration. |

### Managed Server

ManagedServer represents the operator configuration for a single Managed Server.

| Name | Type | Description |
| --- | --- | --- |
| `restartVersion` | string | If present, every time this value is updated the operator will restart the required servers. |
| `serverName` | string | The name of the Managed Server. Required. |
| `serverPod` | [Server Pod](#server-pod) | Configuration affecting server pods. |
| `serverService` | [Server Service](#server-service) | Customization affecting ClusterIP Kubernetes services for WebLogic Server instances. |
| `serverStartPolicy` | string | The strategy for deciding whether to start a server. Legal values are ALWAYS, NEVER, or IF_NEEDED. |
| `serverStartState` | string | The state in which the server is to be started. Use ADMIN if server should start in the admin state. Defaults to RUNNING. |

### Server Pod

ServerPod describes the configuration for a Kubernetes pod for a server.

| Name | Type | Description |
| --- | --- | --- |
| `affinity` | [Affinity](k8s1.13.5.md#affinity) | If specified, the pod's scheduling constraints |
| `annotations` | Map | The annotations to be attached to generated resources. |
| `containers` | array of [Container](k8s1.13.5.md#container) | Additional containers to be included in the server pod. |
| `containerSecurityContext` | [Security Context](k8s1.13.5.md#security-context) | Container-level security attributes. Will override any matching pod-level attributes. |
| `env` | array of [Env Var](k8s1.13.5.md#env-var) | A list of environment variables to add to a server. |
| `initContainers` | array of [Container](k8s1.13.5.md#container) | Initialization containers to be included in the server pod. |
| `labels` | Map | The labels to be attached to generated resources. The label names must not start with 'weblogic.'. |
| `livenessProbe` | [Probe Tuning](#probe-tuning) | Settings for the liveness probe associated with a server. |
| `nodeName` | string | NodeName is a request to schedule this pod onto a specific node. If it is non-empty, the scheduler simply schedules this pod onto that node, assuming that it fits resource requirements. |
| `nodeSelector` | Map | Selector which must match a node's labels for the pod to be scheduled on that node. |
| `podSecurityContext` | [Pod Security Context](k8s1.13.5.md#pod-security-context) | Pod-level security attributes. |
| `priorityClassName` | string | If specified, indicates the pod's priority. "system-node-critical" and "system-cluster-critical" are two special keywords which indicate the highest priorities with the former being the highest priority. Any other name must be defined by creating a PriorityClass object with that name. If not specified, the pod priority will be default or zero if there is no default. |
| `readinessGates` | array of [Pod Readiness Gate](k8s1.13.5.md#pod-readiness-gate) | If specified, all readiness gates will be evaluated for pod readiness. A pod is ready when all its containers are ready AND all conditions specified in the readiness gates have status equal to "True" More info: https://github.com/kubernetes/community/blob/master/keps/sig-network/0007-pod-ready%2B%2B.md |
| `readinessProbe` | [Probe Tuning](#probe-tuning) | Settings for the readiness probe associated with a server. |
| `resources` | [Resource Requirements](k8s1.13.5.md#resource-requirements) | Memory and CPU minimum requirements and limits for the server. |
| `restartPolicy` | string | Restart policy for all containers within the pod. One of Always, OnFailure, Never. Default to Always. More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#restart-policy |
| `runtimeClassName` | string | RuntimeClassName refers to a RuntimeClass object in the node.k8s.io group, which should be used to run this pod.  If no RuntimeClass resource matches the named class, the pod will not be run. If unset or empty, the "legacy" RuntimeClass will be used, which is an implicit class with an empty definition that uses the default runtime handler. More info: https://github.com/kubernetes/community/blob/master/keps/sig-node/0014-runtime-class.md This is an alpha feature and may change in the future. |
| `schedulerName` | string | If specified, the pod will be dispatched by specified scheduler. If not specified, the pod will be dispatched by default scheduler. |
| `shutdown` | [Shutdown](#shutdown) | Configures how the operator should shutdown the server instance. |
| `tolerations` | array of [Toleration](k8s1.13.5.md#toleration) | If specified, the pod's tolerations. |
| `volumeMounts` | array of [Volume Mount](k8s1.13.5.md#volume-mount) | Additional volume mounts for the server pod. |
| `volumes` | array of [Volume](k8s1.13.5.md#volume) | Additional volumes to be created in the server pod. |

### Server Service

| Name | Type | Description |
| --- | --- | --- |
| `annotations` | Map | The annotations to be attached to generated resources. |
| `labels` | Map | The labels to be attached to generated resources. The label names must not start with 'weblogic.'. |
| `precreateService` | Boolean | If true, operator will create server services even for server instances without running pods. |

### Cluster Status

| Name | Type | Description |
| --- | --- | --- |
| `clusterName` | string | WebLogic cluster name. Required. |
| `maximumReplicas` | number | The maximum number of cluster members. Required. |
| `readyReplicas` | number | The number of ready cluster members. Required. |
| `replicas` | number | The number of intended cluster members. Required. |

### Domain Condition

| Name | Type | Description |
| --- | --- | --- |
| `lastProbeTime` | DateTime | Last time we probed the condition. |
| `lastTransitionTime` | DateTime | Last time the condition transitioned from one status to another. |
| `message` | string | Human-readable message indicating details about last transition. |
| `reason` | string | Unique, one-word, CamelCase reason for the condition's last transition. |
| `status` | string | Status is the status of the condition. Can be True, False, Unknown. Required. |
| `type` | string | The type of the condition. Valid types are Progressing, Available, and Failed. Required. |

### Server Status

| Name | Type | Description |
| --- | --- | --- |
| `clusterName` | string | WebLogic cluster name, if the server is part of a cluster. |
| `health` | [Server Health](#server-health) | Current status and health of a specific WebLogic Server. |
| `nodeName` | string | Name of node that is hosting the Pod containing this WebLogic Server. |
| `serverName` | string | WebLogic Server name. Required. |
| `state` | string | Current state of this WebLogic Server. Required. |

### Admin Service

| Name | Type | Description |
| --- | --- | --- |
| `annotations` | Map | Annotations to associate with the external channel service. |
| `channels` | array of [Channel](#channel) | Specifies which of the Administration Server's WebLogic channels should be exposed outside the Kubernetes cluster via a node port service, along with the node port for each channel. If not specified, the Administration Server's node port service will not be created. |
| `labels` | Map | Labels to associate with the external channel service. |

### Kubernetes Resource

| Name | Type | Description |
| --- | --- | --- |
| `annotations` | Map | The annotations to be attached to generated resources. |
| `labels` | Map | The labels to be attached to generated resources. The label names must not start with 'weblogic.'. |

### Istio

| Name | Type | Description |
| --- | --- | --- |
| `enabled` | Boolean | True, if this domain is deployed under an Istio service mesh. Defaults to true when the 'istio' element is included. Not required. |
| `readinessPort` | number | The WebLogic readiness port for Istio. Defaults to 8888. Not required. |

### Probe Tuning

| Name | Type | Description |
| --- | --- | --- |
| `initialDelaySeconds` | number | The number of seconds before the first check is performed. |
| `periodSeconds` | number | The number of seconds between checks. |
| `timeoutSeconds` | number | The number of seconds with no response that indicates a failure. |

### Shutdown

Shutdown describes the configuration for shutting down a server instance.

| Name | Type | Description |
| --- | --- | --- |
| `ignoreSessions` | Boolean | For graceful shutdown only, indicates to ignore pending HTTP sessions during in-flight work handling. Not required. Defaults to false. |
| `shutdownType` | string | Tells the operator how to shutdown server instances. Not required. Defaults to graceful shutdown. |
| `timeoutSeconds` | number | For graceful shutdown only, number of seconds to wait before aborting in-flight work and shutting down the server. Not required. Defaults to 30 seconds. |

### Server Health

| Name | Type | Description |
| --- | --- | --- |
| `activationTime` | DateTime | RFC 3339 date and time at which the server started. |
| `overallHealth` | string | Server health of this WebLogic Server. If the value is "Not available", the operator has failed to read the health. If the value is "Not available (possibly overloaded)", the operator has failed to read the health of the server possibly due to the server is in overloaded state. |
| `subsystems` | array of [Subsystem Health](#subsystem-health) | Status of unhealthy subsystems, if any. |

### Channel

Describes a single channel used by the Administration Server.

| Name | Type | Description |
| --- | --- | --- |
| `channelName` | string | Name of channel.<br/>'default' refers to the Administration Server's default channel (configured via the ServerMBean's ListenPort) <br/>'default-secure' refers to the Administration Server's default secure channel (configured via the ServerMBean's SSLMBean's ListenPort) <br/>'default-admin' refers to the Administration Server's default administrative channel (configured via the DomainMBean's AdministrationPort) <br/>Otherwise, the name is the name of one of the Administration Server's network access points (configured via the ServerMBean's NetworkAccessMBeans). |
| `nodePort` | number | Specifies the port number used to access the WebLogic channel outside of the Kubernetes cluster. If not specified, defaults to the port defined by the WebLogic channel. |

### Subsystem Health

| Name | Type | Description |
| --- | --- | --- |
| `health` | string | Server health of this WebLogic Server. Required. |
| `subsystemName` | string | Name of subsystem providing symptom information. Required. |
| `symptoms` | array of string | Symptoms provided by the reporting subsystem. |