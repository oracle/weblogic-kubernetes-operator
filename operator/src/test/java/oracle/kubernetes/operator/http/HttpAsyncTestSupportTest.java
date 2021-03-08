// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStub;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class HttpAsyncTestSupportTest {
  private final HttpAsyncTestSupport support = new HttpAsyncTestSupport();

  @Test
  public void whenNoDefinedResponse_returnNotFoundResponse() {
    assertThat(getResponse(createGetRequest("http://nowhere")).statusCode(),
          equalTo(HttpURLConnection.HTTP_NOT_FOUND));
  }

  HttpResponse<String> getResponse(HttpRequest request) {
    return support.getHandler(request).getResponse();
  }

  private HttpRequest createGetRequest(String urlString) {
    return HttpRequest.newBuilder().GET().uri(URI.create(urlString)).build();
  }

  @Test
  void afterRequestHandled_mayRetrieveFromTestSupport() {
    final HttpRequest request = createGetRequest("http://known");
    support.defineResponse(request, createStub(HttpResponseStub.class, 200, "It works"));

    getResponse(request);

    assertThat(support.getLastRequest(), sameInstance(request));
  }

  @Test
  public void whenOneGetResponseDefined_selectIt() {
    support.defineResponse(createGetRequest("http://known"), createStub(HttpResponseStub.class, 200, "It works"));

    assertThat(getResponse(createGetRequest("http://known")).statusCode(), equalTo(200));
    assertThat(getResponse(createGetRequest("http://known")).body(), equalTo("It works"));
  }

  @Test
  public void whenGetAndPostRequestDefined_selectGet() {
    support.defineResponse(createPostRequest("http://this", "abc"), createStub(HttpResponseStub.class, 200, "Got it"));
    support.defineResponse(createGetRequest("http://this"), createStub(HttpResponseStub.class, 200, "It works"));

    assertThat(getResponse(createGetRequest("http://this")).body(), equalTo("It works"));
  }

  @Test
  public void whenGetAndPostRequestDefined_selectPost() {
    support.defineResponse(createPostRequest("http://this", "abc"), createStub(HttpResponseStub.class, 200, "Got it"));
    support.defineResponse(createGetRequest("http://this"), createStub(HttpResponseStub.class, 200, "It works"));

    assertThat(getResponse(createPostRequest("http://this", "abc")).body(), equalTo("Got it"));
  }

  @Test
  public void whenMultipleUrlsDefined_selectMatch() {
    support.defineResponse(createGetRequest("http://that"), createStub(HttpResponseStub.class, 200, "Wrong"));
    support.defineResponse(createGetRequest("http://this"), createStub(HttpResponseStub.class, 200, "Got it"));

    assertThat(getResponse(createGetRequest("http://this")).body(), equalTo("Got it"));
  }

  @Test
  void whenMatchingRequestMeetsExpectations_returnMatch() {

  }

  @Test
  void whenMatchingRequestHasUnexpectedContent_fail() {
    support.defineResponse(createPostRequest("http://that", "abcd"),
                                  createStub(HttpResponseStub.class, 200, "Wrong"));


  }

  @SuppressWarnings("SameParameterValue")
  private HttpRequest createPostRequest(String urlString, String body) {
    return HttpRequest.newBuilder().POST(HttpRequest.BodyPublishers.ofString(body)).uri(URI.create(urlString)).build();
  }

  @Test
  public void matchingFuture_markedComplete() {
    support.defineResponse(createGetRequest("http://known"), createStub(HttpResponseStub.class, 200, "It works"));

    assertThat(support.getFuture(createGetRequest("http://known")).isDone(), is(true));
  }
}
