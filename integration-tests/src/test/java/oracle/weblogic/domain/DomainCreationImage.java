// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class DomainCreationImage {

  /**
   * The domain image.
   */
  @ApiModelProperty("The domain creation image containing model files, application archive files, and/or WebLogic "
          + "Deploying Tooling installation files to create the domain in persistent volume. Required.")
  private String image;

  @ApiModelProperty("The image pull policy for the container image. "
      + "Legal values are Always, Never, and IfNotPresent. "
      + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  private String imagePullPolicy;

  @ApiModelProperty("The source location of the WebLogic Deploy Tooling installation within the domain creation image. "
          + "Defaults to `/auxiliary/weblogic-deploy`. If the value is set to `None` or no files are found at "
          + "the default location, then the source directory is ignored. When specifying multiple domain images, "
          + "ensure that only one of the images supplies a WDT install home; if more than one WDT install home is "
          + "provided, then the domain deployment will fail.")
  private String sourceWDTInstallHome;

  @ApiModelProperty("The source location of the WebLogic Deploy Tooling model home within the domain image. "
          + "Defaults to `/auxiliary/models`. If the value is set to `None` or no files are found at the default "
          + "location, then the source directory is ignored. If specifying multiple domain images with model files "
          + "in their respective `sourceModelHome` directories, then model files are merged.")
  private String sourceModelHome;

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public DomainCreationImage image(String image) {
    this.image = image;
    return this;
  }

  public String getImagePullPolicy() {
    return imagePullPolicy;
  }

  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  public DomainCreationImage imagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  public String getSourceWDTInstallHome() {
    return sourceWDTInstallHome;
  }

  public DomainCreationImage sourceWDTInstallHome(String sourceWDTInstallHome) {
    this.sourceWDTInstallHome = sourceWDTInstallHome;
    return this;
  }

  public String getSourceModelHome() {
    return sourceModelHome;
  }

  public void setSourceModelHome(String sourceModelHome) {
    this.sourceModelHome = sourceModelHome;
  }

  public DomainCreationImage sourceModelHome(String sourceModelHome) {
    this.sourceModelHome = sourceModelHome;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
            new ToStringBuilder(this)
                    .append("image", image)
                    .append("imagePullPolicy", imagePullPolicy)
                    .append("sourceWDTInstallHome", sourceWDTInstallHome)
                    .append("sourceModelHome", sourceModelHome);
    return builder.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof DomainCreationImage)) {
      return false;
    }

    DomainCreationImage rhs = ((DomainCreationImage) other);
    EqualsBuilder builder =
            new EqualsBuilder()
                    .append(image, rhs.image)
                    .append(imagePullPolicy, rhs.imagePullPolicy)
                    .append(sourceWDTInstallHome, rhs.sourceWDTInstallHome)
                    .append(sourceModelHome, rhs.sourceModelHome);

    return builder.isEquals();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
            .append(image)
            .append(imagePullPolicy)
            .append(sourceWDTInstallHome)
            .append(sourceModelHome);
    return builder.toHashCode();
  }
}
