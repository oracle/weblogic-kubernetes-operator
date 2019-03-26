# Integration Tests for Oracle WebLogic Server Kubernetes Operator

This documentation describes the functional use cases that are covered in integration testing for the Oracle WebLogic Server Kubernetes Operator. The tests are written in Java (JUnit tests) and driven by Maven profile.

# Environments

The tests currently run in three modes: "Wercker", "Jenkins", and "standalone" Oracle Linux, where the mode is controlled by the `WERCKER` and `JENKINS` environment variables described below. The default is "standalone".

* "Standalone" Oracle Linux, i.e, run the tests manually with the `mvn` command.
* Wercker - https://app.wercker.com/Oracle/weblogic-kubernetes-operator/runs - `integration-test-java` is the pipeline name.
* Jenkins - http://wls-jenkins.us.oracle.com/view/weblogic-operator/job/weblogic-kubernetes-operator-javatest/ - Jenkins Run is restricted to Oracle Internal development process.

Wercker runs only Quick test use cases, Jenkins runs both Quick and Full test use cases.

# Use Cases

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
| Operator Helm Chart add/delete target domain namespaces (domain1, domain2) | create operator helm chart managing domain1, use upgrade to add domain2. Verify that operator is able to manage added domain (domain2). Use helm upgrade to remove domain1, verify that operator not able to manage anymore the deleted one(domain1) |
| Operator Helm Chart delete operator helm chart, leave domain running | create operator helm chart and start domain1, delete operator helm chart, verify domain1 is still functional |
 
| Server Pods Restarted by modifying properties on the domain resource| Use Case |
| --- | --- |
| Server pods restarted by changing Env property | Verify admin and managed server pods being restarted by property change: `-Dweblogic.StdoutDebugEnabled=false` --> `-Dweblogic.StdoutDebugEnabled=true` |
| Server pods restarted by changing image | Verify admin and managed server pods being restarted by property change: image: `store/oracle/weblogic:12.2.1.3` --> image: `store/oracle/weblogic:duplicate` |
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

| Session Migration | Use Case |
| --- | --- |
| Primary Server Repick | A backup server becomes the primary server when a primary server fails|
| HTTP Session Migration | Verify in-memory HTTP session State replication |

# Directory Configuration and Structure
 
Directory structure of source code:

A new module "integration-tests" is added to the Maven project `weblogic-kubernetes-operator`.

`weblogic-kubernetes-operator/integration-tests` - location of module pom.xml  
`weblogic-kubernetes-operator/integration-tests/src/test/java` - integration test(JUnit) classes and utility classes  
`weblogic-kubernetes-operator/integration-tests/src/test/resources` - properties, YAML files (see Configuration Files section) and other scripts

Directory structure used for the test run:

Main external `env vars`:

| Variable | Description |
| --- | --- |
| RESULT_ROOT  | Root path for local test files. |
| PV_ROOT      | Root NFS path behind PV/C directories.  This must have permissions suitable for WL pods to add files |

Defaults for `RESULT_ROOT` & `PV_ROOT`:

| Test Mode  |	RESULT_ROOT |	PV_ROOT |	Where initialized |
| --- | --- | --- | --- |
| stand-alone	| /scratch/$USER/wl_k8s_test_results |	/scratch/$USER/wl_k8s_test_results	| test defaults |
| Jenkins	| /scratch/k8s_dir |	/scratch/k8s_dir	 | jenkins configuration |
| Wercker	| /pipeline/output/k8s_dir	| /scratch	| wercker.yml |


'Physical' subdirectories created by test:

    Local tmp files:      RESULT_ROOT/acceptance_test_tmp/...

    PV dirs K8S NFS:      PV_ROOT/acceptance_test_pv/persistentVolume-${domain_uid}/...

    Archives of above:    PV_ROOT/acceptance_test_pv_archive/...
                          RESULT_ROOT/acceptance_test_tmp_archive/...

'Logical' to 'Physical' K8S PV/PVC mappings:

 |  | Logical   |  Actual |
 | --- | --- | --- |
 | job.sh job | /scratch | PV_ROOT on K8S machines |
 | domain pod: | /shared | PV_ROOT/acceptance_test_pv/persistentVolume-${domain_uid} on K8S machines |

