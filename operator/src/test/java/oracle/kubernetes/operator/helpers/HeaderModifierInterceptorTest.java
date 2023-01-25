// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nonnull;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStub;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class HeaderModifierInterceptorTest {
  private static final String INITIAL_ACCEPT_HEADER = "initialValue";

  private final Interceptor interceptor = new HeaderModifierInterceptor();
  private final Request originalRequest = createRequest();
  private final ChainStub chain = createStub(ChainStub.class, originalRequest);

  @BeforeEach
  void setUp() {
    HeaderModifierInterceptor.setPartialMetadataHeader(true);
  }

  @AfterEach
  void tearDown() {
    HeaderModifierInterceptor.removePartialMetadataHeader();
  }

  @Nonnull
  private Request createRequest() {
    return new Request.Builder()
          .url("http://localhost:1234")
          .addHeader("Accept", INITIAL_ACCEPT_HEADER)
          .build();
  }

  @Test
  void whenPartialMetadataHeaderNotEnabled_dontChangeRequest() throws IOException {
    HeaderModifierInterceptor.setPartialMetadataHeader(false);

    final Response response = interceptor.intercept(chain);

    final String acceptHeader = response.header("Accept");
    assertThat(acceptHeader, equalTo(INITIAL_ACCEPT_HEADER));
  }

  @Test
  void whenPartialMetadataHeaderEnabled_insertMetadataAcceptHeader() throws IOException {
    final Response response = interceptor.intercept(chain);

    final String acceptHeader = response.header("Accept");
    assertThat(acceptHeader, containsString("PartialObjectMetadata"));
  }

  abstract static class ChainStub implements Interceptor.Chain {
    private final Request request;

    ChainStub(Request request) {
      this.request = request;
    }

    @Nonnull
    @Override
    public Response proceed(@Nonnull Request request) {
      return new Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .addHeader("Accept", getAcceptHeader(request))
            .code(200)
            .message("None")
            .build();
    }

    @Nonnull
    private String getAcceptHeader(@Nonnull Request request) {
      return Optional.ofNullable(request.header("Accept")).orElse("");
    }

    @Nonnull
    @Override
    public Request request() {
      return request;
    }
  }

  private static Headers createHeaders() {
    return Headers.of("");
  }
}