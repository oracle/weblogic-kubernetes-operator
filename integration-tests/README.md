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
- Following environment variables must be set before running the tests. BASE_IMAGES_REPO variables are used to download the Oracle public images. TEST_IMAGES_REPO variables are used to store any public images used to certify the WebLogic Kubernates Operator (such as NGNIX Controller, Prometheous etc.) and to store the intermediate WebLogic domain images while running the integration tests.
   ```
    export BASE_IMAGES_REPO="container-registry.oracle.com"
    export BASE_IMAGES_REPO_USERNAME=<ocr_username>
    export BASE_IMAGES_REPO_PASSWORD=<ocr_password> 
    export BASE_IMAGES_REPO_EMAIL=<ocr_email>

    export TEST_IMAGES_REPO="local-registry.oracle.com"
    export TEST_IMAGES_REPO_USERNAME=<local_username>
    export TEST_IMAGES_REPO_PASSWORD=<local_password> 
    export TEST_IMAGES_REPO_EMAIL=<local_email>

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

## Offline WDT introspection tests

The opt-in `offline-introspection` profile runs the current checkout's full
`introspectDomain.sh` Model in Image path: primordial domain creation, WDT model
update and filtering, and WLST introspection. It checks `topology.yaml` using the
operator's Java topology parser. Each model gets a fresh container; the WLS image
and downloaded WDT ZIP are reused. No Kubernetes cluster, operator installation,
image build, or host Python is required.

Use the repository's required JDK and Maven versions. Install the operator artifact
first, then provide a full WLS image reference **with a tag** and a WDT ZIP URL:

```bash
mvn clean install
mvn -pl integration-tests -Poffline-introspection verify \
  -Dwko.it.introspection.image=my-registry/weblogic:my-wls-tag \
  -Dwko.it.introspection.wdt.url=https://my-server/releases/weblogic-deploy.zip \
  -Dwko.it.introspection.wdt.version=4.4.6
```

The values can also come from environment variables without editing files:

```bash
export WLS_IMAGE='my-registry/weblogic:my-wls-tag'
export WDT_URL='http://my-server/releases/weblogic-deploy.zip'
export WDT_VERSION='4.4.6'       # Optional
export WLSIMG_BUILDER='docker'  # Or podman

mvn -pl integration-tests -Poffline-introspection verify \
  -Dwko.it.introspection.image="$WLS_IMAGE" \
  -Dwko.it.introspection.wdt.url="$WDT_URL" \
  -Dwko.it.introspection.wdt.version="${WDT_VERSION:-}"
```

WDT URLs support `http://`, `https://` (including redirects), and `file://` for a
local ZIP, for example `file:///home/test/downloads/weblogic-deploy.zip`.
`wdt.version` is optional; when provided, it must match the ZIP's `VERSION.txt`.
The image tag is resolved to an image ID before executing the matrix. The installed
WLS version is checked: this profile supports **WLS 12.2.1.4 and later**, regardless
of the tag's name. Shared models run on all supported releases. Cases with WLS 26
regression expectations require 26.1 or later and are excluded on older images.
The image must provide `ORACLE_HOME`, Java on `PATH`, Bash, unzip,
and the utilities required by the production introspector (including jq and tar).
It must allow container exec as root to stage files; introspection runs as the
image's default user. Authenticate the container CLI beforehand for private images.

| Option | Purpose | Default |
| --- | --- | --- |
| `wko.it.introspection.image` | Full WLS image reference with tag | Required |
| `wko.it.introspection.wdt.url` | WDT distribution ZIP URL | Required |
| `wko.it.introspection.wdt.version` | Expected WDT version | Read from ZIP |
| `wko.it.introspection.cases` | Regular expression matching full case names | `.*` |
| `wko.it.introspection.results` | Parent directory for unique run directories | `integration-tests/target/offline-introspection` |
| `wko.it.introspection.timeout.seconds` | Timeout for each container CLI command | `600` |
| `WLSIMG_BUILDER` | Container CLI, as a Maven property or environment variable | `docker` |

For a small local selection, add
`'-Dwko.it.introspection.cases=t(06|18|26|31)_.*'`. To select Podman, add
`-DWLSIMG_BUILDER=podman`; the CLI must already have a working container engine.
These tests use container create/cp/start/exec/rm operations without host bind
mounts or exposed ports. They remove only their own uniquely named containers.
The profile does not enable the existing Kubernetes test extensions or select
the regular `It*` tests. CI adoption should verify the selected Podman environment
before adding this profile to a pipeline.

### Manual Jenkins pipeline

