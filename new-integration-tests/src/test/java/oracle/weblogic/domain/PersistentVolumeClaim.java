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

@ApiModel(description = "Describes the configuration for a Persistent Volume Claim.")
public class PersistentVolumeClaim {

  @ApiModelProperty("The labels to be attached to generated persistent volume claim")
  private Map<String, String> labels = new HashMap<>();

  @ApiModelProperty("Persistent Volume Claim Name")
  private String name;

  @ApiModelProperty("Persistent Volume Namespace")
  private String namespace;

  @ApiModelProperty("Access Mode of the volume.")
  private List<String> accessMode = new ArrayList<>();

  @ApiModelProperty("Storage capacity of the volume.")
  private String storage;

  @ApiModelProperty("StorageClass Name of the volume.")
  private String storageClassName;

  @ApiModelProperty("Persistent Volume Mode")
  private String volumeMode;

  @ApiModelProperty("Persistent Volume Claim VolumeName")
  private String volumeName;

  public PersistentVolumeClaim labels(Map<String, String> labels) {
    this.labels = labels;
    return this;
  }

  public Map<String, String> labels() {
    return labels;
  }

  public PersistentVolumeClaim name(String name) {
    this.name = name;
    return this;
  }

  public String name() {
    return name;
  }

  public PersistentVolumeClaim namespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

  public String namespace() {
    return namespace;
  }

  public PersistentVolumeClaim accessMode(List<String> accessMode) {
    this.accessMode = accessMode;
    return this;
  }

  public List<String> accessMode() {
    return accessMode;
  }

  public PersistentVolumeClaim storage(String storage) {
    this.storage = storage;
    return this;
  }

  public String storage() {
    return storage;
  }

  public PersistentVolumeClaim storageClassName(String storageClassName) {
    this.storageClassName = storageClassName;
    return this;
  }

  public String storageClassName() {
    return storageClassName;
  }

  public PersistentVolumeClaim volumeMode(String volumeMode) {
    this.volumeMode = volumeMode;
    return this;
  }

  public String volumeMode() {
    return volumeMode;
  }

  public PersistentVolumeClaim volumeName(String volumeName) {
    this.volumeName = volumeName;
    return this;
  }

  public String volumeName() {
    return volumeName;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("labels", labels)
      .append("name", name)
      .append("namespace", namespace)
      .append("accessMode", accessMode)
      .append("storage", storage)
      .append("storageClassName", storageClassName)
      .append("volumeMode", volumeMode)
      .append("volumeName", volumeName)
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
    PersistentVolumeClaim rhs = (PersistentVolumeClaim) other;
    return new EqualsBuilder()
      .append(labels, rhs.labels)
      .append(name, rhs.name)
      .append(namespace, rhs.namespace)
      .append(accessMode, rhs.accessMode)
      .append(storage, rhs.storage)
      .append(storageClassName, rhs.storageClassName)
      .append(volumeMode, rhs.volumeMode)
      .append(volumeName, rhs.volumeName)
      .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
      .append(labels)
      .append(name)
      .append(namespace)
      .append(accessMode)
      .append(storage)
      .append(storageClassName)
      .append(volumeMode)
      .append(volumeName)
      .toHashCode();
  }

}
