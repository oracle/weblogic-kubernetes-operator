# Integration Tests for Oracle WebLogic Server Kubernetes Operator

This documentation describes the functional use cases that are covered in integration testing for the Oracle WebLogic Server Kubernetes Operator. The tests are written in Java (JUnit tests) and driven by Maven profile.

# Environments

The tests currently run in three modes: "shared cluster", "Jenkins", and "standalone" Oracle Linux, where the mode is controlled by the `SHARED_CLUSTER` and `JENKINS` environment variables described below. The default is "standalone".

* "Standalone" Oracle Linux, i.e, run the tests manually with the `mvn` command.
* Shared cluster(remote k8s cluster) - http://build.weblogick8s.org:8080/job/weblogic-kubernetes-operator-quicktest/
* Jenkins - http://wls-jenkins.us.oracle.com/view/weblogic-operator/job/weblogic-kubernetes-operator-javatest/ - Jenkins Run is restricted to Oracle Internal development process. 

Shared cluster runs only Quick test use cases, Jenkins runs both Quick and Full test use cases.

# Use Cases

Use Cases covered in integration tests for the operator is available [here](USECASES.MD)

JRF Use Cases are covered [here](JRFUSECASES.MD)

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
| Shared cluster	| /pipeline/output/k8s_dir	| /scratch	|  |


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

