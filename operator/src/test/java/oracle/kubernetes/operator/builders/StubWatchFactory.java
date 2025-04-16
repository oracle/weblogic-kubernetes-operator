// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.calls.WatchApi;
import oracle.kubernetes.operator.calls.WatchApiFactory;

/**
 * A test-time replacement for the factory that creates Watch objects, allowing tests to specify
 * directly the events they want returned from the Watch.
 */
public class StubWatchFactory implements WatchApiFactory {

  private static final int MAX_TEST_REQUESTS = 100;
  private static StubWatchFactory factory;
  private static List<Map<String, String>> requestParameters;
  private static RuntimeException exceptionOnNext;
  private static AllWatchesClosedListener listener;

  private final List<List<Watch.Response<?>>> calls = new ArrayList<>();
  private int numCloseCalls;

  private StubWatchFactory() {
  }

  /**
   * Setup static stub support.
   * @return memento from StaticStubSupport install
   * @throws NoSuchFieldException if StaticStubSupport fails to install.
   */
  public static Memento install() throws NoSuchFieldException {
    factory = new StubWatchFactory();
    requestParameters = new ArrayList<>();
    exceptionOnNext = null;

    return StaticStubSupport.install(RequestBuilder.class, "watchApiFactory", factory);
  }

  /**
   * Adds the events to be returned from a single call to Watch.next().
   *
   * @param events the events; will be converted to Watch.Response objects
   */
  public static void addCallResponses(Watch.Response<?>... events) {
    factory.calls.add(Arrays.asList(events));
  }

  public static int getNumCloseCalls() {
    return factory.numCloseCalls;
  }

  public static void setListener(AllWatchesClosedListener listener) {
    StubWatchFactory.listener = listener;
  }

  public static List<Map<String, String>> getRequestParameters() {
    return requestParameters;
  }

  /**
   * Programs the stub to throw the specified exception when {@link Iterator#next()} is invoked.
   * @param e the exception to throw
   */
  public static void throwExceptionOnNext(RuntimeException e) {
    exceptionOnNext = e;
  }

  /**
   * Create watch api.
   * @param <A> Kubernetes object type
   * @param <L> Kubernetes list object type
   * @param apiTypeClass api type
   * @param apiListTypeClass api list type
   * @param apiGroup group
   * @param apiVersion version
   * @param resourcePlural plural
   * @return the watch api
   */
  @Override
  public <A extends KubernetesObject, L extends KubernetesListObject>
      WatchApi<A> create(Class<A> apiTypeClass, Class<L> apiListTypeClass,
                     String apiGroup, String apiVersion, String resourcePlural) {
    return new WatchApi<A>() {
      @Override
      public Watchable<A> watch(ListOptions listOptions) throws ApiException {
        return watch(null, listOptions);
      }

      @Override
      @SuppressWarnings("unchecked")
      public Watchable<A> watch(String namespace, ListOptions listOptions) throws ApiException {
        try {
          addRecordedParameters(getParameters(namespace, listOptions));

          if (nothingToDo()) {
            return new WatchStub<>(Collections.emptyList());
          } else if (exceptionOnNext == null) {
            return new WatchStub<A>((List) calls.remove(0));
          } else {
            try {
              return new ExceptionThrowingWatchStub<>(exceptionOnNext);
            } finally {
              exceptionOnNext = null;
            }
          }
        } catch (IndexOutOfBoundsException e) {
          System.out.println("Failed in thread " + Thread.currentThread());
          throw e;
        }
      }
    };
  }

  @Nonnull
  private Map<String, String> getParameters(String namespace, ListOptions listOptions) {
    final Map<String, String> recordedParams = new HashMap<>();
    if (namespace != null) {
      recordedParams.put("namespace", namespace);
    }
    if (listOptions != null) {
      String resourceVersion = listOptions.getResourceVersion();
      if (resourceVersion != null) {
        recordedParams.put("resourceVersion", resourceVersion);
      }
      String fieldSelector = listOptions.getFieldSelector();
      if (fieldSelector != null) {
        recordedParams.put("fieldSelector", fieldSelector);
      }
      String labelSelector = listOptions.getLabelSelector();
      if (labelSelector != null) {
        recordedParams.put("labelSelector", labelSelector);
      }
      Integer limit = listOptions.getLimit();
      if (limit != null) {
        recordedParams.put("limit", limit.toString());
      }
      Integer timeoutSeconds = listOptions.getTimeoutSeconds();
      if (timeoutSeconds != null) {
        recordedParams.put("timeoutSeconds", timeoutSeconds.toString());
      }
      String cont = listOptions.getContinue();
      if (cont != null) {
        recordedParams.put("cont", cont);
      }
    }
    return recordedParams;
  }

  private void addRecordedParameters(Map<String, String> recordedParams) {
    if (requestParameters.size() > MAX_TEST_REQUESTS) {
      return;
    }
    requestParameters.add(recordedParams);
  }

  private boolean nothingToDo() {
    return calls.isEmpty() && exceptionOnNext == null;
  }

  public interface AllWatchesClosedListener {
    void allWatchesClosed();
  }

  class WatchStub<X> implements Watchable<X> {
    private final List<Watch.Response<X>> responses;
    private final Iterator<Watch.Response<X>> iterator;

    private WatchStub(List<Watch.Response<X>> responses) {
      this.responses = responses;
      iterator = responses.iterator();
    }

    @Override
    public void close() {
      numCloseCalls++;
      if (calls.isEmpty() && listener != null) {
        listener.allWatchesClosed();
      }
    }

    @Override
    public @Nonnull Iterator<Watch.Response<X>> iterator() {
      return responses.iterator();
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public Watch.Response<X> next() {
      return iterator.next();
    }
  }

  class ExceptionThrowingWatchStub<X> extends WatchStub<X> {
    private final RuntimeException exception;

    private ExceptionThrowingWatchStub(RuntimeException exception) {
      super(new ArrayList<>());
      this.exception = exception;
    }

    @Override
    public boolean hasNext() {
      return true;
    }

    @Override
    public Response<X> next() {
      throw exception;
    }
  }
}
