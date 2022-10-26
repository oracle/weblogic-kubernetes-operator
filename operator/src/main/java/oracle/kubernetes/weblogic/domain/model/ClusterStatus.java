// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.Range;
import oracle.kubernetes.utils.OperatorUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.weblogic.domain.model.ObjectPatch.createObjectPatch;

/** ServerStatus describes the current status of a specific WebLogic Server. */
public class ClusterStatus implements Comparable<ClusterStatus>, PatchableComponent<ClusterStatus> {

  @Description("WebLogic cluster name.")
  @SerializedName("clusterName")
  @Expose
  private String clusterName;

  /** The number of currently cluster members. Required. */
  @Description("The number of currently running cluster members.")
  @Range(minimum = 0)
  private Integer replicas;

  @Description("Label selector that can be used to discover Pods associated with WebLogic managed servers belonging "
      + "to this cluster. Must be set to work with HorizontalPodAutoscaler.")
  private String labelSelector;

  /** The number of ready cluster members. Required. */
  @Description("The number of ready cluster members.")
  @Range(minimum = 0)
  private Integer readyReplicas;

  /** The maximum number of cluster members. Required. */
  @Description("The maximum number of cluster members.")
  @Range(minimum = 0)
  private Integer maximumReplicas;

  /** The minimum number of cluster members. */
  @Description("The minimum number of cluster members.")
  @Range(minimum = 0)
  private Integer minimumReplicas;

  /** The requested number of cluster members from the domain spec. */
  @Description("The requested number of cluster members. "
      + "Cluster members will be started by the operator if this value is larger than zero.")
  @Range(minimum = 0)
  private Integer replicasGoal;

  @Description("The Cluster resource generation observed by the WebLogic operator."
      + " If the Cluster resource exists,"
      + " then this value will match the 'cluster.metadata.generation' "
      + " when the 'cluster.status' correctly reflects the latest cluster resource changes.")
  private Long observedGeneration;

  @Description("Current service state of the cluster.")
  private List<ClusterCondition> conditions = new ArrayList<>();

  public ClusterStatus() {
  }

  ClusterStatus(ClusterStatus other) {
    this.clusterName = other.clusterName;
    this.replicas = other.replicas;
    this.labelSelector = other.labelSelector;
    this.readyReplicas = other.readyReplicas;
    this.maximumReplicas = other.maximumReplicas;
    this.minimumReplicas = other.minimumReplicas;
    this.replicasGoal = other.replicasGoal;
    this.observedGeneration = other.observedGeneration;
    this.conditions = new ArrayList<>(other.conditions);
  }

  /**
   * WebLogic cluster name. Required.
   *
   * @return cluster name
   */
  public String getClusterName() {
    return clusterName;
  }

