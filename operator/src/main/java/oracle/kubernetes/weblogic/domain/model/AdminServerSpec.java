// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

/** Represents the effective configuration for an admin server, as seen by the operator runtime. */
public interface AdminServerSpec extends ServerSpec {

  /**
   * Returns the admin service configuration for the admin server, which controls the external
   * channel service.
   *
   * @return the admin service configuration
   */
  AdminService getAdminService();
}
