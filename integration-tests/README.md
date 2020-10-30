# Integration Tests for Oracle WebLogic Server Kubernetes Operator

This documentation describes the functional use cases that are covered in integration testing for the Oracle WebLogic Server Kubernetes Operator. The tests are written in Java (JUnit5 tests) and driven by Maven profile.

# Environments

The tests currently run in three modes: "Jenkins", "Kind Cluster" and "Local".

* "Local" Oracle Linux, i.e, run the tests manually with the `mvn` command.
* "Kind Cluster" Create a Kind Cluster before running the test  
* "Jenkin" - http://build.weblogick8s.org:8080/job/weblogic-kubernetes-operator-integration/ Jenkins Run is restricted to authorized users on Jenkin server.

# Directory Configuration and Structure
 
Directory structure of source code:

A new module "integration-tests" is added to the Maven project `weblogic-kubernetes-operator`.

`weblogic-kubernetes-operator/integration-tests` - location of module pom.xml  
`weblogic-kubernetes-operator/integration-tests/src/test/java/oracle/weblogic/kubernetes` - integration test(JUnit5) classes and utility classes  
`weblogic-kubernetes-operator/integration-tests/src/test/resources` - properties, YAML files and other bash scripts

# How to run Operator integration tests locally on Oracle Linux

- Configure a supported version of Kubernetes cluster.
- Install supported version git.
- Install supported version mvn.
- Install supported version Helm.
- Install supported version JDK.
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
    To build the Weblogic Kubernetes Operator image locally for the first time 
    mvn clean install 
    To run tests in sequential
    mvn -pl -integration-tests -P integration-tests verify
    To run tests in parallel
    mvn -DPARALLEL=true -DNUMBER_OF_THREADS=2 -pl -integration-tests -P integration-tests verify
```

## How to run a single test class 
mvn -Dit.test=ItMiiDomain -pl integration-tests -P integration-tests verify

## How to run a single test method in a test class 

mvn -Dit.test="ItMiiUpdateDomainConfig#testMiiDeleteSystemResources" -pl integration-tests -P integration-tests verify

## How to run multiple tests

mvn -Dit.test="ItCrossDomainTransaction,ItMiiUpdateDomainConfig" -pl integration-tests -P integration-tests verify

## Environment vaiables to manage test execution 
| Variable | Description |
| --- | --- |
| SKIP_CLEANUP  | skips cleanup done by the test infra, all the pods/domains/etc will be left running if set to true  |
| COLLECT_LOGS_ON_SUCCESS  | logs are generated even if test pass if set to true |

## Logging/Archiving

- Temporary installation of tools such as imagetool, istio are in /tmp/it-results diretory
- Test result stdout are in /tmp/it-results diretory
- Test Diagnostic stdout are in /tmp/diagnosticlogs diretory

## Troubleshooting
