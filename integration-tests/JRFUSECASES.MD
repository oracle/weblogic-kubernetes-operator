# Use Cases for FMW Infra Domain in the Operator

Java integration tests cover the following use cases:

## Basic test Configuration & Use Cases

|  |  |
| --- | --- |
| Operator Configuration | operator1 deployed in `weblogic-operator` namespace and manages domains in `default` and `jrfdomains` namespaces |
| Domain Configuration | Domain on PV using WLST, Traefik load balancer |

**Basic Use Cases**

1. Create operator `weblogic-operator` which manages `default` and `jrfdomains` namespaces, verify it's deployed successfully, pods created, operator ready and verify external REST service, if configured.
2. Create domain `jrfdomain` in `jrfdomains` namespace and verify the pods, services are created and servers are in ready state.
3. Verify the admin external service by accessing the admin REST endpoint with `nodeport` in URL.
4. Verify exec into the admin pod and deploying webapp using the admin port with WLST.
5. Verify web app load balancing by accessing the webapp using `loadBalancerWebPort`.

**Advanced Use Cases**

6. Verify domain life cycle (destroy and create) should not have any impact on operator managing the domain and web app load balancing and admin external service.
7. Cluster scale up/down using operator REST endpoint, webapp load balancing should adjust accordingly.
8. Operator life cycle (destroy and create) should not impact the running domain.

Also the below use cases are covered for each test:

9. Verify the liveness probe by killing managed server 1 process 3 times to kick pod auto-restart.
10. Shutdown the domain by changing domain `serverStartPolicy` to `NEVER`.


## Full test Configuration & Use Cases 

Additional Operator Configuration:

|  |  |
| --- | --- |
| Operator Configuration | `operator2` deployed in `weblogic-operator2` namespace and manages domains in `test2` namespace |

Basic Use Cases described above are verified in all the domain configurations. Also the below use cases are covered:

| Domain | Use Case |
| --- | --- |
| Domain on PV using WLST |  as above in basic use cases |
| Domain with ADMIN_ONLY | verify only admin server is started and managed servers are not started. Shutdown domain by deleting domain CRD. Create domain on existing PV dir, pv is already populated by a shutdown domain. |
| Domain with situational config | create domain with listen address not set for admin server and t3 channel/NAP and incorrect file for admin server log location. Introspector should override these with sit-config automatically. Also, with some junk value for t3 channel public address and using custom situational config override replace with valid public address using secret. Also, on Jenkins this domain uses NFS instead of HOSTPATH PV storage |	
| Two domains managed by two operators | verify scaling and restart of one domain doesn't impact another domain. Delete domain resources using delete script from samples. |			
| Two domains in the same namespace managed by one operator | create two FMW Infra Domains in the same namespace managed by one operator. Verify scaling and restart of one domain doesn't impact another domain. |
| Two domains in the different namespaces managed by one operator | create two FMW Infra Domains in the different namespaces managed by one operator. Domain1 uses VOYAGER load balancer. Domain2 uses TRAEFIK load balancer. Verify scaling and restart of one domain doesn't impact another domain. |
| Domain with Recycle policy | create domain with pvReclaimPolicy="Recycle" and using Configured cluster. Verify that the PV is deleted once the domain and PVC are deleted |
| Domain with default sample values | create domain using mostly default values for inputs |	

Test cases cover the bugs found:

| Domain | Use Case |
| --- | --- | 
| Domain on PV using WLST | cluster rolling restart with restartVersion and maxUnavailable set to 2, verify the not-ready server counts can not exceed maxUnavailable value |
| Domain on PV using WLST | in create domain input file, set exposeAdminNodePort to false and exposeAdminT3Channel to true, verify the managed server pods are created |
| Domain on PV using WLST | in create domain input file, set createDomainScriptsMountPath to non-default value, verify the create domain sample script works |
| Domain on PV using WLST | in createFMWDomain.py file, set administration_port_enabled to true, verify the admin sever pod is running and ready and all managed server pods are created |
| Domain on PV using WLST | in create domain input file, set exposeAdminT3Channel to true, verify the admin t3 channel is exposed |
