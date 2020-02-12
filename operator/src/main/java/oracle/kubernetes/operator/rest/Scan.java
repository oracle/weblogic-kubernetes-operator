// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import org.joda.time.DateTime;

public class Scan {
  public final WlsDomainConfig domainConfig;
  public final DateTime lastScanTime;

  public Scan(WlsDomainConfig domainConfig, DateTime lastScanTime) {
    this.domainConfig = domainConfig;
    this.lastScanTime = lastScanTime;
  }

  public WlsDomainConfig getWlsDomainConfig() {
    return domainConfig;
  }

  public DateTime getLastScanTime() {
    return lastScanTime;
  }
}
