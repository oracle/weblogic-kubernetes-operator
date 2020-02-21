// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import java.io.IOException;
import java.util.Iterator;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.helpers.Pool;

/**
 * A pass-through implementation of the Kubernetes Watch class which implements a facade interface.
 */
public class WatchImpl<T> implements WatchI<T> {
  private final Pool<ApiClient> pool;
  private ApiClient client;
  private final Watch<T> impl;

  WatchImpl(Pool<ApiClient> pool, ApiClient client, Watch<T> impl) {
    this.pool = pool;
    this.client = client;
    this.impl = impl;
  }

  @Override
  public void close() throws IOException {
    impl.close();
    if (client != null) {
      pool.recycle(client);
    }
  }

  @Override
  @Nonnull
  public Iterator<Watch.Response<T>> iterator() {
    return impl.iterator();
  }

  @Override
  public boolean hasNext() {
    return impl.hasNext();
  }

  @Override
  public Watch.Response<T> next() {
    try {
      return impl.next();
    } catch (Exception e) {
      client = null;
      throw e;
    }
  }
}
