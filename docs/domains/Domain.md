### Domain

| Name | Description |
| --- | --- |
| apiVersion | The API version for the Domain. Must be 'weblogic.oracle/v2'. |
| kind | The type of resource. Must be 'Domain'. |
| metadata | The domain meta-data. Must include the name and namespace. |
| spec | The specification of the domain. Required. See section 'Domain Spec' |
| status | The current status of the domain. Updated by the operator. See section 'Domain Status' |

### Domain Spec

| Name | Description |
| --- | --- |
| adminServer | Configuration for the admin server. See section 'Admin Server' |
| clusters | Configuration for the clusters. See section 'Cluster' |
| configOverrides | The name of the config map for optional WebLogic configuration overrides. |
| configOverrideSecrets | A list of names of the secrets for optional WebLogic configuration overrides. |
| domainHome | The folder for the Weblogic Domain. Not required. Defaults to /shared/domains/domains/domainUID if domainHomeInImage is false Defaults to /u01/oracle/user_projects/domains/ if domainHomeInImage is true. |
| domainHomeInImage | True if this domain's home is defined in the docker image for the domain. Defaults to true. |
| domainUID | Domain unique identifier. Must be unique across the Kubernetes cluster. Not required. Defaults to the value of metadata.name. |
| image | The Weblogic Docker image; required when domainHomeInImage is true; otherwise, defaults to store/oracle/weblogic:12.2.1.3. |
| imagePullPolicy | The image pull policy for the WebLogic Docker image. Legal values are Always, Never and IfNotPresent. Defaults to Always if image ends in :latest, IfNotPresent otherwise. |
| imagePullSecrets | A list of image pull secrets for the WebLogic Docker image. |
| includeServerOutInPodLog | If true (the default), the server .out file will be included in the pod's stdout. |
| logHome | The in-pod name of the directory in which to store the domain, node manager, server logs, and server  *.out files. |
| logHomeEnabled | Specified whether the log home folder is enabled. Not required. Defaults to true if domainHomeInImage is false. Defaults to false if domainHomeInImage is true. |
| managedServers | Configuration for the managed servers. See section 'Managed Server' |
| replicas | The number of managed servers to run in any cluster that does not specify a replica count. |
| restartVersion | If present, every time this value is updated the operator will restart the required servers. |
| serverPod | Configuration affecting server pods. See section 'Server Pod' |
| serverService | Customization affecting ClusterIP Kubernetes services for WebLogic server instances. See section 'Kubernetes Resource' |
| serverStartPolicy | The strategy for deciding whether to start a server. Legal values are ADMIN_ONLY, NEVER, or IF_NEEDED. |
| serverStartState | The state in which the server is to be started. Use ADMIN if server should start in the admin state. Defaults to RUNNING. |
| webLogicCredentialsSecret | The name of a pre-created Kubernetes secret, in the domain's namepace, that holds the username and password needed to boot WebLogic Server under the 'username' and 'password' fields. |

### Domain Status

| Name | Description |
| --- | --- |
| conditions | Current service state of domain. See section 'Domain Condition' |
| message | A human readable message indicating details about why the domain is in this condition. |
| reason | A brief CamelCase message indicating details about why the domain is in this state. |
| replicas | The number of running managed servers in the WebLogic cluster if there is only one cluster in the domain and where the cluster does not explicitly configure its replicas in a cluster specification. |
| servers | Status of WebLogic servers in this domain. See section 'Server Status' |
| startTime | RFC 3339 date and time at which the operator started the domain. This will be when the operator begins processing and will precede when the various servers or clusters are available. |

### Admin Server

| Name | Description |
| --- | --- |
| adminService | Configures which of the admin server's WebLogic admin channels should be exposed outside the Kubernetes cluster via a node port service. See section 'Admin Service' |
| restartVersion | If present, every time this value is updated the operator will restart the required servers. |
| serverPod | Configuration affecting server pods. See section 'Server Pod' |
| serverService | Customization affecting ClusterIP Kubernetes services for WebLogic server instances. See section 'Kubernetes Resource' |
| serverStartPolicy | The strategy for deciding whether to start a server. Legal values are ALWAYS, NEVER, or IF_NEEDED. |
| serverStartState | The state in which the server is to be started. Use ADMIN if server should start in the admin state. Defaults to RUNNING. |

### Cluster

