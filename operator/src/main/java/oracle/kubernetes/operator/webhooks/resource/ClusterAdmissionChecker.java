// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

/**
 * ClusterAdmissionChecker provides the common functionality of validating webhook for cluster resources.
 */

public abstract class ClusterAdmissionChecker extends AdmissionChecker {
  static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
  protected ClusterResource proposedCluster;
  protected final AdmissionResponse response = new AdmissionResponse();
  protected Exception exception;

  abstract String getErrorMessage();

  AdmissionResponse validateIt() {
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

  boolean isReplicaCountValid() {
    boolean isValid = (getClusterReplicaCount() != null
        ? getClusterReplicaCount() <= getClusterSize(proposedCluster.getStatus())
        : isDomainReplicaCountValid());

    if (!isValid) {
      messages.add(LOGGER.formatMessage(getErrorMessage(),
          proposedCluster.getClusterName(), getClusterSize(proposedCluster.getStatus())));
    }
    return isValid;
  }

  boolean skipValidation(ClusterStatus clusterStatus) {
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

  /**
   * Check if the validation causes an Exception.
   *
   * @return true if the validation causes an Exception
   */
  public boolean hasException() {
    return exception != null;
  }

  List<DomainResource> getDomainResources(ClusterResource clusterResource) throws ApiException {
    return referencingDomains(clusterResource, new CallBuilder().listDomain(getNamespace(clusterResource)));
  }

  private List<DomainResource> referencingDomains(ClusterResource clusterResource, DomainList domains) {
    String name = clusterResource.getMetadata().getName();
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
}
