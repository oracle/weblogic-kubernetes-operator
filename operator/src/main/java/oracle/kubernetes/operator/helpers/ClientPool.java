// Copyright 2017, 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import com.squareup.okhttp.Dispatcher;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.ContainerResolver;

public class ClientPool extends Pool<ApiClient> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static ClientPool SINGLETON = new ClientPool();
  private static ThreadFactory threadFactory;

  private static final ClientFactory FACTORY = new DefaultClientFactory();

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

  private final AtomicBoolean isFirst = new AtomicBoolean(true);

  @Override
  protected ApiClient create() {
    return getApiClient();
  }

  private ApiClient getApiClient() {
    LOGGER.entering();

    ApiClient client = null;
    LOGGER.fine(MessageKeys.CREATING_API_CLIENT);
    try {
      ClientFactory factory = null;
      Container c = ContainerResolver.getInstance().getContainer();
      if (c != null) {
        factory = c.getSPI(ClientFactory.class);
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

  @Override
  protected ApiClient onRecycle(ApiClient instance) {
    // Work around async processing creating, but not cleaning-up network interceptors
    instance.getHttpClient().networkInterceptors().clear();
    return super.onRecycle(instance);
  }

  private static class DefaultClientFactory implements ClientFactory {
    private final AtomicBoolean first = new AtomicBoolean(true);

    @Override
    public ApiClient get() {
      ApiClient client;
      try {
        client = Config.defaultClient();
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
          client.getHttpClient().setDispatcher(new Dispatcher(exec));
        }

        return client;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
