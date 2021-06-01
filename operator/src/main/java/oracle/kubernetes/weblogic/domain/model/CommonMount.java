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

@Description("Use a common mount to automatically include directory content from additional images. "
        + "This is a useful alternative for including Model in Image model files, or other types of files, in a pod "
        + "without requiring modifications to the pod's base image 'domain.spec.image'. "
        + "This feature internally uses a Kubernetes emptyDir volume and Kubernetes init containers to share "
        + "the files from the additional images with the pod.")
public class CommonMount {

  public static final String COMMON_MOUNT_TARGET_PATH = "/tmpCommonMount";
  public static final String COMMON_MOUNT_VOLUME_NAME_PREFIX = "common-mount-volume-";
  public static final String COMMON_MOUNT_INIT_CONTAINER_WRAPPER_SCRIPT = "/weblogic-operator/scripts/commonMount.sh";
  public static final String COMMON_MOUNT_INIT_CONTAINER_NAME_PREFIX = "operator-common-container";
  public static final String COMMON_MOUNT_DEFAULT_INIT_CONTAINER_COMMAND
          = "cp -R $COMMON_MOUNT_PATH/* $COMMON_MOUNT_TARGET_PATH";

  /**
   * The common mount.
   */
  @Description("The name of an image with files located in directory specified by 'spec.commonMountVolumes.mountPath' "
          + "of the common mount volume referenced by serverPod.commonMounts.volume (which defaults to '/common').")
  @NotNull
  private String image;

  @Description(
          "The image pull policy for the common mount container image. "
                  + "Legal values are Always, Never, and IfNotPresent. "
                  + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  @EnumClass(ImagePullPolicy.class)
  private String imagePullPolicy;

  @Description("The command for this init container. Defaults to 'cp -R $COMMON_MOUNT_PATH/* $TARGET_MOUNT_PATH'. "
          + "This is an advanced setting for customizing the container command for copying files from the container "
          + "image to the common mount emptyDir volume. Use the '$COMMON_MOUNT_PATH' environment variable to reference "
          + "the value configured in 'spec.commonMountVolumes.mountPath' (which defaults to '/common'). Use "
          + "'$TARGET_MOUNT_PATH' to refer to the temporary directory created by the Operator that resolves to the "
          + "common mount's internal emptyDir volume.")
  private String command;

  @Description("The name of a common mount volume defined in 'spec.commonMountVolumes'. Required.")
  @NotNull
  private String volume;

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public CommonMount image(String image) {
    this.image = image;
    return this;
  }

  public String getImagePullPolicy() {
    return Optional.ofNullable(imagePullPolicy).orElse(KubernetesUtils.getInferredImagePullPolicy(getImage()));
  }

  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  public CommonMount imagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  public String getCommand() {
    return Optional.ofNullable(command)
            .orElse(COMMON_MOUNT_DEFAULT_INIT_CONTAINER_COMMAND);
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public CommonMount command(String command) {
    this.command = command;
    return this;
  }

  public String getVolume() {
    return volume;
  }

  public void setVolume(String volume) {
    this.volume = volume;
  }

  public CommonMount volume(String volume) {
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
    if (!(other instanceof CommonMount)) {
      return false;
    }

    CommonMount rhs = ((CommonMount) other);
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
