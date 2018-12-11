> **WARNING** This documentation is for version 1.0 of the operator.  To view documenation for the current release, [please click here](/site).

# Developer guide

This guide provides information for developers who wish to understand or contribute to the code.

## Requirements

The following software is required to obtain and build the operator:

*	Git (1.8 or later recommended)
*	Apache Maven (3.3 or later recommended)
*	Java Developer Kit (1.8u131 or later recommended, not 1.9)
*	Docker 17.03.1.ce

The operator is written primarily in Java and BASH shell scripts.  The Java code uses features introduced in Java 1.8 -- for example, closures -- but does not use any Java 1.9 feature.

Because the target runtime environment for the operator is Oracle Linux, no particular effort has been made to ensure the build or tests run on any other operating system.  Please be aware that Oracle will not provide support, or accept pull requests to add support, for other operating systems.

## Obtaining the operator source code

The operator source code is published on GitHub at https://github.com/oracle/weblogic-kubernetes-operator.  Developers may clone this repository to a local machine or, if desired, create a fork in their personal namespace and clone the fork.  Developers who are planning to submit a pull request are advised to create a fork.

To clone the repository from GitHub, issue this command:

```
git clone https://github.com/oracle/weblogic-kubernetes-operator.git
```

## Operator branching model

The ```master``` branch is protected and will always contain source for the latest, generally available (GA) 
release of the operator, including any critical hot fixes.  No general pull requests will be merged to this branch.

Active work will be performed on the ```develop``` branch.  This branch is also protected.  Please submit pull
requests to this branch unless you are collaborating on a feature and have another target branch.  
Please see details on the Oracle Contributor Agreement (OCA) and guidelines for pull requests on the [README] (README.md).

Longer running feature work will be performed on specific branches, such as ```feature/dynamic-clusters```.  Since we want 
to balance separating destabilizing work into feature branches against the possibility of later difficult merges, we
encourage developers working on features to pull out any necessary refactoring or improvements that are general purpose into 
their own shorter-lived branches and create pull requests to ```develop``` when these smaller work items are complete.

When it is time for a release, we will branch off ```develop``` to create a per-release branch.  Here, we will update version
numbers, rebuild javadoc, if necessary, and perform any other pre-release updates.  Finally, this release branch will be merged 
to ```master```.

## Building the operator

