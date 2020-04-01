// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "AdminServer represents the operator configuration for the Administration Server.")
public class AdminServer extends Server {

  @ApiModelProperty(
      "Configures which of the Administration Server's WebLogic admin channels should be exposed outside"
          + " the Kubernetes cluster via a node port service.")
  private AdminService adminService;

  public AdminServer adminService(AdminService adminService) {
    this.adminService = adminService;
    return this;
  }

  public AdminService getAdminService() {
    return adminService;
  }

  public void setAdminService(AdminService adminService) {
    this.adminService = adminService;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("adminService", adminService)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AdminServer that = (AdminServer) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(adminService, that.adminService)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(adminService)
        .toHashCode();
  }

}
