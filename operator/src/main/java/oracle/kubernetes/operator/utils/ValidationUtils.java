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

public class ValidationUtils {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");

  private ValidationUtils() {
  }

  /**
   * Validating a domain resource.
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

  private static boolean unchanged(Domain existingDomain, Domain proposedDomain) {
    return existingDomain == proposedDomain || Objects.equals(existingDomain, proposedDomain);
  }

  private static boolean shouldIntrospect(Domain existingDomain, Domain proposedDomain) {
    return !Objects.equals(existingDomain.getIntrospectVersion(), proposedDomain.getIntrospectVersion())
        || imagesChanged(existingDomain, proposedDomain);
  }

  private static boolean imagesChanged(Domain existingDomain, Domain proposedDomain) {
    if (!Objects.equals(existingDomain.getDomainHomeSourceType(), proposedDomain.getDomainHomeSourceType())) {
      return true;
    }
    switch (proposedDomain.getDomainHomeSourceType()) {
      case IMAGE:
        return !Objects.equals(existingDomain.getSpec().getImage(), proposedDomain.getSpec().getImage());
      case FROM_MODEL:
        if (noAuxiliaryImagesConfigured(existingDomain) && noAuxiliaryImagesConfigured(proposedDomain)) {
          return !Objects.equals(existingDomain.getSpec().getImage(), proposedDomain.getSpec().getImage());
        } else {
          return noAuxiliaryImagesConfigured(existingDomain)
              || noAuxiliaryImagesConfigured(proposedDomain)
              || !isEqualCollection(existingDomain.getAuxiliaryImages(), proposedDomain.getAuxiliaryImages());
        }
      default:
        return false;
    }
  }

  private static boolean noAuxiliaryImagesConfigured(Domain domain) {
    return domain.getAuxiliaryImages() == null;
  }

  private static boolean validReplicas(Domain domain) {
    return Optional.ofNullable(domain)
        .map(Domain::getStatus)
        .map(DomainStatus::getClusters)
        .orElse(Collections.emptyList())
        .stream()
        .allMatch(c -> validClusterReplicas(domain, c));
  }

  private static Boolean validClusterReplicas(Domain domain, ClusterStatus status) {
    return getProposedReplicas(domain, getCluster(domain, status.getClusterName())) <= getClusterSize(status);
  }

  private static Cluster getCluster(Domain domain, String clusterName) {
    return Optional.ofNullable(domain).map(Domain::getSpec)
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

  private static int getProposedReplicas(Domain domain, Cluster cluster) {
    return Optional.ofNullable(cluster).map(Cluster::getReplicas).orElse(getDomainReplicas(domain));
  }


  private static int getDomainReplicas(Domain domain) {
    return Optional.ofNullable(domain).map(Domain::getSpec).map(DomainSpec::getReplicas).orElse(0);
  }
}
