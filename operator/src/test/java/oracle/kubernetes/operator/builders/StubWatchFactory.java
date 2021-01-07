// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.Watchable;
import okhttp3.Call;
import org.jetbrains.annotations.NotNull;

/**
 * A test-time replacement for the factory that creates Watch objects, allowing tests to specify
 * directly the events they want returned from the Watch.
 */
public class StubWatchFactory<T> implements WatchFactory<T> {

  private static final int MAX_TEST_REQUESTS = 100;
  private static final String SYMBOL = "[A-Z_a-z0-9.]+";
  private static final String ENCODED_COMMA = "%2C";
  private static final String PARAMETERS_PATTERN = "(" + SYMBOL + ")=(" + SYMBOL + "(" + ENCODED_COMMA + SYMBOL + ")*)";
  private static final Pattern URL_PARAMETERS = Pattern.compile(PARAMETERS_PATTERN);
  private static StubWatchFactory<?> factory;
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
    factory = new StubWatchFactory<>();
    requestParameters = new ArrayList<>();
    exceptionOnNext = null;

    return StaticStubSupport.install(WatchImpl.class, "FACTORY", factory);
  }

  /**
   * Adds the events to be returned from a single call to Watch.next()
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
   * The factory method called when a watch is created. This method returns a stub for the Kubernetes watch
   * that allows tests to simulate responses and check specified parameters.
   * @param client the underlying Kubernetes http client
   * @param call a definition of the http class to be made to Kubernetes to create the watch
   * @param type the type of the object that the watch will describe
   * @return a test double for the requested watch
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  @NotNull
  public Watchable<T> createWatch(ApiClient client, Call call, Type type) {
    try {
      addRecordedParameters(getParameters(call));

      if (nothingToDo()) {
        return new WatchStub<>(Collections.emptyList());
      } else if (exceptionOnNext == null) {
        return new WatchStub<T>((List) calls.remove(0));
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

  @NotNull
  private Map<String, String> getParameters(Call call) {
    final Matcher matcher = URL_PARAMETERS.matcher(call.request().url().toString());
    final Map<String, String> recordedParams = new HashMap<>();
    while (matcher.find()) {
      recordedParams.put(matcher.group(1), matcher.group(2).replace(ENCODED_COMMA, ","));
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
      if (calls.size() == 0 && listener != null) {
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
