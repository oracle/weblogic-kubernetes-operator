### Domain

Domain represents a WebLogic domain and how it will be realized in the Kubernetes cluster.

| Name | Type | Description |
| --- | --- | --- |
| apiVersion | string | The API version for the Domain. Must be 'weblogic.oracle/v2'. |
| kind | string | The type of resource. Must be 'Domain'. |
| metadata | [Object Meta](k8s1.9.0.md#object-meta) | The domain meta-data. Must include the name and namespace. |
| spec | [Domain Spec](#domain-spec) | The specification of the domain. Required |
| status | [Domain Status](#domain-status) | The current status of the domain. Updated by the operator. |

### Domain Spec

DomainSpec is a description of a domain.

| Name | Type | Description |
| --- | --- | --- |
| adminServer | [Admin Server](#admin-server) | Configuration for the admin server. |
| clusters | array of [Cluster](#cluster) | Configuration for the clusters. |
| configOverrides | string | The name of the config map for optional WebLogic configuration overrides. |
| configOverrideSecrets | array of string | A list of names of the secrets for optional WebLogic configuration overrides. |
| domainHome | string | The folder for the WebLogic Domain. Not required. Defaults to /shared/domains/domains/domainUID if domainHomeInImage is false Defaults to /u01/oracle/user_projects/domains/ if domainHomeInImage is true |
| domainHomeInImage | boolean | True if this domain's home is defined in the docker image for the domain. Defaults to true. |
| domainUID | string | Domain unique identifier. Must be unique across the Kubernetes cluster. Not required. Defaults to the value of metadata.name |
| image | string | The WebLogic Docker image; required when domainHomeInImage is true; otherwise, defaults to store/oracle/weblogic:12.2.1.3. |
| imagePullPolicy | string | The image pull policy for the WebLogic Docker image. Legal values are Always, Never and IfNotPresent. Defaults to Always if image ends in :latest, IfNotPresent otherwise. |
| imagePullSecrets | array of [Local Object Reference](k8s1.9.0.md#local-object-reference) | A list of image pull secrets for the WebLogic Docker image. |
| includeServerOutInPodLog | boolean | If true (the default), the server .out file will be included in the pod's stdout. |
| logHome | string | The in-pod name of the directory in which to store the domain, node manager, server logs, and server  *.out files |
| logHomeEnabled | boolean | Specified whether the log home folder is enabled. Not required. Defaults to true if domainHomeInImage is false. Defaults to false if domainHomeInImage is true.  |
| managedServers | array of [Managed Server](#managed-server) | Configuration for the managed servers. |
| replicas | number | The number of managed servers to run in any cluster that does not specify a replica count. |
| restartVersion | string | If present, every time this value is updated the operator will restart the required servers. |
| serverPod | [Server Pod](#server-pod) | Configuration affecting server pods |
| serverService | [Kubernetes Resource](#kubernetes-resource) | Customization affecting ClusterIP Kubernetes services for WebLogic server instances. |
| serverStartPolicy | string | The strategy for deciding whether to start a server. Legal values are ADMIN_ONLY, NEVER, or IF_NEEDED. |
| serverStartState | string | The state in which the server is to be started. Use ADMIN if server should start in the admin state. Defaults to RUNNING. |
| webLogicCredentialsSecret | [Secret Reference](k8s1.9.0.md#secret-reference) | The name of a pre-created Kubernetes secret, in the domain's namepace, that holds the username and password needed to boot WebLogic Server under the 'username' and 'password' fields. |

### Domain Status

DomainStatus represents information about the status of a domain. Status may trail the actual state of a system.

| Name | Type | Description |
| --- | --- | --- |
| conditions | array of [Domain Condition](#domain-condition) | Current service state of domain. |
| message | string | A human readable message indicating details about why the domain is in this condition. |
| reason | string | A brief CamelCase message indicating details about why the domain is in this state. |
| replicas | number | The number of running managed servers in the WebLogic cluster if there is only one cluster in the domain and where the cluster does not explicitly configure its replicas in a cluster specification. |
| servers | array of [Server Status](#server-status) | Status of WebLogic servers in this domain. |
| startTime | DateTime | RFC 3339 date and time at which the operator started the domain. This will be when the operator begins processing and will precede when the various servers or clusters are available. |

### Admin Server

AdminServer represents the operator configuration for the admin server.

| Name | Type | Description |
| --- | --- | --- |
| adminService | [Admin Service](#admin-service) | Configures which of the admin server's WebLogic admin channels should be exposed outside the Kubernetes cluster via a node port service. |
| restartVersion | string | If present, every time this value is updated the operator will restart the required servers. |
| serverPod | [Server Pod](#server-pod) | Configuration affecting server pods |
| serverService | [Kubernetes Resource](#kubernetes-resource) | Customization affecting ClusterIP Kubernetes services for WebLogic server instances. |
| serverStartPolicy | string | The strategy for deciding whether to start a server. Legal values are ALWAYS, NEVER, or IF_NEEDED. |
| serverStartState | string | The state in which the server is to be started. Use ADMIN if server should start in the admin state. Defaults to RUNNING. |

### Cluster

An element representing a cluster in the domain configuration.

| Name | Type | Description |
| --- | --- | --- |
| clusterName | string | The name of this cluster. Required |
| clusterService | [Kubernetes Resource](#kubernetes-resource) | Customization affecting ClusterIP Kubernetes services for WebLogic cluster. |
| maxUnavailable | number | The maximum number of cluster members that can be temporarily unavailable. Defaults to 1. |
| replicas | number | The number of managed servers to run in this cluster. |
| restartVersion | string | If present, every time this value is updated the operator will restart the required servers. |
| serverPod | [Server Pod](#server-pod) | Configuration affecting server pods |
| serverService | [Kubernetes Resource](#kubernetes-resource) | Customization affecting ClusterIP Kubernetes services for WebLogic server instances. |
| serverStartPolicy | string | The strategy for deciding whether to start a server. Legal values are NEVER, or IF_NEEDED. |
| serverStartState | string | The state in which the server is to be started. Use ADMIN if server should start in the admin state. Defaults to RUNNING. |

### Managed Server

ManagedServer represents the operator configuration for a single managed server.

| Name | Type | Description |
| --- | --- | --- |
| restartVersion | string | If present, every time this value is updated the operator will restart the required servers. |
| serverName | string | The name of the server. Required |
| serverPod | [Server Pod](#server-pod) | Configuration affecting server pods |
| serverService | [Kubernetes Resource](#kubernetes-resource) | Customization affecting ClusterIP Kubernetes services for WebLogic server instances. |
| serverStartPolicy | string | The strategy for deciding whether to start a server. Legal values are ALWAYS, NEVER, or IF_NEEDED. |
| serverStartState | string | The state in which the server is to be started. Use ADMIN if server should start in the admin state. Defaults to RUNNING. |

### Server Pod

ServerPod describes the configuration for a Kubernetes pod for a server.

| Name | Type | Description |
| --- | --- | --- |
| annotations | Map | The annotations to be attached to generated resources. |
| containerSecurityContext | [Security Context](k8s1.9.0.md#security-context) | Container-level security attributes. Will override any matching pod-level attributes. |
| env | array of [Env Var](k8s1.9.0.md#env-var) | A list of environment variables to add to a server |
| labels | Map | The labels to be attached to generated resources. The label names must not start with 'weblogic.'. |
| livenessProbe | [Probe Tuning](#probe-tuning) | Settings for the liveness probe associated with a server. |
| nodeSelector | Map | Selector which must match a node's labels for the pod to be scheduled on that node. |
| podSecurityContext | [Pod Security Context](k8s1.9.0.md#pod-security-context) | Pod-level security attributes. |
| readinessProbe | [Probe Tuning](#probe-tuning) | Settings for the readiness probe associated with a server. |
| resources | [Resource Requirements](k8s1.9.0.md#resource-requirements) | Memory and cpu minimum requirements and limits for the server. |
| volumeMounts | array of [Volume Mount](k8s1.9.0.md#volume-mount) | Additional volume mounts for the server pod. |
| volumes | array of [Volume](k8s1.9.0.md#volume) | Additional volumes to be created in the server pod. |

### Kubernetes Resource

| Name | Type | Description |
| --- | --- | --- |
| annotations | Map | The annotations to be attached to generated resources. |
| labels | Map | The labels to be attached to generated resources. The label names must not start with 'weblogic.'. |

### Domain Condition

| Name | Type | Description |
| --- | --- | --- |
| lastProbeTime | DateTime | Last time we probed the condition. |
| lastTransitionTime | DateTime | Last time the condition transitioned from one status to another. |
| message | string | Human-readable message indicating details about last transition. |
| reason | string | Unique, one-word, CamelCase reason for the condition's last transition. |
| status | string | Status is the status of the condition. Can be True, False, Unknown. Required |
| type | string | The type of the condition. Valid types are Progressing, Available, and Failed. Required |

### Server Status

| Name | Type | Description |
| --- | --- | --- |
| clusterName | string | WebLogic cluster name, if the server is part of a cluster. |
| health | [Server Health](#server-health) | Current status and health of a specific WebLogic server. |
| nodeName | string | Name of node that is hosting the Pod containing this WebLogic server. |
| serverName | string | WebLogic server name. Required |
| state | string | Current state of this WebLogic server. Required |

### Admin Service

| Name | Type | Description |
| --- | --- | --- |
| channels | array of [Channel](#channel) | Specifies which of the admin server's WebLogic channels should be exposed outside the Kubernetes cluster via a node port service, along with the node port for each channel. If not specified, the admin server's node port service will not be created. |

### Probe Tuning

| Name | Type | Description |
| --- | --- | --- |
| initialDelaySeconds | number | The number of seconds before the first check is performed |
| periodSeconds | number | The number of seconds between checks |
| timeoutSeconds | number | The number of seconds with no response that indicates a failure |

### Server Health

| Name | Type | Description |
| --- | --- | --- |
| activationTime | DateTime | RFC 3339 date and time at which the server started. |
| overallHealth | string | Server health of this WebLogic server. |
| subsystems | array of [Subsystem Health](#subsystem-health) | Status of unhealthy subsystems, if any. |

### Channel

Describes a single channel used by the admin server.

| Name | Type | Description |
| --- | --- | --- |
| channelName | string | Name of channel.<br/>'default' refers to the admin server's default channel (configured via the ServerMBean's ListenPort) <br/>'default-secure' refers to the admin server's default secure channel (configured via the ServerMBean's SSLMBean's ListenPort) <br/>'default-admin' refers to the admin server's default administrative channel (configured via the DomainMBean's AdministrationPort) <br/>Otherwise, the name is the name of one of the admin server's network access points (configured via the ServerMBean's NetworkAccessMBeans). |
| nodePort | number | Specifies the port number used to access the WebLogic channel outside of the Kubernetes cluster. If not specified, defaults to the port defined by the WebLogic channel. |

### Subsystem Health

| Name | Type | Description |
| --- | --- | --- |
| health | string | Server health of this WebLogic server. Required |
| subsystemName | string | Name of subsystem providing symptom information. Required |
| symptoms | array of string | Symptoms provided by the reporting subsystem. |