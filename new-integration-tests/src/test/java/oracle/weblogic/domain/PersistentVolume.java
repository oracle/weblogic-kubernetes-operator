// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "Describes the configuration for a PersistentVolume.")
public class PersistentVolume {

  @ApiModelProperty("The labels to be attached to generated volume.")
  private Map<String, String> labels = new HashMap<>();

  @ApiModelProperty("Persistent Volume Name")
  private String name;

  @ApiModelProperty("Access Mode of the volume.")
  private List<String> accessMode = new ArrayList<>();

  @ApiModelProperty("Storage capacity of the volume.")
  private String storage;

  @ApiModelProperty("Persistent Volume Host Path")
  private String path;

  @ApiModelProperty("Persistent Volume Reclaim Policy")
  private String persistentVolumeReclaimPolicy;

  @ApiModelProperty("StorageClass Name of the volume.")
  private String storageClassName;

  @ApiModelProperty("Persistent Volume Mode")
  private String volumeMode;

  public PersistentVolume labels(Map<String, String> labels) {
    this.labels = labels;
    return this;
  }

  public Map<String, String> labels() {
    return labels;
  }

  public PersistentVolume name(String name) {
    this.name = name;
    return this;
  }

  public String name() {
    return name;
  }

  public PersistentVolume accessMode(List<String> accessMode) {
    this.accessMode = accessMode;
    return this;
  }

  public List<String> accessMode() {
    return accessMode;
  }

  public PersistentVolume storage(String storage) {
    this.storage = storage;
    return this;
  }

  public String storage() {
    return storage;
  }

  public PersistentVolume path(String path) {
    this.path = path;
    return this;
  }

  public String path() {
    return path;
  }

  public PersistentVolume persistentVolumeReclaimPolicy(String persistentVolumeReclaimPolicy) {
    this.persistentVolumeReclaimPolicy = persistentVolumeReclaimPolicy;
    return this;
  }

  public String persistentVolumeReclaimPolicy() {
    return persistentVolumeReclaimPolicy;
  }

  public PersistentVolume storageClassName(String storageClassName) {
    this.storageClassName = storageClassName;
    return this;
  }

  public String storageClassName() {
    return storageClassName;
  }

  public PersistentVolume volumeMode(String volumeMode) {
    this.volumeMode = volumeMode;
    return this;
  }

  public String volumeMode() {
    return volumeMode;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("labels", labels)
      .append("name", name)
      .append("accessMode", accessMode)
      .append("capacity", storage)
      .append("path", path)
      .append("persistentVolumeReclaimPolicy", persistentVolumeReclaimPolicy)
      .append("storageClassName", storageClassName)
      .append("volumeMode", volumeMode)
      .toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    PersistentVolume rhs = (PersistentVolume) other;
    return new EqualsBuilder()
      .append(labels, rhs.labels)
      .append(name, rhs.name)
      .append(accessMode, rhs.accessMode)
      .append(storage, rhs.storage)
      .append(path, rhs.path)
      .append(persistentVolumeReclaimPolicy, rhs.persistentVolumeReclaimPolicy)
      .append(storageClassName, rhs.storageClassName)
      .append(volumeMode, rhs.volumeMode)
      .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
      .append(labels)
      .append(name)
      .append(accessMode)
      .append(storage)
      .append(path)
      .append(persistentVolumeReclaimPolicy)
      .append(storageClassName)
      .append(volumeMode)
      .toHashCode();
  }

}
