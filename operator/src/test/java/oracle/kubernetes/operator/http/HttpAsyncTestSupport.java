// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;

import static com.meterware.simplestub.Stub.createStub;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

/**
 * A class to enable unit testing of async http requests.
 */
public class HttpAsyncTestSupport {
  private static final HttpResponse<String> NOT_FOUND = createStub(HttpResponseStub.class, HTTP_NOT_FOUND);
  private static final RequestHandler NO_SUCH_HANDLER = new RequestHandler(null, NOT_FOUND);

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private HttpAsyncRequestStep.FutureFactory futureFactory = this::getFuture;
  private final Map<URI, List<RequestHandler>> cannedResponses = new HashMap<>();
  private final Stack<HttpRequest> receivedRequests = new Stack<>();
  private final List<Consumer<HttpRequest>> callbacks = new ArrayList<>();

  /**
   * Add a callback to be invoked whenever a request is handled.
   * @param callback the callback
   */
  public void addCallback(Consumer<HttpRequest> callback) {
    callbacks.add(callback);
  }

  /**
   * Defines the response for an async http request and returns the handler.
   * @param request the expected request
   * @param response the desired result
   */
  public void defineResponse(HttpRequest request, HttpResponse<String> response) {
    cannedResponses.putIfAbsent(request.uri(), new ArrayList<>());
    cannedResponses.get(request.uri()).add(new RequestHandler(request, response));
  }

  /**
   * Returns the last request handled by this class.
   */
  public HttpRequest getLastRequest() {
    return receivedRequests.peek();
  }

  /**
   * Returns the requests handled by this class, in order. A request is only considered to have been
   * handled if a matching response was defined for it before it was received.
   */
  public List<HttpRequest> getHandledRequests() {
    return Collections.unmodifiableList(receivedRequests);
  }

  /**
   * Returns the contents of the specified request as a string.
   * @return a string, which could be null
   */
  public String getLastRequestContents() {
    final RequestContent requestContent = new RequestContent();
    getLastRequest().bodyPublisher().ifPresent(p -> p.subscribe(requestContent));
    return requestContent.getContents();
  }

  RequestHandler getHandler(HttpRequest request) {
    final RequestHandler requestHandler = Optional.ofNullable(cannedResponses.get(request.uri()))
          .map(l -> getMatchingRequest(l, request))
          .orElse(NO_SUCH_HANDLER);
    requestHandler.ifMatched(r -> recordRequestHandled(request));
    return requestHandler;
  }

  private void recordRequestHandled(HttpRequest request) {
    receivedRequests.push(request);
    callbacks.forEach(callback -> callback.accept(request));
  }

  private RequestHandler getMatchingRequest(List<RequestHandler> handlers, HttpRequest request) {
    return handlers.stream().filter(h -> h.matches(request)).findFirst().orElse(null);
  }

  public CompletableFuture<HttpResponse<String>> getFuture(HttpRequest request) {
    return getHandler(request).future;
  }

  static class RequestHandler {
    private final HttpRequest request;
    private final CompletableFuture<HttpResponse<String>> future;
    private final HttpResponse<String> response;

    RequestHandler(HttpRequest request, HttpResponse<String> response) {
      this.request = request;
      this.future = new CompletableFuture<>();
      this.future.complete(response);
      this.response = response;
    }

    HttpResponse<String> getResponse() {
      return response;
    }

    private boolean matches(HttpRequest request) {
      return isMatchingRequest(request, this.request);
    }

    private boolean isMatchingRequest(HttpRequest left, HttpRequest right) {
      return left.method().equals(right.method());
    }

    public void ifMatched(Consumer<HttpRequest> processRequest) {
      Optional.ofNullable(request).ifPresent(processRequest);
    }
  }

  public Memento install() throws NoSuchFieldException {
    return StaticStubSupport.install(HttpAsyncRequestStep.class, "factory", futureFactory);
  }

  static class RequestContent implements Flow.Subscriber<ByteBuffer> {

    private String contents;

    String getContents() {
      return contents;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(1);
    }

    @Override
    public void onNext(ByteBuffer item) {
      contents = new String(item.array());
    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onComplete() {

    }
  }
}
