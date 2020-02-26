# Use Cases covered in integration tests for the operator

Java integration tests cover the below use cases:

## Quick test Configuration & Use Cases -

|  |  |
| --- | --- |
| Operator Configuration | operator1 deployed in `weblogic-operator1` namespace and manages domains in `default` and `test1` namespaces |
| Domain Configuration | Domain on PV using WLST, Traefik load balancer <p> Domain home in image using WLST |

**Basic Use Cases**

1. Create operator `operator1` which manages `default` and `test1` namespaces, verify it's deployed successfully, pods created, operator ready and verify external REST service, if configured.
2. Create domain `domain1` in `default` namespace and verify the pods, services are created and servers are in ready state.
3. Verify the admin external service by accessing the admin REST endpoint with `nodeport` in URL.
4. Verify admin t3 channel port by exec into the admin pod and deploying webapp using the channel port for WLST.
5. Verify web app load balancing by accessing the webapp using `loadBalancerWebPort`.

**Advanced Use Cases**

6. set precreateService true and verify that the services are created for the managed servers that are not started
7. Cluster scale up/down using operator REST endpoint, webapp load balancing should adjust accordingly.
8. Verify that the services are created for the managed servers that are not started

Also the below use cases are covered for Quick test:

9. Verify the liveness probe by killing managed server 1 process 3 times to kick pod auto-restart.
10. Shutdown the domain by changing domain `serverStartPolicy` to `NEVER`.

Some of the Operator upgrade, pod template, pod restart, operator helm chart usability tests are also run as part of QUICKTEST

## Full test Configuration & Use Cases - Runs Quick test Configuration & Use cases and the below

|  |  |
| --- | --- |
| Operator Configuration | operator2 deployed in weblogic-operator2 namespace and manages domains test2 namespace |
| Domain Configuration | Domain on PV using WDT <p> Domain home in image using WLST <p> Domain home in image using WDT <p> Domain with serverStartPolicy ADMIN_ONLY <p> Domain with auto and custom situational configuration <p> Two domains managed by two operators <p> Domain with Recycle weblogicDomainStorageReclaimPolicy <p> Domain with default sample values <p> Domain on PV with two configured clusters <p> Domain on PV with one configured cluster and one dynamic cluster | 

Basic Use Cases described above are verified in all the domain configurations. Also the below use cases are covered:

| Domain | Use Case |
| --- | --- |
| Domain on PV using WLST | Verify domain life cycle (destroy and create) should not have any impact on operator managing the domain and web app load balancing and admin external service. Operator life cycle (destroy and create) should not impact the running domain |
| Domain on PV using WDT | WLDF scaling |
| Domain with ADMIN_ONLY | making sure only admin server is started and managed servers are not started. Make some changes in domain configuration by changing admin server tuning backlog configuration and restart domain. Shutdown domain by deleting domain CRD. Create domain on existing PV dir, pv is already populated by a shutdown domain. |
| Domain with situational config | create domain with listen address not set for admin server and t3 channel/NAP and incorrect file for admin server log location. Introspector should override these with sit-config automatically. Also, with some junk value for t3 channel public address and using custom situational config override replace with valid public address using secret. Also, on Jenkins this domain uses NFS instead of HOSTPATH PV storage |	
| Two domains managed by two operators | verify scaling and restart of one domain doesn't impact another domain. Delete domain resources using delete script from samples. |			
| Domain with Recycle policy | create domain with pvReclaimPolicy="Recycle" and using Configured cluster. Verify that the PV is deleted once the domain and PVC are deleted |
| Domain with default sample values | create domain using mostly default values for inputs |	
| Domain home in image using WLST | cluster scaling |
| Domain home in image using WDT  | Configured cluster and cluster scaling |