src/integration-tests/resources/domainonpvwlst.yaml - See [Input Yaml to the test](#input-yaml-to-the-test). For all the properties that are not defined here, the default values in the sample inputs are used while generating inputs yaml.

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

# Input Yaml to the test

The input yaml file(for example, domainonpvwlst.yaml) to the java test is used to override any or all the attributes in
- samples domain inputs - click [here](https://github.com/oracle/weblogic-kubernetes-operator/blob/develop/kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain-inputs.yaml) to see samples create-domain-inputs file
- PV/PVC inputs - click [here](https://github.com/oracle/weblogic-kubernetes-operator/blob/develop/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml) to see PV/PVC inputs file
- load balancer attributes and
- custom attributes that are used to provide more flexibility for creating the domain.

Below are the load balancer attributes:
- loadBalancer: value can be `TRAEFIK` or `VOYAGER`, default is `TRAEFIK`
- loadBalancerWebPort: web port for the load balancer
- ingressPerDomain: create ingress per domain or one ingress for all domains in multiple domain configuration, default is true. 

**Note: System env variables LB_TYPE, INGRESSPERDOMAIN take precedence over the domain input attributes. These variables apply for the entire run, not just one domain.**

Below are the custom attributes:
- createDomainPyScript is used to provide a custom create-domain.py script for domain on pv using WLST or create-wls-domain.py for domain in image 
- clusterType is used to create a CONFIGURED or DYNAMIC Cluster. Default is DYNAMIC. This is supported for domain on pv using WLST or domain in image using WDT configurations. 
  
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
	
All the tests that start with It*.java in integration-tests/src/test/java are run. 

**Integration test classes:**

When the integration test class ItOperator is executed, staticPrepare() method is called once before any of the test methods in the class and staticUnPrepare() method once at the end.

staticPrepare() - initializes the application properties from OperatorIT.properties and creates resultRoot, pvRoot, userprojectsDir directories by calling initialize() method from the base class BaseTest.

staticUnPrepare() - releases the cluster lease.

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

# How to run Operator integration tests using Weblogic image

* Maven and latest Git should be in PATH
* export JAVA_HOME
* export IMAGE_NAME_WEBLOGIC and IMAGE_TAG_WEBLOGIC if different from container-registry.oracle.com/middleware/weblogic and 12.2.1.3
* Setup docker access to WebLogic 12c Images 

  * Method 1 
    - Setup a personal account on container-registry.oracle.com
    - Then sign in to container-registry.oracle.com and signup for access to WebLogic 12.2.1.3 images from container-registry.oracle.com/middleware/weblogic:12.2.1.3
    - Then export the following before running the tests: 
	```
	export OCR_USERNAME=<ocr_username>
	export OCR_PASSWORD=<ocr_password>
	```
 
  * Method 2 
    - Make sure the weblogic image i.e. container-registry.oracle.com/middleware/weblogic:12.2.1.3 already exists locally in a docker repository the k8s cluster can access
    - Make sure the weblogic image has patch p29135930 (required for the WebLogic Kubernetes Operator). 
		
		
* Command to run the tests: This will run the tests sequentially.

```
mvn clean verify -P wls-integration-tests 2>&1 | tee log.txt
```
To run QUICKTEST, `export QUICKTEST=true`. 

To run the test classes in parallel, 
```
mvn -DPARALLEL=true clean verify -P wls-integration-tests 2>&1 | tee log.txt
```

The tests accepts optional env var overrides:

| Variable | Description |
| --- | --- |
| RESULT_ROOT | The root directory to use for the tests temporary files. See "Directory Configuration and Structure" for                  defaults and a detailed description of test directories. |
| PV_ROOT    |  The root directory on the kubernetes cluster used for persistent volumes. See "Directory Configuration and Structure" for defaults and a detailed description of test directories. |
| QUICKTEST  | When set to "false", runs all the integration tests. Default is true, which limits to a subset of tests |
| LB_TYPE    | The default value is "TRAEFIK". Set to "VOYAGER" if you want to use it as LB. Using "VOYAGER" requires unique "voyagerWebPort"|
| INGRESSPERDOMAIN  | The defult value is true. If you want to test creating TRAEFIK LB by kubectl yaml for multiple domains, set it to false. |
| SHARED_CLUSTER    | Set to true if invoking on shared cluster, set to false or "" if running stand-alone or from Jenkins. Default is "". |
| JENKINS    | Set to true if invoking from Jenkins, set to false or "" if running stand-alone or on shared cluster. Default is "". |
| K8S_NODEPORT_HOST | DNS name of a Kubernetes worker node. Default is the local host's hostname. |
| BRANCH_NAME  | Git branch name.   Default is determined by calling 'git branch'. |
| LEASE_ID   |   Set to a unique value to (A) periodically renew a lease on the k8s cluster that indicates that no other test run should attempt to use the cluster, and (B) delete this lease when the test completes. |

The following additional overrides are currently only used when
SHARED_CLUSTER=true:

| Variable | Description |
| --- | --- |
| IMAGE_TAG_OPERATOR | Docker image tag for operator. Default generated based off the BRANCH_NAME. |
| IMAGE_NAME_OPERATOR | Docker image name for operator. Default is weblogic-kubernetes-operator |
| IMAGE_PULL_POLICY_OPERATOR | Default 'Never'. |
| IMAGE_PULL_SECRET_OPERATOR | Default ''. |
| IMAGE_PULL_SECRET_WEBLOGIC | Default ''. |

The below env variables are required for SHARED_CLUSTER=true:

| Variable | Description |
| --- | --- |
| REPO_REGISTRY | OCR Server to push/pull the Operator image |
| REPO_USERNAME | OCR username |
| REPO_PASSWORD | OCR password |
| REPO_EMAIL | OCR e-mail |
| DOCKER_USERNAME | Docker username to pull the Weblogic image  |
| DOCKER_PASSWORD | Docker password |
| DOCKER_EMAIL | Docker e-mail |
| K8S_NODEPORT_HOST | DNS name of a Kubernetes worker node. |

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
[ERROR]   ItOperator.testDomainOnPVUsingWLST:145 ? Runtime FAILURE: Couldn't create serv...
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
JUnit test results can be seen at "integration-tests/target/failsafe-reports/TEST-oracle.kubernetes.operator.ItOperator.xml". This file shows how much time each test case took to run and the failed test results if any.

# How to run Operator integration tests using FMW infra image
* Setup docker access to FMW Infrastructure 12c Image and Oracle Database 12c Image
   * Method 1 
      - Setup a personal account on container-registry.oracle.com
      - Then sign in to container-registry.oracle.com and accept license for access to Oracle Database 12c Images:  **_container-registry.oracle.com/database/enterprise:12.2.0.1-slim_**
      - And get access to FMW Infrastructure 12c Image: **_container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3_**
      - export the following before running the tests:
    ```
    export REPO_USERNAME=<ocr_username>
    export REPO_PASSWORD=<ocr_password>
    export REPO_EMAIL=<ocr_email>
    ```
 
   * Method 2 
     - Make sure the FMW Infrastructure image i.e. **_container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.3_** and the Oracle database image i.e. **_container-registry.oracle.com/database/enterprise:12.2.0.1-slim_** already exist locally in a docker repository the k8s cluster can access
		
* Command to run the tests: 
```
mvn clean verify -P jrf-integration-tests 2>&1 | tee log.txt
```

# How to run a single test

mvn -Dit.test="ItOperator#testDomainOnPVUsingWLST" -DfailIfNoTests=false clean verify -P wls-integration-tests

# How to run multiple tests

mvn -Dit.test="ItOperator#testDomainOnPVUsingWLST+testDomainOnPVUsingWDT" -DfailIfNoTests=false clean verify -P wls-integration-tests

# How to run cleanup script

cleanup script deletes the k8s artifacts, local test tmp directory, delete all helm charts and the potentially remote domain pv directories.
cd weblogic-kubernetes-operator
src/integration-tests/bash/cleanup.sh

# Logging/Archiving

Java utils logging is used, writes all the messages to console and java_test_suite.out in $RESULT_ROOT/acceptance_test_tmp directory.
At the end of the test run, all pods logs, describes are logged in individual files and are written to state-dump-logs directory in $RESULT_ROOT/acceptance_test_tmp.

$RESULT_ROOT/acceptance_test_tmp is archived under $RESULT_ROOT/acceptance_test_tmp_archive

$PV_ROOT/acceptance_test_pv is archived under $PV_ROOT/acceptance_test_pv_archive

# How to add a new test

Add a new JUnit test under integration-tests/src/test/java/oracle/kubernetes/operator.

class name must start with It(Integration Test), It*.java

ItOperator.java - take a look at this test for reference

# Future enhancement

Add functional tests

## Troubleshooting

The integration tests are not completely independent of the environment.

You may run into one or more of the following errors when you attempt to execute the command:
```
mvn clean verify -P wls-integration-tests 2>&1 | tee log.txt
```
1. `[ERROR] No permision to create directory /scratch/...`  

  There are a couple ways to resolve this issue:

  * Create a world writable directory named `/scratch`.
  * Create some other world writable directory and then define the environment variables `RESULT_ROOT` and `PV_ROOT` to point to that directory. If you want, you can create two directories to keep things separated. See [Directory Configuration and Structure](#directory-configuration-and-structure) for more details.
