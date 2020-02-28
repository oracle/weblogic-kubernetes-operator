// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.ClientBuilder;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.ContainerResolver;

public class ClientPool extends Pool<ApiClient> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static ClientFactory FACTORY = new DefaultClientFactory();
  private static ClientPool SINGLETON = new ClientPool();
  private static ThreadFactory threadFactory;
  private final AtomicBoolean isFirst = new AtomicBoolean(true);

  // With OKHttp3, each client has it's own connection pool, so instance will be shared
  private final AtomicReference<ApiClient> instance = new AtomicReference<>();

  public static void initialize(ThreadFactory threadFactory) {
    ClientPool.threadFactory = threadFactory;
  }

  private static Runnable wrapRunnable(Runnable r) {
    return new Runnable() {
      @Override
      public void run() {
        try {
          r.run();
        } catch (Throwable t) {
          // These will almost always be spurious exceptions
          LOGGER.finer(MessageKeys.EXCEPTION, t);
        }
      }
    };
  }

  public static ClientPool getInstance() {
    return SINGLETON;
  }

  @Override
  protected ApiClient create() {
    // We no longer need this connection pooling because OkHttp 3 now supports
    // connection pooling within each instance.  Prior to the Kubernetes Java
    // client, version 7.0.0, the ApiClient held an instance of the OkHttp 2
    // HTTP client, which was single threaded.
    // Disable pooling and always return the same instance
    return instance.updateAndGet(prev -> {
      return prev != null ? prev : getApiClient();
    });
  }

  private ApiClient getApiClient() {
    LOGGER.entering();

    ApiClient client = null;
    LOGGER.fine(MessageKeys.CREATING_API_CLIENT);
    try {
      ClientFactory factory = null;
      Container c = ContainerResolver.getInstance().getContainer();
      if (c != null) {
        factory = c.getSpi(ClientFactory.class);
      }
      if (factory == null) {
        factory = FACTORY;
      }

      client = factory.get();
    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }

    if (isFirst.compareAndSet(true, false)) {
      LOGGER.info(MessageKeys.K8S_MASTER_URL, client != null ? client.getBasePath() : null);
    }

    LOGGER.exiting(client);
    return client;
  }

  private static class DefaultClientFactory implements ClientFactory {
    private final AtomicBoolean first = new AtomicBoolean(true);

    @Override
    public ApiClient get() {
      ApiClient client;
      try {
        client = ClientBuilder.standard().setOverridePatchFormat(V1Patch.PATCH_FORMAT_JSON_PATCH).build();
        if (first.getAndSet(false)) {
          Configuration.setDefaultApiClient(client);
        }

        if (threadFactory != null) {
          ExecutorService exec =
              new ThreadPoolExecutor(
                  0,
                  Integer.MAX_VALUE,
                  60,
                  TimeUnit.SECONDS,
                  new SynchronousQueue<Runnable>(),
                  threadFactory) {
                @Override
                public void execute(Runnable command) {
                  super.execute(wrapRunnable(command));
                }
              };
          OkHttpClient httpClient =
              client.getHttpClient().newBuilder().dispatcher(new Dispatcher(exec)).build();
          client.setHttpClient(httpClient);
        }

        return client;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