# Configuration Files

A module "integration-tests" is added in Maven weblogic-kubernetes-operator project.

Below configuration files are used from src/integration-tests/resources:
```
OperatorIT.properties
operator1.yaml
operator2.yaml
operator_bc.yaml
operator_chain.yaml
domainonpvwlst.yaml
domainonpvwdt.yaml
domainadminonly.yaml
domainrecyclepolicy.yaml
domainsampledefaults.yaml
```

src/integration-tests/resources/OperatorIT.properties - This file is used for configuring common attributes for all integration tests
```
baseDir=/scratch  
username=weblogic  
password=welcome1  
maxIterationsPod=50  
waitTimePod=5  
```

src/integration-tests/resources/operator1.yaml - input/customized properties for the Operator, any property can be provided here from kubernetes/charts/weblogic-operator/values.yaml. weblogic-operator-values.yaml is generated using the properties defined in this file.

```
releaseName: op1
serviceAccount: weblogic-operator
namespace: weblogic-operator1
domainNamespaces: [ "default", "test1" ]
externalRestEnabled: true
javaLoggingLevel: FINE
```

src/integration-tests/resources/domainonpvwlst.yaml - input/customized properties for PV/Load Balancer/WebLogic Domain. Any property can be provided here from kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain-inputs.yaml and kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml. For all the properties that are not defined here, the default values in the sample inputs are used while generating inputs yaml.

```
adminServerName: admin-server
domainUID: domainonpvwlst
clusterName: cluster-1
configuredManagedServerCount: 4
initialManagedServerReplicas: 2
managedServerNameBase: managed-server
#weblogicDomainStoragePath will be ignored, PV dir will be created at /<baseDir>/<USER>/acceptance_test_pv
#weblogicDomainStoragePath: /scratch/external-domain-home/pv001/
exposeAdminT3Channel: true
exposeAdminNodePort: true
namespace: default

```

Certain properties like weblogicDomainStoragePath, image, externalOperatorCert are populated at run time.


# How does it work

When the tests are run with mvn command, 
- cleanup the test tmp files, PV dir and k8s artifacts created for the test if any
- creates the required secrets to pull the WL image from docker hub
- Operator image is built with the git branch from where the mvn command is executed. 
- creates Operator and verifies operator is running
- creates Domain crd using samples
- verifies the domain is started and servers are ready, services are created
- executes the basic and advanced use cases 
- shutdown the domain 
- archive logs and results
- cleanup the tmp files, PV dir and k8s artifacts created for the test
	
All the tests that start with IT*.java in integration-tests/src/test/java are run. 

**Integration test classes:**

When the integration test class ITOperator is executed, staticPrepare() method is called once before any of the test methods in the class and staticUnPrepare() method once at the end.

staticPrepare() - initializes the application properties from OperatorIT.properties and creates resultRoot, pvRoot, userprojectsDir directories by calling initialize() method from the base class BaseTest.

staticUnPrepare() - releases the cluster lease on wercker env.

test methods -  testDomainOnPVUsingWLST, testDomainOnPVUsingWDT, testTwoDomainsManagedByTwoOperators, testCreateDomainWithStartPolicyAdminOnly, testCreateDomainPVReclaimPolicyRecycle, testCreateDomainWithDefaultValuesInSampleInputs, testAutoAndCustomSitConfigOverrides, testOperatorRESTIdentityBackwardCompatibility, testOperatorRESTUsingCertificateChain

**Utility classes:**

