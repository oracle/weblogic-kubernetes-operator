// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import jakarta.json.JsonPatchBuilder;
import jakarta.validation.Valid;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.Range;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.utils.SystemClock;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR_MSG;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.AVAILABLE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.ROLLING;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.ABORTED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTROSPECTION;
import static oracle.kubernetes.weblogic.domain.model.ObjectPatch.createObjectPatch;

/**
 * DomainStatus represents information about the status of a domain. Status may trail the actual
 * state of a system.
 */
@Description("The current status of the operation of the WebLogic domain. Updated automatically by the operator.")
public class DomainStatus {

  public static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  @Description("Current service state of the domain.")
  @Valid
  private List<DomainCondition> conditions = new ArrayList<>();

  @Description(
      "A human readable message indicating details about why the domain is in this condition.")
  private String message;

  @Description(
      "A brief CamelCase message indicating details about why the domain is in this state.")
  private String reason;

  @Description("The Domain resource generation observed by the WebLogic operator."
      + " This value will match the 'domain.metadata.generation' "
      + " when the 'domain.status' correctly reflects the latest resource changes.")
  private Long observedGeneration;


  /**
   * The number of introspector job failures since the last success.
   *
   * @deprecated Introspection failure is now controlled by a maximum time rather than a failure count
   **/
  @SuppressWarnings("unused")
  @Description(
      "Non-zero if the introspector job fails for any reason. "
          + "You can configure an introspector job retry limit for jobs that log script failures using "
          + "the Operator tuning parameter 'domainPresenceFailureRetryMaxCount' (default 5). "
          + "You cannot configure a limit for other types of failures, such as a Domain resource reference "
          + "to an unknown secret name; in which case, the retries are unlimited.")
  @Range(minimum = 0)
  @Deprecated(since = "4.0")
  private Integer introspectJobFailureCount;

  @Description("Unique ID of the last failed introspection job.")
  private String failedIntrospectionUid;

  @Description("Status of WebLogic Servers in this domain.")
  @Valid
  // sorted list of ServerStatus
  private final List<ServerStatus> servers;

  @Description("Status of WebLogic clusters in this domain.")
  @Valid
  // sorted list of ClusterStatus
  private final List<ClusterStatus> clusters = new ArrayList<>();

  @Description(
      "RFC 3339 date and time at which the operator started the domain. This will be when "
          + "the operator begins processing and will precede when the various servers "
          + "or clusters are available.")
  private OffsetDateTime startTime = SystemClock.now();

  @Description("RFC 3339 date and time at which a currently failing domain started automatic retries.")
  private OffsetDateTime initialFailureTime;

  @Description("RFC 3339 date and time at which a currently failing domain last experienced a Severe failure.")
  private OffsetDateTime lastFailureTime;

  @Description(
      "The number of running cluster member Managed Servers in the WebLogic cluster if there is "
      + "exactly one cluster defined in the domain configuration and where the `replicas` field is set at the `spec` "
      + "level rather than for the specific cluster under `clusters`. This field is provided to support use of "
      + "Kubernetes scaling for this limited use case.")
  @Range(minimum = 0)
  private Integer replicas;

  public DomainStatus() {
    servers = new ArrayList<>();
  }

  /**
   * A copy constructor that creates a deep copy.
   * @param that the object to copy
   */
  @SuppressWarnings("CopyConstructorMissesField")
  public DomainStatus(DomainStatus that) {
    message = that.message;
    reason = that.reason;
    observedGeneration = that.observedGeneration;
    conditions = that.conditions.stream().map(DomainCondition::new).collect(Collectors.toList());
    servers = that.servers.stream().map(ServerStatus::new).collect(Collectors.toList());
    clusters.addAll(that.clusters.stream().map(ClusterStatus::new).collect(Collectors.toList()));
    startTime = that.startTime;
    initialFailureTime = that.initialFailureTime;
    lastFailureTime = that.lastFailureTime;
    replicas = that.replicas;
    failedIntrospectionUid = that.failedIntrospectionUid;
  }

