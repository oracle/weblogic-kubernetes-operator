// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

public class AdminServerSpecCommonImpl extends ServerSpecCommonImpl implements AdminServerSpec {
  private final AdminServer adminServer;

  AdminServerSpecCommonImpl(DomainSpec spec, AdminServer server) {
    super(spec, server, null, null);
    adminServer = server;
  }

  @Override
  public AdminService getAdminService() {
    return adminServer.getAdminService();
  }
}
