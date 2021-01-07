// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

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