  /**
   * Current service state of domain.
   *
   * @return conditions
   */
  public @Nonnull List<DomainCondition> getConditions() {
    return conditions;
  }

  public DomainStatus addCondition(DomainCondition newCondition) {
    return addCondition(newCondition, false);
  }

  /**
   * Adds a condition to the status, replacing any existing conditions with the same type, and removing other
   * conditions according to the domain rules. Any existing matching condition will be preserved and unmarked.
   *
   * @param newCondition the condition to add.
   * @return this object.
   */
  public DomainStatus addCondition(DomainCondition newCondition, boolean isInitDomainOnPV) {
    if (newCondition.isNotValid()) {
      throw new IllegalArgumentException("May not add condition " + newCondition);
    } else if (isRetryableFailure(newCondition, isInitDomainOnPV)) {
      lastFailureTime = newCondition.getLastTransitionTime();
    }


    if (conditions.contains(newCondition)) {
      unmarkMatchingCondition(newCondition);
      return this;
    }

    conditions = conditions.stream()
          .filter(c -> !c.getType().isObsolete())
          .filter(c -> c.isCompatibleWith(newCondition))
          .collect(Collectors.toList());

    conditions.add(newCondition);
    Collections.sort(conditions);
    setStatusSummary();
    return this;
  }

  private boolean isRetryableFailure(DomainCondition newCondition, boolean isInitDomainOnPV) {
    return newCondition.isRetriableFailure()
        && !initDomainOnPVIntropsectionFailure(getReason(newCondition), isInitDomainOnPV);
  }

  private boolean initDomainOnPVIntropsectionFailure(DomainFailureReason reason, boolean isInitDomainOnPV) {
    return isInitDomainOnPV && reason.equals(INTROSPECTION);
  }

  private void unmarkMatchingCondition(DomainCondition newCondition) {
    conditions.stream().filter(c -> c.equals(newCondition)).forEach(DomainCondition::unMarkForDeletion);
  }

  private void setStatusSummary() {
    final DomainCondition selected = getSummaryCondition();
    reason = Optional.ofNullable(selected.getReason()).map(DomainFailureReason::toString).orElse(null);
    message = selected.getMessage();
    if (selected.isRetriableFailure()) {
      initialFailureTime = Optional.ofNullable(initialFailureTime).orElse(selected.getLastTransitionTime());
    } else {
      initialFailureTime = lastFailureTime = null;
    }
  }

  /**
   * Updates the summary message with retry information, if applicable.
   * @param retryMessageFactory an object which can create a summary message with retry information
   */
  public void updateSummaryMessage(RetryMessageFactory retryMessageFactory) {
    DomainCondition selected = getSummaryCondition();
    if (retryMessageFactory != null && selected.isRetriableFailure()) {
      message = retryMessageFactory.createRetryMessage(this, selected);
    } else {
      message = selected.getMessage();
    }
  }

  /**
   * Returns the domain condition that will be used to set the status summary information:
   * reason, message and retry information. Since failures are sorted first, a failure will always be returned
   * if one is present.
   */
  @Nonnull
  public DomainCondition getSummaryCondition() {
    return conditions.stream()
          .filter(this::maySupplyStatusMessage)
          .findFirst().orElse(createEmptyCondition());
  }

  // Returns a condition with null reason, message and retry information.
  @Nonnull
  private DomainCondition createEmptyCondition() {
    return new DomainCondition(AVAILABLE);
  }

  private boolean maySupplyStatusMessage(DomainCondition c) {
    return c.getMessage() != null && "True".equals(c.getStatus());
  }

  /**
   * Returns true if any condition of the specified type is present.
   * @param type the type of condition to find
   */
  public boolean hasConditionWithType(DomainConditionType type) {
    return conditions.stream().anyMatch(c -> c.getType() == type);
  }

  /**
   * Returns true if there is no condition matching the specified predicate.
   * @param predicate a predicate to match against a condition
   */
  public boolean lacksConditionWith(Predicate<DomainCondition> predicate) {
    return getConditionsMatching(predicate).isEmpty();
  }

