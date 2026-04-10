// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.CreateOptions;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import io.kubernetes.client.util.generic.options.GetOptions;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import io.kubernetes.client.util.generic.options.UpdateOptions;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.tuning.TuningParameters.CALL_MAX_RETRY_COUNT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestBuilderTest {

  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    mementos.add(TuningParametersStub.install());
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenDirectCreateGetsRetryableIoFailure_retriesAndReturnsResult() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    mementos.add(installFactory(createFactory(ignored -> {
      if (attempts.getAndIncrement() == 0) {
        throw new IllegalStateException(new IOException("connect timed out"));
      }
      return ignored;
    })));
    TuningParametersStub.setParameter(CALL_MAX_RETRY_COUNT, "1");

    V1TokenReview tokenReview = new V1TokenReview().metadata(new V1ObjectMeta().name("review"));

    V1TokenReview result = RequestBuilder.TR.create(tokenReview);

    assertThat(result, equalTo(tokenReview));
    assertThat(attempts.get(), equalTo(2));
  }

  @Test
  void whenDirectCreateExhaustsRetryableIoFailures_throwsApiException() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    mementos.add(installFactory(createFactory(ignored -> {
      attempts.incrementAndGet();
      throw new IllegalStateException(new IOException("connect timed out"));
    })));
    TuningParametersStub.setParameter(CALL_MAX_RETRY_COUNT, "1");

    ApiException apiException = assertThrows(ApiException.class, () -> RequestBuilder.TR.create(new V1TokenReview()));

    assertThat(apiException.getCause().getMessage(), equalTo("connect timed out"));
    assertThat(attempts.get(), equalTo(2));
  }

  private Memento installFactory(KubernetesApiFactory factory) throws NoSuchFieldException {
    return StaticStubSupport.install(RequestBuilder.class, "kubernetesApiFactory", factory);
  }

  private KubernetesApiFactory createFactory(Function<V1TokenReview, V1TokenReview> createAction) {
    return new KubernetesApiFactory() {
      @Override
      @SuppressWarnings("unchecked")
      public <A extends KubernetesObject, L extends KubernetesListObject> KubernetesApi<A, L> create(
          Class<A> apiTypeClass, Class<L> apiListTypeClass, String apiGroup, String apiVersion, String resourcePlural,
          UnaryOperator<ApiClient> clientSelector) {
        return new StubKubernetesApi<>((Function<A, A>) createAction);
      }
    };
  }

  private static class StubKubernetesApi<A extends KubernetesObject, L extends KubernetesListObject>
      implements KubernetesApi<A, L> {

    private final Function<A, A> createAction;

    StubKubernetesApi(Function<A, A> createAction) {
      this.createAction = createAction;
    }

    @Override
    public KubernetesApiResponse<A> get(String name, GetOptions getOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KubernetesApiResponse<A> get(String namespace, String name, GetOptions getOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KubernetesApiResponse<L> list(ListOptions listOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KubernetesApiResponse<L> list(String namespace, ListOptions listOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KubernetesApiResponse<A> create(A object, CreateOptions createOptions) {
      return new KubernetesApiResponse<>(createAction.apply(object));
    }

    @Override
    public KubernetesApiResponse<A> create(String namespace, A object, CreateOptions createOptions) {
      return create(object, createOptions);
    }

    @Override
    public KubernetesApiResponse<A> update(A object, UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KubernetesApiResponse<A> updateStatus(A object, Function<A, Object> status, UpdateOptions updateOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KubernetesApiResponse<A> patch(String name, String patchType, V1Patch patch, PatchOptions patchOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KubernetesApiResponse<A> patch(String namespace, String name, String patchType, V1Patch patch,
                                          PatchOptions patchOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KubernetesApiResponse<A> delete(String name, DeleteOptions deleteOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KubernetesApiResponse<A> delete(String namespace, String name, DeleteOptions deleteOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KubernetesApiResponse<RequestBuilder.V1StatusObject> deleteCollection(
        String namespace, ListOptions listOptions, DeleteOptions deleteOptions) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KubernetesApiResponse<RequestBuilder.StringObject> logs(String namespace, String name, String container) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KubernetesApiResponse<RequestBuilder.VersionInfoObject> getVersionCode() {
      throw new UnsupportedOperationException();
    }
  }
}
