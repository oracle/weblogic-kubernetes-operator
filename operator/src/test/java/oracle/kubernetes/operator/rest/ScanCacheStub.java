// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;

/**
 * Installs a per-test instance into ScanCache.INSTANCE to prevent state from spilling over from one test to another.
 */
public class ScanCacheStub {

  public static Memento install() throws NoSuchFieldException {
    return StaticStubSupport.install(ScanCache.class, "INSTANCE", new ScanCacheImpl());
  }
}