[`Jenkinsfile.offline-introspection`](../Jenkinsfile.offline-introspection) runs this
profile on the remote Jenkins agent with Podman. It requires `WLS_IMAGE` and
`WDT_URL` as build parameters; both start empty. The image is pulled from its
explicit registry on every build, and WDT is downloaded from the supplied HTTP
or HTTPS URL. Scripts and models come from the SCM checkout. Paths, images,
credentials, and environment settings from a developer's machine are not inputs.

A Jenkins administrator can create a separate **Pipeline from SCM** job:

1. Select this repository and the branch containing the Jenkinsfile and profile.
   The job checks out that configured branch; there is no separate branch parameter.
2. Set **Script Path** to `Jenkinsfile.offline-introspection`.
3. Disable periodic builds, SCM polling, webhook triggers, and upstream triggers.
   Use **Build with Parameters** to run it. The pipeline also checks for
   `UserIdCause` before allocating an agent.
4. Provide the agent label `large-ol9u4`, Jenkins tools `jdk25` and `maven-3.8.7`,
   and managed Maven settings `wkt-maven-settings-xml`, matching the existing
   Podman pipeline. The agent needs Git, Bash, and a working Podman engine, plus
   network access to Maven repositories, the image registry, and the WDT URL.
   Any required proxy configuration belongs to Jenkins and that agent.
5. For a private image, create a Jenkins username/password credential accepted by
   its registry and supply its ID in `REGISTRY_CREDENTIALS_ID`. Leaving it empty
   requests an anonymous pull. The job uses a fresh authentication file for each
   build; an agent's existing registry login is not required. OCR and OCIR use
   their respective credentials.

The Jenkinsfile declares these parameters. If a new job does not yet display
**Build with Parameters**, its first manual build registers the parameters and
stops at validation with empty required values; then start a parameterized build.

| Build parameter | Required | Purpose |
| --- | --- | --- |
| `WLS_IMAGE` | Yes | Full registry/repository:tag, such as `container-registry.oracle.com/middleware/weblogic_cpu:14.1.2.0-generic-jdk21-ol9-260413` |
| `WDT_URL` | Yes | HTTP or HTTPS URL of the WDT ZIP; prefer a pinned release |
| `WDT_VERSION` | No | Expected version; otherwise read from the ZIP |
| `MODEL_CASES` | No | Case-name regex; default `.*` |
| `REGISTRY_CREDENTIALS_ID` | For private pulls | Jenkins username/password credential ID for the image registry |

Each build tests one image, publishes Failsafe JUnit results, and archives the
offline inputs, configuration, topology, version metadata, and logs even on
failure. Containers carry the build's `oracle.weblogic.offline.build` label when
`WKO_OFFLINE_BUILD_ID` is supplied by Jenkins; abort cleanup selects only that
build's containers. Normal test cleanup continues to remove each unique container.
The temporary registry authentication file is removed after the build.

The pipeline does not create a Kubernetes cluster or build container images.
Existing Jenkinsfiles and their nightly schedules remain unchanged. Local Docker
validation does not establish Podman compatibility: the first remote manual run
must verify the selected Jenkins agent's Podman environment.

### Model coverage

The 35 model cases are in
[`src/offline-test/resources/offline-introspection/cases.json`](src/offline-test/resources/offline-introspection/cases.json).
Thirteen shared cases cover development and production modes, omitted and custom
admin listen ports, SSL enablement and custom ports, administration port overrides,
custom channels, and omitted/default/custom ServerTemplate ports. They run on
12.2.1.4 and later. The other 22 cases retain WLS 26 regression expectations.
`minimumWlsVersion` controls applicability; `expectationsBefore` supplies explicit
expected values for older releases without rewriting the WDT model. Overrides
are ordered by their exclusive upper version bound; the first matching override
is used. There are separate boundaries at 14.1.2 and 26.1. The expected values
below are generated topology values for the baseline WDT 4.4.6 ZIP:

| WLS release | t18 omitted template plain / SSL | t19 explicit template plain / SSL | t34 forwarding listen / public port |
| --- | --- | --- | --- |
| 12.2.1.4, 14.1.1 | 7100 / 8100 | 7001 / 7002 | 18334 / 0 |
| 14.1.2, 15.1.1 | 7001 / 7002 | 7001 / 7002 | 18334 / 18334 |
| 26.1 | 7100 / 8100 | 7001 / 8100 | 18334 / 18334 |

For t18, WDT writes explicit 7100/8100 template ports into `config.xml` on
12.2.1.4 and 14.1.1; the existing introspector preserves those values. On 14.1.2
and 15.1.1, those entries are omitted and the existing template workaround
reports 7001/7002. From 26.1, that workaround is disabled. Thus a pre-26 version
alone does not imply 7001/7002. In t34, the forwarding channel is present on
all releases, but the offline public-port getter reports 0 before 14.1.2.
`requiredForwardChannels` asserts protocol, listen port, and public port
independently; a field mismatch identifies the field instead of reporting a
missing channel.

