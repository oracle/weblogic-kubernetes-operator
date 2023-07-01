// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Default;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.operator.WebLogicConstants.JRF;
import static oracle.kubernetes.operator.WebLogicConstants.RESTRICTED_JRF;
import static oracle.kubernetes.operator.WebLogicConstants.WLS;

public class DomainOnPV {

  @Description("Specifies if the operator should create only the domain or the domain with RCU (for JRF-based domains)."
      + " Legal values: Domain, DomainAndRCU. Defaults to Domain.")
  @Default(strDefault = "Domain")
  private CreateIfNotExists createIfNotExists = CreateIfNotExists.DOMAIN;

  @Description("WebLogic Deploy Tooling domain type. Known values are: WLS, RestrictedJRF, JRF. Defaults to JRF.")
  @SerializedName("domainType")
  @Default(strDefault = "JRF")
  private String domainType = JRF;

  /**
   * The domain images.
   *
   */
  @Description("Domain creation images containing WebLogic Deploy Tooling model, application archive, and WebLogic"
      + " Deploy Tooling installation files."
      + " These files will be used to create the domain during introspection. This feature"
      + " internally uses a Kubernetes emptyDir volume and Kubernetes init containers to share"
      + " the files from the additional images ")
  private List<DomainCreationImage> domainCreationImages;

  @Description("Name of a ConfigMap containing the WebLogic Deploy Tooling model.")
  private String domainCreationConfigMap;

  @Description("Settings for OPSS security.")
  private Opss opss;

  public CreateIfNotExists getCreateIfNotExists() {
    return createIfNotExists;
  }

  public DomainOnPV createMode(CreateIfNotExists createIfNotExists) {
    this.createIfNotExists = createIfNotExists;
    return this;
  }

  /**
   * Returns the domain type in upper case for WLS and JRF domains, camel case for RestrictedJRF domain,
   * and user provided value otherwise.
   *
   * @return the domain type in proper case.
   */
  public String getDomainType() {
    if (JRF.equalsIgnoreCase(domainType)) {
      return JRF;
    } else if (WLS.equalsIgnoreCase(domainType)) {
      return WLS;
    } else if (RESTRICTED_JRF.equalsIgnoreCase(domainType)) {
      return RESTRICTED_JRF;
    }
    return domainType;
  }

  public DomainOnPV domainType(String domainType) {
    this.domainType = domainType;
    return this;
  }

  public List<DomainCreationImage> getDomainCreationImages() {
    return domainCreationImages;
  }

  public DomainOnPV domainCreationImages(List<DomainCreationImage> domainCreationImages) {
    this.domainCreationImages = domainCreationImages;
    return this;
  }

  public String getDomainCreationConfigMap() {
    return domainCreationConfigMap;
  }

  public DomainOnPV domainCreationConfigMap(String domainCreationConfigMap) {
    this.domainCreationConfigMap = domainCreationConfigMap;
    return this;
  }

  public Opss getOpss() {
    return opss;
  }

  public DomainOnPV opss(Opss opss) {
    this.opss = opss;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("createMode", createIfNotExists)
            .append("domainType", domainType)
            .append("domainCreationImages", domainCreationImages)
            .append("domainCreationConfigMap", domainCreationConfigMap)
            .append("opss", opss);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(createIfNotExists)
        .append(domainType)
        .append(domainCreationImages)
        .append(domainCreationConfigMap)
        .append(opss);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof DomainOnPV)) {
      return false;
    }

    DomainOnPV rhs = ((DomainOnPV) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(createIfNotExists, rhs.createIfNotExists)
            .append(opss, rhs.opss)
            .append(domainType, rhs.domainType)
            .append(domainCreationConfigMap, rhs.domainCreationConfigMap)
            .append(opss, rhs.opss);

    return builder.isEquals();
  }
}