Operator - constructor takes yaml file with operator properties and generates operator valus yaml with required properties and certs,  creates service account, namespace and calls helm install using the generated values yaml file. Also contains methods to delete operator release, verify operator created and ready, scale using rest api, verify a given domain via rest, verify external rest service, etc  <p>
Domain - constructor takes Map with domain, LB, PV properties and creates domain crd, LB operator/ingress and PV artifacts using the sample scripts provided in the project. Also contains helper methods to destroy domain by deleting domain crd, verify domain created and servers are ready, deploy webapp, verify load balancing of http requests, etc <p>
PersistentVolume - runs k8s job to create PV directory and creates PV and PVC using sample scripts  <p>
LoadBalancer - creates load balancer, currently TREFIK and VOYAGER are supported <p>
Secret - creates a k8s secret <p>
TestUtils - mostly runs kubectl commands. Contains utility methods to check if a pod is created, ready, deleted, service created, get pod restart cnt, get cluster replica, delete PVC, check PV released, create rbac policy, create wldf module, etc. <p>
ExecCommand - Class for executing shell commands from java <p>
ExecResult - Class that holds the results of using java to exec a command (i.e. exit value, stdout and stderr) <p>
K8sTestUtils - uses k8s java client api, this is used only for delete domain use cases for now. <p>

# How to run the Java integration tests

