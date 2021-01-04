// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ThreadFactory;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;

/** A test implementation of the starter which does nothing, thus avoiding creating threads. */
public class NoopWatcherStarter implements WatcherStarter {

  public static Memento install() throws NoSuchFieldException {
    return StaticStubSupport.install(Watcher.class, "STARTER", new NoopWatcherStarter());
  }
  
  @Override
  public Thread startWatcher(ThreadFactory factory, Runnable watch) {
    return null;
  }
}