  /**
   * Removes any condition with the specified type.
   *
   * @param type the type of the condition
   */
  public void removeConditionsWithType(DomainConditionType type) {
    removeConditionsMatching(c -> c.hasType(type));
  }

  /**
   * Removes any condition matching the specified predicate.
   * @param predicate the criteria for removing a condition
   */
  public void removeConditionsMatching(Predicate<DomainCondition> predicate) {
    for (DomainCondition condition : getConditionsMatching(predicate)) {
      removeCondition(condition);
    }
  }

  private List<DomainCondition> getConditionsMatching(Predicate<DomainCondition> predicate) {
    return conditions.stream().filter(predicate).collect(Collectors.toList());
  }

  /**
   * Removes the specified condition from the status.
   * @param condition a condition
   */
  public void removeCondition(@Nonnull DomainCondition condition) {
    conditions.remove(condition);
    setStatusSummary();
  }

  /**
   * Marks any failures in the status with the specified reason as ready to be removed.
   * Adding a failure will unmark any matching one, allowing it to be preserved.
   * @see #removeMarkedFailures()
   * @param reason the reason for the failure
   */
  public void markFailuresForRemoval(DomainFailureReason reason) {
    conditions.stream().filter(c -> c.isSpecifiedFailure(reason)).forEach(DomainCondition::markForDeletion);
  }

