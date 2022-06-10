// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.operator.processing.EffectiveAdminServerSpec;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class EffectiveAdminServerSpecCommonImpl extends EffectiveServerSpecCommonImpl
        implements EffectiveAdminServerSpec {
  private final AdminServer adminServer;

  EffectiveAdminServerSpecCommonImpl(DomainSpec spec, AdminServer server) {
    super(spec, server, null, null);
    adminServer = server;
  }

  @Override
  public AdminService getAdminService() {
    return adminServer.getAdminService();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("adminServer", adminServer)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof EffectiveAdminServerSpecCommonImpl)) {
      return false;
    }

    EffectiveAdminServerSpecCommonImpl that = (EffectiveAdminServerSpecCommonImpl) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(adminServer, that.adminServer)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(adminServer)
        .toHashCode();
  }
}
