// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;

import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.operator.ImagePullPolicy;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Container {

  public static final String INIT_CONTAINER_NAME_PREFIX = "operator-common-container";
  public static final String DEFAULT_INIT_CONTAINER_COMMAND = "cp -R $COMMON_MOUNT_PATH/* $COMMON_TARGET_PATH";
  public static final String INIT_CONTAINER_WRAPPER_SCRIPT = "/weblogic-operator/scripts/commonMount.sh";

  /**
   * The WDT resources container image.
   */
  @Description("The name of an image with files located in directory 'commonMount.mountPath' "
          + "(which defaults to '/common').")
  private String image;

  @Description(
          "The image pull policy for the common mount container image. "
                  + "Legal values are Always, Never, and IfNotPresent. "
                  + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  @EnumClass(ImagePullPolicy.class)
  private String imagePullPolicy;

  @Description("The command for this init container. Defaults to 'cp -R $COMMON_MOUNT_PATH/* $TARGET_MOUNT_PATH'. "
          + "This is an advanced setting for customizing the container command for copying files from the container "
          + "image to the common mount emptyDir volume. Use the 'COMMON_DIR' environment variable to reference the "
          + "value configured in 'commonMount.mountPath' (which defaults to '/common'). Use 'TARGET_DIR' to refer to "
          + "the temporary directory created by the Operator that resolves to the common mount's internal emptyDir "
          + "volume.")
  private String command;

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public Container image(String image) {
    this.image = image;
    return this;
  }

  public String getImagePullPolicy() {
    return Optional.ofNullable(imagePullPolicy).orElse(KubernetesUtils.getInferredImagePullPolicy(getImage()));
  }

  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  public Container imagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  public String getCommand() {
    return Optional.ofNullable(command)
            .orElse(DEFAULT_INIT_CONTAINER_COMMAND);
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public Container command(String command) {
    this.command = command;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
            new ToStringBuilder(this)
                    .append("image", image)
                    .append("imagePullPolicy", imagePullPolicy)
                    .append("command", command);
    return builder.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Container)) {
      return false;
    }

    Container rhs = ((Container) other);
    EqualsBuilder builder =
            new EqualsBuilder()
                    .append(image, rhs.image)
                    .append(imagePullPolicy, rhs.imagePullPolicy)
                    .append(command, rhs.command);

    return builder.isEquals();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
            .append(image)
            .append(imagePullPolicy)
            .append(command);
    return builder.toHashCode();
  }
}