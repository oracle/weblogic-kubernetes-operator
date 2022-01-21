// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Model {

  public static final String DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH = "/aux";

  @ApiModelProperty(
      value = "WDT domain type: Legal values: WLS, RestrictedJRF, JRF. Defaults to WLS.",
      allowableValues = "WLS, RestrictedJRF, JRF")
  private String domainType;

  @ApiModelProperty("WDT config map name.")
  private String configMap;

  @ApiModelProperty("Location of the WebLogic Deploy Tooling model home. Defaults to `/u01/wdt/models` if no "
          + "`spec.configuration.model.AuxiliaryImages` are specified, and to `/aux/models` otherwise.")
  private String modelHome;

  @ApiModelProperty("Location of the WebLogic Deploy Tooling installation. Defaults to `/u01/wdt/weblogic-deploy` if "
          + "no `spec.configuration.model.AuxiliaryImages` are specified, and to `/aux/weblogic-deploy` otherwise.")
  private String wdtInstallHome;

  @ApiModelProperty("Online update option for Model In Image dynamic update.")
  private OnlineUpdate onlineUpdate;

  @ApiModelProperty(
      "Runtime encryption secret. Required when domainHomeSourceType is set to FromModel.")
  private String runtimeEncryptionSecret;

  /**
   * The auxiliary images.
   *
   */
  @ApiModelProperty("Optionally, use auxiliary images to provide Model in Image model, application archive, and "
          + "WebLogic Deploy Tooling files. This is a useful alternative for providing these files without requiring "
          + "modifications to the pod's base image `domain.spec.image`. "
          + "This feature internally uses a Kubernetes emptyDir volume and Kubernetes init containers to share "
          + "the files from the additional images with the pod.")
  private List<AuxiliaryImage> auxiliaryImages;

  @ApiModelProperty("The auxiliary image volume mount path. This is an advanced setting that rarely needs to be "
          + "configured. Defaults to `/aux`, which means the emptyDir volume will be mounted at `/aux` path in the "
          + "WebLogic-Server container within every pod. The defaults for `modelHome` and `wdtInstallHome` will start "
          + "with the new mount path, and files from `sourceModelHome` and `sourceWDTInstallHome` will be copied to "
          + "the new default locations.")
  private String auxiliaryImageVolumeMountPath;

  @ApiModelProperty("The emptyDir volume withAuxiliaryImageVolumeMedium. This is an advanced setting that rarely "
          + "needs to be configured. Defaults to unset, which means the volume's files are stored on the local node's "
          + "file system for the life of the pod.")
  private String auxiliaryImageVolumeMedium;

  @ApiModelProperty("The emptyDir volume size limit. This is an advanced setting that rarely needs to be configured. "
          + "Defaults to unset.")
  private String auxiliaryImageVolumeSizeLimit;

  public Model domainType(String domainType) {
    this.domainType = domainType;
    return this;
  }

  public String domainType() {
    return domainType;
  }

  public String getDomainType() {
    return domainType;
  }

  public void setDomainType(String domainType) {
    this.domainType = domainType;
  }

  public Model configMap(String configMap) {
    this.configMap = configMap;
    return this;
  }

  public String configMap() {
    return configMap;
  }

  public String getConfigMap() {
    return configMap;
  }

  public void setConfigMap(String configMap) {
    this.configMap = configMap;
  }

  public Model runtimeEncryptionSecret(String runtimeEncryptionSecret) {
    this.runtimeEncryptionSecret = runtimeEncryptionSecret;
    return this;
  }

  public String runtimeEncryptionSecret() {
    return runtimeEncryptionSecret;
  }

  public String getRuntimeEncryptionSecret() {
    return runtimeEncryptionSecret;
  }

  public void setRuntimeEncryptionSecret(String runtimeEncryptionSecret) {
    this.runtimeEncryptionSecret = runtimeEncryptionSecret;
  }

  String getModelHome() {
    return modelHome;
  }

  void setModelHome(String modelHome) {
    this.modelHome = modelHome;
  }

  public Model withModelHome(String modelHome) {
    this.modelHome = modelHome;
    return this;
  }

  String getWdtInstallHome() {
    return wdtInstallHome;
  }

  void setWdtInstallHome(String wdtInstallHome) {
    this.wdtInstallHome = wdtInstallHome;
  }

  public Model withWdtInstallHome(String wdtInstallHome) {
    this.wdtInstallHome = wdtInstallHome;
    return this;
  }

  public OnlineUpdate getOnlineUpdate() {
    return onlineUpdate;
  }

  public OnlineUpdate onlineUpdate() {
    return onlineUpdate;
  }

  public Model onlineUpdate(OnlineUpdate onlineUpdate) {
    this.onlineUpdate = onlineUpdate;
    return this;
  }

  public void setOnlineUpdate(OnlineUpdate onlineUpdate) {
    this.onlineUpdate = onlineUpdate;
  }

  public List<AuxiliaryImage> getAuxiliaryImages() {
    return this.auxiliaryImages;
  }

  void setAuxiliaryImages(List<AuxiliaryImage> auxiliaryImages) {
    this.auxiliaryImages = auxiliaryImages;
  }

  public Model withAuxiliaryImages(@Nullable List<AuxiliaryImage> auxiliaryImages) {
    this.auxiliaryImages = auxiliaryImages;
    return this;
  }

  /**
  * Create the domain resource model with auxiliary image.
  *
  * @param auxiliaryImage Auxiliary image to be added
  * @return Model with auxiliary image
  */
  public Model withAuxiliaryImage(@Nullable AuxiliaryImage auxiliaryImage) {
    if (this.auxiliaryImages == null) {
      this.auxiliaryImages = new ArrayList<>();
    }
    this.auxiliaryImages.add(auxiliaryImage);
    return this;
  }

  public String getAuxiliaryImageVolumeMountPath() {
    return Optional.ofNullable(auxiliaryImageVolumeMountPath).orElse(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH);
  }

  public void setAuxiliaryImageVolumeMountPath(String auxiliaryImageVolumeMountPath) {
    this.auxiliaryImageVolumeMountPath = auxiliaryImageVolumeMountPath;
  }

  public Model withAuxiliaryImageVolumeMountPath(@Nullable String auxiliaryImageVolumeMountPath) {
    this.auxiliaryImageVolumeMountPath = auxiliaryImageVolumeMountPath;
    return this;
  }

  public String getAuxiliaryImageVolumeMedium() {
    return auxiliaryImageVolumeMedium;
  }

  public void setAuxiliaryImageVolumeMedium(String auxiliaryImageVolumeMedium) {
    this.auxiliaryImageVolumeMedium = auxiliaryImageVolumeMedium;
  }

  public Model withAuxiliaryImageVolumeMedium(@Nullable String auxiliaryImageVolumeMedium) {
    this.auxiliaryImageVolumeMedium = auxiliaryImageVolumeMedium;
    return this;
  }

  public String getAuxiliaryImageVolumeSizeLimit() {
    return auxiliaryImageVolumeSizeLimit;
  }

  public void setAuxiliaryImageVolumeSizeLimit(String auxiliaryImageVolumeSizeLimit) {
    this.auxiliaryImageVolumeSizeLimit = auxiliaryImageVolumeSizeLimit;
  }

  public Model withAuxiliaryImageVolumeSizeLimit(@Nullable String auxiliaryImageVolumeSizeLimit) {
    this.auxiliaryImageVolumeSizeLimit = auxiliaryImageVolumeSizeLimit;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("domainType", domainType)
            .append("configMap", configMap)
            .append("modelHome", modelHome)
            .append("wdtInstallHome", wdtInstallHome)
            .append("runtimeEncryptionSecret", runtimeEncryptionSecret)
            .append("onlineUpdate", onlineUpdate)
            .append("auxiliaryImages", auxiliaryImages)
            .append("auxiliaryImageVolumeMountPath", auxiliaryImageVolumeMountPath)
            .append("auxiliaryImageVolumeMedium", auxiliaryImageVolumeMedium)
            .append("auxiliaryImageVolumeSizeLimit", auxiliaryImageVolumeSizeLimit);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(domainType)
        .append(configMap)
        .append(modelHome)
        .append(wdtInstallHome)
        .append(onlineUpdate)
        .append(runtimeEncryptionSecret)
        .append(auxiliaryImages)
        .append(auxiliaryImageVolumeMountPath)
        .append(auxiliaryImageVolumeMedium)
        .append(auxiliaryImageVolumeSizeLimit);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    Model rhs = ((Model) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(domainType, rhs.domainType)
            .append(configMap, rhs.configMap)
            .append(modelHome,rhs.modelHome)
            .append(wdtInstallHome,rhs.wdtInstallHome)
            .append(runtimeEncryptionSecret, rhs.runtimeEncryptionSecret)
            .append(onlineUpdate,rhs.onlineUpdate)
            .append(auxiliaryImages, rhs.auxiliaryImages)
            .append(auxiliaryImageVolumeMountPath, rhs.auxiliaryImageVolumeMountPath)
            .append(auxiliaryImageVolumeMedium, rhs.auxiliaryImageVolumeMedium)
            .append(auxiliaryImageVolumeSizeLimit, rhs.auxiliaryImageVolumeSizeLimit);
    return builder.isEquals();
  }
}
