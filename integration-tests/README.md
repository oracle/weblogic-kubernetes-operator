# Integration Tests for Oracle WebLogic Server Kubernetes Operator

This documentation describes the functional use cases that are covered in integration testing for the Oracle WebLogic Server Kubernetes Operator. The tests are written in Java (JUnit tests) and driven by Maven profile. All the use cases covered in run.sh are covered in Java integration tests. 

# Environments

The tests currently run in three modes: "Wercker", "Jenkins", and "standalone" Oracle Linux, where the mode is controlled by the WERCKER and JENKINS environment variables described below. The default is "standalone".

* "Standalone" Oracle Linux, i.e, run the tests manually with mvn command. 
* Wercker - https://app.wercker.com/Oracle/weblogic-kubernetes-operator/runs - integration-test-java is the pipeline name
* Jenkins - http://wls-jenkins.us.oracle.com/view/weblogic-operator/job/weblogic-kubernetes-operator-javatest/ - Jenkins Run is restricted to Oracle Internal development Process

# Use Cases

Java integration tests cover all use cases from run.sh. The below are the use cases:

* create operator operator1 which manages default and test1 namespaces, verify its deployed successfully, pod created, operator Ready and verify external REST service if configured
* create domain domain1 in default namespace and verify the pods, services are created and servers are in Ready
* verify admin external service by accessing admin REST endpoint with nodeport in URL
* verify admin t3 channel port by exec into the admin pod and deploying webapp using the channel port for WLST
* verify web app load balancing  
* verify domain life cycle(destroy and create) should not any impact on Operator managing the domain and web app load balancing and admin external service
* cluster scale up/down using Operator REST endpoint, webapp load balancing should adjust accordingly. (run.sh does scaling by editing the replicas in domain-custom-resource.yaml.)
* Operator life cycle(destroy and create) should not impact the running domain

Full test use cases

* keep the first domain and operator running
* create another domain domain2 in default namespace and verify the domain by doing the checks 2 - 5 listed in quick test
* destroy domain domain2
* create another domain domain3 in test1 namespace and verify the domain by doing the checks 2 - 5 listed in quick test
* destroy domain domain3
* create another operator operator2 which manages test2 namespace and verify domain1 is not affected
* create another domain domain4 in test2 namespace and verify the domain by doing the checks 2 - 5 listed in quick test
* verify scaling for domain4 cluster from 2 to 3 servers and back to 2, plus verify no impact on domain1
* cycle domain1 down and back up, plus verify no impact on domain4
* create domain5 in the default namespace with startupControl="ADMIN", and verify that only admin server is created. on Jenkins, this domain will also test NFS instead of HOSTPATH PV storage
* create domain6 in the default namespace with pvReclaimPolicy="Recycle", and verify that the PV is deleted once the domain and PVC are deleted
* test managed server 1 pod auto-restart in domain1
* destroy domain1
* test that create domain fails when its pv is already populated by a shutdown domain
* create another domain domain7 with APACHE load balancer and access admin console via LB port. 


# Directory Configuration and Structure

Directory structure of source code:

A new module "integration-tests" is added to the Maven project weblogic-kubernetes-operator.

weblogic-kubernetes-operator/integration-tests - location of module pom.xml  
weblogic-kubernetes-operator/integration-tests/src/test/java - integration test(JUnit) classes and utility classes  
weblogic-kubernetes-operator/integration-tests/src/test/resources - properties files(see Configuration Files section) and other scripts

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

Below configuration files are used:

src/integration-tests/resources/OperatorIT.properties - This file is used for configuring common attributes for all integration tests
```
baseDir=/scratch  
username=weblogic  
password=welcome1  
maxIterationsPod=50  
waitTimePod=5  
```

src/integration-tests/resources/ITFirstOperator.properties - customized properties for Operator. Any property can be provided here from create-weblogic-operator-inputs.yaml, for all the properties that are not defined here default values in create-weblogic-operator-inputs.yaml are used while generating the operator inputs yaml file.

```
serviceAccount= weblogic-operator
namespace= weblogic-operator1
targetNamespaces= default
#if SELF_SIGNED_CERT, externalSans value is set to the local host name if not provided here
externalRestOption= SELF_SIGNED_CERT
elkIntegrationEnabled= false
```

src/integration-tests/resources/ITFirstDomain.properties - customized properties for WebLogic Domain. Any property can be provided here from create-weblogic-domain-inputs.yaml. For all the properties that are not defined here, the default values in create-weblogic-domain-inputs.yaml are used while generating the domain inputs yaml file.

```
adminServerName= admin-server
domainName= base_domain
domainUID= domain1
startupControl= AUTO
clusterName= cluster-1
configuredManagedServerCount= 4
initialManagedServerReplicas= 2
managedServerNameBase= managed-server
#weblogicDomainStoragePath will be ignored, PV dir will be created at /<baseDir>/<USER>/acceptance_test_pv
#weblogicDomainStoragePath= /scratch/external-domain-home/pv001/
secretName= domain1-weblogic-credentials
exposeAdminT3Channel= true
exposeAdminNodePort= true
namespace= default
javaOptions= -Dweblogic.StdoutDebugEnabled=false

```
Certain properties like weblogicDomainStoragePath, weblogicOperatorImage, externalSans are populated at run time.


# How does it work

When the tests are run manually with mvn command on hosted Linux, WebLogic image and server jre images are pulled from a local repository. Operator image is built with the git branch from where the mvn command is executed.
All the tests that start with IT*.java are run. The test builds the operator, runs a series of tests and archives the results into tar.gz files upon completion.

Integration test classes:

When the integration test class ITFirstDomain is executed, staticPrepare() method is called once before any of the test methods in the class and staticUnPrepare() method once at the end.

staticPrepare() - initializes the application properties from OperatorIT.properties and creates resultRoot, pvRoot, userprojectsDir directories by calling initialize() method from the base class BaseTest. It also creates the operator and domain objects which are used later in the test methods.

staticUnPrepare() - releases the cluster lease on wercker env.

test methods - testAdminServerExternalService, testAdminT3Channel, testDomainLifecyle, testClusterScaling, testOperatorLifecycle

Utility classes:

Operator - contains methods to create/destroy operator, verify operator created, scale using rest api, etc
Domain - contains methods to create/destroy domain, verify domain created,deploy webapp, load balancing, etc
PersistentVolume - to create PV
Secret - to create secret

# How to run the Java integration tests

Maven and latest Git should be in PATH
export JAVA_HOME

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
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 970.765 s - in oracle.kubernetes.operator.ITSingleDomain
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO]
[INFO] --- maven-failsafe-plugin:2.20.1:verify (integration-tests) @ operator-integration-tests ---
[INFO]
[INFO] --- maven-failsafe-plugin:2.20.1:integration-test (acceptance-test) @ operator-integration-tests ---
[INFO]
[INFO] --- maven-failsafe-plugin:2.20.1:verify (acceptance-test) @ operator-integration-tests ---
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary:
[INFO]
[INFO] weblogic-kubernetes-operator ....................... SUCCESS [  0.472 s]
[INFO] operator-model ..................................... SUCCESS [  9.696 s]
[INFO] operator-swagger ................................... SUCCESS [  2.300 s]
[INFO] operator-runtime ................................... SUCCESS [ 52.737 s]
[INFO] operator-integration-tests ......................... SUCCESS [16:33 min]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 17:39 min
[INFO] Finished at: 2018-05-16T14:25:30-07:00
[INFO] Final Memory: 54M/871M
[INFO] ------------------------------------------------------------------------
bash-4.2$

```
Failed run will have the output
```
[ERROR] Tests run: 5, Failures: 0, Errors: 3, Skipped: 0, Time elapsed: 1,271.204 s <<< FAILURE! - in oracle.kubernetes.operator.ITSingleDomain
[ERROR] testAdminT3Channel(oracle.kubernetes.operator.ITSingleDomain)  Time elapsed: 145.59 s  <<< ERROR!
java.lang.RuntimeException: FAILURE: testwebapp did not return 200 status code, got 404
    at oracle.kubernetes.operator.ITSingleDomain.testAdminT3Channel(ITSingleDomain.java:84)

[ERROR] testDomainLifecyle(oracle.kubernetes.operator.ITSingleDomain)  Time elapsed: 378.096 s  <<< ERROR!
java.lang.RuntimeException: FAILURE: testwebapp did not return 200 status code, got 404
    at oracle.kubernetes.operator.ITSingleDomain.testDomainLifecyle(ITSingleDomain.java:103)

[ERROR] testClusterScaling(oracle.kubernetes.operator.ITSingleDomain)  Time elapsed: 223.707 s  <<< ERROR!
java.lang.RuntimeException: FAILURE: testwebapp did not return 200 status code, got 404
    at oracle.kubernetes.operator.ITSingleDomain.testClusterScaling(ITSingleDomain.java:144)

[INFO]
[INFO] Results:
[INFO]
[ERROR] Errors:
[ERROR]   ITSingleDomain.testAdminT3Channel:84 » Runtime FAILURE: testwebapp did not ret...
[ERROR]   ITSingleDomain.testClusterScaling:144 » Runtime FAILURE: testwebapp did not re...
[ERROR]   ITSingleDomain.testDomainLifecyle:103 » Runtime FAILURE: testwebapp did not re...
[INFO]
[ERROR] Tests run: 5, Failures: 0, Errors: 3, Skipped: 0
[INFO]
[INFO]
[INFO] --- maven-failsafe-plugin:2.20.1:verify (integration-tests) @ operator-integration-tests ---
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary:
[INFO]
[INFO] weblogic-kubernetes-operator ....................... SUCCESS [  0.756 s]
[INFO] operator-model ..................................... SUCCESS [ 12.394 s]
[INFO] operator-swagger ................................... SUCCESS [  2.014 s]
[INFO] operator-runtime ................................... SUCCESS [ 53.771 s]
[INFO] operator-integration-tests ......................... FAILURE [21:35 min]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 22:44 min
[INFO] Finished at: 2018-05-17T06:42:35-07:00
[INFO] Final Memory: 55M/933M
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-failsafe-plugin:2.20.1:verify (integration-tests) on project operator-integration-tests: There are test failures.
[ERROR]
[ERROR] Please refer to /scratch/vmukkara/mychanges/weblogic-kubernetes-operator/integration-tests/target/failsafe-reports for the individual test results.
[ERROR] Please refer to dump files (if any exist) [date]-jvmRun[N].dump, [date].dumpstream and [date]-jvmRun[N].dumpstream

```
JUnit test results can be seen at "integration-tests/target/failsafe-reports/TEST-oracle.kubernetes.operator.ITSingleDomain.xml". This file shows how much time each test case took to run and the failed test results if any.

# How to run a single test

mvn -Dit.test="ITOperator#test6CreateConfiguredDomainInTest2NS" -DfailIfNoTests=false integration-test -P java-integration-tests

# How to run multiple tests

mvn -Dit.test="ITOperator#test6CreateConfiguredDomainInTest2NS+test7CreateDomainPVReclaimPolicyRecycle" -DfailIfNoTests=false integration-test -P java-integration-tests

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
