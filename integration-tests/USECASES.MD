# Use Cases covered in integration tests for the operator

Java integration tests cover the below use cases:

Quick test Configuration & Use Cases - 

|  |  |
| --- | --- |
| Operator Configuration | operator1 deployed in `weblogic-operator1` namespace and manages domains in `default` and `test1` namespaces |
| Domain Configuration | Domain on PV using WLST, Traefik load balancer |

Basic Use Cases

1. Create operator `operator1` which manages `default` and `test1` namespaces, verify it's deployed successfully, pods created, operator ready and verify external REST service, if configured.
2. Create domain `domain1` in `default` namespace and verify the pods, services are created and servers are in ready state.
3. Verify the admin external service by accessing the admin REST endpoint with `nodeport` in URL.
4. Verify admin t3 channel port by exec into the admin pod and deploying webapp using the channel port for WLST.
5. Verify web app load balancing by accessing the webapp using `loadBalancerWebPort`.

Advanced Use Cases

6. Verify domain life cycle (destroy and create) should not have any impact on operator managing the domain and web app load balancing and admin external service.
7. Cluster scale up/down using operator REST endpoint, webapp load balancing should adjust accordingly.
8. Operator life cycle (destroy and create) should not impact the running domain.

Also the below use cases are covered for Quick test:

9. Verify the liveness probe by killing managed server 1 process 3 times to kick pod auto-restart.
10. Shutdown the domain by changing domain `serverStartPolicy` to `NEVER`.


Full test Configuration & Use Cases - Runs Quick test Configuration & Use cases and the below

|  |  |
| --- | --- |
| Operator Configuration | operator2 deployed in weblogic-operator2 namespace and manages domains test2 namespace |
| Domain Configuration | Domain on PV using WDT <p> Domain home in image using WLST <p> Domain home in image using WDT <p> Domain with serverStartPolicy ADMIN_ONLY <p> Domain with auto and custom situational configuration <p> Two domains managed by two operators <p> Domain with Recycle weblogicDomainStorageReclaimPolicy <p> Domain with default sample values |


Basic Use Cases described above are verified in all the domain configurations. Also the below use cases are covered:

| Domain | Use Case |
| --- | --- |
| Domain on PV using WDT | WLDF scaling |
| Domain with ADMIN_ONLY | making sure only admin server is started and managed servers are not started. Shutdown domain by deleting domain CRD. Create domain on existing PV dir, pv is already populated by a shutdown domain. |
| Domain with situational config | create domain with listen address not set for admin server and t3 channel/NAP and incorrect file for admin server log location. Introspector should override these with sit-config automatically. Also, with some junk value for t3 channel public address and using custom situational config override replace with valid public address using secret. Also, on Jenkins this domain uses NFS instead of HOSTPATH PV storage |	
| Two domains managed by two operators | verify scaling and restart of one domain doesn't impact another domain. Delete domain resources using delete script from samples. |			
| Domain with Recycle policy | create domain with pvReclaimPolicy="Recycle" Verify that the PV is deleted once the domain and PVC are deleted |
| Domain with default sample values | create domain using mostly default values for inputs |	
| Domain home in image using WLST | cluster scaling |
| Domain home in image using WDT  | cluster scaling |

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
 
| Server Pods Restarted by modifying properties on the domain resource| Use Case |
| --- | --- |
| Server pods restarted by changing Env property | Verify admin and managed server pods being restarted by property change: "-Dweblogic.StdoutDebugEnabled=false" --> "-Dweblogic.StdoutDebugEnabled=true" |
| Server pods restarted by changing image | Verify admin and managed server pods being restarted by property change: image: "store/oracle/weblogic:12.2.1.3" --> image: "store/oracle/weblogic:duplicate" |
| Server pods restarted by changing imagePullPolicy | Verify  admin and managed server pods being restarted by property change: imagePullPolicy: IfNotPresent --> imagePullPolicy: Never |
| Server pods restarted by changing includeServerOutInPodLog | Verify admin and managed server pods being restarted by property change: includeServerOutInPodLog: true --> includeServerOutInPodLog: false |
| Server pods restarted by changing logHomeEnable | Verify admin and managed server pods being restarted by property change: logHomeEnabled: true --> logHomeEnabled: false |

Configuration Overrides Usecases

| Override | Usecase |
| --- | --- |
| Configuration override | Override the administration server properties `connect-timeout`, `max-message-size`, `restart-max`, `debug-server-life-cycle` and `debug-jmx-core` debug flags. Also T3Channel public address using Kubernetes secret. The override is verified by JMX client connecting to the serverConfig MBean tree and the values are checked against the expected values. The test client connects to the overridden T3 public address and port to connect to the MBean servers |
| JDBC Resource Override | Override JDBC connection pool properties; `initialCapacity`, `maxCapacity`, `test-connections-on-reserve`, `connection-harvest-max-count`, `inactive-connection-timeout-seconds`. Override the JDBC driver parameters like data source `URL`, `DB` `user` and `password` using kubernetes secret. The test verifies the overridden functionality datasource `URL`, `user`, `password` by getting the data source connection and running DDL statement it is connected to. |
| JMS Resource Override | Override UniformDistributedTopic Delivery Failure Parameters, `redelivery-limit` and `expiration-policy`. The JMX test client verifies the serverConfig MBean tree for the expected delivery failure parameters, `redelivery-limit` and `expiration-policy`. |
| WLDF Resource Override | Override `wldf-instrumentation-monitor` and `harvester` in a diagnostics module. The test client verifies the new instrumentation monitors/harvesters set by getting the WLDF resource from serverConfig tree with expected values.  |