// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class InitPvDomain {

  @Description("Metadata and specs of the persistent volume to initialize.")
  InitPv initPv;

  @Description("Metadata and specs of the persistent volume claim to initialize.")
  InitPvc initPvc;

  @Description("Details of the PV domain to be initialized.")
  InitDomain initDomain;

  public InitPv getInitPv() {
    return initPv;
  }

  public void setInitPv(InitPv initPv) {
    this.initPv = initPv;
  }

  public InitPvc getInitPvc() {
    return initPvc;
  }

  public void setInitPvc(InitPvc initPvc) {
    this.initPvc = initPvc;
  }

  public InitDomain getInitDomain() {
    return initDomain;
  }

  public void setInitDomain(InitDomain initDomain) {
    this.initDomain = initDomain;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("initPv", initPv)
            .append("domainType", initPvc)
            .append("initDomain", initDomain);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(initPv)
        .append(initPvc)
        .append(initDomain);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof InitDomain)) {
      return false;
    }

    InitPvDomain rhs = ((InitPvDomain) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(initPv, rhs.initPv)
            .append(initPvc, rhs.initPvc)
            .append(initDomain, rhs.initDomain);

    return builder.isEquals();
  }
}
