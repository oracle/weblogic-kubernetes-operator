// Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.util.Watch;
import java.io.IOException;
import java.util.Iterator;
import oracle.kubernetes.operator.helpers.Pool;

/**
 * A pass-through implementation of the Kubernetes Watch class which implements a facade interface.
 */
public class WatchImpl<T> implements WatchI<T> {
  private final Pool<ApiClient> pool;
  private final ApiClient client;
  private Watch<T> impl;

  WatchImpl(Pool<ApiClient> pool, ApiClient client, Watch<T> impl) {
    this.pool = pool;
    this.client = client;
    this.impl = impl;
  }

  @Override
  public void close() throws IOException {
    impl.close();
    pool.recycle(client);
  }

  @Override
  public Iterator<Watch.Response<T>> iterator() {
    return impl.iterator();
  }

  @Override
  public boolean hasNext() {
    return impl.hasNext();
  }

  @Override
  public Watch.Response<T> next() {
    return impl.next();
  }
}
