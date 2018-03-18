package oracle.kubernetes.operator;

import static org.junit.Assert.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import oracle.kubernetes.operator.work.Engine;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ServerStatusReaderTest {

  @Test
  public void test() throws InterruptedException, ExecutionException, TimeoutException {
    Step strategy = ServerStatusReader.createServerStatusReaderStep("default", "domain1", "admin-server", 300, null);
    
    Engine engine = new Engine("ServerStatusReaderTest");
    Packet p = new Packet();

    AtomicReference<Throwable> t = new AtomicReference<>(null);
    ConcurrentMap<String, String> serverStateMap = new ConcurrentHashMap<>();
    p.put(ProcessingConstants.SERVER_STATE_MAP, serverStateMap);

    Fiber f = engine.createFiber();
    f.start(strategy, p, new CompletionCallback() {
      @Override
      public void onCompletion(Packet packet) {
      }

      @Override
      public void onThrowable(Packet packet, Throwable throwable) {
        t.set(throwable);
      }
    });
    
    f.get(60, TimeUnit.SECONDS);
    String state = serverStateMap.get("admin-server");
    
    assertEquals("RUNNING", state);
  }

}
