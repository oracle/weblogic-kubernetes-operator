// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.DomainType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class InitDomain {

  @Description("create domain mode.")
  private CreateMode createMode = CreateMode.CREATE_DOMAIN_IF_NOT_EXISTS;

  @Description("Type of the domain.")
  private DomainType domainType = DomainType.JRF;

  /**
   * The auxiliary images.
   *
   */
  @Description("Optionally, use auxiliary images to provide Model in Image model, application archive, and WebLogic "
      + "Deploy Tooling files. This is a useful alternative for providing these files without requiring "
      + "modifications to the pod's base image `domain.spec.image`. "
      + "This feature internally uses a Kubernetes emptyDir volume and Kubernetes init containers to share "
      + "the files from the additional images with the pod.")
  private List<AuxiliaryImage> wdtImages;

  @Description("Name of a ConfigMap containing the WebLogic Deploy Tooling model.")
  private String wdtConfigMap;

  @Description("Settings for OPSS security.")
  private Opss opss;

  public CreateMode getCreateMode() {
    return createMode;
  }

  public InitDomain createMode(CreateMode createMode) {
    this.createMode = createMode;
    return this;
  }

  public DomainType getDomainType() {
    return domainType;
  }

  public InitDomain domainType(DomainType domainType) {
    this.domainType = domainType;
    return this;
  }

  public List<AuxiliaryImage> getWdtImages() {
    return wdtImages;
  }

  public InitDomain wdtImages(List<AuxiliaryImage> wdtImages) {
    this.wdtImages = wdtImages;
    return this;
  }

  public String getWdtConfigMap() {
    return wdtConfigMap;
  }

  public InitDomain wdtConfigMap(String wdtConfigMap) {
    this.wdtConfigMap = wdtConfigMap;
    return this;
  }

  public Opss getOpss() {
    return opss;
  }

  public InitDomain opss(Opss opss) {
    this.opss = opss;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("createMode", createMode)
            .append("domainType", domainType)
            .append("wdtImages", wdtImages)
            .append("wdtConfigMap", wdtConfigMap)
            .append("opss", opss);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(createMode)
        .append(domainType)
        .append(wdtImages)
        .append(wdtConfigMap)
        .append(opss);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof InitDomain)) {
      return false;
    }

    InitDomain rhs = ((InitDomain) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(createMode, rhs.createMode)
            .append(opss, rhs.opss)
            .append(domainType, rhs.domainType)
            .append(wdtConfigMap, rhs.wdtConfigMap)
            .append(opss, rhs.opss);

    return builder.isEquals();
  }
}
