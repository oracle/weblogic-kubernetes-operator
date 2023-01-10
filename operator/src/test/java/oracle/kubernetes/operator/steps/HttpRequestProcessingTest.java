// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.http.HttpRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.operator.helpers.AuthorizationSource;
import oracle.kubernetes.operator.http.client.HttpAsyncTestSupport;
import oracle.kubernetes.operator.http.client.HttpResponseStepImpl;
import oracle.kubernetes.operator.http.client.HttpResponseStub;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.ProcessingConstants.AUTHORIZATION_SOURCE;
import static oracle.kubernetes.operator.steps.HttpRequestProcessing.createRequestStep;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class HttpRequestProcessingTest {
  private static final String HOST1_URL_STRING1 = "http://myHost:123/search/here";
  private static final String HOST1_URL_STRING2 = "http://myHost:123/search/there";
  private static final String HOST2_STRING = "http://notMyHost:123/";
  private static final int SECONDS_PER_HOUR = 60 * 60;

  private final Packet packet = new Packet();
  private final HttpRequestProcessing processing = createStub(HttpRequestProcessing.class, packet, null, null);
  private final HttpResponseStepImpl responseStep = new HttpResponseStepImpl(null);
  private final FiberTestSupport testSupport = new FiberTestSupport();
  private final HttpAsyncTestSupport httpSupport = new HttpAsyncTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private OffsetDateTime expirationTime;

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(httpSupport.install());
    mementos.add(SystemClockTestSupport.installClock());

    HttpRequestProcessing.clearCookies();
    packet.put(AUTHORIZATION_SOURCE, new AuthorizationSourceStub());
    expirationTime = SystemClock.now().plusSeconds(SECONDS_PER_HOUR);
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void initiallyRequestsCreatedWithoutCookies() {
    final HttpRequest request = processing.createRequestBuilder(HOST1_URL_STRING1).GET().build();

    assertThat(request.headers().allValues("Cookie"), empty());
  }

  @Test
  void afterSetCookieReceived_requestToSameUrlSendsMatchingCookie() {
    cacheSessionCookie(HOST1_URL_STRING1, "1234");

    assertThat(createGetRequest(HOST1_URL_STRING1).headers().allValues("Cookie"), contains("SESSION=1234"));
  }

  private void cacheSessionCookie(String urlString, String sessionId) {
    defineResponseWithSessionCookie(urlString, sessionId);

    testSupport.runSteps(createRequestStep(createGetRequest(urlString), responseStep));
  }

  private void defineResponseWithSessionCookie(String urlString, String sessionId) {
    httpSupport.defineResponse(
          createGetRequest(urlString),
          createStub(HttpResponseStub.class, 200).withHeader("Set-Cookie", "SESSION=" + sessionId));
  }

  @Test
  void afterSetCookieReceived_requestToDifferentUrlDoesNotIncludeCookie() {
    cacheSessionCookie(HOST2_STRING, "1234");

    assertThat(createGetRequest(HOST1_URL_STRING1).headers().allValues("Cookie"), empty());
  }

  @Test
  void afterSetCookieReceived_requestToDifferentUrlOnSameHostAndPortSendsMatchingCookie() {
    cacheSessionCookie(HOST1_URL_STRING1, "1234");

    assertThat(createGetRequest(HOST1_URL_STRING2).headers().allValues("Cookie"), contains("SESSION=1234"));
  }

  @Test
  void afterSetCookieReceived_requestToSameUrlUpdatesIt() {
    cacheSessionCookie(HOST1_URL_STRING1, "1234");
    httpSupport.clearResponses(HOST1_URL_STRING1);
    cacheSessionCookie(HOST1_URL_STRING1, "4567");

    assertThat(createGetRequest(HOST1_URL_STRING2).headers().allValues("Cookie"), contains("SESSION=4567"));
  }

  @Test
  void afterOneHourWithNoAccess_clearCookie() {
    cacheSessionCookie(HOST1_URL_STRING1, "1234");

    SystemClockTestSupport.setCurrentTime(expirationTime);

    assertThat(createGetRequest(HOST1_URL_STRING1).headers().allValues("Cookie"), empty());
  }

  @Test
  void intermediateAccesses_delayExpiration() {
    cacheSessionCookie(HOST1_URL_STRING1, "1234");
    SystemClockTestSupport.increment(SECONDS_PER_HOUR / 2);
    createGetRequest(HOST1_URL_STRING1);

    SystemClockTestSupport.setCurrentTime(expirationTime);

    assertThat(createGetRequest(HOST1_URL_STRING2).headers().allValues("Cookie"), contains("SESSION=1234"));
  }

  private HttpRequest createGetRequest(String url) {
    return processing.createRequestBuilder(url).GET().build();
  }

  private static class AuthorizationSourceStub implements AuthorizationSource {
    @Override
    public byte[] getUserName() {
      return new byte[0];
    }

    @Override
    public byte[] getPassword() {
      return new byte[0];
    }
  }

}