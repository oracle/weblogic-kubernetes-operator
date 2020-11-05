# Integration tests for Oracle WebLogic Server Kubernetes Operator
This documentation describes the procedure to run integration tests for the WebLogic Server Kubernetes Operator locally in the Oracle Linux environment. The tests are written in the JUnit5 framework and driven by maven profile.

# Directory Configuration and Structure
Directory structure of integration test source is distributed as follows 
```
weblogic-kubernetes-operator/integration-tests - location of module pom.xml  
weblogic-kubernetes-operator/integration-tests/src/test/java/oracle/weblogic/kubernetes - integration test(JUnit5) classes and utility classes  
weblogic-kubernetes-operator/integration-tests/src/test/resources - properties, YAML files and other bash scripts
```

# How does integration test infrastructure works
 - Build weblogic kubernetes operator image from the downloaded branch.
 - Download the latest version of WebLogic Deploy Tooling (WDT) and WebLogic Image Tool (WIT)
 - Pull the specified version of WebLogic Image from container-registry.oracle.com (OCR) or phx.ocir.io (OCIR) 
 - Build a simple weblogic domain image with a dynamic weblogic cluster and a sample web application using Model-In-Image model.
 - Install istio service mesh.
 - After test execution, clean all the Namespaces, Kubernetes Objects created during test execution.
 - Archive the test stdout for each test class and kubernates object details for each test method in the diagnostic directory for triage. 
 
# How to run Operator integration tests locally on Oracle Linux

- Install supported version Helm and Kubernetes cluster. For detail see [Weblogic Kubernetes Operator guide](https://oracle.github.io/weblogic-kubernetes-operator/userguide/introduction/introduction/).
- Install supported version JDK. Need JDK Version 11+ and above.
- Clone the weblogic-kubernetes-operator repository
- Setup a personal account on container-registry.oracle.com
- Log into container-registry.oracle.com and signup for access to WebLogic 12.2.1.4 images from container-registry.oracle.com/middleware/weblogic:12.2.1.4
- Export the following environment variable before running the tests
   ```
    export BASE_IMAGES_REPO="container-registry.oracle.com"
    export OCR_EMAIL=<ocr_email>
    export OCR_USERNAME=<ocr_username>
    export OCR_PASSWORD=<ocr_password> 
   ```
- In weblogic-kubernetes-operator run the following commands
  ```
    # Build the Weblogic Kubernetes Operator binary 
    mvn clean install 
    # Run tests in sequential
    mvn -pl -integration-tests -P integration-tests verify
    # Run tests in parallel
    mvn -DPARALLEL=true -DNUMBER_OF_THREADS=2 -pl -integration-tests -P integration-tests verify
   ```
## How to run individual test class(es)
mvn -Dit.test=ItMiiDomain,ItMiiUpdateDomainConfig -pl integration-tests -P integration-tests verify

## How to run a single test method in a test class 
mvn -Dit.test="ItMiiUpdateDomainConfig#testMiiDeleteSystemResources" -pl integration-tests -P integration-tests verify

## How to exclude test class(es) 
mvn -Dit.test="!ItCrossDomainTransaction,!ItMiiUpdateDomainConfig" -pl integration-tests -P integration-tests verify

## Environment variables to manage test execution 
| Variable | Description | Default Value
| --- | --- | --- |
| SKIP_CLEANUP  | Test infra remove for all kubernetes objects created during test execution. To retain all such objects for triaging a test failure this environment variable should be set to true. User need to run weblogic-kubernetes-operator/src/integration-tests/bash/cleanup.sh to clean up kubernetes objects later.    | true
| COLLECT_LOGS_ON_SUCCESS  | Test infra does not keep the diagnostic log for successful tests. To archive the diagnostic log for successful tests, this environment variable should be set to true. | false
| LOGS_DIR  | Root directory for the diagnostic logs. | /tmp/it-diagnosticlogs
| RESULTS_ROOT  | Root directory for the intermediate artifacts such istio installation, dynamically generated YAML files | /tmp/it-testresults
| BASE_IMAGES_REPO  | The repository URL to download WebLogic and FMW image. Make sure you have access to this repository. Other supported repository is phx.ocir.io (OCIR)  | container-registry.oracle.com(OCR)
| WEBLOGIC_IMAGE_NAME  | Name of the WebLogic image in the chosen repository.| middleware/weblogic (OCR) 
| WEBLOGIC_IMAGE_TAG  | The tag for WebLogic base image. Generally, it represents the WebLogic version with JDK and/or installation type. Possible values are 12.2.1.3, 12.2.1.3-dev, 12.2.1.4-slim, 14.1.1.0-11 or 14.1.1.0-8. Please check the repository for the availability of these images. | 12.2.1.4
| FMWINFRA_IMAGE_TAG  | The tag for Fusion Middleware Infrastructure base image. Generally, it represents the FMW version. Possible values are 12.2.1.3, 12.2.1.4. Please check the repository for the availability of these images. | 12.2.1.4
| FMWINFRA_IMAGE_NAME  | Name of the Fusion Middleware Infrastructure image in the chosen repository.| middleware/fmw-infrastructure (OCR) 

## Logging/Archiving

On completion of integration test execution, the results are archived in a directory based on environment variable  LOG_DIR  ( the default value is /tmp/it-diagnosticlogs) and runtime artifacts are available in a directory based on environment variable RESULTS_ROOT ( the default value is /tmp/it-testresults) . 

A typical diagnosticlogs directory content will look as follows after the completion of test ItMiiDomain. The stdout for the tests is captured in ItMiiDomain.out. For each test method ( say testCreateMiiDomain, testCreateMiiSecondDomain) a directory is created and the corresponding k8s object description log(s) and server pod logs are saved if the test fails or environment variable COLLECT_LOGS_ON_SUCCESS set to true.

```
itMiiDomain

|-- ItMiiDomain.out

|-- testCreateMiiDomain

|   |-- ns-gggs.list.services.log

|   |-- ns-gggs.pod.weblogic-operator-7b8-lwqzm.container.weblogic-operator.log

|   |-- ns-luaf.pod.domain1-admin-server.container.weblogic-server.log

|   |-- ns-luaf.pod.domain1-managed-server1.container.weblogic-server.log

|   |-- ns-luaf.pod.domain1-managed-server2.container.weblogic-server.log

-- testCreateMiiSecondDomain

    |-- ns-gggs.list.configmaps.log

    |-- ns-gggs.list.domains.log

    |-- ns-gggs.list.services.log

    |-- ns-gggs.pod.weblogic-operator-7b8f-lwqzm.container.weblogic-operator.log

    |-- ns-luaf.list.configmaps.log

    |-- ns-luaf.pod.domain1-admin-server.container.weblogic-server.log

    |-- ns-luaf.pod.domain1-managed-server1.container.weblogic-server.log
```
## Troubleshooting
The diagnostic logs files in method scoped directory in ${LOGS_DIR}/`<testClass>`/`<testMehod>` and the test stdout ${LOGS_DIR}/`<testClass>`.out is the starting point to triage a test failure.