* Maven and latest Git should be in PATH
* export JAVA_HOME
* export WEBLOGIC_IMAGE_NAME and WEBLOGIC_IMAGE_TAG if different from store/oracle/weblogic and 12.2.1.3
* Setup docker access to WebLogic 12c Images 

 Method 1 
  - Setup a personal account on hub.docker.com
  - Then sign in to hub.docker.com and signup for access to WebLogic 12c Images via https://hub.docker.com/_/oracle-weblogic-server-12c
  - Then export the following before running the tests: 
	```
	export DOCKER_USERNAME=<docker_username>
	export DOCKER_PASSWORD=<docker_password>
	export DOCKER_EMAIL=<docker_email>
	```
 
 Method 2 
 - Make sure the weblogic image i.e. store/oracle/weblogic:12.2.1.3 already exists locally in a docker repository the k8s cluster can access
 - Make sure the weblogic image has patch p29135930 (required for the WebLogic Kubernetes Operator). 
  	- If not, see [https://github.com/oracle/docker-images/tree/master/OracleWebLogic/samples/12213-patch-wls-for-k8s].
		
		
* Command to run the tests: 
```
mvn clean verify -P java-integration-tests 2>&1 | tee log.txt
```

The tests accepts optional env var overrides:

| Variable | Description |
| --- | --- |
| RESULT_ROOT | The root directory to use for the tests temporary files. See "Directory Configuration and Structure" for                  defaults and a detailed description of test directories. |
| PV_ROOT    |  The root directory on the kubernetes cluster used for persistent volumes. See "Directory Configuration and Structure" for defaults and a detailed description of test directories. |
| QUICKTEST  | When set to "true", limits testing to a subset of the tests. |
| LB_TYPE    | The default value is "TRAEFIK". Set to "VOYAGER" if you want to use it as LB. |
| INGRESSPERDOMAIN  | The defult value is true. If you want to test creating TRAEFIK LB by kubectl yaml for multiple domains, set it to false. |
| WERCKER    | Set to true if invoking from Wercker, set to false or "" if running stand-alone or from Jenkins. Default is "". |
| JENKINS    | Set to true if invoking from Jenkins, set to false or "" if running stand-alone or from Wercker. Default is "". |
| K8S_NODEPORT_HOST | DNS name of a Kubernetes worker node. Default is the local host's hostname. |
| BRANCH_NAME  | Git branch name.   Default is determined by calling 'git branch'. |
| LEASE_ID   |   Set to a unique value to (A) periodically renew a lease on the k8s cluster that indicates that no other test run should attempt to use the cluster, and (B) delete this lease when the test completes. |

The following additional overrides are currently only used when
WERCKER=true:

| Variable | Description |
| --- | --- |
| IMAGE_TAG_OPERATOR | Docker image tag for operator. Default generated based off the BRANCH_NAME. |
| IMAGE_NAME_OPERATOR | Docker image name for operator. Default is weblogic-kubernetes-operator |
| IMAGE_PULL_POLICY_OPERATOR | Default 'Never'. |
| IMAGE_PULL_SECRET_OPERATOR | Default ''. |
 | IMAGE_PULL_SECRET_WEBLOGIC | Default ''.


Successful run will have the output like below:
```
[INFO] Reactor Summary:
[INFO]
[INFO] weblogic-kubernetes-operator ....................... SUCCESS [  0.305 s]
[INFO] operator-model ..................................... SUCCESS [ 10.274 s]
[INFO] operator-swagger ................................... SUCCESS [  0.436 s]
[INFO] operator-runtime ................................... SUCCESS [ 21.567 s]
[INFO] operator-integration-tests ......................... SUCCESS [  01:08 h]
[INFO] installation-tests ................................. SUCCESS [ 34.097 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 01:09 h
[INFO] Finished at: 2018-10-31T12:38:18-07:00
[INFO] Final Memory: 60M/1236M

```
Failed run will have the output like
```

[INFO]
[INFO] Results:
[INFO]
[ERROR] Errors:
[ERROR]   ITOperator.testDomainOnPVUsingWLST:145 ? Runtime FAILURE: Couldn't create serv...
[INFO]
[ERROR] Tests run: 9, Failures: 0, Errors: 1, Skipped: 0
[INFO]
[INFO]
[INFO] --- maven-failsafe-plugin:2.20.1:verify (integration-tests) @ operator-integration-tests ---
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary:
[INFO]
[INFO] Build Tools ........................................ SUCCESS [  1.193 s]
[INFO] weblogic-kubernetes-operator ....................... SUCCESS [  2.671 s]
[INFO] json-schema ........................................ SUCCESS [ 14.917 s]
[INFO] jsonschema-maven-plugin Maven Mojo ................. SUCCESS [  8.600 s]
[INFO] operator-model ..................................... SUCCESS [ 23.065 s]
[INFO] operator-swagger ................................... SUCCESS [  4.487 s]
[INFO] operator-runtime ................................... SUCCESS [ 53.675 s]
[INFO] operator-integration-tests ......................... FAILURE [41:09 min]
[INFO] installation-tests ................................. SKIPPED
[INFO] Project Reports .................................... SKIPPED
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 42:58 min
[INFO] Finished at: 2019-02-11T09:42:08-08:00
[INFO] Final Memory: 124M/1534M
```
JUnit test results can be seen at "integration-tests/target/failsafe-reports/TEST-oracle.kubernetes.operator.ITOperator.xml". This file shows how much time each test case took to run and the failed test results if any.

# How to run a single test

mvn -Dit.test="ITOperator#testDomainOnPVUsingWLST" -DfailIfNoTests=false integration-test -P java-integration-tests

# How to run multiple tests

mvn -Dit.test="ITOperator#testDomainOnPVUsingWLST+testDomainOnPVUsingWDT" -DfailIfNoTests=false integration-test -P java-integration-tests

# How to run cleanup script

cleanup script deletes the k8s artifacts, local test tmp directory, delete all helm charts and the potentially remote domain pv directories.
cd weblogic-kubernetes-operator
src/integration-tests/bash/cleanup.sh

# Logging/Archiving

Java utils logging is used, writes all the messages to console and java_test_suite.out in $RESULT_ROOT/acceptance_test_tmp directory.
At the end of the test run, all pods logs, describes are logged in individual files and are written to state-dump-logs directory in $RESULT_ROOT/acceptance_test_tmp.

$RESULT_ROOT/acceptance_test_tmp is archived under $RESULT_ROOT/acceptance_test_tmp_archive

$PV_ROOT/acceptance_test_pv is archived under $PV_ROOT/acceptance_test_pv_archive

On Wercker, these logs can be downloaded by clicking "Download artifact" on cleanup and store step.

# How to add a new test

Add a new JUnit test under integration-tests/src/test/java/oracle/kubernetes/operator.

class name must start with IT(Integration Test), IT*.java

ITOperator.java - take a look at this test for reference

# Future enhancement

Add functional tests

## Troubleshooting

The integration tests are not completely independent of the environment.

You may run into one or more of the following errors when you attempt to execute the command:
```
mvn clean verify -P java-integration-tests 2>&1 | tee log.txt
```
1. `[ERROR] No permision to create directory /scratch/...`  

  There are a couple ways to resolve this issue:

  * Create a world writable directory named `/scratch`.
  * Create some other world writable directory and then define the environment variables `RESULT_ROOT` and `PV_ROOT` to point to that directory. If you want, you can create two directories to keep things separated. See [Directory Configuration and Structure](#directory-configuration-and-structure) for more details.