| Operator Usability | Use Case |
| --- | --- |
| Operator Helm Chart with Invalid Attributes | create chart with invalid attributes, verify that deployment fails with expected error |
| Two Operators using same Operator Namespace | create two operators sharing same namespace,verify that deployment fails with expected error |
| Operator Helm Chart using default target domains Namespace| create chart using default target domains namespace |
| Operator Helm Chart using empty target domains Namespace| create chart using empty target domains namespace |
| Operator Helm Chart using UpperCase target domains Namespace| create chart using invalid UpperCase target domains namespace, verify that deployment fails with expected error |
| Operator Helm Chart using not preexisted Operator Namespace | create chart using not preexisted Operator namespace, verify that deployment will fail |
| Operator Helm Chart using not preexisted Operator ServiceAccount | create chart using not preexisted Operator ServiceAccount, verify that deployment will fail, but will change to running after SA is created |
| Operator Helm Chart create delete create | create delete create chart with same values |
| Two Operators using same External Https Port | create chart using same https rest port as already running first operator, verify that deployment fails with expected error |
| Two Operators using same target domains namespace | create chart using target domains namespace as already running first operator, verify that deployment fails with expected error |
| Operator Helm Chart using not preexisted target domains namespace | create chart using not preexisted target domains namespace as already running first operator, verify that deployment fails with expected error |
| Operator Helm Chart add/delete target domain namespaces (domain1, domain2) | create operator helm chart managing domain1, use upgrade to add domain2. Verify that operator is able to manage added domain (domain2). Use helm upgrade to remove domain1, verify that operator not able to manage anymore the deleted one(domain1) |
| Operator Helm Chart delete operator helm chart, leave domain running | create operator helm chart and start domain1, delete operator helm chart, verify domain1 is still functional |
 
| Server Pods Restarted by modifying properties on the domain resource| Use Case |
| --- | --- |
| Server pods restarted by changing Env property | Verify admin and managed server pods being restarted by property change: `-Dweblogic.StdoutDebugEnabled=false` --> `-Dweblogic.StdoutDebugEnabled=true` |
| Server pods restarted by changing image | Verify admin and managed server pods being restarted by property change: image: `container-registry.oracle.com/middleware/weblogic:12.2.1.3` --> image: `container-registry.oracle.com/middleware/weblogic:duplicate` |
| Server pods restarted by changing imagePullPolicy | Verify  admin and managed server pods being restarted by property change: imagePullPolicy: IfNotPresent --> imagePullPolicy: Never |
| Server pods restarted by changing includeServerOutInPodLog | Verify admin and managed server pods being restarted by property change: includeServerOutInPodLog: true --> includeServerOutInPodLog: false |
| Server pods restarted by changing logHomeEnable | Verify admin and managed server pods being restarted by property change: logHomeEnabled: true --> logHomeEnabled: false |
| Server pods restarted by changing containerSecurityContext | Verify admin and managed server pods being restarted by adding property change: containerSecurityContext: runAsUser: 1000 |
| Server pods restarted by changing podSecurityContex | Verify admin and managed server pods being restarted by adding property change: podSecurityContext: runAsUser: 1000 fsGroup: 2000 |
| Server pods restarted by changing resources | Verify admin and managed server pods being restarted by adding property change: resources: limits: cpu: "1" requests: cpu: "0.5" args: - -cpus - "2" |
| Server pods restarted by changing restartVersion at admin level | Verify admin pod restarted by adding restartVersion property at admin server level: `restartVersion: "v1.1"` |
| Server pods restarted by changing restartVersion at cluster level | Verify managed server pods part of the dynamic cluster are restarted by adding restartVersion property at cluster level : `restartVersion: "v1.1"` |
| Server pods restarted by changing restartVersion at domain level | Verify all the server pods in the weblogic domain are restarted by adding restartVersion property at domain level: `restartVersion: "v1.1"` |

| Server Pods Shutdown options| Use Case |
| --- | --- |
| Shutdown options: domain level| Verify that shutdown options set at the domain level will apply to all servers in the domain  |
| Shutdown options: managed server level | Verify that shutdown options set at the managed server level will apply only to the specified server  |
| Shutdown options: cluster level | Verify that shutdown options set at the cluster level will apply only to the all servers in that cluster  |
| Shutdown options: use env variables | Verify that shutdown options set using env vars will apply   |
| Shutdown option ignoreSessions | Verify that shutdown option: ignoreSessions(true, false) will effect shutdown time of the pod   |
| Shutdown option timeout | Verify that shutdown option: timeout will effect shutdown time of the pod   |
| Shutdown option Forced | Verify that shutdown option: Forced will effect shutdown time of the pod   |
| Shutdown overrides | Verify that shutdown options set on (managed server or cluster) (lowest level) will take priority over domain level settings   |

Configuration Overrides Usecases

