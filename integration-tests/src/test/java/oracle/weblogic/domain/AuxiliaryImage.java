// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel("Use an auxiliary image to automatically include directory content from additional images. "
        + "This is a useful alternative for including Model in Image model files, or other types of files, in a pod "
        + "without requiring modifications to the pod's base image 'domain.spec.image'. "
        + "This feature internally uses a Kubernetes emptyDir volume and Kubernetes init containers to share "
        + "the files from the additional images with the pod.")
public class AuxiliaryImage {

  /**
   * The auxiliary image.
   */
  @ApiModelProperty("The name of an image with files located in directory specified by "
      + "'spec.auxiliaryImageVolumes.mountPath' of the auxiliary image volume referenced by "
      + "serverPod.auxiliaryImage.volume (which defaults to '/auxiliary').")
  private String image;

  @ApiModelProperty(
          "The image pull policy for the container image. "
                  + "Legal values are Always, Never, and IfNotPresent. "
                  + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  private String imagePullPolicy;

  @ApiModelProperty(
      "The command for this init container. Defaults to 'cp -R $AUXILIARY_IMAGE_PATH/* $TARGET_MOUNT_PATH'. "
          + "This is an advanced setting for customizing the container command for copying files from the container "
          + "image to the emptyDir volume. Use the '$AUXILIARY_IMAGE_PATH' environment variable to reference "
          + "the value configured in 'spec.auxiliaryImageVolumes.mountPath' (which defaults to '/auxiliary'). Use "
          + "'$TARGET_MOUNT_PATH' to refer to the temporary directory created by the Operator that resolves to the "
          + "internal emptyDir volume.")
  private String command;

  @ApiModelProperty("The name of a auxiliary image volume defined in 'spec.auxiliaryImageVolumes'. Required.")
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
    return imagePullPolicy;
  }

  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  public AuxiliaryImage imagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  public String getCommand() {
    return command;
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
