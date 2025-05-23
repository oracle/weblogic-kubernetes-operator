// Copyright (c) 2021, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.Optional;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel("Optionally, use auxiliary images to provide Model in Image model, application archive, and WebLogic Deploy "
        + "Tooling files. This is a useful alternative for providing these files without requiring modifications "
        + "to the pod's base image `domain.spec.image`. "
        + "This feature internally uses a Kubernetes emptyDir volume and Kubernetes init containers to share "
        + "the files from the additional images with the pod.")
public class AuxiliaryImage {

  public static final String AUXILIARY_IMAGE_DEFAULT_SOURCE_WDT_INSTALL_HOME = "/auxiliary/weblogic-deploy";
  public static final String AUXILIARY_IMAGE_DEFAULT_SOURCE_MODEL_HOME = "/auxiliary/models";

  /**
   * The auxiliary image.
   */
  @ApiModelProperty("The auxiliary image containing Model in Image model files, application archive files, and/or "
          + "WebLogic Deploying Tooling installation files. Required.")
  private String image;

  @ApiModelProperty(
          "The image pull policy for the container image. "
                  + "Legal values are Always, Never, and IfNotPresent. "
                  + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  private String imagePullPolicy;

  @ApiModelProperty("The source location of the WebLogic Deploy Tooling installation within the auxiliary image "
          + "that will be made available in the `/aux/weblogic-deploy` directory of the WebLogic Server container in "
          + "all pods. Defaults to `/auxiliary/weblogic-deploy`. If the value is set to `None` or no files are found "
          + "at the default location, then the source directory is ignored. When specifying multiple auxiliary images, "
          + "ensure that only one of the images supplies a WDT install home; if more than one WDT install home is "
          + "provided, then the domain deployment will fail.")
  private String sourceWDTInstallHome;

  @ApiModelProperty("The source location of the WebLogic Deploy Tooling model home within the auxiliary image that "
          + "will be made available in the `/aux/models` directory of the WebLogic Server container in all pods. "
          + "Defaults to `/auxiliary/models`. If the value is set to `None` or no files are found at the default "
          + "location, then the source directory is ignored. If specifying multiple auxiliary images with model files "
          + "in their respective `sourceModelHome` directories, then model files are merged.")
  private String sourceModelHome;

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public AuxiliaryImage image(String image) {
    this.image = image;
    return this;
  }

  public String getImagePullPolicy() {
    return imagePullPolicy;
  }

  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  public AuxiliaryImage imagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  public String getSourceWDTInstallHome() {
    return Optional.ofNullable(sourceWDTInstallHome)
            .orElse(AUXILIARY_IMAGE_DEFAULT_SOURCE_WDT_INSTALL_HOME);
  }

  public void setSourceWDTInstallHome(String sourceWDTInstallHome) {
    this.sourceWDTInstallHome = sourceWDTInstallHome;
  }

  public AuxiliaryImage sourceWDTInstallHome(String sourceWDTInstallHome) {
    this.sourceWDTInstallHome = sourceWDTInstallHome;
    return this;
  }

  public String getSourceModelHome() {
    return Optional.ofNullable(sourceModelHome)
            .orElse(AUXILIARY_IMAGE_DEFAULT_SOURCE_MODEL_HOME);
  }

  public void setSourceModelHome(String sourceModelHome) {
    this.sourceModelHome = sourceModelHome;
  }

  public AuxiliaryImage sourceModelHome(String sourceModelHome) {
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
    if (!(other instanceof AuxiliaryImage)) {
      return false;
    }

    AuxiliaryImage rhs = ((AuxiliaryImage) other);
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