| Override | Usecase |
| --- | --- |
| Configuration override | Override the administration server  `connect-timeout`, `max-message-size`, `restart-max`, `debug-server-life-cycle` and `debug-jmx-core` debug flags. Also T3Channel public address using Kubernetes secret. The dynamic cluster server template's `max-message-size` is overridden for managed servers. The override is verified by JMX client connecting to the serverConfig MBean tree and the values are checked against the expected values. The test client connects to the overridden T3 public address and port to connect to the MBean servers |
| JDBC Resource Override | Override JDBC connection pool properties; `initialCapacity`, `maxCapacity`, `test-connections-on-reserve`, `connection-harvest-max-count`, `inactive-connection-timeout-seconds`. Override the JDBC driver parameters like data source `URL`, `DB` `user` and `password` using kubernetes secret. The test verifies the overridden functionality datasource `URL`, `user`, `password` by getting the data source connection and running DDL statement it is connected to. |
| JMS Resource Override | Override UniformDistributedTopic Delivery Failure Parameters, `redelivery-limit` and `expiration-policy`. The JMX test client verifies the serverConfig MBean tree for the expected delivery failure parameters, `redelivery-limit` and `expiration-policy`. |
| WLDF Resource Override | Override `wldf-instrumentation-monitor` and `harvester` in a diagnostics module. The test client verifies the new instrumentation monitors/harvesters set by getting the WLDF resource from serverConfig tree with expected values.  |
| Configuration override with running domain | Override the administration server with Startup and Shutdown class by editing the configmap and recreating the domain CRD. The override is verified by JMX client connecting to the serverConfig MBean tree and the values are checked against the expected values. |
| JDBC Resource Override with running domain | Override non dynamic JDBC connection pool properties; `ignore-in-use-connections`, `login-delay-Seconds`, `connection-cache-type`, `global-transactions-protocol`  by editing the configmap and recreating the domain CRD. The test only verifies the expected values against the config tree |

| Session Migration | Use Case |
| --- | --- |
| Primary Server Repick | A backup server becomes the primary server when a primary server fails|
| HTTP Session Migration | Verify in-memory HTTP session State replication |

| Server Discovery | Use Case |
| --- | --- |
| Discover a newly started server | Stop operator and apply the modified domain.yaml with replicas count increased, restart Operator and verify that the cluster is scaled up accordingly |
| Discover dead weblogic servers | Stop Operator, kill admin server and all managed servers in the cluster, restart Operator and verify that it restarts all dead servers | 

| Sticky Session | Use Case |
| --- | --- |
| Server affinity | Use a web application deployed on Weblogic cluster to track HTTP session. Test server-affinity by sending two HTTP requests to Weblogic and verify that all requests are directed to same Weblogic server |
| Session state isolation | Verify that values saved in a client session state are not visible to another client |

| Monitoring Exporter | Use Case |
| --- | --- |
| Check Metrics via Prometheus | build, deploy webapp for Monitoring Exporter, start Prometheus and verify the metrics was produced by using Prometheus APIs |
| Replace Configuration via exporter console| Verify that configuration for monitoring exporter can be replaced during runtime, check applied metrics via Prometheus APIs|
| Append Configuration via exporter console| Verify that configuration for monitoring exporter can be appended during runtime, check applied metrics via Prometheus APIs|
| Append Configuration with varios combinations of attributes via exporter console| Append monitoring exporter configuration [a] to new config [a,b] and verify it was applied |
| Replace Configuration with only one attribute as array via exporter console| Replace monitoring exporter configuration [a,b,c] attributes with new config [a] and verify it was applied |
| Replace Configuration with empty config file via exporter console| Replace monitoring exporter configuration with empty config file, verify it was applied |
| Replace/Append Configuration with  config file written in non yml format via exporter console| Try to replace/append monitoring exporter configuration with config file written in non yml format, verify configuration has not changed |
| Replace/Append Configuration with corrupted yml file via exporter console| Try to replace/append monitoring exporter configuration with config file written in corrupted yml format, verify configuration has not changed |
| Replace/Append Configuration with dublicated values in the config file via exporter console| Try to replace/append monitoring exporter configuration with dublicated values in the config file, verify configuration has not changed |
| End to end test to demonstrate how to setup and run WebLogic Monitoring Exporter with operator and WebLogic domain| This is fully automated version of the sample, provided in the https://github.com/oracle/weblogic-monitoring-exporter/tree/alert/samples/kubernetes/end2end |
| Test to scale up cluster using Prometheus Alert Manager and webhook | Use webhook, prometheus, monitoring exporter to scale up WebLogic Cluster based on metrics condition |


