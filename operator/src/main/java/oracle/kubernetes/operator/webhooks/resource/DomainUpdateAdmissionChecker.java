// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponse;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponseStatus;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;

import static java.lang.Boolean.FALSE;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INTROSPECTION_TRIGGER_CHANGED;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_REPLICAS_CANNOT_BE_HONORED;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_REPLICAS_CANNOT_BE_HONORED_MULTIPLE_CLUSTERS;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_REPLICAS_TOO_HIGH;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_REPLICAS_TOO_HIGH_MULTIPLE_CLUSTERS;
import static oracle.kubernetes.operator.KubernetesConstants.AUXILIARY_IMAGES;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_INTROSPECT_VERSION;
import static org.apache.commons.collections4.CollectionUtils.isEqualCollection;

/**
 * DomainUpdateAdmissionChecker provides the validation functionality for the validating webhook. It takes an existing
 * resource and a proposed resource and returns a result to indicate if the proposed changes are allowed, and if not,
 * what the problem is.
 *
 * <p>Currently it checks the following:
 * <ul>
 * <li>There are fatal domain validation errors.
 * </li>
 * <li>The proposed replicas settings at the domain level and/or cluster level can be honored by WebLogic domain config.
 * </li>
 * </ul>
 * </p>
 */

public class DomainUpdateAdmissionChecker extends AdmissionChecker {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");

  private final DomainResource existingDomain;
  private final DomainResource proposedDomain;
  final List<ClusterStatus> failed = new ArrayList<>();
  final List<String> warnings = new ArrayList<>();
  private Exception exception;

  /** Construct a DomainAdmissionChecker. */
  public DomainUpdateAdmissionChecker(@Nonnull DomainResource existingDomain, @Nonnull DomainResource proposedDomain) {
    this.existingDomain = existingDomain;
    this.proposedDomain = proposedDomain;
  }

  @Override
  AdmissionResponse validate() {
    LOGGER.fine("Validating DomainResource " + proposedDomain + " against " + existingDomain);

    AdmissionResponse response = new AdmissionResponse().allowed(isProposedChangeAllowed());
    if (!response.isAllowed()) {
      return response.status(new AdmissionResponseStatus().message(createMessage()));
    } else if (!warnings.isEmpty()) {
      return response.warnings(warnings);
    }
    return response;
  }

  @Override
  public boolean isProposedChangeAllowed() {
    return isSpecUnchanged() || areChangesAllowed();
  }

  private boolean areChangesAllowed() {
    return hasNoFatalValidationErrors(proposedDomain) && isProposedReplicaCountValid();
  }

  private boolean isProposedReplicaCountValid() {
    return areAllClusterReplicaCountsValid(proposedDomain) || shouldIntrospect();
  }

  private boolean isSpecUnchanged() {
    return Optional.of(existingDomain)
        .map(DomainResource::getSpec)
        .map(this::isProposedSpecUnchanged)
        .orElse(false);
  }

  private boolean isProposedSpecUnchanged(@Nonnull DomainSpec existingSpec) {
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

  boolean areAllClusterReplicaCountsValid(DomainResource domain) {
    boolean allValid = true;
    List<String> names = new ArrayList<>();
    List<Integer> replicaCounts = new ArrayList<>();
    for (ClusterStatus status : getClusterStatusList(domain)) {
      if (FALSE.equals(isReplicaCountValid(domain, status))) {
        allValid = false;
        names.add(status.getClusterName());
        replicaCounts.add(getClusterSize(status));
      }
    }
    String nameList = String.join(", ", names);
    String replicasList = replicaCounts.stream().map(Object::toString).collect(Collectors.joining(", "));
    if (!allValid) {
      messages.add(LOGGER.formatMessage(getDomainReplicasCannotBeHonoredMsg(names),
            domain.getDomainUid(), nameList, replicasList));
      warnings.add(LOGGER.formatMessage(getDomainReplicasTooHighMsg(names),
          domain.getDomainUid(), nameList, replicasList));
    }
    return allValid;
  }

  @Nonnull
  private String getDomainReplicasTooHighMsg(List<String> list) {
    return list.size() > 1 ? DOMAIN_REPLICAS_TOO_HIGH_MULTIPLE_CLUSTERS : DOMAIN_REPLICAS_TOO_HIGH;
  }

  @Nonnull
  private String getDomainReplicasCannotBeHonoredMsg(List<String> list) {
    return list.size() > 1 ? DOMAIN_REPLICAS_CANNOT_BE_HONORED_MULTIPLE_CLUSTERS : DOMAIN_REPLICAS_CANNOT_BE_HONORED;
  }

  @Nonnull
  private List<ClusterStatus> getClusterStatusList(@Nonnull DomainResource domain) {
    return Optional.of(domain)
        .map(DomainResource::getStatus)
        .map(DomainStatus::getClusters)
        .orElse(Collections.emptyList());
  }

  private Boolean isReplicaCountValid(@Nonnull DomainResource domain, @Nonnull ClusterStatus status) {
    boolean isValid = false;
    try {
      isValid = getProposedReplicaCount(domain, getCluster(domain, status.getClusterName())) <= getClusterSize(status);
      if (!isValid) {
        failed.add(status);
      }
    } catch (ApiException e) {
      exception = e;
    }
    return isValid;
  }

  private int getProposedReplicaCount(@Nonnull DomainResource domain, ClusterSpec clusterSpec) {
    return Optional.ofNullable(clusterSpec).map(ClusterSpec::getReplicas).orElse(getDomainReplicaCount(domain));
  }

  /**
   * Check if the validation causes an Exception.
   *
   * @return true if the validation causes an Exception
   */
  public boolean hasException() {
    return exception != null;
  }

  /**
   * Check if the validation causes warnings.
   * For unit test only.
   *
   * @return true if the validation causes an Exception
   */
  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }

  /**
   * Get the validation warnings.
   * For unit test only.
   *
   * @return list of warnings
   */
  public List<String> getWarnings() {
    return warnings;
  }

  private ClusterSpec getCluster(@Nonnull DomainResource domain, String clusterName) throws ApiException {
    List<ClusterResource> clusters = getClusters(domain.getNamespace());
    return clusters.stream().filter(cluster -> clusterName.equals(cluster.getClusterName())
        && isReferenced(domain, cluster)).findFirst().map(ClusterResource::getSpec).orElse(null);
  }

  private boolean isReferenced(@Nonnull DomainResource domain, ClusterResource cluster) {
    String name = Optional.ofNullable(cluster).map(ClusterResource::getMetadata).map(V1ObjectMeta::getName).orElse("");
    return Optional.of(domain).map(DomainResource::getSpec).map(DomainSpec::getClusters)
        .orElse(Collections.emptyList()).stream().anyMatch(ref -> name.equals(ref.getName()));
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

  private String getImage(@Nonnull DomainResource existingDomain) {
    return Optional.of(existingDomain).map(DomainResource::getSpec).map(DomainSpec::getImage).orElse(null);
  }

  private boolean noAuxiliaryImagesConfigured(@Nonnull DomainResource domain) {
    return domain.getAuxiliaryImages() == null;
  }
}
