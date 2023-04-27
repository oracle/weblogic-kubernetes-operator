// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;
import oracle.kubernetes.common.utils.CommonUtils;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class AuxiliaryImage implements DeploymentImage {

  public static final String AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME = "aux-image-internal-volume";
  public static final String AUXILIARY_IMAGE_DEFAULT_SOURCE_WDT_INSTALL_HOME = "/auxiliary/weblogic-deploy";
  public static final String AUXILIARY_IMAGE_DEFAULT_SOURCE_MODEL_HOME = "/auxiliary/models";

  /**
   * The auxiliary image.
   */
  @Description("The auxiliary image containing Model in Image model files, application archive files, and/or WebLogic "
          + "Deploying Tooling installation files. Required.")
  @NotNull
  private String image;

  @Description("The image pull policy for the container image. "
      + "Legal values are Always, Never, and IfNotPresent. "
      + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  private String imagePullPolicy;

  @Description("The source location of the WebLogic Deploy Tooling installation within the auxiliary image that will "
          + "be made available in the `/aux/weblogic-deploy` directory of the WebLogic Server container in all pods. "
          + "Defaults to `/auxiliary/weblogic-deploy`. If the value is set to `None` or no files are found at "
          + "the default location, then the source directory is ignored. When specifying multiple auxiliary images, "
          + "ensure that only one of the images supplies a WDT install home; if more than one WDT install home is "
          + "provided, then the domain deployment will fail.")
  private String sourceWDTInstallHome;

  @Description("The source location of the WebLogic Deploy Tooling model home within the auxiliary image that will "
          + "be made available in the `/aux/models` directory of the WebLogic Server container in all pods. "
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
    return Optional.ofNullable(imagePullPolicy).orElse(CommonUtils.getInferredImagePullPolicy(getImage()));
  }

  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  public AuxiliaryImage imagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  public String getSourceWDTInstallHome() {
    return sourceWDTInstallHome;
  }

  public String getSourceWDTInstallHomeOrDefault() {
    return Optional.ofNullable(sourceWDTInstallHome)
            .orElse(AUXILIARY_IMAGE_DEFAULT_SOURCE_WDT_INSTALL_HOME);
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