| Logging with Elastic Stack | Use Case |
| --- | --- |
| Search log level | Use Elasticsearch Count API to query logs of level=INFO and verify that total number of logs for level=INFO is not zero and failed count is zero |
| Search Operator log | Use Elasticsearch Search APIs to query Operator log info and verify that log hits for type=weblogic-operator are not empty |
| Search Weblogic log | Use Elasticsearch Search APIs to query Weblogic log info and verify that log hits for Weblogic servers are not empty |

| Operator Upgrade | Use Case |
| --- | --- |
| Upgrade 2.0 operator to develop | Upgrade the 2.0 operator running a Weblogic domain to develop version of the Operator and verify apiVersion of the domain CRD shows latest version |
| Upgrade 2.0.1 operator to develop | Upgrade the 2.0.1 operator running a Weblogic domain to develop version of the Operator and verify apiVersion of the domain CRD shows latest version |
| Upgrade 2.1 operator to develop | Upgrade the 2.1 operator running a Weblogic domain to develop version of the Operator and verify apiVersion of the domain CRD shows latest version |
| Upgrade 2.2.0 operator to develop | Upgrade the 2.2.0 operator running a Weblogic domain to develop version of the Operator and verify apiVersion of the domain CRD shows latest version |
| Upgrade 2.2.1 operator to develop | Upgrade the 2.2.1 operator running a Weblogic domain to develop version of the Operator and verify apiVersion of the domain CRD shows latest version |
| Upgrade 2.3.0 operator to develop | Upgrade the 2.3.0 operator running a Weblogic domain to develop version of the Operator and verify apiVersion of the domain CRD shows latest version |

| Pod Templates | Use Case |
| --- | --- |
| Using pod templates/variables | Use DOMAIN_UID, DOMAIN_NAME, DOMAIN_HOME, SERVER_NAME, LOG_HOME variables at domain level and CLUSTER_NAME at cluster level. Bring up the domain and make sure domain started successfully when pod templates are used |

| Init Container | Use Case |
| --- | --- |
| Add initContainers to domain | Add a initContainers object to spec level and verify the init containers are created for Weblogic server pods prior to starting it and runs to completion and then Weblogic pod are started |
| Add initContainers to adminServer | Add a initContainers object to adminServer level and verify the init container is created for administration server Weblogic server pod prior to starting it and runs to completion and then Weblogic pod is started |
| Add initContainers to Clusters | Add a initContainers object to Clusters level and verify the init containers are created for Weblogic server pods prior to starting the clusters and runs to completion and then Weblogic pod are started |
| Add initContainers to managedServers | Add a initContainers object to managed server level and verify the init container is created for managed server Weblogic server pod prior to starting it and runs to completion and then Weblogic pod is started |
| Add bad initContainers to domain | Add a bad initContainers object to domain and verify the init container run fails and no Weblogic pod is started |
| Add multiple initContainers to domain | Add multiple initContainers object to domain level and verify all of the init container are run before Weblogic server pod are started |
| Add initContainers with different names at different level | Add a multiple initContainers object at domain level and server level and verify all of the init containers are run before Weblogic server pods are started |
| Add initContainers with same names at different level | Add a multiple initContainers object at domain level and server level and verify only the server level init containers are run before Weblogic server pods are started |

| Managed Coherence Tests | Use Case |
| --- | --- |
| Managed Coherence - Domain on PV | Sanity testing of managed coherence in domain on pv using WLST. The test adds data to be stored in the cache, retrieves it and clears the cache |
| Managed Coherence - Domain in image | Sanity testing of managed coherence in domain in image using WLST. The test adds data to be stored in the cache, retrieves it and clears the cache |

| Using WebLogic Image Tool (WIT) to Create WebLogic Domain | Use Case |
| --- | --- |
| Create WebLogic Domain using an image built by WIT |  Download WIT, build a WebLogic Docker image using WIT and then use the image to create WebLogic Domain  |
