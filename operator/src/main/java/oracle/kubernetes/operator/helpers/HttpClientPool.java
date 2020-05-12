// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.ContainerResolver;

public class HttpClientPool extends Pool<HttpClient> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final HttpClientFactory FACTORY = new DefaultClientFactory();
  private static HttpClientPool SINGLETON = new HttpClientPool();
  private static ThreadFactory threadFactory;
  private final AtomicBoolean isFirst = new AtomicBoolean(true);

  // HttpClient instance that will be shared
  private final AtomicReference<HttpClient> instance = new AtomicReference<>();

  public static void initialize(ThreadFactory threadFactory) {
    HttpClientPool.threadFactory = threadFactory;
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

  public static HttpClientPool getInstance() {
    return SINGLETON;
  }

  @Override
  protected HttpClient create() {
    return instance.updateAndGet(
        prev -> {
          return prev != null ? prev : getHttpClient();
        });
  }

  private HttpClient getHttpClient() {
    LOGGER.entering();

    HttpClient httpClient = null;
    try {
      HttpClientFactory factory = null;
      Container c = ContainerResolver.getInstance().getContainer();
      if (c != null) {
        factory = c.getSpi(HttpClientFactory.class);
      }
      if (factory == null) {
        factory = FACTORY;
      }

      httpClient = factory.get();
    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }

    if (isFirst.compareAndSet(true, false)) {
      LOGGER.info(MessageKeys.K8S_MASTER_URL, httpClient != null ? httpClient.toString() : null);
    }

    LOGGER.exiting(httpClient);
    return httpClient;
  }

  private static class DefaultClientFactory implements HttpClientFactory {
    @Override
    public HttpClient get() {
      HttpClient httpClient;
      try {
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
          httpClient = HttpClient.newBuilder().executor(exec).build();
        } else {
          httpClient = HttpClient.newBuilder().build();
        }

        return httpClient;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
