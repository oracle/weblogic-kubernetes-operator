// Copyright (c) 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class ScanCacheImpl implements ScanCache {
  static final ScanCache INSTANCE = new ScanCacheImpl();
  private final ConcurrentMap<String, ConcurrentMap<String, Scan>> map = new ConcurrentHashMap<>();

  private ScanCacheImpl() {
  }

  @Override
  public void registerScan(String ns, String domainUid, Scan domainScan) {
    map.computeIfAbsent(ns, k -> new ConcurrentHashMap<>())
        .compute(domainUid, (k, current) -> domainScan);
  }

  @Override
  public Scan lookupScan(String ns, String domainUid) {
    ConcurrentMap<String, Scan> m = map.get(ns);
    return m != null ? m.get(domainUid) : null;
  }
}
