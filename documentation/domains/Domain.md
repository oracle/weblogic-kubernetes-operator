### Domain

A Domain resource describes the configuration, logging, images, and lifecycle of a WebLogic domain, including Java options, environment variables, additional Pod content, and the ability to explicitly start, stop, or restart its members. The Domain resource references its Cluster resources using `.spec.clusters`.

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
| `clusters` | Array of [Local Object Reference](k8s1.13.5.md#local-object-reference) | References to Cluster resources that describe the lifecycle options for all of the Managed Server members of a WebLogic cluster, including Java options, environment variables, additional Pod content, and the ability to explicitly start, stop, or restart cluster members. The Cluster resource must describe a cluster that already exists in the WebLogic domain configuration. |
| `configuration` | [Configuration](#configuration) | Models and overrides affecting the WebLogic domain configuration. |
| `dataHome` | string | An optional directory in a server's container for data storage of default and custom file stores. If `dataHome` is not specified or its value is either not set or empty, then the data storage directories are determined from the WebLogic domain configuration. |
| `domainHome` | string | The directory containing the WebLogic domain configuration inside the container. Defaults to /shared/domains/<domainUID> if `domainHomeSourceType` is PersistentVolume. Defaults to /u01/oracle/user_projects/domains/ if `domainHomeSourceType` is Image. Defaults to /u01/domains/<domainUID> if `domainHomeSourceType` is FromModel. |
| `domainHomeSourceType` | string | Domain home file system source type: Legal values: `Image`, `PersistentVolume`, `FromModel`. `Image` indicates that the domain home file system is present in the container image specified by the `image` field. `PersistentVolume` indicates that the domain home file system is located on a persistent volume. `FromModel` indicates that the domain home file system will be created and managed by the operator based on a WDT domain model. Defaults to `Image`, unless `configuration.model` is set, in which case the default is `FromModel`. |
| `domainUID` | string | Domain unique identifier. It is recommended that this value be unique to assist in future work to identify related domains in active-passive scenarios across data centers; however, it is only required that this value be unique within the namespace, similarly to the names of Kubernetes resources. This value is distinct and need not match the domain name from the WebLogic domain configuration. Defaults to the value of `metadata.name`. |
| `failureRetryIntervalSeconds` | integer | The wait time in seconds before the start of the next retry after a Severe failure. Defaults to 120. |
| `failureRetryLimitMinutes` | integer | The time in minutes before the operator will stop retrying Severe failures. Defaults to 1440. |
| `fluentdSpecification` | [Fluentd Specification](#fluentd-specification) | Automatic fluentd sidecar injection. If specified, the operator will deploy a sidecar container alongside each WebLogic Server instance that runs the fluentd, Optionally, the introspector job pod can be enabled to deploy with the fluentd sidecar container. WebLogic Server instances that are already running when the `fluentdSpecification` field is created or deleted, will not be affected until they are restarted. When any given server is restarted for another reason, such as a change to the `restartVersion`, then the newly created pod  will have the fluentd sidecar or not, as appropriate |
| `httpAccessLogInLogHome` | Boolean | Specifies whether the server HTTP access log files will be written to the same directory specified in `logHome`. Otherwise, server HTTP access log files will be written to the directory configured in the WebLogic domain configuration. Defaults to true. |
| `image` | string | The WebLogic Server image; required when `domainHomeSourceType` is Image or FromModel; otherwise, defaults to container-registry.oracle.com/middleware/weblogic:12.2.1.4. |
| `imagePullPolicy` | string | The image pull policy for the WebLogic Server image. Legal values are Always, Never, and IfNotPresent. Defaults to Always if image ends in :latest; IfNotPresent, otherwise. |
| `imagePullSecrets` | Array of [Local Object Reference](k8s1.13.5.md#local-object-reference) | A list of image pull Secrets for the WebLogic Server image. |
| `includeServerOutInPodLog` | Boolean | Specifies whether the server .out file will be included in the Pod's log. Defaults to true. |
| `introspectVersion` | string | Changes to this field cause the operator to repeat its introspection of the WebLogic domain configuration. Repeating introspection is required for the operator to recognize changes to the domain configuration, such as adding a new WebLogic cluster or Managed Server instance, to regenerate configuration overrides, or to regenerate the WebLogic domain home when the `domainHomeSourceType` is `FromModel`. Introspection occurs automatically, without requiring change to this field, when servers are first started or restarted after a full domain shut down. For the `FromModel` `domainHomeSourceType`, introspection also occurs when a running server must be restarted because of changes to any of the fields listed here: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#properties-that-cause-servers-to-be-restarted. The introspectVersion value must be a valid label value in Kubernetes. See also `domains.spec.configuration.overrideDistributionStrategy`. |
| `livenessProbeCustomScript` | string | Full path of an optional liveness probe custom script for WebLogic Server instance pods. The existing liveness probe script `livenessProbe.sh` will invoke this custom script after the existing script performs its own checks. This element is optional and is for advanced usage only. Its value is not set by default. If the custom script fails with non-zero exit status, then pod will fail the liveness probe and Kubernetes will restart the container. If the script specified by this element value is not found, then it is ignored. |
| `logHome` | string | The directory in a server's container in which to store the domain, Node Manager, server logs, server *.out, introspector .out, and optionally HTTP access log files if `httpAccessLogInLogHome` is true. Default is `/shared/logs/DOMAIN-UID`. Ignored if `logHomeEnabled` is false.See also `domains.spec.logHomeLayout`. |
| `logHomeEnabled` | Boolean | Specifies whether the log home folder is enabled. Defaults to true if `domainHomeSourceType` is PersistentVolume; false, otherwise. |
| `logHomeLayout` | string | Control how log files under `logHome` are organized when logHome is set and `logHomeEnabled` is true. `Flat` specifies that all files are kept directly in the `logHome` root directory. `ByServers` specifies that domain log files and `introspector.out` are at the `logHome` root level, all other files are organized under the respective server name logs directory  `logHome/servers/<server name>/logs`. Defaults to `ByServers`. |
| `managedServers` | Array of [Managed Server](#managed-server) | Lifecycle options for individual Managed Servers, including Java options, environment variables, additional Pod content, and the ability to explicitly start, stop, or restart a named server instance. The `serverName` field of each entry must match a Managed Server that already exists in the WebLogic domain configuration or that matches a dynamic cluster member based on the server template. |
| `maxClusterConcurrentShutdown` | integer | The default maximum number of WebLogic Server instances that a cluster will shut down in parallel when it is being partially shut down by lowering its replica count. You can override this default on a per cluster basis by setting the cluster's `maxConcurrentShutdown` field. A value of 0 means there is no limit. Defaults to 1. |
| `maxClusterConcurrentStartup` | integer | The maximum number of cluster member Managed Server instances that the operator will start in parallel for a given cluster, if `maxConcurrentStartup` is not specified for a specific cluster under the `clusters` field. A value of 0 means there is no configured limit. Defaults to 0. |
| `maxClusterUnavailable` | integer | The maximum number of cluster members that can be temporarily unavailable. You can override this default on a per cluster basis by setting the cluster's `maxUnavailable` field. Defaults to 1. |
| `monitoringExporter` | [Monitoring Exporter Specification](#monitoring-exporter-specification) | Automatic deployment and configuration of the WebLogic Monitoring Exporter. If specified, the operator will deploy a sidecar container alongside each WebLogic Server instance that runs the exporter. WebLogic Server instances that are already running when the `monitoringExporter` field is created or deleted, will not be affected until they are restarted. When any given server is restarted for another reason, such as a change to the `restartVersion`, then the newly created pod will have the exporter sidecar or not, as appropriate. See https://github.com/oracle/weblogic-monitoring-exporter. |
| `replicas` | integer | The default number of cluster member Managed Server instances to start for each WebLogic cluster in the domain configuration, unless `replicas` is specified for that cluster under the `clusters` field. For each cluster, the operator will sort cluster member Managed Server names from the WebLogic domain configuration by normalizing any numbers in the Managed Server name and then sorting alphabetically. This is done so that server names such as "managed-server10" come after "managed-server9". The operator will then start Managed Servers from the sorted list, up to the `replicas` count, unless specific Managed Servers are specified as starting in their entry under the `managedServers` field. In that case, the specified Managed Servers will be started and then additional cluster members will be started, up to the `replicas` count, by finding further cluster members in the sorted list that are not already started. If cluster members are started because of their entries under `managedServers`, then a cluster may have more cluster members running than its `replicas` count. Defaults to 1. |
| `restartVersion` | string | Changes to this field cause the operator to restart WebLogic Server instances. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#restarting-servers. |
| `serverPod` | [Server Pod](#server-pod) | Customization affecting the generation of Pods for WebLogic Server instances. |
| `serverService` | [Server Service](#server-service) | Customization affecting the generation of ClusterIP Services for WebLogic Server instances. |
| `serverStartPolicy` | string | The strategy for deciding whether to start a WebLogic Server instance. Legal values are AdminOnly, Never, or IfNeeded. Defaults to IfNeeded. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers. |
| `webLogicCredentialsSecret` | [Local Object Reference](k8s1.13.5.md#local-object-reference) | Reference to a Kubernetes Secret that contains the user name and password needed to boot a WebLogic Server under the `username` and `password` fields. |

### Domain Status

The current status of the operation of the WebLogic domain. Updated automatically by the operator.

| Name | Type | Description |
| --- | --- | --- |
| `clusters` | Array of [Cluster Status](#cluster-status) | Status of WebLogic clusters in this domain. |
| `conditions` | Array of [Domain Condition](#domain-condition) | Current service state of the domain. |
| `failedIntrospectionUid` | string | Unique ID of the last failed introspection job. |
| `initialFailureTime` | DateTime | RFC 3339 date and time at which a currently failing domain started automatic retries. |
| `introspectJobFailureCount` | integer | Non-zero if the introspector job fails for any reason. You can configure an introspector job retry limit for jobs that log script failures using the Operator tuning parameter 'domainPresenceFailureRetryMaxCount' (default 5). You cannot configure a limit for other types of failures, such as a Domain resource reference to an unknown secret name; in which case, the retries are unlimited. |
| `lastFailureTime` | DateTime | RFC 3339 date and time at which a currently failing domain last experienced a Severe failure. |
| `message` | string | A human readable message indicating details about why the domain is in this condition. |
| `observedGeneration` | integer | The Domain resource generation observed by the WebLogic operator. This value will match the 'domain.metadata.generation'  when the 'domain.status' correctly reflects the latest resource changes. |
| `reason` | string | A brief CamelCase message indicating details about why the domain is in this state. |
| `replicas` | integer | The number of running cluster member Managed Servers in the WebLogic cluster if there is exactly one cluster defined in the domain configuration and where the `replicas` field is set at the `spec` level rather than for the specific cluster under `clusters`. This field is provided to support use of Kubernetes scaling for this limited use case. |
| `servers` | Array of [Server Status](#server-status) | Status of WebLogic Servers in this domain. |
| `startTime` | DateTime | RFC 3339 date and time at which the operator started the domain. This will be when the operator begins processing and will precede when the various servers or clusters are available. |

### Admin Server

| Name | Type | Description |
| --- | --- | --- |
| `adminChannelPortForwardingEnabled` | Boolean | When this flag is enabled, the operator updates the domain's WebLogic configuration for its Administration Server to have an admin protocol NetworkAccessPoint with a 'localhost' address for each existing admin protocol capable port. This allows external Administration Console and WLST 'T3' access when using the 'kubectl port-forward' pattern. Defaults to true. |
| `adminService` | [Admin Service](#admin-service) | Customization affecting the generation of a NodePort Service for the Administration Server used to expose specific channels or network access points outside the Kubernetes cluster. See also `domains.spec.adminServer.serverService` for configuration affecting the generation of the ClusterIP Service. |
| `restartVersion` | string | Changes to this field cause the operator to restart WebLogic Server instances. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#restarting-servers. |
| `serverPod` | [Server Pod](#server-pod) | Customization affecting the generation of Pods for WebLogic Server instances. |
| `serverService` | [Server Service](#server-service) | Customization affecting the generation of ClusterIP Services for WebLogic Server instances. |
| `serverStartPolicy` | string | The strategy for deciding whether to start a WebLogic Server instance. Legal values are Always, Never, or IfNeeded. Defaults to IfNeeded. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers. |

### Configuration

| Name | Type | Description |
| --- | --- | --- |
| `introspectorJobActiveDeadlineSeconds` | integer | The introspector job timeout value in seconds. If this field is specified, then the operator's ConfigMap `data.introspectorJobActiveDeadlineSeconds` value is ignored. Defaults to 120 seconds. |
| `model` | [Model](#model) | Model in image model files and properties. |
| `opss` | [Opss](#opss) | Settings for OPSS security. |
| `overrideDistributionStrategy` | string | Determines how updated configuration overrides are distributed to already running WebLogic Server instances following introspection when the `domainHomeSourceType` is PersistentVolume or Image. Configuration overrides are generated during introspection from Secrets, the `overridesConfigMap` field, and WebLogic domain topology. Legal values are `Dynamic`, which means that the operator will distribute updated configuration overrides dynamically to running servers, and `OnRestart`, which means that servers will use updated configuration overrides only after the server's next restart. The selection of `OnRestart` will not cause servers to restart when there are updated configuration overrides available. See also `domains.spec.introspectVersion`. Defaults to `Dynamic`. |
| `overridesConfigMap` | string | The name of the ConfigMap for WebLogic configuration overrides. |
| `secrets` | Array of string | A list of names of the Secrets for WebLogic configuration overrides or model. |

### Fluentd Specification

| Name | Type | Description |
| --- | --- | --- |
| `elasticSearchCredentials` | string | Fluentd elastic search credentials. A Kubernetes secret in the same namespace of the domain. It must contains 4 keys: elasticsearchhost - ElasticSearch Host Service Address, elasticsearchport - Elastic Search Service Port, elasticsearchuser - Elastic Search Service User Name, elasticsearchpassword - Elastic Search User Password |
| `env` | Array of [Env Var](k8s1.13.5.md#env-var) | A list of environment variables to set in the fluentd container. See `kubectl explain pods.spec.containers.env`. |
| `fluentdConfiguration` | string | The fluentd configuration text, specify your own custom fluentd configuration. |
| `image` | string | The Fluentd container image name. Defaults to fluent/fluentd-kubernetes-daemonset:v1.14.5-debian-elasticsearch7-1.1 |
| `imagePullPolicy` | string | The image pull policy for the Fluentd sidecar container image. Legal values are Always, Never, and IfNotPresent. Defaults to Always if image ends in :latest; IfNotPresent, otherwise. |
| `resources` | [Resource Requirements](k8s1.13.5.md#resource-requirements) | Memory and CPU minimum requirements and limits for the fluentd container. See `kubectl explain pods.spec.containers.resources`. |
| `volumeMounts` | Array of [Volume Mount](k8s1.13.5.md#volume-mount) | Volume mounts for fluentd container |
| `watchIntrospectorLogs` | Boolean | Fluentd will watch introspector logs |

### Managed Server

| Name | Type | Description |
| --- | --- | --- |
| `restartVersion` | string | Changes to this field cause the operator to restart WebLogic Server instances. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#restarting-servers. |
| `serverName` | string | The name of the Managed Server. This name must match the name of a Managed Server instance or of a dynamic cluster member name from a server template already defined in the WebLogic domain configuration. Required. |
| `serverPod` | [Server Pod](#server-pod) | Customization affecting the generation of Pods for WebLogic Server instances. |
| `serverService` | [Server Service](#server-service) | Customization affecting the generation of ClusterIP Services for WebLogic Server instances. |
| `serverStartPolicy` | string | The strategy for deciding whether to start a WebLogic Server instance. Legal values are Always, Never, or IfNeeded. Defaults to IfNeeded. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-lifecycle/startup/#starting-and-stopping-servers. |

### Monitoring Exporter Specification

| Name | Type | Description |
| --- | --- | --- |
| `configuration` | Map | The configuration for the WebLogic Monitoring Exporter. If WebLogic Server instances are already running and have the monitoring exporter sidecar container, then changes to this field will be propagated to the exporter without requiring the restart of the WebLogic Server instances. |
| `image` | string | The WebLogic Monitoring Exporter sidecar container image name. Defaults to ghcr.io/oracle/weblogic-monitoring-exporter:2.1.0 |
| `imagePullPolicy` | string | The image pull policy for the WebLogic Monitoring Exporter sidecar container image. Legal values are Always, Never, and IfNotPresent. Defaults to Always if image ends in :latest; IfNotPresent, otherwise. |
| `port` | integer | The port exposed by the WebLogic Monitoring Exporter running in the sidecar container. Defaults to 8080. The port value must not conflict with a port used by any WebLogic Server instance, including the ports of built-in channels or network access points (NAPs). |
| `resources` | [Resource Requirements](k8s1.13.5.md#resource-requirements) | Memory and CPU minimum requirements and limits for the Monitoring exporter sidecar. See `kubectl explain pods.spec.containers.resources`. |

### Server Pod

| Name | Type | Description |
| --- | --- | --- |
| `affinity` | [Affinity](k8s1.13.5.md#affinity) | The Pod's scheduling constraints. More info: https://oracle.github.io/weblogic-kubernetes-operator/faq/node-heating/.  See `kubectl explain pods.spec.affinity`. |
| `annotations` | Map | The annotations to be added to generated resources. |
| `containers` | Array of [Container](k8s1.13.5.md#container) | Additional containers to be included in the server Pod. See `kubectl explain pods.spec.containers`. |
| `containerSecurityContext` | [Security Context](k8s1.13.5.md#security-context) | Container-level security attributes. Will override any matching Pod-level attributes. See `kubectl explain pods.spec.containers.securityContext`. |
| `env` | Array of [Env Var](k8s1.13.5.md#env-var) | A list of environment variables to set in the container running a WebLogic Server instance. More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/domain-resource/#jvm-memory-and-java-option-environment-variables. See `kubectl explain pods.spec.containers.env`. |
| `hostAliases` | Array of [Host Alias](k8s1.13.5.md#host-alias) | HostAliases is an optional list of hosts and IPs that will be injected into the pod's hosts file if specified. This is only valid for non-hostNetwork pods. |
| `initContainers` | Array of [Container](k8s1.13.5.md#container) | Initialization containers to be included in the server Pod. See `kubectl explain pods.spec.initContainers`. |
| `labels` | Map | The labels to be added to generated resources. The label names must not start with "weblogic.". |
| `livenessProbe` | [Probe Tuning](#probe-tuning) | Settings for the liveness probe associated with a WebLogic Server instance. |
| `maxPendingWaitTimeSeconds` | integer | The maximum time in seconds that the operator waits for a WebLogic Server pod to reach the running state before it considers the pod failed. Defaults to 5 minutes. |
| `maxReadyWaitTimeSeconds` | integer | The maximum time in seconds that the operator waits for a WebLogic Server pod to reach the ready state before it considers the pod failed. Defaults to 1800 seconds. |
| `nodeName` | string | NodeName is a request to schedule this Pod onto a specific Node. If it is non-empty, the scheduler simply schedules this pod onto that node, assuming that it fits the resource requirements. See `kubectl explain pods.spec.nodeName`. |
| `nodeSelector` | Map | Selector which must match a Node's labels for the Pod to be scheduled on that Node. See `kubectl explain pods.spec.nodeSelector`. |
| `podSecurityContext` | [Pod Security Context](k8s1.13.5.md#pod-security-context) | Pod-level security attributes. See `kubectl explain pods.spec.securityContext`. |
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

### Domain Condition

| Name | Type | Description |
| --- | --- | --- |
| `lastTransitionTime` | DateTime | Last time the condition transitioned from one status to another. |
| `message` | string | Human-readable message indicating details about last transition. |
| `reason` | string | Unique, one-word, CamelCase reason for the condition's last transition. |
| `severity` | string | The severity of the failure. Can be Fatal, Severe or Warning. |
| `status` | string | The status of the condition. Can be True, False, Unknown. |
| `type` | string | The type of the condition. Valid types are Completed, Available, Failed, Rolling, and ConfigChangesPendingRestart. |

### Server Status

| Name | Type | Description |
| --- | --- | --- |
| `clusterName` | string | WebLogic cluster name, if the server is a member of a cluster. |
| `health` | [Server Health](#server-health) | Current status and health of a specific WebLogic Server instance. |
| `nodeName` | string | Name of Node that is hosting the Pod containing this WebLogic Server instance. |
| `podPhase` | string | Phase of the WebLogic Server pod. Possible values are: Pending, Succeeded, Failed, Running, or Unknown. |
| `podReady` | string | Status of the WebLogic Server pod's Ready condition if the pod is in Running phase, otherwise Unknown. Possible values are: True, False or Unknown. |
| `serverName` | string | WebLogic Server instance name. |
| `state` | string | Current state of this WebLogic Server instance. |
| `stateGoal` | string | Desired state of this WebLogic Server instance. Values are RUNNING, ADMIN, or SHUTDOWN. |

### Admin Service

| Name | Type | Description |
| --- | --- | --- |
| `annotations` | Map | Annotations to associate with the Administration Server's NodePort Service, if it is created. |
| `channels` | Array of [Channel](#channel) | Specifies which of the Administration Server's WebLogic channels should be exposed outside the Kubernetes cluster via a NodePort Service, along with the port for each channel. If not specified, the Administration Server's NodePort Service will not be created. |
| `labels` | Map | Labels to associate with the Administration Server's NodePort Service, if it is created. |

### Model

| Name | Type | Description |
| --- | --- | --- |
| `auxiliaryImages` | Array of [Auxiliary Image](#auxiliary-image) | Optionally, use auxiliary images to provide Model in Image model, application archive, and WebLogic Deploy Tooling files. This is a useful alternative for providing these files without requiring modifications to the pod's base image `domain.spec.image`. This feature internally uses a Kubernetes emptyDir volume and Kubernetes init containers to share the files from the additional images with the pod. |
| `auxiliaryImageVolumeMedium` | string | The emptyDir volume medium. This is an advanced setting that rarely needs to be configured. Defaults to unset, which means the volume's files are stored on the local node's file system for the life of the pod. |
| `auxiliaryImageVolumeMountPath` | string | The auxiliary image volume mount path. This is an advanced setting that rarely needs to be configured. Defaults to `/aux`, which means the emptyDir volume will be mounted at `/aux` path in the WebLogic-Server container within every pod. The defaults for `modelHome` and `wdtInstallHome` will start with the new mount path, and files from `sourceModelHome` and `sourceWDTInstallHome` will be copied to the new default locations. |
| `auxiliaryImageVolumeSizeLimit` | string | The emptyDir volume size limit. This is an advanced setting that rarely needs to be configured. Defaults to unset. |
| `configMap` | string | Name of a ConfigMap containing the WebLogic Deploy Tooling model. |
| `domainType` | string | WebLogic Deploy Tooling domain type. Legal values: WLS, RestrictedJRF, JRF. Defaults to WLS. |
| `modelHome` | string | Location of the WebLogic Deploy Tooling model home. Defaults to `/u01/wdt/models` if no `spec.configuration.model.AuxiliaryImages` are specified, and to `/aux/models` otherwise. |
| `onlineUpdate` | [Online Update](#online-update) | Online update option for Model In Image dynamic update. |
| `runtimeEncryptionSecret` | string | Runtime encryption secret. Required when `domainHomeSourceType` is set to FromModel. |
| `wdtInstallHome` | string | Location of the WebLogic Deploy Tooling installation. Defaults to `/u01/wdt/weblogic-deploy` if no `spec.configuration.model.AuxiliaryImages` are specified, and to `/aux/weblogic-deploy` otherwise. |

### Opss

| Name | Type | Description |
| --- | --- | --- |
| `walletFileSecret` | string | Name of a Secret containing the OPSS key wallet file, which must be in a field named `walletFile`. Use this to allow a JRF domain to reuse its entries in the RCU database. This allows you to specify a wallet file that was obtained from the domain home after the domain was booted for the first time. |
| `walletPasswordSecret` | string | Name of a Secret containing the OPSS key passphrase, which must be in a field named `walletPassword`. Used to encrypt and decrypt the wallet that is used for accessing the domain's entries in its RCU database. |

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

### Cluster Condition

| Name | Type | Description |
| --- | --- | --- |
| `lastTransitionTime` | DateTime | Last time the condition transitioned from one status to another. |
| `message` | string | Human-readable message indicating details about last transition. |
| `status` | string | The status of the condition. Can be True, False. |
| `type` | string | The type of the condition. Valid types are Completed, Available, Failed, and Rolling. |

### Server Health

| Name | Type | Description |
| --- | --- | --- |
| `activationTime` | DateTime | RFC 3339 date and time at which the server started. |
| `overallHealth` | string | Server health of this WebLogic Server instance. If the value is "Not available", the operator has failed to read the health. If the value is "Not available (possibly overloaded)", the operator has failed to read the health of the server possibly due to the server is in the overloaded state. |
| `subsystems` | Array of [Subsystem Health](#subsystem-health) | Status of unhealthy subsystems, if any. |

### Channel

| Name | Type | Description |
| --- | --- | --- |
| `channelName` | string | Name of the channel. The "default" value refers to the Administration Server's default channel, which is configured using the ServerMBean's ListenPort. The "default-secure" value refers to the Administration Server's default secure channel, which is configured using the ServerMBean's SSLMBean's ListenPort. The "default-admin" value refers to the Administration Server's default administrative channel, which is configured using the DomainMBean's AdministrationPort. Otherwise, provide the name of one of the Administration Server's network access points, which is configured using the ServerMBean's NetworkAccessMBeans. The "default", "default-secure", and "default-admin" channels may not be specified here when using Istio. |
| `nodePort` | integer | Specifies the port number used to access the WebLogic channel outside of the Kubernetes cluster. If not specified, defaults to the port defined by the WebLogic channel. |

### Auxiliary Image

| Name | Type | Description |
| --- | --- | --- |
| `image` | string | The auxiliary image containing Model in Image model files, application archive files, and/or WebLogic Deploying Tooling installation files. Required. |
| `imagePullPolicy` | string | The image pull policy for the container image. Legal values are Always, Never, and IfNotPresent. Defaults to Always if image ends in :latest; IfNotPresent, otherwise. |
| `sourceModelHome` | string | The source location of the WebLogic Deploy Tooling model home within the auxiliary image that will be made available in the `/aux/models` directory of the WebLogic Server container in all pods. Defaults to `/auxiliary/models`. If the value is set to `None` or no files are found at the default location, then the source directory is ignored. If specifying multiple auxiliary images with model files in their respective `sourceModelHome` directories, then model files are merged. |
| `sourceWDTInstallHome` | string | The source location of the WebLogic Deploy Tooling installation within the auxiliary image that will be made available in the `/aux/weblogic-deploy` directory of the WebLogic Server container in all pods. Defaults to `/auxiliary/weblogic-deploy`. If the value is set to `None` or no files are found at the default location, then the source directory is ignored. When specifying multiple auxiliary images, ensure that only one of the images supplies a WDT install home; if more than one WDT install home is provided, then the domain deployment will fail. |

### Online Update

| Name | Type | Description |
| --- | --- | --- |
| `enabled` | Boolean | Enable online update. Default is 'false'. |
| `onNonDynamicChanges` | string | Controls behavior when non-dynamic WebLogic configuration changes are detected during an online update. Non-dynamic changes are changes that require a domain restart to take effect. Valid values are 'CommitUpdateOnly' and 'CommitUpdateAndRoll'. Defaults to `CommitUpdateOnly`. If set to 'CommitUpdateOnly' and any non-dynamic changes are detected, then all changes will be committed, dynamic changes will take effect immediately, the domain will not automatically restart (roll), and any non-dynamic changes will become effective on a pod only if the pod is later restarted. If set to 'CommitUpdateAndRoll' and any non-dynamic changes are detected, then all changes will be committed, dynamic changes will take effect immediately, the domain will automatically restart (roll), and non-dynamic changes will take effect on each pod once the pod restarts. For more information, see the runtime update section of the Model in Image user guide. |
| `wdtTimeouts` | [WDT Timeouts](#wdt-timeouts) |  |

### Subsystem Health

| Name | Type | Description |
| --- | --- | --- |
| `health` | string | Server health of this WebLogic Server instance. |
| `subsystemName` | string | Name of subsystem providing symptom information. |
| `symptoms` | Array of string | Symptoms provided by the reporting subsystem. |

### WDT Timeouts

| Name | Type | Description |
| --- | --- | --- |
| `activateTimeoutMillis` | integer | WDT activate WebLogic configuration changes timeout in milliseconds. Default: 180000. |
| `connectTimeoutMillis` | integer | WDT connect to WebLogic admin server timeout in milliseconds. Default: 120000. |
| `deployTimeoutMillis` | integer | WDT application or library deployment timeout in milliseconds. Default: 180000. |
| `redeployTimeoutMillis` | integer | WDT application or library redeployment timeout in milliseconds. Default: 180000. |
| `setServerGroupsTimeoutMillis` | integer | WDT set server groups timeout for extending a JRF domain configured cluster in milliseconds. Default: 180000. |
| `startApplicationTimeoutMillis` | integer | WDT application start timeout in milliseconds. Default: 180000. |
| `stopApplicationTimeoutMillis` | integer | WDT application stop timeout in milliseconds. Default: 180000. |
| `undeployTimeoutMillis` | integer | WDT application or library undeployment timeout in milliseconds. Default: 180000. |