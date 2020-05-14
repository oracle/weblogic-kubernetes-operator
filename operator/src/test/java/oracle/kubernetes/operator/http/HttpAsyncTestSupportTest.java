// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpRequest;

import org.junit.Ignore;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStub;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class HttpAsyncTestSupportTest {
  private HttpAsyncTestSupport support = new HttpAsyncTestSupport();

  @Test
  public void whenNoDefinedResponse_returnNotFoundResponse() {
    assertThat(support.getResponse(createGetRequest("http://nowhere")).statusCode(),
          equalTo(HttpURLConnection.HTTP_NOT_FOUND));
  }

  private HttpRequest createGetRequest(String urlString) {
    return HttpRequest.newBuilder().GET().uri(URI.create(urlString)).build();
  }

  @Test
  public void whenOneGetResponseDefined_selectIt() {
    support.defineResponse(createGetRequest("http://known"), createStub(HttpResponseStub.class, 200, "It works"));

    assertThat(support.getResponse(createGetRequest("http://known")).statusCode(), equalTo(200));
    assertThat(support.getResponse(createGetRequest("http://known")).body(), equalTo("It works"));
  }

  @Test
  public void whenGetAndPostRequestDefined_selectGet() {
    support.defineResponse(createPostRequest("http://this", "abc"), createStub(HttpResponseStub.class, 200, "Got it"));
    support.defineResponse(createGetRequest("http://this"), createStub(HttpResponseStub.class, 200, "It works"));

    assertThat(support.getResponse(createGetRequest("http://this")).body(), equalTo("It works"));
  }

  @Test
  public void whenGetAndPostRequestDefined_selectPost() {
    support.defineResponse(createPostRequest("http://this", "abc"), createStub(HttpResponseStub.class, 200, "Got it"));
    support.defineResponse(createGetRequest("http://this"), createStub(HttpResponseStub.class, 200, "It works"));

    assertThat(support.getResponse(createPostRequest("http://this", "abc")).body(), equalTo("Got it"));
  }

  @Test
  @Ignore("See if we're actually going to need this")
  public void whenMultiplePostRequestsDefined_selectBasedOnBody() {
    support.defineResponse(createPostRequest("http://here", "abc"), createStub(HttpResponseStub.class, 200, "First"));
    support.defineResponse(createPostRequest("http://here", "def"), createStub(HttpResponseStub.class, 200, "Second"));

    assertThat(support.getResponse(createPostRequest("http://here", "def")).body(), equalTo("Second"));
  }

  private HttpRequest createPostRequest(String urlString, String body) {
    return HttpRequest.newBuilder().POST(HttpRequest.BodyPublishers.ofString(body)).uri(URI.create(urlString)).build();
  }

  @Test
  public void matchingFuture_markedComplete() {
    support.defineResponse(createGetRequest("http://known"), createStub(HttpResponseStub.class, 200, "It works"));

    assertThat(support.getFuture(createGetRequest("http://known")).isDone(), is(true));
  }
}