The four pre-26 image tags used for this baseline are
`12.2.1.4-generic-jdk8-ol9-260908`, `14.1.1.0-slim-jdk11-ol8-260824`,
`14.1.2.0-generic-jdk21-ol9-260413`, and `15.1.1.0-generic-jdk17-ol8-260828`,
all under `container-registry.oracle.com/middleware/weblogic_cpu`.
The WLS 26.1 baseline uses the local `wls26100:100526` image.
Changing the WDT ZIP or WLS image can expose serialization or getter differences;
investigate its retained model, configuration and topology before changing an
expectation. Expected values are explicit fixtures, never learned from test output.

The original 31 models were checked with WLS 26.1.0.0.0 and WDT 4.4.6 in actual
operator domains, including managed server readiness. Runtime listener and
readiness checks are separate validation; an offline profile pass establishes
the generated topology's compatibility with the expected introspector behavior.

| Cases | Coverage |
| --- | --- |
| t01–t05 | Development, production, secure mode, production flag, explicit secure mode false |
| t06–t15, t22–t23, t27, t30 | Domain and server listen/SSL overrides; enabled, disabled, omitted and string flags; SSL port defaults |
| t16–t17, t28–t29 | Custom channels with omitted protocol and administration disabled |
| t18–t20 | ServerTemplate ports omitted, explicit defaults, and custom ports |
| t21, t24–t25 | Removed server administration flag and domain administration port overrides |
| t26 | WDT rejects a model with no enabled ports; no valid topology may be published |
| t31 | Production with domain SSL and four dynamic managed servers |
| t32–t35 | Shared development/production admin defaults, SSL-only custom port, and production with explicit admin/template SSL ports |

In t19, the expected template SSL port is 7002 before WLS 26.1 and 8100 from
26.1, even when the model explicitly assigns 7002. The newer expectation was
calibrated with WLS 26.1 and WDT 4.4.6. Custom template ports in t20 have the
same expectations across releases. Changes to runtime behavior need separate
verification with running servers for the chosen image and WDT combination.

Each run retains the models, resolved expectations, introspector logs, domain `config.xml`, topology
files, image ID, installed WLS/WDT versions, ZIP checksum, and checksums of the input
scripts. `versions.json` lists selected and version-excluded cases, and the console
reports their counts. A filter matching only inapplicable cases fails instead of
passing with zero model tests. Maven Failsafe reports individual model cases.
Additional tests cover HTTP redirects and download errors, local file URLs and
version mismatches, invalid ZIPs, and release/expectation selection boundaries.
Offline assertions cover topology and effective managed server ports; actual
listeners and Pod readiness probe definitions still require operator end-to-end
tests. The normal build does not compile or run this opt-in source directory.

## Environment variables to manage test execution 
| Variable | Description | Default Value
| --- | --- | --- |
| `SKIP_CLEANUP`  | Test infrastructure removes all Kubernetes objects created during test execution. To retain all such objects for triaging test failures, this environment variable should be set to true. Users need to run `weblogic-kubernetes-operator/operator/integration-tests/bash/cleanup.sh` to clean up Kubernetes objects after traiging.    | false
| `COLLECT_LOGS_ON_SUCCESS`  | Test infrastructure does not keep the diagnostic log for successful tests. To archive the diagnostic log for successful tests, this environment variable should be set to true. | false
| `RESULT_ROOT` | Root directory for the integration test results and artifacts generated during test execution. | `/tmp/it-testsresults`
| `BASE_IMAGES_REPO`  | The repository URL to download WebLogic and FMW images. Make sure you have access to this repository. | No Default
| `WEBLOGIC_IMAGE_NAME`  | Name of the WebLogic image in the chosen repository.| `middleware/weblogic` (If OCR repository is set as BASE_IMAGES_REPO)
| `WEBLOGIC_IMAGE_TAG`  | The tag for WebLogic base image. Generally, it represents the WebLogic Server version with JDK and/or installation type. Possible values are 12.2.1.3, 12.2.1.3-dev, 12.2.1.4-slim, 14.1.1.0-11 or 14.1.1.0-8. Please check the repository for the availability of these images. | 12.2.1.4
| `FMWINFRA_IMAGE_TAG`  | The tag for Fusion Middleware Infrastructure base image. Generally, it represents the FMW version. Possible values are 12.2.1.3, 12.2.1.4. Please check the repository for the availability of these images. | 12.2.1.4
| `FMWINFRA_IMAGE_NAME` | Name of the Fusion Middleware Infrastructure image in the chosen repository.| `middleware/fmw-infrastructure` (If OCR repository is set as BASE_IMAGES_REPO)

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
