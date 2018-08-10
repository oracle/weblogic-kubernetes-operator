// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Handler;
import java.util.logging.Logger;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StepTest {
  private static final String MARK = "mark";
  private static final String NA = "na";
  private static final String ACQUIRE_SEMAPHORE_SUFFIX = "-Acquire-Semaphore";
  private static final String RELEASE_SEMAPHORE_SUFFIX = "-Release-Semaphore";

  private static final int INVOKE_NEXT = 0;
  private static final int RETRY = 1;
  private static final int SUSPEND_AND_RESUME = 2;
  private static final int SUSPEND_AND_THROW = 3;
  private static final int THROW = 4;
  private static final int RELEASE_SEMAPHORE = 5;
  private static final int ACQUIRE_SEMAPHORE = 6;

  private static final Command DEFAULT_COMMAND = new Command(INVOKE_NEXT);

  private Engine engine = null;

  private static final Logger UNDERLYING_LOGGER =
      LoggingFactory.getLogger("Operator", "Operator").getUnderlyingLogger();
  private List<Handler> savedhandlers;
  private ScheduledExecutorService executorService;

  @Before
  public void disableConsoleLogging() {
    savedhandlers = TestUtils.removeConsoleHandlers(UNDERLYING_LOGGER);
  }

  @After
  public void restoreConsoleLogging() {
    TestUtils.restoreConsoleHandlers(UNDERLYING_LOGGER, savedhandlers);
  }

  @Before
  public void setup() {
    executorService = Engine.wrappedExecutorService("StepTest", getDefaultContainer());
    engine = new Engine(executorService);
  }

  private Container getDefaultContainer() {
    return ContainerResolver.getDefault().getContainer();
  }

  @After
  public void tearDown() throws Exception {
    executorService.shutdownNow();
    executorService.awaitTermination(100, TimeUnit.MILLISECONDS);
  }

  @Test
  public void test() throws InterruptedException {
    Step stepline = createStepline(Arrays.asList(Step1.class, Step2.class, Step3.class));
    Packet p = new Packet();

    Semaphore signal = new Semaphore(0);
    List<Step> called = new ArrayList<>();
    List<Throwable> throwables = new ArrayList<>();

    engine
        .createFiber()
        .start(
            stepline,
            p,
            new CompletionCallback() {
              @SuppressWarnings({"unchecked", "rawtypes"})
              @Override
              public void onCompletion(Packet packet) {
                List<Step> l = (List) packet.get(MARK);
                if (l != null) {
                  called.addAll(l);
                }
                signal.release();
              }

              @SuppressWarnings({"unchecked", "rawtypes"})
              @Override
              public void onThrowable(Packet packet, Throwable throwable) {
                List<Step> l = (List) packet.get(MARK);
                if (l != null) {
                  called.addAll(l);
                }
                throwables.add(throwable);
                signal.release();
              }
            });

    boolean result = signal.tryAcquire(5, TimeUnit.SECONDS);
    assertTrue(result);
    assertEquals(3, called.size());
    assertTrue(called.get(0) instanceof Step1);
    assertTrue(called.get(1) instanceof Step2);
    assertTrue(called.get(2) instanceof Step3);
    assertTrue(throwables.isEmpty());
  }

  /**
   * Simplifies creation of stepline. Steps will be connected following the list ordering of their
   * classes
   *
   * @param steps List of step classes
   * @return Head step
   */
  private static Step createStepline(List<Class<? extends Step>> steps) {
    try {
      Step s = null;
      ListIterator<Class<? extends Step>> it = steps.listIterator(steps.size());
      while (it.hasPrevious()) {
        Class<? extends Step> c = it.previous();
        Constructor<? extends Step> construct = c.getConstructor(Step.class);
        s = construct.newInstance(s);
      }
      return s;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testRetry() throws InterruptedException {
    Step stepline = createStepline(Arrays.asList(Step1.class, Step2.class, Step3.class));
    Packet p = new Packet();

    Map<Class<? extends BaseStep>, Command> commandMap = new HashMap<>();
    Command c = new Command(RETRY);
    c.setCount(2);
    commandMap.put(Step2.class, c);
    p.put(NA, commandMap);

    Semaphore signal = new Semaphore(0);
    List<Step> called = new ArrayList<>();
    List<Throwable> throwables = new ArrayList<>();

    engine
        .createFiber()
        .start(
            stepline,
            p,
            new CompletionCallback() {
              @SuppressWarnings({"unchecked", "rawtypes"})
              @Override
              public void onCompletion(Packet packet) {
                List<Step> l = (List) packet.get(MARK);
                if (l != null) {
                  called.addAll(l);
                }
                signal.release();
              }

              @SuppressWarnings({"unchecked", "rawtypes"})
              @Override
              public void onThrowable(Packet packet, Throwable throwable) {
                List<Step> l = (List) packet.get(MARK);
                if (l != null) {
                  called.addAll(l);
                }
                throwables.add(throwable);
                signal.release();
              }
            });

    boolean result = signal.tryAcquire(5, TimeUnit.SECONDS);
    assertTrue(result);
    assertEquals(5, called.size());
    assertTrue(called.get(0) instanceof Step1);
    assertTrue(called.get(1) instanceof Step2);
    assertTrue(called.get(2) instanceof Step2);
    assertTrue(called.get(3) instanceof Step2);
    assertTrue(called.get(4) instanceof Step3);
    assertTrue(throwables.isEmpty());
  }

  @Test
  public void testThrow() throws InterruptedException {
    Step stepline = createStepline(Arrays.asList(Step1.class, Step2.class, Step3.class));
    Packet p = new Packet();

    Map<Class<? extends BaseStep>, Command> commandMap = new HashMap<>();
    commandMap.put(Step2.class, new Command(THROW));
    p.put(NA, commandMap);

    Semaphore signal = new Semaphore(0);
    List<Step> called = new ArrayList<>();
    List<Throwable> throwables = new ArrayList<>();

    engine
        .createFiber()
        .start(
            stepline,
            p,
            new CompletionCallback() {
              @SuppressWarnings({"unchecked", "rawtypes"})
              @Override
              public void onCompletion(Packet packet) {
                List<Step> l = (List) packet.get(MARK);
                if (l != null) {
                  called.addAll(l);
                }
                signal.release();
              }

              @SuppressWarnings({"unchecked", "rawtypes"})
              @Override
              public void onThrowable(Packet packet, Throwable throwable) {
                List<Step> l = (List) packet.get(MARK);
                if (l != null) {
                  called.addAll(l);
                }
                throwables.add(throwable);
                signal.release();
              }
            });

    boolean result = signal.tryAcquire(5, TimeUnit.SECONDS);
    assertTrue(result);
    assertEquals(2, called.size());
    assertTrue(called.get(0) instanceof Step1);
    assertTrue(called.get(1) instanceof Step2);
    assertEquals(1, throwables.size());
    assertTrue(throwables.get(0) instanceof NullPointerException);
  }

  @Test
  public void testSuspendAndThrow() throws InterruptedException {
    Step stepline = createStepline(Arrays.asList(Step1.class, Step2.class, Step3.class));
    Packet p = new Packet();

    Map<Class<? extends BaseStep>, Command> commandMap = new HashMap<>();
    commandMap.put(
        Step2.class, new Command(RELEASE_SEMAPHORE, ACQUIRE_SEMAPHORE, SUSPEND_AND_THROW));
    p.put(NA, commandMap);

    Semaphore releaseSemaphore = new Semaphore(0);
    Semaphore acquireSemaphore = new Semaphore(0);
    p.put(Step2.class.getName() + RELEASE_SEMAPHORE_SUFFIX, releaseSemaphore);
    p.put(Step2.class.getName() + ACQUIRE_SEMAPHORE_SUFFIX, acquireSemaphore);

    Semaphore signal = new Semaphore(0);
    List<Throwable> throwables = new ArrayList<>();

    engine
        .createFiber()
        .start(
            stepline,
            p,
            new CompletionCallback() {
              @Override
              public void onCompletion(Packet packet) {
                signal.release();
              }

              @Override
              public void onThrowable(Packet packet, Throwable throwable) {
                throwables.add(throwable);
                signal.release();
              }
            });

    releaseSemaphore.acquire();
    // Fiber is inside Step2
    acquireSemaphore.release();

    @SuppressWarnings({"unchecked", "rawtypes"})
    List<Step> called = (List) p.get(MARK);

    boolean result = signal.tryAcquire(1, TimeUnit.SECONDS);
    assertFalse(result);
    assertEquals(2, called.size());
    assertTrue(called.get(0) instanceof Step1);
    assertTrue(called.get(1) instanceof Step2);
    // assertEquals(1, throwables.size());
    // assertTrue(throwables.get(0) instanceof NullPointerException);
  }

  @Test
  public void testMany() throws InterruptedException {
    Step stepline = createStepline(Arrays.asList(Step1.class, Step2.class, Step3.class));

    List<Semaphore> sems = new ArrayList<>();
    List<List<Step>> calls = new ArrayList<>();
    List<List<Throwable>> ts = new ArrayList<>();

    for (int i = 0; i < 1000; i++) {
      Packet p = new Packet();

      Semaphore signal = new Semaphore(0);
      List<Step> called = new ArrayList<>();
      List<Throwable> throwables = new ArrayList<>();

      sems.add(signal);
      calls.add(called);
      ts.add(throwables);

      engine
          .createFiber()
          .start(
              stepline,
              p,
              new CompletionCallback() {
                @SuppressWarnings({"unchecked", "rawtypes"})
                @Override
                public void onCompletion(Packet packet) {
                  List<Step> l = (List) packet.get(MARK);
                  if (l != null) {
                    called.addAll(l);
                  }
                  signal.release();
                }

                @SuppressWarnings({"unchecked", "rawtypes"})
                @Override
                public void onThrowable(Packet packet, Throwable throwable) {
                  List<Step> l = (List) packet.get(MARK);
                  if (l != null) {
                    called.addAll(l);
                  }
                  throwables.add(throwable);
                  signal.release();
                }
              });
    }

    for (int i = 0; i < 1000; i++) {
      Semaphore signal = sems.get(i);
      List<Step> called = calls.get(i);
      List<Throwable> throwables = ts.get(i);

      boolean result = signal.tryAcquire(5, TimeUnit.SECONDS);
      assertTrue(result);
      assertEquals(3, called.size());
      assertTrue(called.get(0) instanceof Step1);
      assertTrue(called.get(1) instanceof Step2);
      assertTrue(called.get(2) instanceof Step3);
      assertTrue(throwables.isEmpty());
    }
  }

  @Test
  public void testFuture() throws InterruptedException, ExecutionException, TimeoutException {
    Step stepline = createStepline(Arrays.asList(Step1.class, Step2.class, Step3.class));
    Packet p = new Packet();

    List<Step> called = new ArrayList<>();
    List<Throwable> throwables = new ArrayList<>();

    Fiber f = engine.createFiber();
    f.start(
        stepline,
        p,
        new CompletionCallback() {
          @SuppressWarnings({"unchecked", "rawtypes"})
          @Override
          public void onCompletion(Packet packet) {
            List<Step> l = (List) packet.get(MARK);
            if (l != null) {
              called.addAll(l);
            }
          }

          @SuppressWarnings({"unchecked", "rawtypes"})
          @Override
          public void onThrowable(Packet packet, Throwable throwable) {
            List<Step> l = (List) packet.get(MARK);
            if (l != null) {
              called.addAll(l);
            }
            throwables.add(throwable);
          }
        });

    f.get(5, TimeUnit.SECONDS);

    assertEquals(3, called.size());
    assertTrue(called.get(0) instanceof Step1);
    assertTrue(called.get(1) instanceof Step2);
    assertTrue(called.get(2) instanceof Step3);
    assertTrue(throwables.isEmpty());
  }

  @Test
  public void testCancel() throws InterruptedException {
    Step stepline = createStepline(Arrays.asList(Step1.class, Step2.class, Step3.class));
    Packet p = new Packet();

    Map<Class<? extends BaseStep>, Command> commandMap = new HashMap<>();
    commandMap.put(Step2.class, new Command(RELEASE_SEMAPHORE, ACQUIRE_SEMAPHORE, INVOKE_NEXT));
    p.put(NA, commandMap);

    Semaphore releaseSemaphore = new Semaphore(0);
    Semaphore acquireSemaphore = new Semaphore(0);
    p.put(Step2.class.getName() + RELEASE_SEMAPHORE_SUFFIX, releaseSemaphore);
    p.put(Step2.class.getName() + ACQUIRE_SEMAPHORE_SUFFIX, acquireSemaphore);

    Semaphore signal = new Semaphore(0);
    List<Throwable> throwables = new ArrayList<>();

    Fiber f = engine.createFiber();
    f.start(
        stepline,
        p,
        new CompletionCallback() {
          @Override
          public void onCompletion(Packet packet) {
            signal.release();
          }

          @Override
          public void onThrowable(Packet packet, Throwable throwable) {
            throwables.add(throwable);
            signal.release();
          }
        });

    releaseSemaphore.acquire();
    // Fiber is inside Step2
    f.cancel(false);
    acquireSemaphore.release();

    @SuppressWarnings({"unchecked", "rawtypes"})
    List<Step> called = (List) p.get(MARK);

    boolean result = signal.tryAcquire(1, TimeUnit.SECONDS);
    assertFalse(result);
    assertTrue(f.isCancelled());
    assertFalse(f.isDone());
    assertEquals(2, called.size());
    assertTrue(called.get(0) instanceof Step1);
    assertTrue(called.get(1) instanceof Step2);
    assertTrue(throwables.isEmpty());
  }

  private abstract static class BaseStep extends Step {
    public BaseStep(Step next) {
      super(next);
    }

    @Override
    public final NextAction apply(Packet packet) {
      mark(packet);
      Command command = DEFAULT_COMMAND;
      @SuppressWarnings({"unchecked", "rawtypes"})
      Map<Class<? extends BaseStep>, Command> commandMap = (Map) packet.get(NA);
      if (commandMap != null) {
        Command c = commandMap.get(getClass());
        if (c != null) {
          command = c;
        }
      }

      for (int kind : command.kind) {
        switch (kind) {
          case RETRY:
            if (command.count-- > 0) {
              return doRetry(packet, 1, TimeUnit.SECONDS);
            }
            // fall through
          case INVOKE_NEXT:
            return doNext(packet);
          case SUSPEND_AND_RESUME:
            return doSuspend(
                (fiber) -> {
                  fiber.resume(packet);
                });
          case SUSPEND_AND_THROW:
            return doSuspend(
                (fiber) -> {
                  throw new NullPointerException("Thrown from suspend callback of " + getClass());
                });
          case THROW:
            throw new NullPointerException("Thrown from " + getClass());
          case ACQUIRE_SEMAPHORE:
            Semaphore as = (Semaphore) packet.get(getClass().getName() + ACQUIRE_SEMAPHORE_SUFFIX);
            try {
              as.acquire();
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
            break;
          case RELEASE_SEMAPHORE:
            Semaphore rs = (Semaphore) packet.get(getClass().getName() + RELEASE_SEMAPHORE_SUFFIX);
            rs.release();
            break;
          default:
            throw new RuntimeException();
        }
      }

      throw new RuntimeException();
    }

    private void mark(Packet packet) {
      @SuppressWarnings({"rawtypes", "unchecked"})
      List<Step> called = (List) packet.get(MARK);
      if (called == null) {
        called = new ArrayList<>();
        packet.put(MARK, called);
      }
      called.add(this);
    }
  }

  private static class Command {
    private int[] kind;
    private int count;

    public Command(int... kind) {
      this.kind = kind;
      this.count = 0;
    }

    public void setCount(int count) {
      this.count = count;
    }
  }

  private static class Step1 extends BaseStep {
    public Step1(Step next) {
      super(next);
    }
  }

  private static class Step2 extends BaseStep {
    public Step2(Step next) {
      super(next);
    }
  }

  private static class Step3 extends BaseStep {
    public Step3(Step next) {
      super(next);
    }
  }
}
