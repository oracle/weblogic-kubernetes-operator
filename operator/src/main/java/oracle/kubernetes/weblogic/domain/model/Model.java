// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import jakarta.validation.Valid;
import oracle.kubernetes.json.Default;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.ModelInImageDomainType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Model {
  public static final String DEFAULT_WDT_MODEL_HOME = "/u01/wdt/models";
  public static final String DEFAULT_WDT_INSTALL_HOME = "/u01/wdt/weblogic-deploy";
  public static final String DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH = "/aux";

  @Description("WebLogic Deploy Tooling domain type. Legal values: WLS, RestrictedJRF, JRF. Defaults to WLS.")
  @Default(strDefault = "WLS")
  private ModelInImageDomainType domainType;

  @Description("Name of a ConfigMap containing the WebLogic Deploy Tooling model.")
  private String configMap;

  @Description("Location of the WebLogic Deploy Tooling model home. Defaults to `/u01/wdt/models` if no "
          + "`spec.configuration.model.AuxiliaryImages` are specified, and to `/aux/models` otherwise. "
          + "NOTE: if `modelHome` is set to a non-default value, "
          + "then model files in all specified `spec.configuration.model.AuxiliaryImages` are ignored.")
  private String modelHome;

  @Description("Location of the WebLogic Deploy Tooling installation. Defaults to `/u01/wdt/weblogic-deploy` if no "
          + "`spec.configuration.model.AuxiliaryImages` are specified, and to `/aux/weblogic-deploy` otherwise. "
          + "NOTE: if `wdtInstallHome` is set to a non-default value, "
          + "then the WDT install in any specified `spec.configuration.model.AuxiliaryImages` is ignored.")
  private String wdtInstallHome;

  @Description("Online update option for Model In Image dynamic update.")
  private OnlineUpdate onlineUpdate;

  /**
   * The auxiliary images.
   *
   */
  @Description("Optionally, use auxiliary images to provide Model in Image model, application archive, and WebLogic "
          + "Deploy Tooling files. This is a useful alternative for providing these files without requiring "
          + "modifications to the pod's base image `domain.spec.image`. "
          + "This feature internally uses a Kubernetes emptyDir volume and Kubernetes init containers to share "
          + "the files from the additional images with the pod.")
  private List<AuxiliaryImage> auxiliaryImages;

  @Description("The auxiliary image volume mount path. This is an advanced setting that rarely needs to be configured. "
          + "Defaults to `/aux`, which means the emptyDir volume will be mounted at `/aux` path in the WebLogic-Server "
          + "container within every pod. The defaults for `modelHome` and `wdtInstallHome` will start with the new "
          + "mount path, and files from `sourceModelHome` and `sourceWDTInstallHome` will be copied to the new "
          + "default locations.")
  private String auxiliaryImageVolumeMountPath;

  @Description("The emptyDir volume medium. This is an advanced setting that rarely needs to "
          + "be configured. Defaults to unset, which means the volume's files are stored on the local node's file "
          + "system for the life of the pod.")
  private String auxiliaryImageVolumeMedium;

  @Description("The emptyDir volume size limit. This is an advanced setting that rarely needs to be configured. "
          + "Defaults to unset.")
  private String auxiliaryImageVolumeSizeLimit;

  @Nullable
  public OnlineUpdate getOnlineUpdate() {
    return onlineUpdate;
  }

  public void setOnlineUpdate(OnlineUpdate onlineUpdate) {
    this.onlineUpdate = onlineUpdate;
  }

  public Model withOnlineUpdate(@Nullable OnlineUpdate onlineUpdate) {
    this.onlineUpdate = onlineUpdate;
    return this;
  }

  @Valid
  @Nullable
  @Description("Runtime encryption secret. Required when `domainHomeSourceType` is set to FromModel.")
  private String runtimeEncryptionSecret;

  @Nullable
  public ModelInImageDomainType getDomainType() {
    return domainType;
  }

  public void setDomainType(@Nullable ModelInImageDomainType domainType) {
    this.domainType = domainType;
  }

  public Model withDomainType(@Nullable ModelInImageDomainType domainType) {
    this.domainType = domainType;
    return this;
  }

  @Nullable
  String getConfigMap() {
    return configMap;
  }

  void setConfigMap(@Nullable String configMap) {
    this.configMap = configMap;
  }

  public Model withConfigMap(@Nullable String configMap) {
    this.configMap = configMap;
    return this;
  }

  String getRuntimeEncryptionSecret() {
    return runtimeEncryptionSecret;
  }

  void setRuntimeEncryptionSecret(String runtimeEncryptionSecret) {
    this.runtimeEncryptionSecret = runtimeEncryptionSecret;
  }

  public Model withRuntimeEncryptionSecret(String runtimeEncryptionSecret) {
    this.runtimeEncryptionSecret = runtimeEncryptionSecret;
    return this;
  }

  @Nullable
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
            .append("onlineUpdate", onlineUpdate)
            .append("runtimeEncryptionSecret", runtimeEncryptionSecret)
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
    if (other == this) {
      return true;
    }

    if (!(other instanceof Model)) {
      return false;
    }

    Model rhs = ((Model) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(domainType, rhs.domainType)
            .append(configMap,rhs.configMap)
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
