# Integration Tests for Oracle WebLogic Server Kubernetes Operator

This documentation describes the functional use cases that are covered in integration testing for the Oracle WebLogic Server Kubernetes Operator. The tests are written in Java (JUnit tests) and driven by Maven profile. 

# Environments

The tests currently run in three modes: "Wercker", "Jenkins", and "standalone" Oracle Linux, where the mode is controlled by the WERCKER and JENKINS environment variables described below. The default is "standalone".

* "Standalone" Oracle Linux, i.e, run the tests manually with mvn command. 
* Wercker - https://app.wercker.com/Oracle/weblogic-kubernetes-operator/runs - integration-test-java is the pipeline name
* Jenkins - http://wls-jenkins.us.oracle.com/view/weblogic-operator/job/weblogic-kubernetes-operator-javatest/ - Jenkins Run is restricted to Oracle Internal development Process

Wercker runs only Quick test use cases, Jenkins run both Quick and Full test use cases.

# Use Cases

Java integration tests cover the below use cases:

Quick test use cases - 

|  |  |
| --- | --- |
| Operator Configuration | operator1 deployed in weblogic-operator1 namespace and manages domains in default and test1 namespaces |
| Domain Configuration | Domain on PV using WLST, traefik load balancer |

Basic Use Cases

1. create operator operator1 which manages default and test1 namespaces, verify its deployed successfully, pod created, operator Ready and verify external REST service if configured
2. create domain domain1 in default namespace and verify the pods, services are created and servers are in Ready
3. verify admin external service by accessing admin REST endpoint with nodeport in URL
4. verify admin t3 channel port by exec into the admin pod and deploying webapp using the channel port for WLST
5. verify web app load balancing by accessing the webapp using loadBalancerWebPort

Advanced Use Cases

6. verify domain life cycle(destroy and create) should not any impact on Operator managing the domain and web app load balancing and admin external service
7. cluster scale up/down using Operator REST endpoint, webapp load balancing should adjust accordingly.
8. Operator life cycle(destroy and create) should not impact the running domain

Also the below use cases are covered for Quick test

9. verify liveness probe by killing managed server 1 process 3 times to kick pod auto-restart
10. shutdown the domain by changing domain serverStartPolicy to NEVER

Full test use cases -

|  |  |
| --- | --- |
| Operator Configuration | operator2 deployed in weblogic-operator2 namespace and manages domains test2 namespace |
| Domain Configuration | Domain on PV using WDT, Domain with serverStartPolicy ADMIN_ONLY, Domain with auto and custom situational configuration, Two domains managed by two operators, Domain with Recycle weblogicDomainStorageReclaimPolicy, Domain with default sample values |


Basic Use Cases described above are verified in all the domain configurations. Also the below use cases are covered:

| Domain | Use Case |
| --- | --- |
| Domain on PV using WDT | WLDF scaling |
| Domain with ADMIN_ONLY | making sure only admin server is started and managed servers are not started. Shutdown domain by deleting domain CRD. Create domain on existing PV dir, pv is already populated by a shutdown domain. |
| Domain with situational config | create domain with listen address not set for admin server and t3 channel/NAP and incorrect file for admin server log location. Introspector should override these with sit-config automatically. Also, with some junk value for t3 channel public address and using custom situational config override replace with valid public address using secret. Also, on Jenkins this domain uses NFS instead of HOSTPATH PV storage |	
| Two domains managed by two operators | verify scaling and restart of one domain doesn't impact another domain. Delete domain resources using delete script from samples. |			
| Domain with Recycle policy | create domain with pvReclaimPolicy="Recycle" Verify that the PV is deleted once the domain and PVC are deleted |
| Domain with default sample values | create domain using mostly default values for inputs |					
						

# Directory Configuration and Structure

Directory structure of source code:

A new module "integration-tests" is added to the Maven project weblogic-kubernetes-operator.

weblogic-kubernetes-operator/integration-tests - location of module pom.xml  
weblogic-kubernetes-operator/integration-tests/src/test/java - integration test(JUnit) classes and utility classes  
weblogic-kubernetes-operator/integration-tests/src/test/resources - properties, yaml files(see Configuration Files section) and other scripts

Directory structure used for the test run:

Main external env vars:

| Variable | Description |
| --- | --- |
| RESULT_ROOT  | Root path for local test files. |
| PV_ROOT      | Root NFS path behind PV/C directories.  This must have permissions suitable for WL pods to add files |

Defaults for RESULT_ROOT & PV_ROOT:

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

When the tests are run manually with mvn command on hosted Linux, WebLogic image and server jre images are pulled from a local repository wlsldi-v2.docker.oraclecorp.com. Operator image is built with the git branch from where the mvn command is executed.
All the tests that start with IT*.java are run. The test builds the operator, runs a series of tests and archives the results into tar.gz files upon completion.

Integration test classes:

When the integration test class ITOperator is executed, staticPrepare() method is called once before any of the test methods in the class and staticUnPrepare() method once at the end.

staticPrepare() - initializes the application properties from OperatorIT.properties and creates resultRoot, pvRoot, userprojectsDir directories by calling initialize() method from the base class BaseTest. 

staticUnPrepare() - releases the cluster lease on wercker env.

test methods -  testDomainOnPVUsingWLST, testDomainOnPVUsingWDT, testTwoDomainsManagedByTwoOperators, testCreateDomainWithStartPolicyAdminOnly, testCreateDomainPVReclaimPolicyRecycle, testCreateDomainWithDefaultValuesInSampleInputs, testAutoAndCustomSitConfigOverrides, testOperatorRESTIdentityBackwardCompatibility, testOperatorRESTUsingCertificateChain

Utility classes:

Operator - contains methods to create/destroy operator, verify operator created, scale using rest api, etc
Domain - contains methods to create/destroy domain, verify domain created,deploy webapp, load balancing, etc
PersistentVolume - to create PV
LoadBalancer - to create load balancer
Secret - to create secret

# How to run the Java integration tests

* Maven and latest Git should be in PATH
* export JAVA_HOME

Command to run the tests:
```
mvn clean verify -P java-integration-tests 2>&1 | tee log.txt
```

The tests accepts optional env var overrides:

| Variable | Description |
| --- | --- |
| RESULT_ROOT | The root directory to use for the tests temporary files. See "Directory Configuration and Structure" for                  defaults and a detailed description of test directories. |
| PV_ROOT    |  The root directory on the kubernetes cluster used for persistent volumes. See "Directory Configuration and Structure" for defaults and a detailed description of test directories. |
| QUICKTEST  | When set to "true", limits testing to a subset of the tests. |
| WERCKER    | Set to true if invoking from Wercker, set to false or "" if running stand-alone or from Jenkins. Default is "". |
| JENKINS    | Set to true if invoking from Jenkins, set to false or "" if running stand-alone or from Wercker. Default is "". |
| NODEPORT_HOST | DNS name of a Kubernetes worker node. Default is the local host's hostname. |
| BRANCH_NAME  | Git branch name.   Default is determined by calling 'git branch'. |
| LEASE_ID   |   Set to a unique value to (A) periodically renew a lease on the k8s cluster that indicates that no other test run should attempt to use the cluster, and (B) delete this lease when the test completes. |

The following additional overrides are currently only used when
WERCKER=true:

| Variable | Description |
| --- | --- |
| IMAGE_TAG_OPERATOR | Docker image tag for operator. Default generated based off the BRANCH_NAME. |
| IMAGE_NAME_OPERATOR | Docker image name for operator. Default is wlsldi-v2.docker.oraclecorp.com/weblogic-operator |
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
