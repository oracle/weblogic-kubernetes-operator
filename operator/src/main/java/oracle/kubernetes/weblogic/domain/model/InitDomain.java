// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class InitDomain {

  @Description("create domain mode.")
  private String createMode;

  @Description("Type of the domain.")
  private String domainType;

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

  public String getCreateMode() {
    return createMode;
  }

  public void setCreateMode(String createMode) {
    this.createMode = createMode;
  }

  public String getDomainType() {
    return domainType;
  }

  public void setDomainType(String domainType) {
    this.domainType = domainType;
  }

  public List<AuxiliaryImage> getWdtImages() {
    return wdtImages;
  }

  public void setWdtImages(List<AuxiliaryImage> wdtImages) {
    this.wdtImages = wdtImages;
  }

  public String getWdtConfigMap() {
    return wdtConfigMap;
  }

  public void setWdtConfigMap(String wdtConfigMap) {
    this.wdtConfigMap = wdtConfigMap;
  }

  public Opss getOpss() {
    return opss;
  }

  public void setOpss(Opss opss) {
    this.opss = opss;
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
