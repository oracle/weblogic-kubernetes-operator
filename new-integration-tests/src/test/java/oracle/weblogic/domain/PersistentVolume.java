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

  @ApiModelProperty(
      "The labels to be attached to generated resources. The label names must "
      + "not start with 'weblogic.'.")
  private Map<String, String> labels = new HashMap<>();

  @ApiModelProperty("StorageClass Name of the volume.")
  private String storageClassName;

  @ApiModelProperty("Storage capacity of the volume.")
  private String capacity;

  @ApiModelProperty("Access Mode of the volume.")
  private List<String> accessMode = new ArrayList<>();

  @ApiModelProperty("Persistent Volume Reclaim Policy")
  private String persistentVolumeReclaimPolicy;

  @ApiModelProperty("Persistent Volume Mode")
  private String volumeMode;

  @ApiModelProperty("Persistent Volume Host Path")
  private String path;

  @ApiModelProperty("Persistent Volume Name")
  private String name;

  @ApiModelProperty("Persistent Volume Namespace")
  private String namespace;

  public PersistentVolume labels(Map<String, String> labels) {
    this.labels = labels;
    return this;
  }

  public Map<String, String> labels() {
    return labels;
  }

  public PersistentVolume storageClassName(String storageClassName) {
    this.storageClassName = storageClassName;
    return this;
  }

  public String storageClassName() {
    return storageClassName;
  }

  public PersistentVolume capacity(String capacity) {
    this.capacity = capacity;
    return this;
  }

  public String capacity() {
    return capacity;
  }

  public PersistentVolume accessMode(List<String> accessMode) {
    this.accessMode = accessMode;
    return this;
  }

  public List<String> accessMode() {
    return accessMode;
  }

  public PersistentVolume persistentVolumeReclaimPolicy(String persistentVolumeReclaimPolicy) {
    this.persistentVolumeReclaimPolicy = persistentVolumeReclaimPolicy;
    return this;
  }

  public String persistentVolumeReclaimPolicy() {
    return persistentVolumeReclaimPolicy;
  }

  public PersistentVolume volumeMode(String volumeMode) {
    this.volumeMode = volumeMode;
    return this;
  }

  public String volumeMode() {
    return volumeMode;
  }

  public PersistentVolume path(String path) {
    this.path = path;
    return this;
  }

  public String path() {
    return path;
  }

  public PersistentVolume name(String name) {
    this.name = name;
    return this;
  }

  public String name() {
    return name;
  }

  public PersistentVolume namespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

  public String namespace() {
    return namespace;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("labels", labels)
      .append("storageClassName", storageClassName)
      .append("capacity", capacity)
      .append("accessMode", accessMode)
      .append("persistentVolumeReclaimPolicy", persistentVolumeReclaimPolicy)
      .append("volumeMode", volumeMode)
      .append("path", path)
      .append("name", name)
      .append("namespace", namespace)
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
      .append(storageClassName, rhs.storageClassName)
      .append(capacity, rhs.capacity)
      .append(accessMode, rhs.accessMode)
      .append(persistentVolumeReclaimPolicy, rhs.persistentVolumeReclaimPolicy)
      .append(volumeMode, rhs.volumeMode)
      .append(path, rhs.path)
      .append(name, rhs.name)
      .append(namespace, rhs.namespace)
      .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
      .append(labels)
      .append(storageClassName)
      .append(capacity)
      .append(accessMode)
      .append(persistentVolumeReclaimPolicy)
      .append(volumeMode)
      .append(path)
      .append(name)
      .append(namespace)
      .toHashCode();
  }

}
