// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_REPLACED;

public class AdminServerServiceHelperTest extends ServiceHelperTest {

  public AdminServerServiceHelperTest() {
    super(new AdminServerTestFacade());
  }

  static class AdminServerTestFacade extends ServiceHelperTest.ServerTestFacade {
    @Override
    public String getServiceCreateLogMessage() {
      return ADMIN_SERVICE_CREATED;
    }

    @Override
    public String getServiceExistsLogMessage() {
      return ADMIN_SERVICE_EXISTS;
    }

    @Override
    public String getServiceReplacedLogMessage() {
      return ADMIN_SERVICE_REPLACED;
    }

    @Override
    public Integer getExpectedListenPort() {
      return getAdminPort();
    }

    @Override
    public String getServerName() {
      return getAdminServerName();
    }
  }

}