| Name | Description |
| --- | --- |
| clusterName | The name of this cluster. Required. |
| clusterService | Customization affecting ClusterIP Kubernetes services for WebLogic cluster. See section 'Kubernetes Resource' |
| maxUnavailable | The maximum number of cluster members that can be temporarily unavailable. Defaults to 1. |
| replicas | The number of managed servers to run in this cluster. |
| restartVersion | If present, every time this value is updated the operator will restart the required servers. |
| serverPod | Configuration affecting server pods. See section 'Server Pod' |
| serverService | Customization affecting ClusterIP Kubernetes services for WebLogic server instances. See section 'Kubernetes Resource' |
| serverStartPolicy | The strategy for deciding whether to start a server. Legal values are NEVER, or IF_NEEDED. |
| serverStartState | The state in which the server is to be started. Use ADMIN if server should start in the admin state. Defaults to RUNNING. |

### Managed Server

| Name | Description |
| --- | --- |
| restartVersion | If present, every time this value is updated the operator will restart the required servers. |
| serverName | The name of the server. Required. |
| serverPod | Configuration affecting server pods. See section 'Server Pod' |
| serverService | Customization affecting ClusterIP Kubernetes services for WebLogic server instances. See section 'Kubernetes Resource' |
| serverStartPolicy | The strategy for deciding whether to start a server. Legal values are ALWAYS, NEVER, or IF_NEEDED. |
| serverStartState | The state in which the server is to be started. Use ADMIN if server should start in the admin state. Defaults to RUNNING. |

### Server Pod

| Name | Description |
| --- | --- |
| annotations | The annotations to be attached to generated resources. |
| containerSecurityContext | Container-level security attributes. Will override any matching pod-level attributes. |
| env | A list of environment variables to add to a server. |
| labels | The labels to be attached to generated resources. The label names must not start with 'weblogic.'. |
| livenessProbe | Settings for the liveness probe associated with a server. See section 'Probe Tuning' |
| nodeSelector | Selector which must match a node's labels for the pod to be scheduled on that node. |
| podSecurityContext | Pod-level security attributes. |
| readinessProbe | Settings for the readiness probe associated with a server. See section 'Probe Tuning' |
| resources | Memory and cpu minimum requirements and limits for the server. |
| volumeMounts | Additional volume mounts for the server pod. |
| volumes | Additional volumes to be created in the server pod. |

### Kubernetes Resource

| Name | Description |
| --- | --- |
| annotations | The annotations to be attached to generated resources. |
| labels | The labels to be attached to generated resources. The label names must not start with 'weblogic.'. |

### Domain Condition

| Name | Description |
| --- | --- |
| lastProbeTime | Last time we probed the condition. |
| lastTransitionTime | Last time the condition transitioned from one status to another. |
| message | Human-readable message indicating details about last transition. |
| reason | Unique, one-word, CamelCase reason for the condition's last transition. |
| status | Status is the status of the condition. Can be True, False, Unknown. Required. |
| type | Type is the type of the condition. Currently, valid types are Progressing, Available, and Failure. Required. |

### Server Status

| Name | Description |
| --- | --- |
| clusterName | WebLogic cluster name, if the server is part of a cluster. |
| health | Current status and health of a specific WebLogic server. See section 'Server Health' |
| nodeName | Name of node that is hosting the Pod containing this WebLogic server. |
| serverName | WebLogic server name. Required. |
| state | Current state of this WebLogic server. Required. |

### Admin Service

| Name | Description |
| --- | --- |
| channels | Specifies which of the admin server's WebLogic channels should be exposed outside the Kubernetes cluster via a node port service, along with the node port for each channel. If not specified, the admin server's node port service will not be created. See section 'Channel' |

### Probe Tuning

| Name | Description |
| --- | --- |
| initialDelaySeconds | The number of seconds before the first check is performed. |
| periodSeconds | The number of seconds between checks. |
| timeoutSeconds | The number of seconds with no response that indicates a failure. |

### Server Health

| Name | Description |
| --- | --- |
| activationTime | RFC 3339 date and time at which the server started. |
| overallHealth | Server health of this WebLogic server. |
| subsystems | Status of unhealthy subsystems, if any. See section 'Subsystem Health' |

### Channel

| Name | Description |
| --- | --- |
| channelName | Name of channel. default' refers to the admin server's default channel (configured via the ServerMBean's ListenPort) 'default-secure' refers to the admin server's default secure channel (configured via the ServerMBean's SSLMBean's ListenPort) 'default-admin' refers to the admin server's default administrative channel (configured via the DomainMBean's AdministrationPort) Otherwise, the name is the name of one of the admin server's network access points (configured via the ServerMBean's NetworkAccessMBeans). |
| nodePort | Specifies the port number used to access the WebLogic channel outside of the Kubernetes cluster. If not specified, defaults to the port defined by the WebLogic channel. |

### Subsystem Health

| Name | Description |
| --- | --- |
| health | Server health of this WebLogic server. Required. |
| subsystemName | Name of subsystem providing symptom information. Required. |
| symptoms | Symptoms provided by the reporting subsystem. |