// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import okhttp3.Call;
import oracle.kubernetes.operator.helpers.ClientPool;

/**
 * A wrapper of the Kubernetes Watch class that includes management of clients.
 */
public class WatchImpl<T> implements Watchable<T> {
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"}) // non-final to allow unit testing
  private static WatchFactory<?> FACTORY = WatchImpl::createWatch;

  private ApiClient client;
  private final Watchable<T> impl;

  @SuppressWarnings("unchecked")
  WatchImpl(CallParams callParams, Class<?> responseBodyType, BiFunction<ApiClient, CallParams, Call> function) {
    client = ClientPool.getInstance().take();
    impl = (Watchable<T>) FACTORY.createWatch(client, function.apply(client, callParams), getType(responseBodyType));
  }

  private static <W> Watchable<W> createWatch(ApiClient client, Call call, Type type) {
    try {
      return Watch.createWatch(client, call, type);
    } catch (ApiException e) {
      throw new UncheckedApiException(e);
    }
  }

  static Type getType(Class<?> responseBodyType) {
    return new ParameterizedType() {
      @Override
      public Type[] getActualTypeArguments() {
        return new Type[] {responseBodyType};
      }

      @Override
      public Type getRawType() {
        return Watch.Response.class;
      }

      @Override
      public Type getOwnerType() {
        return Watch.class;
      }
    };
  }

  @Override
  public void close() throws IOException {
    impl.close();
    if (client != null) {
      ClientPool.getInstance().recycle(client);
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
