// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class AdminServer extends Server {

  public static final AdminServer NULL_ADMIN_SERVER = new AdminServer();

  /**
   * List of T3 network access points to export, along with label and annotations to apply to
   * corresponding channel services.
   *
   * @since 2.0
   */
  @Description("T3 network access points to export")
  private Map<String, ExportedNetworkAccessPoint> exportedNetworkAccessPoints = new HashMap<>();

  @Description("")
  private AdminService adminService;

  /**
   * Configures an exported T3 network access point.
   *
   * @param name the name of the NAP
   */
  ExportedNetworkAccessPoint addExportedNetworkAccessPoint(String name) {
    if (exportedNetworkAccessPoints == null) exportedNetworkAccessPoints = new HashMap<>();

    ExportedNetworkAccessPoint exportedNetworkAccessPoint = new ExportedNetworkAccessPoint();
    exportedNetworkAccessPoints.put(name, exportedNetworkAccessPoint);
    return exportedNetworkAccessPoint;
  }

  public List<String> getExportedNetworkAccessPointNames() {
    return new ArrayList<>(exportedNetworkAccessPoints.keySet());
  }

  public ExportedNetworkAccessPoint getExportedNetworkAccessPoint(String napName) {
    return exportedNetworkAccessPoints.get(napName);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("exportedNetworkAccessPoints", exportedNetworkAccessPoints)
        .append("adminService", adminService)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    AdminServer that = (AdminServer) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(exportedNetworkAccessPoints, that.exportedNetworkAccessPoints)
        .append(adminService, that.adminService)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(exportedNetworkAccessPoints)
        .append(adminService)
        .toHashCode();
  }

  public AdminService getAdminService() {
    return adminService;
  }

  public void setAdminService(AdminService adminService) {
    this.adminService = adminService;
  }
}
