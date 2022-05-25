// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

public interface ScanCache {
  ScanCache INSTANCE = ScanCacheImpl.INSTANCE;

  void registerScan(String ns, String domainUid, Scan domainScan);

  Scan lookupScan(String ns, String domainUid);
}