  /**
   * Removes any failures currently marked.
   * @see #markFailuresForRemoval(DomainFailureReason) 
   */
  public void removeMarkedFailures() {
    removeConditionsMatching(DomainCondition::isMarkedForDeletion);
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

  private DomainFailureReason getReason(DomainCondition newCondition) {
    return newCondition.getReason();
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

  public Long getObservedGeneration() {
    return observedGeneration;
  }

  public void setObservedGeneration(Long observedGeneration) {
    this.observedGeneration = observedGeneration;
  }

  /**
   * Returns true if the status has a condition indicating that the domain is currently rolling.
   */
  public boolean isRolling() {
    return conditions.stream().anyMatch(c -> c.getType().equals(ROLLING));
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
   */
  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
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
   * Returns the UID of the last failed introspection job.
   */
  public String getFailedIntrospectionUid() {
    return failedIntrospectionUid;
  }

  /**
   * Records the UID of a failed introspection job.
   * @param failedIntrospectionUid  the Kubernetes-assigned UID of the job which discovered the introspection failure
   */
  public void setFailedIntrospectionUid(String failedIntrospectionUid) {
    this.failedIntrospectionUid = failedIntrospectionUid;
  }

  /**
   * Status of WebLogic Servers in this domain.
   *
   * @return a sorted list of ServerStatus containing status of WebLogic Servers in this domain
   */
  public List<ServerStatus> getServers() {
    synchronized (servers) {
      return new ArrayList<>(servers);
    }
  }

  /**
   * Status of WebLogic Servers in this domain.
   *
   * @param servers servers
   */
  public void setServers(List<ServerStatus> servers) {
    synchronized (this.servers) {
      if (this.servers.equals(servers)) {
        return;
      }

      List<ServerStatus> newServers = servers
            .stream()
            .map(ServerStatus::new)
            .map(this::adjust)
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.toList());

      this.servers.clear();
      this.servers.addAll(newServers);
    }
  }

  private ServerStatus adjust(ServerStatus server) {
    if (server.getState() == null) {
      ServerStatus oldServer = getMatchingServer(server);
      if ((oldServer != null) && (oldServer.getHealth() == null)) {
        return server;
      }
      server.setState(oldServer == null ? SHUTDOWN_STATE : oldServer.getState());
    }
    return server;
  }

  private ServerStatus getMatchingServer(ServerStatus server) {
    return getServers()
          .stream()
          .filter(s -> Objects.equals(s.getClusterName(), server.getClusterName()))
          .filter(s -> Objects.equals(s.getServerName(), server.getServerName()))
          .findFirst()
          .orElse(null);
  }


  /**
   * Status of WebLogic Servers in this domain.
   *
   * @param servers servers
   * @return this
   */
  public DomainStatus withServers(List<ServerStatus> servers) {
    setServers(servers);
    return this;
  }

  /**
   * Add the status for a server.
   * @param server the status for one server
   * @return this object
   */
  public DomainStatus addServer(ServerStatus server) {
    synchronized (servers) {
      servers.removeIf(serverStatus -> Objects.equals(serverStatus.getServerName(), server.getServerName()));

      servers.add(server);
      Collections.sort(servers);
    }
    return this;
  }

  /**
   * Status of WebLogic clusters in this domain.
   *
   * @return a sorted list of ClusterStatus containing status of WebLogic clusters in this domain
   */
  public List<ClusterStatus> getClusters() {
    synchronized (clusters) {
      return new ArrayList<>(clusters);
    }
  }

  /**
   * Set the clusters list.
   * @param clusters the list of clusters to use
   */
  public void setClusters(List<ClusterStatus> clusters) {
    synchronized (this.clusters) {
      if (isClustersEqualIgnoringOrder(clusters, this.clusters)) {
        return;
      }

      List<ClusterStatus> sortedClusters = new ArrayList<>(clusters);
      sortedClusters.sort(Comparator.naturalOrder());

      this.clusters.clear();
      this.clusters.addAll(sortedClusters);
    }
  }

  private boolean isClustersEqualIgnoringOrder(List<ClusterStatus> clusters1, List<ClusterStatus> clusters2) {
    return new HashSet<>(clusters1).equals(new HashSet<>(clusters2));
  }

  /**
   * Add the status for a cluster.
   * @param cluster the status for one cluster
   * @return this object
   */
  public DomainStatus addCluster(ClusterStatus cluster) {
    synchronized (clusters) {
      clusters.removeIf(clusterStatus -> Objects.equals(clusterStatus.getClusterName(), cluster.getClusterName()));

      clusters.add(cluster);
      Collections.sort(clusters);
    }
    return this;
  }

  /**
   * RFC 3339 date and time at which the operator started the domain. This will be when the operator
   * begins processing and will precede when the various servers or clusters are available.
   *
   * @return start time
   */
  OffsetDateTime getStartTime() {
    return startTime;
  }

  /**
   * The time that the domain was started.
   *
   * @param startTime time
   */
  public void setStartTime(OffsetDateTime startTime) {
    this.startTime = startTime;
  }

  /**
   * The time that the domain was started.
   *
   * @param startTime time
   * @return this
   */
  public DomainStatus withStartTime(OffsetDateTime startTime) {
    this.startTime = startTime;
    return this;
  }

  /**
   * Returns the time that the current domain started automatic retries in response to a severe failure.
   */
  public OffsetDateTime getInitialFailureTime() {
    return initialFailureTime;
  }

  /**
   * Returns the time that the last severe failure was reported.
   */
  public OffsetDateTime getLastFailureTime() {
    return lastFailureTime;
  }

  /**
   * Return the number of times that failure timeouts should be increased as a result of failures.
   * Will be approximately the number of times a failure has been reported, assuming one failure per retry.
   * @param retrySeconds the number of seconds between retries.
   */
  public int getNumDeadlineIncreases(long retrySeconds) {
    if (initialFailureTime == null || lastFailureTime == null) {
      return 0;
    } else {
      return (int) (1 + divideRoundingUp(getSecondsFromInitialToLastFailure(), retrySeconds));
    }
  }

  private long divideRoundingUp(Long dividend, long divisor) {
    return (dividend + divisor - 1) / divisor;
  }

  /**
   * Returns the number of seconds between the first failure reported and the most recent one.
   */
  public Long getSecondsFromInitialToLastFailure() {
    return Duration.between(initialFailureTime, lastFailureTime).getSeconds();
  }

  public long getMinutesFromInitialToLastFailure() {
    return TimeUnit.SECONDS.toMinutes(getSecondsFromInitialToLastFailure());
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("conditions", conditions)
        .append("message", message)
        .append("reason", reason)
        .append("observedGeneration", observedGeneration)
        .append("servers", servers)
        .append("clusters", clusters)
        .append("startTime", startTime)
        .append("initialFailureTime", initialFailureTime)
        .append("lastFailureTime", lastFailureTime)
        .append("failedIntrospectionUid", failedIntrospectionUid)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(reason)
        .append(observedGeneration)
        .append(startTime)
        .append(initialFailureTime)
        .append(lastFailureTime)
        .append(DomainResource.sortList(servers))
        .append(DomainResource.sortList(clusters))
        .append(DomainResource.sortList(conditions))
        .append(message)
        .append(failedIntrospectionUid)
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
        .append(observedGeneration, rhs.observedGeneration)
        .append(startTime, rhs.startTime)
        .append(initialFailureTime, rhs.initialFailureTime)
        .append(lastFailureTime, rhs.lastFailureTime)
        .append(servers, rhs.servers)
        .append(DomainResource.sortList(clusters), DomainResource.sortList(rhs.clusters))
        .append(DomainResource.sortList(conditions), DomainResource.sortList(rhs.conditions))
        .append(message, rhs.message)
        .append(failedIntrospectionUid, rhs.failedIntrospectionUid)
        .isEquals();
  }

  private static final ObjectPatch<DomainStatus> statusPatch = createObjectPatch(DomainStatus.class)
        .withConstructor(DomainStatus::new)
        .withStringField("message", DomainStatus::getMessage)
        .withStringField("reason", DomainStatus::getReason)
        .withBooleanField("rolling", DomainStatus::isRolling)
        .withStringField("failedIntrospectionUid", DomainStatus::getFailedIntrospectionUid)
        .withIntegerField("replicas", DomainStatus::getReplicas)
        .withListField("conditions", DomainCondition.getObjectPatch(), DomainStatus::getConditions)
        .withListField("clusters", ClusterStatus.getObjectPatch(), DomainStatus::getClusters)
        .withListField("servers", ServerStatus.getObjectPatch(), DomainStatus::getServers);

  public void createPatchFrom(JsonPatchBuilder builder, @Nullable DomainStatus oldStatus) {
    statusPatch.createPatch(builder, "/status", oldStatus, this);
  }

  /**
   * Returns true if the failure count in the status is equal to or greater than the configured maximum.
   */
  public boolean isAborted() {
    return Optional.ofNullable(conditions).orElse(Collections.emptyList()).stream().anyMatch(this::isAbortedFailure);
  }

  private boolean isAbortedFailure(DomainCondition domainCondition) {
    return ABORTED == domainCondition.getReason();
  }

  /**
   * Computes a failure condition that accounts for retries and failed inspection messages.
   * @param reason the underlying reason
   * @param message the underlying message
   */
  public DomainCondition createAdjustedFailedCondition(DomainFailureReason reason, String message,
                                                       boolean isInitDomainOnPV) {
    DomainFailureReason effectiveReason = reason;
    String effectiveMessage = message;
    if (hasJustGotFatalIntrospectorError(effectiveMessage)
        || initDomainOnPVIntropsectionFailure(reason, isInitDomainOnPV)) {
      effectiveReason = ABORTED;
      effectiveMessage = FATAL_INTROSPECTOR_ERROR_MSG + effectiveMessage;
    }
    return new DomainCondition(FAILED).withReason(effectiveReason).withMessage(effectiveMessage);
  }

  private boolean hasJustGotFatalIntrospectorError(String message) {
    return isFatalIntrospectorMessage(message) && lacksFatalIntrospectorError();
  }

  private boolean lacksFatalIntrospectorError() {
    return !isFatalIntrospectorMessage(getMessage());
  }

  private boolean isFatalIntrospectorMessage(String statusMessage) {
    return statusMessage != null && statusMessage.contains(FATAL_INTROSPECTOR_ERROR);
  }
}
