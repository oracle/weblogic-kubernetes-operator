// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponse;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponseStatus;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_REPLICAS_CANNOT_BE_HONORED;

/**
 * AdmissionChecker provides the validation functionality for the validating webhook. It takes an existing resource and
 * a proposed resource and returns a result to indicate if the proposed changes are allowed, and if not,
 * what the problem is.
 *
 * <p>Currently it checks the following:
 * <ul>
 * <li>The proposed replicas settings at the domain level and/or cluster level can be honored by WebLogic domain config.
 * </li>
 * </ul>
 * </p>
 */

public class ClusterAdmissionChecker extends AdmissionChecker {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");

  private final ClusterResource existingCluster;
  private final ClusterResource proposedCluster;
  private final AdmissionResponse response = new AdmissionResponse();
  private Exception exception;

  /** Construct a ClusterAdmissionChecker. */
  public ClusterAdmissionChecker(@NotNull ClusterResource existingCluster, @NotNull ClusterResource proposedCluster) {
    this.existingCluster = existingCluster;
    this.proposedCluster = proposedCluster;
  }

  /**
   * Validating a proposed ClusterResource resource against an existing ClusterResource resource.
   *
   * @return a AdmissionResponse object
   */
  @Override
  public AdmissionResponse validate() {
    LOGGER.fine("Validating ClusterResource " + proposedCluster + " against " + existingCluster);

    response.allowed(isProposedChangeAllowed());
    if (!response.isAllowed()) {
      if (exception == null) {
        return response.status(new AdmissionResponseStatus().message(createMessage()));
      } else {
        return response.status(new AdmissionResponseStatus().message(exception.getMessage()));
      }
    }
    return response;
  }

  /**
   * Validating a proposed Cluster resource against an existing ClusterResource resource. It returns true if the
   * proposed changes in the proposed ClusterResource resource can be honored, otherwise, returns false.
   *
   * @return true if valid, otherwise false
   */
  @Override
  public boolean isProposedChangeAllowed() {
    return isUnchanged() || skipValidation(proposedCluster.getStatus()) || isReplicaCountValid();
  }

  private boolean isReplicaCountValid() {
    boolean isValid = (getClusterReplicaCount() != null
        ? getClusterReplicaCount() <= getClusterSize(proposedCluster.getStatus())
        : isDomainReplicaCountValid());

    if (!isValid) {
      messages.add(LOGGER.formatMessage(CLUSTER_REPLICAS_CANNOT_BE_HONORED,
          existingCluster.getClusterName(), getClusterSize(existingCluster.getStatus())));
    }
    return isValid;
  }

  private boolean skipValidation(ClusterStatus clusterStatus) {
    return getClusterSizeOptional(clusterStatus).isEmpty();
  }

  private Integer getClusterReplicaCount() {
    return Optional.of(proposedCluster).map(ClusterResource::getSpec).map(ClusterSpec::getReplicas).orElse(null);
  }

  private boolean isDomainReplicaCountValid() {
    try {
      return getDomainResources(proposedCluster).stream()
          .allMatch(domain -> getDomainReplicaCount(domain) <= getClusterSize(proposedCluster.getStatus()));
    } catch (ApiException e) {
      exception = e;
      return false;
    }

  }

  public boolean hasException() {
    return exception != null;
  }

  private List<DomainResource> getDomainResources(ClusterResource proposedCluster) throws ApiException {
    return referencingDomains(proposedCluster, new CallBuilder().listDomain(getNamespace(proposedCluster)));
  }

  private List<DomainResource> referencingDomains(ClusterResource proposedCluster, DomainList domains) {
    String name = proposedCluster.getMetadata().getName();
    List<DomainResource> referencingDomains = new ArrayList<>();
    Optional.ofNullable(domains).map(DomainList::getItems).ifPresent(list -> list.stream()
        .filter(item -> referencesCluster(name, item)).forEach(referencingDomains::add));
    return referencingDomains;
  }

  private boolean referencesCluster(String name, DomainResource domain) {
    List<V1LocalObjectReference> refs = Optional.ofNullable(domain).map(DomainResource::getSpec)
        .map(DomainSpec::getClusters).orElse(Collections.emptyList());
    return refs.stream().anyMatch(item -> name.equals(item.getName()));
  }

  private String getNamespace(ClusterResource cluster) {
    return Optional.of(cluster).map(ClusterResource::getMetadata).map(V1ObjectMeta::getNamespace).orElse("");
  }

  private boolean isUnchanged() {
    return isSpecUnchanged();
  }

  private boolean isSpecUnchanged() {
    return Optional.of(existingCluster)
        .map(ClusterResource::getSpec)
        .map(this::isProposedSpecUnchanged)
        .orElse(false);
  }

  private boolean isProposedSpecUnchanged(@NotNull ClusterSpec existingSpec) {
    return Objects.equals(existingSpec, proposedCluster.getSpec());
  }

}