The operator is built using [Apache Maven](http://maven.apache.org).  The build machine will also need to have Docker installed.

To build the operator, issue the following command in the project directory:

```
mvn clean install
```

This will compile the source files, build JAR files containing the compiled classes and libraries needed to run the operator, and will also execute all of the unit tests.

## Building Javadoc

To build the Javadoc for the operator, issue the following command:

```
mvn javadoc:javadoc
```

The Javadoc is also available in the GitHub repository [here](https://oracle.github.io/weblogic-kubernetes-operator/v1.0/apidocs/index.html).

## Running integration tests

The project includes integration tests that can be run against a Kubernetes cluster.  If you want to use these tests, you will need to provide your own Kubernetes cluster.  You will need to obtain the `kube.config` file for an administrator user and make it available on the machine running the build.  Tests will run against Kubernetes 1.7.5+, 1.8.0+, 1.9.0+, and 1.10.0.

To run the tests, uncomment the following `execution` element in the `pom.xml` file and update the `KUBECONFIG` to point to your kube config file.

```
<!--
<execution>
  <id>kubernetes-config</id>
  <phase>test</phase>
  <goals>
      <goal>test</goal>
  </goals>
  <configuration>
      <argLine>${surefireArgLine} -Xms512m -Xmx1500m</argLine>
      <environmentVariables>
          <KUBECONFIG>
              ${project.basedir}/your.kube.config
          </KUBECONFIG>
      </environmentVariables>
  </configuration>
</execution>
-->
```

These tests assume that the RBAC definitions exist on the Kubernetes cluster.

To create them, first, make a copy of the inputs file (`create-weblogic-operator-inputs.yaml`) and update it.

Next, choose and create a directory that generated operator-related files will be stored in, for example, `/path/to/weblogic-operator-output-directory`.

Finally, run the operator installation script with the "generate only" option as shown below, pointing it at your inputs file and your output directory.  (See the [installation](installation.md) page for details about this script and the inputs):

```
./create-weblogic-operator.sh -g \
  -i create-weblogic-operator-inputs.yaml \
  -o /path/to/weblogic-operator-output-directory
```

This will create a file called `/path/to/weblogic-operator-output-directory/weblogic-operators/weblogic-operator/weblogic-operator-security.yaml`, which you will need to apply to your cluster:

```
kubectl apply -f /path/to/weblogic-operator-output-directory/weblogic-operators/webogic-operator/weblogic-operator-security.yaml
```

After this is done, and the `execution` is uncommented, the tests will run against your cluster.

## Running the operator from an IDE

The operator can be run from an IDE, which is useful for debugging.  In order to do so, the machine running the IDE must be configured with a Kubernetes configuration file in `~/.kube/config` or in a location pointed to by the `KUBECONFIG` environment variable.

Configure the IDE to run the class `oracle.kubernetes.operator.Main`.

You may need to create a directory called `/operator` on your machine.  Please be aware that the operator code is targeted to Linux, and although it will run fine on macOS, it will probably not run on other operating systems.  If you develop on another operating system, you should deploy the operator to a Kubernetes cluster and use remote debugging instead.

## Running the operator in a Kubernetes cluster

To run the operator in a Kubernetes cluster, you need to build the Docker image and then deploy it to your cluster.

After you have run the build (that is, `mvn clean install`), create the Docker image as follows:

```
docker build -t weblogic-kubernetes-operator:some-tag --no-cache=true .
```

We recommend that you use a tag other than `latest` to make it easy to distinguish your image from the "real" one.  In the example above, we used the GitHub ID of the developer.

Next, upload your image to your Kubernetes server as follows:

```
# on your build machine
docker save weblogic-kubernetes-operator:some-tag > operator.tar
scp operator.tar YOUR_USER@YOUR_SERVER:/some/path/operator.tar
# on the Kubernetes server
docker load < /some/path/operator.tar
```

Verify that you have the right image by running `docker images | grep webloogic-kubernetes-operator` on both machines and comparing the image IDs.

To create and deploy the operator, first, make a copy of the inputs file (`create-weblogic-operator-inputs.yaml`) and update it, making sure that `weblogicOperatorImagePullPolicy` is set to `Never` and `weblogicOperatorImage` matches the name you used in your `docker build` command.

Next, choose and create a directory that generated operator-related files will be stored in, for example, `/path/to/weblogic-operator-output-directory`.

Finally, run the operator installation script to deploy the operator, pointing it at your inputs file and your output directory:

```
./create-weblogic-operator.sh \
  -i /path/to/create-weblogic-operator-inputs.yaml \
  -o /path/to/weblogic-operator-output-directory
```


## Coding standards

This project has adopted the following coding standards:

* All indents are two spaces.
* Javadoc must be provided for all public packages, classes, and methods, and must include all parameters and returns.  Javadoc is not required for methods that override or implement methods that are already documented.
* All non-trivial methods should include `LOGGER.entering()` and `LOGGER.exiting()` calls.
* The `LOGGER.exiting()` call should include the value that is going to be returned from the method, unless that value includes a credential or other sensitive information.
* All logged messages must be internationalized using the resource bundle `src/main/resources/Operator.properties` and using a key itemized in `src/main/java/oracle/kubernetes/operator/logging/MessageKeys.java`.
* Before throwing an exception, there should be a call to `LOGGER.throwing(e)` to log the exception.
* After operator initialization, all operator work must be implemented using the asynchronous call model (described below).  In particular, worker threads must not use `sleep()` or IO or lock-based blocking methods.

## Source code structure

This project has the following directory structure:

* `docs`: Generated javadoc and Swagger
* `kubernetes`: BASH scripts and YAML templates for operator installation and WebLogic domain creation job.
* `site`: This documentation
* `src/main/java`: Java source code for the operator
* `src/test/java`: Java unit-tests for the operator
* `src-generated-swagger`: Snapshot of Java source files generated from the domain custom resource's Swagger
* `swagger`: Swagger files for the Kubernetes API server and domain custom resource

### Watch package

The Watch API in the Kubernetes Java client provides a watch capability across a specific list of resources for a limited amount of time. As such, it is not ideally suited for our use case, where a continuous stream of watches is desired, with watch events generated in real time. The watch-wrapper in this repository extends the default Watch API to provide a continuous stream of watch events until the stream is specifically closed. It also provides `resourceVersion` tracking to exclude events that have already been seen.  The watch-wrapper provides callbacks so events, as they occur, can trigger actions.

## Asynchronous call model

Our expectation is that certain customers will task the operator with managing thousands of WebLogic domains across dozens of Kubernetes namespaces.  Therefore, we have designed the operator with an efficient user-level threads pattern based on a simplified version of the code from the JAX-WS reference implementation.  We have then used that pattern to implement an asynchronous call model for Kubernetes API requests.  This call model has built-in support for timeouts, retries with exponential back-off, and lists that exceed the requested maximum size using the continuance functionality.

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

```
    Engine engine = new Engine("worker-pool");

    Fiber fiber = engine.createFiber();

    Step step = new StepOne(new StepTwo(new StepThree(null)));
    Packet packet = new Packet();

    fiber.start(step, packet, new CompletionCallback() {
      @Override
      public void onCompletion(Packet packet) {
        // Fiber has completed successfully
      }

      @Override
      public void onThrowable(Packet packet, Throwable throwable) {
        // Fiber processing was terminated with an exception
      }
    });
```

`Steps` must not invoke sleep or blocking calls from within `apply()`.  This prevents the worker threads from serving other `Fibers`.  Instead, use asynchronous calls and the `Fiber` suspend/resume pattern.  `Step` provides a method, `doDelay()`, which creates a `NextAction` to drive `Fiber` suspend/resume that is a better option than sleep precisely because the worker thread can serve other `Fibers` during the delay.  For asynchronous IO or similar patterns, suspend the `Fiber`.  In the callback as the `Fiber` suspends, initiate the asynchronous call.  Finally, when the call completes, resume the `Fiber`.  The suspend/resume functionality handles the case where resumed before the suspending callback completes.

In this sample, the step uses asynchronous file IO and the suspend/resume `Fiber` pattern.

```
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
              public void completed(Integer result, ByteBuffer attachment) {
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

```
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
          public NextAction onSuccess(Packet packet, V1PodList result, int statusCode,
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
