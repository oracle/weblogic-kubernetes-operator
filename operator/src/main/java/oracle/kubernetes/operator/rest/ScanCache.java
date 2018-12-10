// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

public interface ScanCache {
  public static final ScanCache INSTANCE = ScanCacheImpl.INSTANCE;

  public void registerScan(String ns, String domainUID, Scan domainScan);

  public Scan lookupScan(String ns, String domainUID);
}
