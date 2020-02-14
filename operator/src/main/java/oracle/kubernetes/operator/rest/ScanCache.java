// Copyright (c) 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

public interface ScanCache {
  public static final ScanCache INSTANCE = ScanCacheImpl.INSTANCE;

  public void registerScan(String ns, String domainUid, Scan domainScan);

  public Scan lookupScan(String ns, String domainUid);
}
