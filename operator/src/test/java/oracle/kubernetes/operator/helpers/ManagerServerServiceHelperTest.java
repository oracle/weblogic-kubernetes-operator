// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_REPLACED;

public class ManagerServerServiceHelperTest extends ServiceHelperTest {

  public ManagerServerServiceHelperTest() {
    super(new ManagedServerTestFacade());
  }

  static class ManagedServerTestFacade extends ServiceHelperTest.ServerTestFacade {
    @Override
    public String getServiceCreateLogMessage() {
      return MANAGED_SERVICE_CREATED;
    }

    @Override
    public String getServiceExistsLogMessage() {
      return MANAGED_SERVICE_EXISTS;
    }

    @Override
    public String getServiceReplacedLogMessage() {
      return MANAGED_SERVICE_REPLACED;
    }

    @Override
    public Integer getExpectedListenPort() {
      return getTestPort();
    }

    @Override
    public Integer getExpectedAdminPort() {
      return getAdminPort();
    }

    @Override
    public String getServerName() {
      return getManagedServerName();
    }
  }

}
