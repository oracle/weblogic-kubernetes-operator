package oracle.kubernetes.operator.calls;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import oracle.kubernetes.operator.helpers.Pool;

public interface SynchronousCallDispatcher {
  <T> T execute(
      SynchronousCallFactory<T> factory, RequestParams requestParams, Pool<ApiClient> helper)
      throws ApiException;
}
