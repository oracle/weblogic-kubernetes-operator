// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.jetbrains.annotations.NotNull;

import static org.apache.commons.collections4.CollectionUtils.isEqualCollection;

/**
 * ValidationUtil provides the utility methods to perform the validation of a proposed change to an existing
 * Domain or Cluster resource. It is used by the validating webhook.
 *
 * <p>The current version supports validation of the following:
 * <ul>
 * <li>The proposed replicas settings at the domain level and/or cluster level can be honored by WebLogic domain config.
 * </li>
 * </ul>
 * </p>
 */

public class ValidationUtils {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");

  private ValidationUtils() {
  }

  /**
   * Validating a proposed domain resource against an existing domain resource. It returns true if the proposed changes
   * in the proposed domain resource can be honored, otherwise, returns false.
   *
   * @param existingDomain domain that needs to be validated against.
   * @param proposedDomain domain that needs to be validated.
   * @return true if valid, otherwise false
   */
  public static boolean validateDomain(@NotNull Domain existingDomain, @NotNull Domain proposedDomain) {
    LOGGER.fine("Validating domain " + proposedDomain + " against " + existingDomain);
    return unchanged(existingDomain, proposedDomain)
        || validReplicas(proposedDomain)
        || shouldIntrospect(existingDomain, proposedDomain);
  }

  private static boolean unchanged(@NotNull Domain existingDomain, @NotNull Domain proposedDomain) {
    return existingDomain == proposedDomain || specEquals(existingDomain, proposedDomain);
  }

  private static boolean specEquals(@NotNull Domain existingDomain, @NotNull Domain proposedDomain) {
    return Optional.of(existingDomain)
        .map(Domain::getSpec)
        .map(s -> isProposedSpecUnchanged(s, proposedDomain))
        .orElse(false);
  }

  private static boolean isProposedSpecUnchanged(@NotNull DomainSpec existingSpec, @NotNull Domain proposedDomain) {
    return existingSpec == proposedDomain.getSpec() || Objects.equals(existingSpec, proposedDomain.getSpec());
  }

  private static boolean shouldIntrospect(@NotNull Domain existingDomain, @NotNull Domain proposedDomain) {
    return !Objects.equals(existingDomain.getIntrospectVersion(), proposedDomain.getIntrospectVersion())
        || imagesChanged(existingDomain, proposedDomain);
  }

  private static boolean imagesChanged(@NotNull Domain existingDomain, @NotNull Domain proposedDomain) {
    if (!Objects.equals(existingDomain.getDomainHomeSourceType(), proposedDomain.getDomainHomeSourceType())) {
      return true;
    }
    switch (proposedDomain.getDomainHomeSourceType()) {
      case IMAGE:
        return !Objects.equals(getImage(existingDomain), getImage(proposedDomain));
      case FROM_MODEL:
        if (noAuxiliaryImagesConfigured(existingDomain) && noAuxiliaryImagesConfigured(proposedDomain)) {
          return !Objects.equals(getImage(existingDomain), getImage(proposedDomain));
        } else {
          return noAuxiliaryImagesConfigured(existingDomain)
              || noAuxiliaryImagesConfigured(proposedDomain)
              || !isEqualCollection(existingDomain.getAuxiliaryImages(), proposedDomain.getAuxiliaryImages());
        }
      default:
        return false;
    }
  }

  private static String getImage(@NotNull Domain existingDomain) {
    return Optional.of(existingDomain).map(Domain::getSpec).map(DomainSpec::getImage).orElse(null);
  }

  private static boolean noAuxiliaryImagesConfigured(@NotNull Domain domain) {
    return domain.getAuxiliaryImages() == null;
  }

  private static boolean validReplicas(@NotNull Domain domain) {
    return Optional.of(domain)
        .map(Domain::getStatus)
        .map(DomainStatus::getClusters)
        .orElse(Collections.emptyList())
        .stream()
        .allMatch(c -> validClusterReplicas(domain, c));
  }

  private static Boolean validClusterReplicas(@NotNull Domain domain, @NotNull ClusterStatus status) {
    return getProposedReplicas(domain, getCluster(domain, status.getClusterName())) <= getClusterSize(status);
  }

  private static Cluster getCluster(@NotNull Domain domain, String clusterName) {
    return Optional.of(domain).map(Domain::getSpec)
        .map(DomainSpec::getClusters)
        .orElse(Collections.emptyList())
        .stream().filter(c -> nameMatches(c, clusterName)).findAny().orElse(null);
  }

  private static boolean nameMatches(Cluster cluster, String clusterName) {
    return Optional.ofNullable(cluster).map(Cluster::getClusterName).orElse("").equals(clusterName);
  }

  private static int getClusterSize(ClusterStatus clusterStatus) {
    return Optional.ofNullable(clusterStatus).map(ClusterStatus::getMaximumReplicas).orElse(0);
  }

  private static int getProposedReplicas(@NotNull Domain domain, Cluster cluster) {
    return Optional.ofNullable(cluster).map(Cluster::getReplicas).orElse(getDomainReplicas(domain));
  }

  private static int getDomainReplicas(@NotNull Domain domain) {
    return Optional.of(domain).map(Domain::getSpec).map(DomainSpec::getReplicas).orElse(0);
  }
}
