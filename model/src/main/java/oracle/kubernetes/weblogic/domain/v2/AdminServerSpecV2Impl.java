// Copyright 2018,2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

public class AdminServerSpecV2Impl extends ServerSpecV2Impl implements AdminServerSpec {
  private final AdminServer adminServer;

  AdminServerSpecV2Impl(DomainSpec spec, AdminServer server) {
    super(spec, server, null, null);
    adminServer = server;
  }

  @Override
  public AdminService getAdminService() {
    return adminServer.getAdminService();
  }
}
