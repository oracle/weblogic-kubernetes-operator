// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.time.OffsetDateTime;

import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;

public class Scan {
  public final WlsDomainConfig domainConfig;
  public final OffsetDateTime lastScanTime;

  public Scan(WlsDomainConfig domainConfig, OffsetDateTime lastScanTime) {
    this.domainConfig = domainConfig;
    this.lastScanTime = lastScanTime;
  }

  public WlsDomainConfig getWlsDomainConfig() {
    return domainConfig;
  }

}
