# Integration Tests for Oracle WebLogic Server Kubernetes Operator

This documentation describes the procedure to run integration test for the Oracle WebLogic Server Kubernetes Operator. The tests are written in Java (JUnit5 tests) and driven by Maven profile.

# Environments

*  Oracle Linux, i.e, run the tests manually with the `mvn` command.

# Directory Configuration and Structure
 
Directory structure of source code:

A module "integration-tests" is added to the Maven project `weblogic-kubernetes-operator`.

`weblogic-kubernetes-operator/integration-tests` - location of module pom.xml  
`weblogic-kubernetes-operator/integration-tests/src/test/java/oracle/weblogic/kubernetes` - integration test(JUnit5) classes and utility classes  
`weblogic-kubernetes-operator/integration-tests/src/test/resources` - properties, YAML files and other bash scripts

# How does integration test infrastructure works
 - A weblogic domain image is built using Model In Image model 
 - Istio mesh is installed 
 
# How to run Operator integration tests locally on Oracle Linux

- Install supported version Helm and Kubernetes cluster. For detail see [Weblogic Kubernetes Operator guide](https://oracle.github.io/weblogic-kubernetes-operator/userguide/introduction/introduction/).
- Install supported version JDK. Need JDK Version 11+ and above.
- Clone the weblogic-kubernetes-operator repository
- Setup a personal account on container-registry.oracle.com
- Log into container-registry.oracle.com and signup for access to WebLogic 12.2.1.4 images from container-registry.oracle.com/middleware/weblogic:12.2.1.4
- Export the following before running the tests:
   ```
    export BASE_IMAGES_REPO="container-registry.oracle.com"
    export OCR_EMAIL=<ocr_email>
    export OCR_USERNAME=<ocr_username>
    export OCR_PASSWORD=<ocr_password> 
   ```
- In weblogic-kubernetes-operator run the following commands
  ```
    # Build the Weblogic Kubernetes Operator image locally for the first time 
    mvn clean install 
    # Run tests in sequential
    mvn -pl -integration-tests -P integration-tests verify
    # Run tests in parallel
    mvn -DPARALLEL=true -DNUMBER_OF_THREADS=2 -pl -integration-tests -P integration-tests verify
   ```
## How to run a single test class 
mvn -Dit.test=ItMiiDomain -pl integration-tests -P integration-tests verify

## How to run a single test method in a test class 

mvn -Dit.test="ItMiiUpdateDomainConfig#testMiiDeleteSystemResources" -pl integration-tests -P integration-tests verify

## How to run multiple tests

mvn -Dit.test="ItCrossDomainTransaction,ItMiiUpdateDomainConfig" -pl integration-tests -P integration-tests verify

## Environment vaiables to manage test execution 
| Variable | Description | Default Value
| --- | --- | --- |
| SKIP_CLEANUP  | skips cleanup done by the test infra, all the pods/domains/etc will be left running if set to true  | true
| COLLECT_LOGS_ON_SUCCESS  | logs are generated even if test pass if set to true | true
| RESULTS_ROOT | Root directory for test result | /tmp/ittestsresults
| LOGS_DIR  | Root directory for the test log | /tmp/diagnosticlogs
| PV_ROOT  | Root directory for Persistent Volume  | /tmp/ittestspvroot

## Logging/Archiving

- Temporary installation of tools such as imagetool, istio are in /tmp/it-results diretory
- Test result stdout are in /tmp/ittestsresults diretory
- Test Diagnostic stdout are in /tmp/diagnosticlogs diretory

## Troubleshooting
