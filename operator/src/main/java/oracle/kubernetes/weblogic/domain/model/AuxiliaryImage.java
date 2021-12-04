// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.operator.ImagePullPolicy;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class AuxiliaryImage {

  public static final String AUXILIARY_IMAGE_TARGET_PATH = "/tmpAuxiliaryImage";
  public static final String AUXILIARY_IMAGE_VOLUME_NAME_PREFIX = "aux-image-volume-";
  public static final String AUXILIARY_IMAGE_INIT_CONTAINER_WRAPPER_SCRIPT = "/weblogic-operator/scripts/auxImage.sh";
  public static final String AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX = "operator-aux-container";
  public static final String AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND
          = "cp -R $AUXILIARY_IMAGE_PATH/* $AUXILIARY_IMAGE_TARGET_PATH";

  /**
   * The auxiliary image.
   */
  @Description("The name of an image with files located in the directory specified by "
      + "`spec.auxiliaryImageVolumes.mountPath` of the auxiliary image volume referenced by "
      + "`serverPod.auxiliaryImages.volume`, which defaults to \"/auxiliary\".")
  @NotNull
  private String image;

  @Description("The image pull policy for the container image. "
      + "Legal values are Always, Never, and IfNotPresent. "
      + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  @EnumClass(ImagePullPolicy.class)
  private String imagePullPolicy;

  @Description("The command for this init container. Defaults to `cp -R $AUXILIARY_IMAGE_PATH/* "
      + "$AUXILIARY_IMAGE_TARGET_PATH`. This is an advanced setting for customizing the container command for copying "
      + "files from the container image to the auxiliary image emptyDir volume. Use the `$AUXILIARY_IMAGE_PATH` "
      + "environment variable to reference the value configured in `spec.auxiliaryImageVolumes.mountPath`, which "
      + "defaults to \"/auxiliary\". Use '$AUXILIARY_IMAGE_TARGET_PATH' to refer to the temporary directory created by "
      + "the operator that resolves to the auxiliary image's internal emptyDir volume.")
  private String command;

  @Description("The name of an auxiliary image volume defined in `spec.auxiliaryImageVolumes`. Required.")
  @NotNull
  private String volume;

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
    return Optional.ofNullable(imagePullPolicy).orElse(KubernetesUtils.getInferredImagePullPolicy(getImage()));
  }

  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  public AuxiliaryImage imagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  public String getCommand() {
    return Optional.ofNullable(command)
            .orElse(AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND);
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public AuxiliaryImage command(String command) {
    this.command = command;
    return this;
  }

  public String getVolume() {
    return volume;
  }

  public void setVolume(String volume) {
    this.volume = volume;
  }

  public AuxiliaryImage volume(String volume) {
    this.volume = volume;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
            new ToStringBuilder(this)
                    .append("image", image)
                    .append("imagePullPolicy", imagePullPolicy)
                    .append("command", command)
                    .append("volume", volume);
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
                    .append(command, rhs.command)
                    .append(volume, rhs.volume);

    return builder.isEquals();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
            .append(image)
            .append(imagePullPolicy)
            .append(command)
            .append(volume);
    return builder.toHashCode();
  }
}
