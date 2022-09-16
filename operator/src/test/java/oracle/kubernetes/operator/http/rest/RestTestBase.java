// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.meterware.simplestub.Memento;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.HttpHeaders;
import oracle.kubernetes.common.utils.BaseTestUtils;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.utils.TestUtils;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static oracle.kubernetes.operator.http.rest.AuthenticationFilter.ACCESS_TOKEN_PREFIX;

@SuppressWarnings("SameParameterValue")
public class RestTestBase extends JerseyTest {
  private static final String ACCESS_TOKEN = "dummy token";

  private final List<Memento> mementos = new ArrayList<>();
  protected final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  boolean includeRequestedByHeader = true;
  String authorizationHeader = ACCESS_TOKEN_PREFIX + " " + ACCESS_TOKEN;

  @BeforeEach
  public void setupRestTest() throws Exception {
    setUp();
    mementos.add(testSupport.install());
    mementos.add(BaseTestUtils.silenceJsonPathLogger());
    mementos.add(TestUtils.silenceOperatorLogger());
  }

  @AfterEach
  public void restore() throws Exception {
    tearDown();
    mementos.forEach(Memento::revert);
  }

  protected Invocation.Builder createRequest(String href) {
    Invocation.Builder request = target(href).request();
    if (authorizationHeader != null) {
      request.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
    }
    if (includeRequestedByHeader) {
      request.header("X-Requested-By", "TestClient");
    }
    return request;
  }

  @Override
  protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
    return new InMemoryTestContainerFactory();
  }

  @SuppressWarnings("unused")
  static class JsonArrayMatcher extends TypeSafeDiagnosingMatcher<List<Object>> {
    private final Object[] expectedContents;

    private JsonArrayMatcher(Object[] expectedContents) {
      this.expectedContents = expectedContents;
    }

    static JsonArrayMatcher withValues(Object... expectedContents) {
      return new JsonArrayMatcher(expectedContents);
    }

    @Override
    protected boolean matchesSafely(List<Object> item, Description mismatchDescription) {
      Set<Object> actuals = new HashSet<>(item);
      for (Object o : expectedContents) {
        if (!actuals.remove(o)) {
          return reportMismatch(item, mismatchDescription);
        }
      }
      return true;
    }

    private boolean reportMismatch(List<Object> item, Description mismatchDescription) {
      mismatchDescription.appendValueList("JSON array containing [", ",", "]", item);
      return false;
    }

    @Override
    public void describeTo(Description description) {
      description.appendValueList("JSON array containing [", ",", "]", expectedContents);
    }
  }

}
