---
title: "Asynchronous call model"
date: 2019-02-23T17:20:00-05:00
draft: false
weight: 7
---


Our expectation is that customers will task the operator with managing hundreds of WebLogic domains across dozens of Kubernetes namespaces.  Therefore, we have designed the operator with an efficient user-level threads pattern.  We've used that pattern to implement an asynchronous call model for Kubernetes API requests.  This call model has built-in support for timeouts, retries with exponential back-off, and lists that exceed the requested maximum size using the continuance functionality.

#### User-level thread pattern

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

#### Call builder pattern

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
