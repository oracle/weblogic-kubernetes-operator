// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

public interface ScanCache {
  public static ScanCache INSTANCE = ScanCacheImpl.INSTANCE;

  public void registerScan(String ns, String domainUid, Scan domainScan);

  public Scan lookupScan(String ns, String domainUid);
}
