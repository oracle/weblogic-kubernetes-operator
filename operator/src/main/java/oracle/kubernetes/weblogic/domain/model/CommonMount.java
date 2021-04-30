// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class CommonMount {

  public static final String DEFAULT_COMMON_MOUNT_PATH = "/common";
  public static final String COMMON_TARGET_PATH = "/tmpCommonMount";
  public static final String COMMON_VOLUME_NAME = "operator-common-volume";

  @Description("The common mount containers.")
  private List<Container> containers;

  @Description("The common mount path. The files in the path are populated from the same named directory in the images "
          + "supplied by each container in 'commonMount.containers'. Defaults to '/common'.")
  private String mountPath;

  @Description("The emptyDir volume medium. This is an advanced setting that rarely needs to be configured. "
          + "Defaults to unset, which means the volume's files are stored on the local node's file system for "
          + "the life of the pod.")
  private String medium;

  @Description("The emptyDir volume size limit. Defaults to unset.")
  private String sizeLimit;

  public List<Container> getContainers() {
    return containers;
  }

  public void setContainers(List<Container> containers) {
    this.containers = containers;
  }

  public CommonMount container(Container container) {
    this.containers = Arrays.asList(container);
    return this;
  }

  public CommonMount containers(List<Container> containers) {
    this.containers = containers;
    return this;
  }

  public String getMountPath() {
    return Optional.ofNullable(mountPath).orElse(DEFAULT_COMMON_MOUNT_PATH);
  }

  public void setMountPath(String mountPath) {
    this.mountPath = mountPath;
  }

  public CommonMount mountPath(String commonMountPath) {
    this.mountPath = commonMountPath;
    return this;
  }

  public String getMedium() {
    return medium;
  }

  public void setMedium(String medium) {
    this.medium = medium;
  }

  public CommonMount medium(String medium) {
    this.medium = medium;
    return this;
  }

  public String getSizeLimit() {
    return sizeLimit;
  }

  public void setSizeLimit(String sizeLimit) {
    this.sizeLimit = sizeLimit;
  }

  public CommonMount sizeLimit(String sizeLimit) {
    this.sizeLimit = sizeLimit;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("containers", containers)
            .append("commonDir", mountPath)
            .append("medium", medium)
            .append("sizeLimit", sizeLimit);
    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(containers)
        .append(mountPath)
        .append(medium)
        .append(sizeLimit);
    return builder.toHashCode();
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
            .append(containers, rhs.containers)
            .append(mountPath, rhs.mountPath)
            .append(medium, rhs.medium)
            .append(sizeLimit, rhs.sizeLimit);

    return builder.isEquals();
  }

}