  /**
   * WebLogic cluster name. Required.
   *
   * @param clusterName cluster name
   */
  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  /**
   * WebLogic cluster name. Required.
   *
   * @param clusterName cluster name
   * @return this
   */
  public ClusterStatus withClusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }

  public Integer getReplicas() {
    return replicas;
  }

  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  public ClusterStatus withReplicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  public String getLabelSelector() {
    return labelSelector;
  }

  public void setLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
  }

  public ClusterStatus withLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
    return this;
  }

  public Integer getReadyReplicas() {
    return readyReplicas;
  }

  public ClusterStatus withReadyReplicas(Integer readyReplicas) {
    this.readyReplicas = readyReplicas;
    return this;
  }

  public Integer getMaximumReplicas() {
    return maximumReplicas;
  }

  public ClusterStatus withMaximumReplicas(Integer maximumReplicas) {
    this.maximumReplicas = maximumReplicas;
    return this;
  }

  public Integer getMinimumReplicas() {
    return minimumReplicas;
  }

  public ClusterStatus withMinimumReplicas(Integer minimumReplicas) {
    this.minimumReplicas = minimumReplicas;
    return this;
  }

  public Integer getReplicasGoal() {
    return replicasGoal;
  }

  public ClusterStatus withReplicasGoal(Integer replicasGoal) {
    this.replicasGoal = replicasGoal;
    return this;
  }

  public Long getObservedGeneration() {
    return observedGeneration;
  }

  /**
   * The observedGeneration attribute is defined as an integer type in the Cluster Resource
   * schema, hence convert to integer when publishing the status.
   * @return integer value of the observedGeneration, if set, otherwise defaults to 1.
   */
  private Integer getObservedGenerationAsInteger() {
    return observedGeneration != null ? observedGeneration.intValue() : 1;
  }

  public ClusterStatus withObservedGeneration(Long observedGeneration) {
    this.observedGeneration = observedGeneration;
    return this;
  }

  public void setObservedGeneration(Long observedGeneration) {
    this.observedGeneration = observedGeneration;
  }

  /**
   * Current service state of cluster.
   *
   * @return conditions
   */
  public @Nonnull List<ClusterCondition> getConditions() {
    return conditions;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("clusterName", clusterName)
        .append("replicas", replicas)
        .append("labelSelector", labelSelector)
        .append("readyReplicas", readyReplicas)
        .append("maximumReplicas", maximumReplicas)
        .append("minimumReplicas", minimumReplicas)
        .append("replicasGoal", replicasGoal)
        .append("observedGeneration", observedGeneration)
        .append("conditions", conditions)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(clusterName)
        .append(replicas)
        .append(labelSelector)
        .append(readyReplicas)
        .append(maximumReplicas)
        .append(minimumReplicas)
        .append(replicasGoal)
        .append(observedGeneration)
        .append(DomainResource.sortList(conditions))
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof ClusterStatus)) {
      return false;
    }
    ClusterStatus rhs = ((ClusterStatus) other);
    return new EqualsBuilder()
        .append(clusterName, rhs.clusterName)
        .append(replicas, rhs.replicas)
        .append(labelSelector, rhs.labelSelector)
        .append(readyReplicas, rhs.readyReplicas)
        .append(maximumReplicas, rhs.maximumReplicas)
        .append(minimumReplicas, rhs.minimumReplicas)
        .append(replicasGoal, rhs.replicasGoal)
        .append(observedGeneration, rhs.observedGeneration)
        .append(DomainResource.sortList(conditions), DomainResource.sortList(rhs.conditions))
        .isEquals();
  }

  @Override
  public int compareTo(@Nonnull ClusterStatus o) {
    return OperatorUtils.compareSortingStrings(clusterName, o.clusterName);
  }

  @Override
  public boolean isPatchableFrom(ClusterStatus other) {
    return other.getClusterName() != null && other.getClusterName().equals(clusterName);
  }

  private static final ObjectPatch<ClusterStatus> clusterPatch =
      createObjectPatch(ClusterStatus.class)
          .withStringField("clusterName", ClusterStatus::getClusterName)
          .withIntegerField("maximumReplicas", ClusterStatus::getMaximumReplicas)
          .withIntegerField("minimumReplicas", ClusterStatus::getMinimumReplicas)
          .withIntegerField("observedGeneration", ClusterStatus::getObservedGenerationAsInteger)
          .withIntegerField("readyReplicas", ClusterStatus::getReadyReplicas)
          .withIntegerField("replicas", ClusterStatus::getReplicas)
          .withIntegerField("replicasGoal", ClusterStatus::getReplicasGoal);

  static ObjectPatch<ClusterStatus> getObjectPatch() {
    return clusterPatch;
  }

  /**
   * Adds a condition to the status, replacing any existing conditions with the same type, and removing other
   * conditions according to the cluster rules.
   *
   * @param newCondition the condition to add.
   * @return this object.
   */
  public ClusterStatus addCondition(ClusterCondition newCondition) {
    if (conditions.contains(newCondition)) {
      return this;
    }

    conditions = conditions.stream()
        .filter(c -> c.isCompatibleWith(newCondition))
        .collect(Collectors.toList());

    conditions.add(newCondition);
    Collections.sort(conditions);
    return this;
  }
}
