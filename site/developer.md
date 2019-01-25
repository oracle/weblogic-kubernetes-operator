# Developer guide

This guide provides information for developers who wish to understand or contribute to the code.

## Requirements

In addition to the requirements listed in the [User Guide](user-guide.md#prerequisites), the following software is also required to obtain and build the operator:

* Git (1.8 or later recommended)
* Java Developer Kit (1.8u131 or later recommended; please use 1.8, tests will not work on 1.9 or later versions)
* Apache Maven (3.3 or later recommended)

The operator is written primarily in Java, BASH shell scripts, and WLST scripts.  The Java code uses features introduced in Java 1.8 -- for example, closures -- but does not use any Java 1.9 features.

Because the target runtime environment for the operator is Oracle Linux, no particular effort has been made to ensure the build or tests run on any other operating system.  Please be aware that Oracle will not provide support, or accept pull requests to add support, for other operating systems.

## Obtaining the operator source code

The operator source code is published on GitHub at https://github.com/oracle/weblogic-kubernetes-operator.  Developers may clone this repository to a local machine or, if desired, create a fork in their personal namespace and clone the fork.  Developers who are planning to submit a pull request are advised to create a fork.

To clone the repository from GitHub, issue this command:

```
$ git clone https://github.com/oracle/weblogic-kubernetes-operator.git
```

## Operator branching model

The `master` branch is protected and contains source for the most recently published release, including release candidates.

The `develop` branch is protected and contains source for the latest completed features and bug fixes.  While this branch contains active work, we expect to keep it always "ready to release."  Therefore, longer running feature work will be performed on specific branches, such as `feature/dynamic-clusters`.

Because we want to balance separating destabilizing work into feature branches against the possibility of later difficult merges, we encourage developers working on features to pull out any necessary refactoring or improvements that are general purpose into their own shorter-lived branches and create pull requests to `develop` when these smaller work items are completed.

All commits to `develop` must pass the [integration test suite](#running-integration-tests).  Please run these tests locally before submitting a pull request.  Additionally, each push to a branch in our GitHub repository triggers a run of a subset of the integration tests with the results visible [here](https://app.wercker.com/Oracle/weblogic-kubernetes-operator/runs).

Please submit pull requests to the `develop` branch unless you are collaborating on a feature and have another target branch.  Please see details on the Oracle Contributor Agreement (OCA) and guidelines for pull requests on the [README](../README.md).

We will create git tags for each release candidate and generally available (GA) release of the operator.

## Building the operator

The operator is built using [Apache Maven](http://maven.apache.org).  The build machine will also need to have Docker installed.

To build the operator, issue the following command in the project directory:

```
$ mvn clean install
```

This will compile the source files, build JAR files containing the compiled classes and libraries needed to run the operator, and will also execute all of the unit tests.

Contributions must conform to [coding and formatting standards](#coding-standards).  To automatically update local code to conform to formatting standards, issue the following command:

```
$ mvn fmt:format
```

## Building Javadoc

To build the Javadoc for the operator, issue the following command:

```
$ mvn javadoc:javadoc
```

The Javadoc is also available in the GitHub repository [here](https://oracle.github.io/weblogic-kubernetes-operator/apidocs/index.html).

## Building the operator Docker image

Log in to the Docker Store so that you will be able to pull the base image and create the Docker image as follows.  These commands should be executed in the project root directory:

```
$ docker login
$ docker build --build-arg VERSION=<version> -t weblogic-kubernetes-operator:some-tag --no-cache=true .
```

Replace `<version>` with the version of the project found in the `pom.xml` file in the project root directory.

**Note**: If you have not used the base image (`store/oracle/serverjre:8`) before, you will need to visit the [Docker Store web interface](https://store.docker.com/images/oracle-serverjre-8) and accept the license agreement before the Docker Store will give you permission to pull that image.

We recommend that you use a tag other than `latest`, to make it easy to distinguish your image.  In the example above, the tag could be the GitHub ID of the developer.

## Running the operator from an IDE

The operator can be run from an IDE, which is useful for debugging.  In order to do so, the machine running the IDE must be configured with a Kubernetes configuration file in `~/.kube/config` or in a location pointed to by the `KUBECONFIG` environment variable.

Configure the IDE to run the class `oracle.kubernetes.operator.Main`.

You may need to create a directory called `/operator` on your machine.  Please be aware that the operator code is targeted to Linux, and although it will run fine on macOS, it will probably not run on other operating systems.  If you develop on another operating system, you should deploy the operator to a Kubernetes cluster and use remote debugging instead.

## Running the operator in a Kubernetes cluster

If you're not running Kubernetes on your development machine, you'll need to make the Docker image available to a registry visible to your Kubernetes cluster.  Either `docker push` the image to a private registry or upload your image to a machine running Docker and Kubernetes as follows:

```
# on your build machine
$ docker save weblogic-kubernetes-operator:some-tag > operator.tar
$ scp operator.tar YOUR_USER@YOUR_SERVER:/some/path/operator.tar
# on the Kubernetes server
$ docker load < /some/path/operator.tar
```

Use the Helm charts to [install the operator](install.md).

If the operator's behavior or pod log is insufficient to diagnose and resolve failures, then you can connect a Java debugger to the operator using the [debugging options](install.md#debugging-options).

## Running integration tests

The project includes integration tests that can be run against a Kubernetes cluster.  If you want to use these tests, you will need to provide your own Kubernetes cluster.  The Kubernetes cluster must meet the version number requirements and have Helm installed.  Ensure that the operator Docker image is in a Docker registry visible to the Kubernetes cluster.


You will need to obtain the `kube.config` file for an administrative user and make it available on the machine running the build.  To run the tests, update the `KUBECONFIG` environment variable to point to your config file and then execute:

```
$ mvn clean verify -P java-integration-tests
```
**NOTE**: When you run the integrations tests, they do a cleanup of any operator or domains on that cluster.   

## Coding standards

This project has adopted the following coding standards:

* Code will be formated using Oracle / WebLogic standards, which are identical to the [Google Java Style](https://google.github.io/styleguide/javaguide.html).
* Javadoc must be provided for all public packages, classes, and methods, and must include all parameters and returns.  Javadoc is not required for methods that override or implement methods that are already documented.
* All non-trivial methods should include `LOGGER.entering()` and `LOGGER.exiting()` calls.
* The `LOGGER.exiting()` call should include the value that is going to be returned from the method, unless that value includes a credential or other sensitive information.
* All logged messages must be internationalized using the resource bundle `src/main/resources/Operator.properties` and using a key itemized in `src/main/java/oracle/kubernetes/operator/logging/MessageKeys.java`.
* After operator initialization, all operator work must be implemented using the asynchronous call model (described below).  In particular, worker threads must not use `sleep()` or IO or lock-based blocking methods.

## Code formatting plugins

The following IDE plugins are available to assist with following the code formatting standards

### IntelliJ

An [IntelliJ plugin](https://plugins.jetbrains.com/plugin/8527) is available from the plugin repository.

The plugin will be enabled by default. To disable it in the current project, go to `File > Settings... > google-java-format Settings` (or `IntelliJ IDEA > Preferences... > Other Settings > google-java-format Settings` on macOS) and uncheck the "Enable google-java-format" checkbox.

To disable it by default in new projects, use `File > Other Settings > Default Settings...`.

When enabled, it will replace the normal "Reformat Code" action, which can be triggered from the "Code" menu or with the Ctrl-Alt-L (by default) keyboard shortcut.

The import ordering is not handled by this plugin, unfortunately. To fix the import order, download the [IntelliJ Java Google Style file](https://github.com/google/styleguide/blob/gh-pages/intellij-java-google-style.xml) and import it into File→Settings→Editor→Code Style.

### Eclipse

An [Eclipse plugin](https://github.com/google/google-java-format/releases/download/google-java-format-1.3/google-java-format-eclipse-plugin-1.3.0.jar) can be downloaded from the releases page. Drop it into the Eclipse [drop-ins folder](http://help.eclipse.org/neon/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Freference%2Fmisc%2Fp2_dropins_format.html) to activate the plugin.

The plugin adds a google-java-format formatter implementation that can be configured in `Eclipse > Preferences > Java > Code Style > Formatter > Formatter Implementation`.

## Source code structure

This project has the following directory structure:

* `docs`: Generated Javadoc and Swagger
* `integration-tests`: Integration test suite
* `json-schema`: Java model to JSON schema generator
* `json-schema-maven-plugin`: Maven plugin for schema generator
* `kubernetes/charts`: Helm charts
* `kubernetes/samples`: All samples, including for WebLogic domain creation
* `model`: Domain resource Java model
* `operator`: Operator runtime
* `site`: This documentation
* `src/scripts`: Scripts operator injects into WebLogic server instance Pods
* `swagger`: Swagger files for the Kubernetes API server and domain resource

### Watch package

The Watch API in the Kubernetes Java client provides a watch capability across a specific list of resources for a limited amount of time. As such, it is not ideally suited for our use case, where a continuous stream of watches is desired, with watch events generated in real time. The watch-wrapper in this repository extends the default Watch API to provide a continuous stream of watch events until the stream is specifically closed. It also provides `resourceVersion` tracking to exclude events that have already been seen.  The watch-wrapper provides callbacks so events, as they occur, can trigger actions.

## Asynchronous call model

Our expectation is that customers will task the operator with managing hundreds of WebLogic domains across dozens of Kubernetes namespaces.  Therefore, we have designed the operator with an efficient user-level threads pattern.  We've used that pattern to implement an asynchronous call model for Kubernetes API requests.  This call model has built-in support for timeouts, retries with exponential back-off, and lists that exceed the requested maximum size using the continuance functionality.

### User-Level Thread Pattern

The user-level thread pattern is implemented by the classes in the `oracle.kubernetes.operator.work` package.

* `Engine`: The executor service and factory for `Fibers`.
* `Fiber`: The user-level thread.  `Fibers` represent the execution of a single processing flow through a series of `Steps`.  `Fibers` may be suspended and later resumed, and do not consume a `Thread` while suspended.
* `Step`: Individual CPU-bound activity in a processing flow.
* `Packet`: Context of the processing flow.
* `NextAction`: Used by a `Step` when it returns control to the `Fiber` to indicate what should happen next.  Common 'next actions' are to execute another `Step` or to suspend the `Fiber`.
* `Component`: Provider of SPI's that may be useful to the processing flow.
* `Container`: Represents the containing environment and is a `Component`.

Each `Step` has a reference to the next `Step` in the processing flow; however, `Steps` are not required to indicate that the next `Step` be invoked by the `Fiber` when the `Step` returns a `NextAction` to the `Fiber`.  This leads to common use cases where `Fibers` invoke a series of `Steps` that are linked by the 'is-next' relationship, but just as commonly, use cases where the `Fiber` will invoke sets of `Steps` along a detour before returning to the normal flow.

In this sample, the caller creates an `Engine`, `Fiber`, linked set of `Step` instances, and `Packet`.  The `Fiber` is then started.  The `Engine` would typically be a singleton, since it's backed by a `ScheduledExecutorService`.  The `Packet` would also typically be pre-loaded with values that the `Steps` would use in their `apply()` methods.

```java
static class SomeClass {
  public static void main(String[] args) {
    Engine engine = new Engine("worker-pool");
  
    Fiber fiber = engine.createFiber();
  
    Step step = new StepOne(new StepTwo(new StepThree(null)));
      Packet packet = new Packet();
  
    fiber.start(
        step,
        packet,
        new CompletionCallback() {
          @Override
          public void onCompletion(Packet packet) {
            // Fiber has completed successfully
          }

          @Override
          public void onThrowable(Packet packet, Throwable throwable) {
            // Fiber processing was terminated with an exception
          }
        });
  }
}
```

`Steps` must not invoke sleep or blocking calls from within `apply()`.  This prevents the worker threads from serving other `Fibers`.  Instead, use asynchronous calls and the `Fiber` suspend/resume pattern.  `Step` provides a method, `doDelay()`, which creates a `NextAction` to drive `Fiber` suspend/resume that is a better option than sleep precisely because the worker thread can serve other `Fibers` during the delay.  For asynchronous IO or similar patterns, suspend the `Fiber`.  In the callback as the `Fiber` suspends, initiate the asynchronous call.  Finally, when the call completes, resume the `Fiber`.  The suspend/resume functionality handles the case where resumed before the suspending callback completes.

In this sample, the step uses asynchronous file IO and the suspend/resume `Fiber` pattern.

```java
    static class StepTwo extends Step {
      public StepTwo(Step next) {
        super(next);
      }

      @Override
      public NextAction apply(Packet packet) {
        return doSuspend((fiber) -> {
          // The Fiber is now suspended
          // Start the asynchronous call
          try {
            Path path = Paths.get(URI.create(this.getClass().getResource("/somefile.dat").toString()));
            AsynchronousFileChannel fileChannel =
                AsynchronousFileChannel.open(path, StandardOpenOption.READ);

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            fileChannel.read(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
              @Override
              void completed(Integer result, ByteBuffer attachment) {
                // Store data in Packet and resume Fiber
                packet.put("DATA_SIZE_READ", result);
                packet.put("DATA_FROM_SOMEFILE", attachment);
                fiber.resume(packet);
              }

              @Override
              public void failed(Throwable exc, ByteBuffer attachment) {
                // log exc
                completed(0, null);
              }
            });
          } catch (IOException e) {
            // log exception
            // If not resumed here, Fiber will never be resumed
          }
        });
      }
    }
```

### Call Builder Pattern

The asynchronous call model is implemented by classes in the `oracle.kubernetes.operator.helpers` package, including `CallBuilder` and `ResponseStep`.  The model is based on the `Fiber` suspend/resume pattern described above.  `CallBuilder` provides many methods having names ending with "Async", such as `listPodAsync()` or `deleteServiceAsync()`.  These methods return a `Step` that can be returned as part of a `NextAction`.  When creating these `Steps`, the developer must provide a `ResponseStep`.  Only `ResponseStep.onSuccess()` must be implemented; however, it is often useful to override `onFailure()` as Kubernetes treats `404 (Not Found)` as a failure.

In this sample, the developer is using the pattern to list pods from the default namespace that are labeled as part of `cluster-1`.

```java
    static class StepOne extends Step {
      public StepOne(Step next) {
        super(next);
      }

      @Override
      public NextAction apply(Packet packet) {
        String namespace = "default";
        Step step = CallBuilder.create().with($ -> {
          $.labelSelector = "weblogic.clusterName=cluster-1";
          $.limit = 50;
          $.timeoutSeconds = 30;
        }).listPodAsync(namespace, new ResponseStep<V1PodList>(next) {
          @Override
          public NextAction onFailure(Packet packet, ApiException e, int statusCode,
              Map<String, List<String>> responseHeaders) {
            if (statusCode == CallBuilder.NOT_FOUND) {
              return onSuccess(packet, null, statusCode, responseHeaders);
            }
            return super.onFailure(packet, e, statusCode, responseHeaders);
          }

          @Override
          NextAction onSuccess(Packet packet, V1PodList result, int statusCode,
              Map<String, List<String>> responseHeaders) {
            // do something with the result Pod, if not null
            return doNext(packet);
          }
        });

        return doNext(step, packet);
      }
    }
```

Notice that the required parameters, such as `namespace`, are method arguments, but optional parameters are designated using a simplified builder pattern using `with()` and a lambda.

The default behavior of `onFailure()` will retry with an exponential backoff the request on status codes `429 (TooManyRequests)`, `500 (InternalServerError)`, `503 (ServiceUnavailable)`, `504 (ServerTimeout)` or a simple timeout with no response from the server.

If the server responds with status code `409 (Conflict)`, then this indicates an optimistic locking failure.  Common use cases are that the code read a Kubernetes object in one asynchronous step, modified the object, and attempted to replace the object in another asynchronous step; however, another activity replaced that same object in the interim.  In this case, retrying the request would give the same result.  Therefore, developers may provide an "on conflict" step when calling `super.onFailure()`.  The conflict step will be invoked after an exponential backoff delay.  In this example, that conflict step should be the step that reads the existing Kubernetes object.

## Domain processing

When the operator starts, it lists all existing Domain resources and processes these domains to create the necessary Kubernetes resources, such as Pods and Services, if they don't already exist.  This initialization also includes looking for any stranded resources that, while created by the operator, no longer correlate with a Domain resource.

After this, the operator starts watches for changes to Domain resources and any changes to other resources created by the operator.  When a watch event is received, the operator processes the modified Domain resource to again bring the runtime presence in to alignment with the desired state.

The operator ensures that at most one `Fiber` is running for any given Domain.  For instance, if the customer modifies a Domain resource to trigger a rolling restart, then the operator will create a `Fiber` to process this activity.  However, if while the rolling restart is in process, the customer makes another change to the Domain resource, such as to increase the `replicas` field for a cluster, then the operator will cancel the in-flight `Fiber` and replace it with a new `Fiber`.  This replacement processing must be able to handle taking over for the cancelled work regardless of where the earlier processing may have been in its flow.  Therefore, domain processing always starts at the beginning of the "make right" flow without any state other than the current Domain resource.

Finally, the operator periodically lists all Domains and rechecks them.  This is a backstop against the possibility that a watch event is missed, such as because of a temporary network outage.  Recheck activities will not interrupt already running processes for a given Domain.

## Backward Compatibility Guidelines

Starting with the 2.0 release, future operator releases must be backward compatible with respect to the domain resource schema, operator Helm chart input values, configuration overrides template, Kubernetes resources created by the operator Helm chart, Kubernetes resources created by the operator, and the operator REST interface.  We will maintain compatibility for three releases, except in the case of a clearly communicated deprecated feature, which will be maintained for one release after a replacement is available.
