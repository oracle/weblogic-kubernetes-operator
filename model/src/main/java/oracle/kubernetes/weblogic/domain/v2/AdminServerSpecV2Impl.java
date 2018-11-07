// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import oracle.kubernetes.weblogic.domain.v1.DomainSpec;

public class AdminServerSpecV2Impl extends ServerSpecV2Impl {
  public AdminServerSpecV2Impl(DomainSpec spec, AdminServer server) {
    super(spec, server, null, server);
  }
}
