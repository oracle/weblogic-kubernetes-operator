// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/**
 * ContainerResolver based on {@link ThreadLocal}.
 *
 * <p>The ThreadLocalContainerResolver is the default implementation available from the
 * ContainerResolver using {@link ContainerResolver#getDefault()}. Code sections that run with a
 * Container must use the following pattern:
 *
 * <pre>
 * public void m() {
 *   Container old = ContainerResolver.getDefault().enterContainer(myContainer);
 *   try {
 *     // ... method body
 *   } finally {
 *     ContainerResolver.getDefault().exitContainer(old);
 *   }
 * }
 * </pre>
 */
public class ThreadLocalContainerResolver extends ContainerResolver {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private ThreadLocal<Container> containerThreadLocal =
      new ThreadLocal<Container>() {
        @Override
        protected Container initialValue() {
          return Container.NONE;
        }
      };

  public Container getContainer() {
    return containerThreadLocal.get();
  }

  /**
   * Enters container.
   *
   * @param container Container to set
   * @return Previous container; must be remembered and passed to exitContainer
   */
  public Container enterContainer(Container container) {
    Container old = containerThreadLocal.get();
    containerThreadLocal.set(container);
    return old;
  }

  /**
   * Exits container.
   *
   * @param old Container returned from enterContainer
   */
  public void exitContainer(Container old) {
    containerThreadLocal.set(old);
  }

  ScheduledExecutorService wrapExecutor(
      final Container container, final ScheduledExecutorService ex) {
    if (ex == null) {
      return null;
    }

    Function<Runnable, Runnable> wrap =
        (x) -> {
          return () -> {
            Container old = enterContainer(container);
            try {
              x.run();
            } catch (RuntimeException runtime) {
              LOGGER.severe(MessageKeys.EXCEPTION, runtime);
              throw runtime;
            } catch (Error error) {
              LOGGER.severe(MessageKeys.EXCEPTION, error);
              throw error;
            } catch (Throwable throwable) {
              LOGGER.severe(MessageKeys.EXCEPTION, throwable);
              throw new RuntimeException(throwable);
            } finally {
              exitContainer(old);
            }
          };
        };

    Function<Callable<?>, Callable<?>> wrap2 =
        (x) -> {
          return () -> {
            Container old = enterContainer(container);
            try {
              return x.call();
            } catch (RuntimeException runtime) {
              LOGGER.severe(MessageKeys.EXCEPTION, runtime);
              throw runtime;
            } catch (Error error) {
              LOGGER.severe(MessageKeys.EXCEPTION, error);
              throw error;
            } catch (Throwable throwable) {
              LOGGER.severe(MessageKeys.EXCEPTION, throwable);
              throw new RuntimeException(throwable);
            } finally {
              exitContainer(old);
            }
          };
        };

    Function<Collection<? extends Callable<?>>, Collection<? extends Callable<?>>> wrap2c =
        (x) -> {
          return x.stream().map(wrap2).collect(Collectors.toList());
        };

    return new ScheduledExecutorService() {

      @Override
      public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return ex.awaitTermination(timeout, unit);
      }

      @SuppressWarnings({"rawtypes", "unchecked"})
      @Override
      public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
          throws InterruptedException {
        return ex.invokeAll((List) wrap2c.apply(tasks));
      }

      @SuppressWarnings({"rawtypes", "unchecked"})
      @Override
      public <T> List<Future<T>> invokeAll(
          Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
          throws InterruptedException {
        return ex.invokeAll((List) wrap2c.apply(tasks), timeout, unit);
      }

      @SuppressWarnings({"rawtypes", "unchecked"})
      @Override
      public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
          throws InterruptedException, ExecutionException {
        return (T) ex.invokeAny((List) wrap2c.apply(tasks));
      }

      @SuppressWarnings({"rawtypes", "unchecked"})
      @Override
      public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        return (T) ex.invokeAny((List) wrap2c.apply(tasks), timeout, unit);
      }

      @Override
      public boolean isShutdown() {
        return ex.isShutdown();
      }

      @Override
      public boolean isTerminated() {
        return ex.isTerminated();
      }

      @Override
      public void shutdown() {
        ex.shutdown();
      }

      @Override
      public List<Runnable> shutdownNow() {
        return ex.shutdownNow();
      }

      @SuppressWarnings({"rawtypes", "unchecked"})
      @Override
      public <T> Future<T> submit(Callable<T> task) {
        return (Future) ex.submit(wrap2.apply(task));
      }

      @SuppressWarnings({"rawtypes"})
      @Override
      public Future<?> submit(Runnable task) {
        return (Future) ex.submit(wrap.apply(task));
      }

      @SuppressWarnings({"rawtypes", "unchecked"})
      @Override
      public <T> Future<T> submit(Runnable task, T result) {
        return (Future) ex.submit(wrap.apply(task), result);
      }

      @Override
      public void execute(Runnable command) {
        ex.execute(wrap.apply(command));
      }

      @Override
      public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return ex.schedule(wrap.apply(command), delay, unit);
      }

      @SuppressWarnings({"rawtypes", "unchecked"})
      @Override
      public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return ex.schedule((Callable) wrap2.apply(callable), delay, unit);
      }

      @Override
      public ScheduledFuture<?> scheduleAtFixedRate(
          Runnable command, long initialDelay, long period, TimeUnit unit) {
        return ex.scheduleAtFixedRate(wrap.apply(command), initialDelay, period, unit);
      }

      @Override
      public ScheduledFuture<?> scheduleWithFixedDelay(
          Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return ex.scheduleWithFixedDelay(wrap.apply(command), initialDelay, delay, unit);
      }
    };
  }
}
