// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import oracle.kubernetes.operator.http.rest.model.AdmissionResponse;
import oracle.kubernetes.operator.http.rest.model.AdmissionResponseStatus;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.jetbrains.annotations.NotNull;

import static java.lang.System.lineSeparator;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_REPLICAS_CANNOT_BE_HONORED;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_REPLICAS_TOO_HIGH;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INTROSPECTION_TRIGGER_CHANGED;
import static oracle.kubernetes.operator.KubernetesConstants.AUXILIARY_IMAGES;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_INTROSPECT_VERSION;
import static org.apache.commons.collections4.CollectionUtils.isEqualCollection;

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

public class AdmissionChecker {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");

  private final DomainResource existingDomain;
  private final DomainResource proposedDomain;
  private final List<String> messages = new ArrayList<>();
  private final List<String> warnings = new ArrayList<>();
  private final String domainUid;

  /** Construct a AdmissionChecker. */
  public AdmissionChecker(@NotNull DomainResource existingDomain, @NotNull DomainResource proposedDomain) {
    this.existingDomain = existingDomain;
    this.proposedDomain = proposedDomain;
    this.domainUid = existingDomain.getDomainUid();
  }

  /**
   * Validating a proposed DomainResource resource against an existing DomainResource resource.
   *
   * @return a AdmissionResponse object
   */
  public AdmissionResponse validate() {
    LOGGER.fine("Validating DomainResource " + proposedDomain + " against " + existingDomain);

    AdmissionResponse response = new AdmissionResponse().allowed(isProposedChangeAllowed());
    if (!response.isAllowed()) {
      return response.status(new AdmissionResponseStatus().message(createMessage()));
    } else if (!warnings.isEmpty()) {
      return response.warnings(warnings);
    }
    return response;
  }

  private String createMessage() {
    return perLine(messages);
  }

  /**
   * Validating a proposed Domain resource against an existing DomainResource resource. It returns true if the
   * proposed changes in the proposed DomainResource resource can be honored, otherwise, returns false.
   *
   * @return true if valid, otherwise false
   */
  public boolean isProposedChangeAllowed() {
    return isUnchanged()
        || areAllClusterReplicaCountsValid(proposedDomain)
        || shouldIntrospect();
  }

  private boolean isUnchanged() {
    return isSpecUnchanged();
  }

  private boolean isSpecUnchanged() {
    return Optional.of(existingDomain)
        .map(DomainResource::getSpec)
        .map(this::isProposedSpecUnchanged)
        .orElse(false);
  }

  private boolean isProposedSpecUnchanged(@NotNull DomainSpec existingSpec) {
    return Objects.equals(existingSpec, proposedDomain.getSpec());
  }

  private boolean shouldIntrospect() {
    return isIntrospectVersionChanged() || imagesChanged();
  }

  private boolean isIntrospectVersionChanged() {
    boolean changed = !Objects.equals(existingDomain.getIntrospectVersion(), proposedDomain.getIntrospectVersion());
    if (changed) {
      warnings.add(LOGGER.formatMessage(DOMAIN_INTROSPECTION_TRIGGER_CHANGED, DOMAIN_INTROSPECT_VERSION));
    }
    return changed;
  }

  private boolean imagesChanged() {
    if (!Objects.equals(existingDomain.getDomainHomeSourceType(), proposedDomain.getDomainHomeSourceType())) {
      return true;
    }
    switch (proposedDomain.getDomainHomeSourceType()) {
      case IMAGE:
        return isDomainImageChanged();
      case FROM_MODEL:
        return areAuxiliaryImagesOrDomainImageChanged();
      default:
        return false;
    }
  }

  private boolean areAuxiliaryImagesOrDomainImageChanged() {
    if (noAuxiliaryImagesConfigured(existingDomain) && noAuxiliaryImagesConfigured(proposedDomain)) {
      return isDomainImageChanged();
    } else {
      boolean changed =  noAuxiliaryImagesConfigured(existingDomain)
          || noAuxiliaryImagesConfigured(proposedDomain)
          || areAuxiliaryImagesChanged();
      if (changed) {
        warnings.add(LOGGER.formatMessage(DOMAIN_INTROSPECTION_TRIGGER_CHANGED, AUXILIARY_IMAGES));
      }
      return changed;
    }
  }

  private boolean isDomainImageChanged() {
    boolean imageChanged = !Objects.equals(getImage(existingDomain), getImage(proposedDomain));
    if (imageChanged) {
      warnings.add(LOGGER.formatMessage(DOMAIN_INTROSPECTION_TRIGGER_CHANGED, DOMAIN_IMAGE));
    }
    return imageChanged;
  }

  private boolean areAuxiliaryImagesChanged() {
    return !isEqualCollection(existingDomain.getAuxiliaryImages(), proposedDomain.getAuxiliaryImages());
  }

  private String getImage(@NotNull DomainResource existingDomain) {
    return Optional.of(existingDomain).map(DomainResource::getSpec).map(DomainSpec::getImage).orElse(null);
  }

  private boolean noAuxiliaryImagesConfigured(@NotNull DomainResource domain) {
    return domain.getAuxiliaryImages() == null;
  }

  private boolean areAllClusterReplicaCountsValid(@NotNull DomainResource domain) {
    return getClusterStatusList(domain).stream().allMatch(c -> isClusterReplicaCountValid(domain, c));
  }

  @NotNull
  private List<ClusterStatus> getClusterStatusList(@NotNull DomainResource domain) {
    return Optional.of(domain)
        .map(DomainResource::getStatus)
        .map(DomainStatus::getClusters)
        .orElse(Collections.emptyList());
  }

  private Boolean isClusterReplicaCountValid(@NotNull DomainResource domain, @NotNull ClusterStatus status) {
    boolean isValid =
        getProposedReplicaCount(domain, getCluster(domain, status.getClusterName())) <= getClusterSize(status);
    if (!isValid) {
      messages.add(LOGGER.formatMessage(CLUSTER_REPLICAS_CANNOT_BE_HONORED,
          domainUid, status.getClusterName(), getClusterSize(status)));
      warnings.add(LOGGER.formatMessage(CLUSTER_REPLICAS_TOO_HIGH,
          domainUid, status.getClusterName(), getClusterSize(status)));
    }
    return isValid;
  }

  private Cluster getCluster(@NotNull DomainResource domain, String clusterName) {
    return Optional.of(domain).map(DomainResource::getSpec)
        .map(DomainSpec::getClusters)
        .orElse(Collections.emptyList())
        .stream().filter(c -> nameMatches(c, clusterName)).findAny().orElse(null);
  }

  private boolean nameMatches(Cluster cluster, String clusterName) {
    return Optional.ofNullable(cluster).map(Cluster::getClusterName).orElse("").equals(clusterName);
  }

  private int getClusterSize(ClusterStatus clusterStatus) {
    return Optional.ofNullable(clusterStatus).map(ClusterStatus::getMaximumReplicas).orElse(0);
  }

  private int getProposedReplicaCount(@NotNull DomainResource domain, Cluster cluster) {
    return Optional.ofNullable(cluster).map(Cluster::getReplicas).orElse(getDomainReplicaCount(domain));
  }

  private int getDomainReplicaCount(@NotNull DomainResource domain) {
    return Optional.of(domain).map(DomainResource::getSpec).map(DomainSpec::getReplicas).orElse(0);
  }

  private String perLine(List<String> errors) {
    return String.join(lineSeparator(), errors);
  }
}
