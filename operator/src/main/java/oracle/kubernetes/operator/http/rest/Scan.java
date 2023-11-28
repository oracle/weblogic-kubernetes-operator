// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest;

import java.time.OffsetDateTime;

import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;

public record Scan(WlsDomainConfig domainConfig, OffsetDateTime lastScanTime) {

  public WlsDomainConfig getWlsDomainConfig() {
    return domainConfig;
  }

}
