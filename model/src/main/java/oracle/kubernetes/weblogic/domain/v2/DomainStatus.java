// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.Range;
import oracle.kubernetes.utils.SystemClock;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;

/**
 * DomainStatus represents information about the status of a domain. Status may trail the actual
 * state of a system.
 */
@Description(
    "DomainStatus represents information about the status of a domain. "
        + "Status may trail the actual state of a system.")
public class DomainStatus {

  @Description("Current service state of domain.")
  @Valid
  private List<DomainCondition> conditions = new ArrayList<DomainCondition>();

  @Description(
      "A human readable message indicating details about why the domain is in this condition.")
  private String message;

  @Description(
      "A brief CamelCase message indicating details about why the domain is in this state.")
  private String reason;

  @Description("Status of WebLogic servers in this domain.")
  @Valid
  private List<ServerStatus> servers = new ArrayList<ServerStatus>();

  @Description(
      "RFC 3339 date and time at which the operator started the domain. This will be when "
          + "the operator begins processing and will precede when the various servers "
          + "or clusters are available.")
  private DateTime startTime = SystemClock.now();

  @Description(
      "The number of running managed servers in the WebLogic cluster if there is "
          + "only one cluster in the domain and where the cluster does not explicitly "
          + "configure its replicas in a cluster specification.")
  @Range(minimum = 0)
  private Integer replicas;

  /** true if the domain status has been modified. * */
  private volatile boolean modified;

  /**
   * Current service state of domain.
   *
   * @return conditions
   */
  public @Nonnull List<DomainCondition> getConditions() {
    return conditions;
  }

  /**
   * Adds a condition to the status, replacing any existing conditions with the same type.
   *
   * @param condition the condition to add.
   * @return this object.
   */
  public DomainStatus addCondition(DomainCondition condition) {
    if (!isNewCondition(condition)) {
      return this;
    }

    conditions.add(condition);
    modified = true;
    return this;
  }

  /**
   * True, if condition present based on predicate.
   *
   * @param predicate Predicate
   * @return True, if predicate is satisfied
   */
  public boolean hasConditionWith(Predicate<DomainCondition> predicate) {
    return !getConditionsMatching(predicate).isEmpty();
  }

  /**
   * Removes condition based on predicate.
   *
   * @param predicate Predicate
   */
  public void removeConditionIf(Predicate<DomainCondition> predicate) {
    for (DomainCondition condition : getConditionsMatching(predicate)) {
      removeCondition(condition);
    }
  }

  private List<DomainCondition> getConditionsMatching(Predicate<DomainCondition> predicate) {
    return conditions.stream().filter(predicate).collect(Collectors.toList());
  }

  private void removeCondition(DomainCondition condition) {
    if (condition != null && conditions.remove(condition)) {
      modified = true;
    }
  }

  private DomainCondition getConditionWithType(DomainConditionType type) {
    for (DomainCondition condition : conditions) {
      if (type == condition.getType()) {
        return condition;
      }
    }

    return null;
  }

  private boolean isNewCondition(DomainCondition condition) {
    DomainCondition oldCondition = getConditionWithType(condition.getType());
    if (oldCondition == null) {
      return true;
    }

    if (oldCondition.equals(condition)) {
      return false;
    }

    conditions.remove(oldCondition);
    return true;
  }

  /**
   * A human readable message indicating details about why the domain is in this condition.
   *
   * @return message
   */
  public String getMessage() {
    return message;
  }

  /**
   * A human readable message indicating details about why the domain is in this condition.
   *
   * @param message message
   */
  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * A human readable message indicating details about why the domain is in this condition.
   *
   * @param message message
   * @return this
   */
  public DomainStatus withMessage(String message) {
    this.message = message;
    return this;
  }

  /**
   * A brief CamelCase message indicating details about why the domain is in this state.
   *
   * @return reason
   */
  public String getReason() {
    return reason;
  }

  /**
   * A brief CamelCase message indicating details about why the domain is in this state.
   *
   * @param reason reason
   */
  public void setReason(String reason) {
    this.reason = reason;
  }

  /**
   * A brief CamelCase message indicating details about why the domain is in this state.
   *
   * @param reason reason
   * @return this
   */
  public DomainStatus withReason(String reason) {
    this.reason = reason;
    return this;
  }

  /**
   * The number of running managed servers in the WebLogic cluster if there is only one cluster in
   * the domain and where the cluster does not explicitly configure its replicas in a cluster
   * specification.
   *
   * @param replicas replicas
   */
  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  /**
   * The number of running managed servers in the WebLogic cluster if there is only one cluster in
   * the domain and where the cluster does not explicitly configure its replicas in a cluster
   * specification.
   *
   * @return replicas
   */
  public Integer getReplicas() {
    return this.replicas;
  }

  /**
   * The number of running managed servers in the WebLogic cluster if there is only one cluster in
   * the domain and where the cluster does not explicitly configure its replicas in a cluster
   * specification.
   *
   * @param replicas replicas
   * @return this
   */
  public DomainStatus withReplicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  /**
   * Status of WebLogic servers in this domain.
   *
   * @return servers
   */
  public List<ServerStatus> getServers() {
    return servers;
  }

  /**
   * Status of WebLogic servers in this domain.
   *
   * @param servers servers
   */
  public void setServers(List<ServerStatus> servers) {
    if (isEqualIgnoringOrder(servers, this.servers)) {
      return;
    }

    this.servers = servers;
    modified = true;
  }

  private boolean isEqualIgnoringOrder(List<ServerStatus> servers1, List<ServerStatus> servers2) {
    return new HashSet<>(servers1).equals(new HashSet<>(servers2));
  }

  /**
   * Status of WebLogic servers in this domain.
   *
   * @param servers servers
   * @return this
   */
  public DomainStatus withServers(List<ServerStatus> servers) {
    this.servers = servers;
    return this;
  }

  /**
   * RFC 3339 date and time at which the operator started the domain. This will be when the operator
   * begins processing and will precede when the various servers or clusters are available.
   *
   * @return start time
   */
  DateTime getStartTime() {
    return startTime;
  }

  public boolean isModified() {
    return modified;
  }

  public DomainStatus clearModified() {
    modified = false;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("conditions", conditions)
        .append("message", message)
        .append("reason", reason)
        .append("servers", servers)
        .append("startTime", startTime)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(reason)
        .append(startTime)
        .append(Domain.sortOrNull(servers))
        .append(Domain.sortOrNull(conditions))
        .append(message)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof DomainStatus)) {
      return false;
    }
    DomainStatus rhs = ((DomainStatus) other);
    return new EqualsBuilder()
        .append(reason, rhs.reason)
        .append(startTime, rhs.startTime)
        .append(Domain.sortOrNull(servers), Domain.sortOrNull(rhs.servers))
        .append(Domain.sortOrNull(conditions), Domain.sortOrNull(rhs.conditions))
        .append(message, rhs.message)
        .isEquals();
  }
}
