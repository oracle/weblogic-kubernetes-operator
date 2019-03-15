// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watch.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.helpers.Pool;

/**
 * A test-time replacement for the factory that creates Watch objects, allowing tests to specify
 * directly the events they want returned from the Watch.
 */
public class StubWatchFactory implements WatchBuilder.WatchFactory {

  private static final int MAX_TEST_REQUESTS = 100;
  private static StubWatchFactory factory;
  private static List<Map<String, String>> requestParameters;
  private static RuntimeException exceptionOnNext;
  private static AllWatchesClosedListener listener;

  private List<List<Watch.Response<Object>>> calls = new ArrayList<>();
  private int numCloseCalls;

  public static Memento install() throws NoSuchFieldException {
    factory = new StubWatchFactory();
    requestParameters = new ArrayList<>();
    exceptionOnNext = null;

    return StaticStubSupport.install(WatchBuilder.class, "FACTORY", factory);
  }

  /**
   * Adds the events to be returned from a single call to Watch.next()
   *
   * @param events the events; will be converted to Watch.Response objects
   */
  @SuppressWarnings("unchecked")
  public static void addCallResponses(Watch.Response<Object>... events) {
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public <T> WatchI<T> createWatch(
      Pool<ApiClient> pool,
      CallParams callParams,
      Class<?> responseBodyType,
      BiFunction<ApiClient, CallParams, Call> function) {
    try {
      Map<String, String> recordedParams = recordedParams(callParams);
      addRecordedParameters(recordedParams);

      if (nothingToDo()) return new WatchStub<>(Collections.emptyList());
      else if (exceptionOnNext == null) return new WatchStub<T>((List) calls.remove(0));
      else
        try {
          return new ExceptionThrowingWatchStub<>(exceptionOnNext);
        } finally {
          exceptionOnNext = null;
        }
    } catch (IndexOutOfBoundsException e) {
      System.out.println("Failed in thread " + Thread.currentThread());
      throw e;
    }
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

  private Map<String, String> recordedParams(CallParams callParams) {
    Map<String, String> result = new HashMap<>();
    if (callParams.getResourceVersion() != null)
      result.put("resourceVersion", callParams.getResourceVersion());
    if (callParams.getLabelSelector() != null)
      result.put("labelSelector", callParams.getLabelSelector());

    return result;
  }

  private StubWatchFactory() {}

  public static void throwExceptionOnNext(RuntimeException e) {
    exceptionOnNext = e;
  }

  public interface AllWatchesClosedListener {
    void allWatchesClosed();
  }

  class WatchStub<T> implements WatchI<T> {
    private List<Watch.Response<T>> responses;
    private Iterator<Watch.Response<T>> iterator;

    private WatchStub(List<Watch.Response<T>> responses) {
      this.responses = responses;
      iterator = responses.iterator();
    }

    @Override
    public void close() {
      numCloseCalls++;
      if (calls.size() == 0 && listener != null) listener.allWatchesClosed();
    }

    @Override
    public @Nonnull Iterator<Watch.Response<T>> iterator() {
      return responses.iterator();
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public Watch.Response<T> next() {
      return iterator.next();
    }
  }

  class ExceptionThrowingWatchStub<T> extends WatchStub<T> {
    private RuntimeException exception;

    private ExceptionThrowingWatchStub(RuntimeException exception) {
      super(new ArrayList<>());
      this.exception = exception;
    }

    @Override
    public boolean hasNext() {
      return true;
    }

    @Override
    public Response<T> next() {
      throw exception;
    }
  }
}
