# Integration tests for Oracle WebLogic Server Kubernetes Operator
This documentation describes the steps to run integration tests for the Oracle WebLogic Server Kubernetes Operator locally on a Oracle Linux environment. The tests are written in the JUnit5 framework and driven by Maven profile.

# Directory Configuration and Structure
Directory structure of the integration test source is as follows: 
```
weblogic-kubernetes-operator/integration-tests - location of module pom.xml  
weblogic-kubernetes-operator/integration-tests/src/test/java/oracle/weblogic/kubernetes - integration test(JUnit5) classes and utility classes  
weblogic-kubernetes-operator/integration-tests/src/test/resources - properties, YAML files and other bash scripts
```

# How do WebLogic Server Kubernetes Operator integration tests work?
 - Build WebLogic Server Kubernetes Operator image from the downloaded Git branch.
 - Install the latest version of WebLogic Server Deploy Tooling (WDT) and WebLogic Image Tool (WIT).
 - Install supported Istio service mesh.
 - Pull the specified version of WebLogic image from `container-registry.oracle.com` (OCR).
 - Install WebLogic Server Kubernetes Operator.
 - Build a WebLogic domain resource.
 - Make sure that the WebLogic domain is running by verifying the corresponding server pod and Kubernetes service status. 
 - After test execution, delete all the namespaces, Kubernetes Objects created during test execution.
 - Archive the test standard output for each test class and Kubernetes object details for each test method in the diagnostic directory for triage when tests fail or when the env variable `COLLECT_LOGS_ON_SUCCESS` is set to true.
 
# How to run operator integration tests locally on Oracle Linux
- Install supported version of Helm and Kubernetes cluster. For details, see the [WebLogic Server Kubernetes Operator guide](https://oracle.github.io/weblogic-kubernetes-operator/userguide/introduction/introduction/).
- Install supported version of JDK. Need JDK version 11+ or later.
- Clone the `weblogic-kubernetes-operator` repository.
- Set up a personal account on `container-registry.oracle.com`.
- Log in to `https://container-registry.oracle.com` and accept Oracle Standard Terms and Restrictions to pull WebLogic images from `container-registry.oracle.com/middleware/weblogic`.
- Export the following environment variables before running the tests:
   ```
    export BASE_IMAGES_REPO="container-registry.oracle.com"
    export OCR_EMAIL=<ocr_email>
    export OCR_USERNAME=<ocr_username>
    export OCR_PASSWORD=<ocr_password> 
   ```
- `cd  weblogic-kubernetes-operator` and run the following commands:
  ```
    # Build the WebLogic Server Kubernetes Operator binary 
    mvn clean install 
    # Run tests in sequential order
    mvn -pl -integration-tests -P integration-tests clean verify
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
| `SKIP_CLEANUP`  | Test infrastructure removes all Kubernetes objects created during test execution. To retain all such objects for triaging test failures, this environment variable should be set to true. Users need to run `weblogic-kubernetes-operator/operator/integration-tests/bash/cleanup.sh` to clean up Kubernetes objects after traiging.    | false
| `COLLECT_LOGS_ON_SUCCESS`  | Test infrastructure does not keep the diagnostic log for successful tests. To archive the diagnostic log for successful tests, this environment variable should be set to true. | false
| `RESULT_ROOT` | Root directory for the integration test results and artifacts generated during test execution. | `/tmp/it-testsresults`
| `BASE_IMAGES_REPO`  | The repository URL to download WebLogic and FMW image. Make sure you have access to this repository. The other supported repository is `phx.ocir.io` (OCIR).  | `container-registry.oracle.com` (OCR)
| `WEBLOGIC_IMAGE_NAME`  | Name of the WebLogic image in the chosen repository.| `middleware/weblogic` (OCR) 
| `WEBLOGIC_IMAGE_TAG`  | The tag for WebLogic base image. Generally, it represents the WebLogic Server version with JDK and/or installation type. Possible values are 12.2.1.3, 12.2.1.3-dev, 12.2.1.4-slim, 14.1.1.0-11 or 14.1.1.0-8. Please check the repository for the availability of these images. | 12.2.1.4
| `FMWINFRA_IMAGE_TAG`  | The tag for Fusion Middleware Infrastructure base image. Generally, it represents the FMW version. Possible values are 12.2.1.3, 12.2.1.4. Please check the repository for the availability of these images. | 12.2.1.4
| `FMWINFRA_IMAGE_NAME` | Name of the Fusion Middleware Infrastructure image in the chosen repository.| `middleware/fmw-infrastructure` (OCR) 

## Logging/Archiving

After the completion of running the integration tests, the results are archived in a directory-based environment variable `LOG_DIR` (the default value is `/tmp/it-testsresults/diagnostic`) and runtime artifacts are available in a directory-based environment variable `RESULTS_ROOT` (the default value is `/tmp/it-testresults`). 
After the completion of the test `ItMiiDomain`, a typical diagnostic logs directory content will look as follows. The standard output for the tests is captured in `ItMiiDomain.out`. For each test method ( for example `testCreateMiiDomain`, `testCreateMiiSecondDomain`), a directory is created and the corresponding Kubernetes object description log(s) and server pod logs are saved if the test fails or environment variable `COLLECT_LOGS_ON_SUCCESS` set to true.

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
The diagnostic logs files in the method-scoped directory in ${LOGS_DIR}/`<testClass>`/`<testMehod>` and the test stdout ${LOGS_DIR}/`<testClass>`.out is the starting point to triage a test failure.
