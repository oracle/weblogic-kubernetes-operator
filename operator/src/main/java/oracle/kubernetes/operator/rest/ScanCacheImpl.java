// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class ScanCacheImpl implements ScanCache {
  static final ScanCache INSTANCE = new ScanCacheImpl();

  private ScanCacheImpl() {}

  private final ConcurrentMap<String, ConcurrentMap<String, Scan>> map = new ConcurrentHashMap<>();

  @Override
  public void registerScan(String ns, String domainUID, Scan domainScan) {
    map.computeIfAbsent(ns, k -> new ConcurrentHashMap<>())
        .compute(domainUID, (k, current) -> domainScan);
  }

  @Override
  public Scan lookupScan(String ns, String domainUID) {
    ConcurrentMap<String, Scan> m = map.get(ns);
    return m != null ? m.get(domainUID) : null;
  }
}
