// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.KubernetesResourceLookup;

import static java.lang.System.lineSeparator;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_VALIDATION_FAILED;
import static oracle.kubernetes.operator.DomainStatusUpdater.createRemoveSelectedFailuresStep;
import static oracle.kubernetes.operator.DomainStatusUpdater.createStatusUpdateStep;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_DOMAIN_INVALID_ERROR;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.DOMAIN_INVALID;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.REPLICAS_TOO_HIGH;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.TOPOLOGY_MISMATCH;

public class DomainValidationSteps {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String SECRETS = "secrets";
  private static final String CONFIGMAPS = "configmaps";
  private static final String CLUSTERS = "clusters";

  private DomainValidationSteps() {
  }

  /**
   * Returns a chain of steps to validate the domain in the current packet.
   * @param namespace the namespace for the domain
   */
  public static Step createDomainValidationSteps(String namespace) {
    return Step.chain(
          createListSecretsStep(namespace),
          createListConfigMapsStep(namespace),
          createListClustersStep(namespace),
          new DomainValidationStep());
  }

  static Step createAdditionalDomainValidationSteps(V1PodSpec podSpec) {
    return new DomainAdditionalValidationStep(podSpec);
  }

  static Step createValidateDomainTopologySteps(Step next) {
    return createStatusUpdateStep(new ValidateDomainTopologyStep(next));
  }

  private static Step createListSecretsStep(String domainNamespace) {
    return new CallBuilder().listSecretsAsync(domainNamespace, new ListSecretsResponseStep());
  }

  static class ListSecretsResponseStep extends DefaultResponseStep<V1SecretList> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1SecretList> callResponse) {
      List<V1Secret> list = getSecrets(packet);
      list.addAll(callResponse.getResult().getItems());
      packet.put(SECRETS, list);

      return doContinueListOrNext(callResponse, packet);
    }

    static List<V1Secret> getSecrets(Packet packet) {
      return Optional.ofNullable(packet.<List<V1Secret>>getValue(SECRETS)).orElse(new ArrayList<>());
    }
  }

  private static Step createListConfigMapsStep(String domainNamespace) {
    return new CallBuilder().listConfigMapsAsync(domainNamespace, new ListConfigMapsResponseStep());
  }

  static class ListConfigMapsResponseStep extends DefaultResponseStep<V1ConfigMapList> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMapList> callResponse) {
      List<V1ConfigMap> list = getConfigMaps(packet);
      list.addAll(callResponse.getResult().getItems());
      packet.put(CONFIGMAPS, list);

      return doContinueListOrNext(callResponse, packet);
    }

    static List<V1ConfigMap> getConfigMaps(Packet packet) {
      return Optional.ofNullable(packet.<List<V1ConfigMap>>getValue(CONFIGMAPS)).orElse(new ArrayList<>());
    }
  }

  private static Step createListClustersStep(String domainNamespace) {
    return new CallBuilder().listClusterAsync(domainNamespace, new ListClustersResponseStep());
  }

  static class ListClustersResponseStep extends DefaultResponseStep<ClusterList> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<ClusterList> callResponse) {
      List<ClusterResource> list = getClusters(packet);
      list.addAll(callResponse.getResult().getItems());
      packet.put(CLUSTERS, list);

      return doContinueListOrNext(callResponse, packet);
    }

    static List<ClusterResource> getClusters(Packet packet) {
      return Optional.ofNullable(packet.<List<ClusterResource>>getValue(CLUSTERS)).orElse(new ArrayList<>());
    }
  }

  static class DomainValidationStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      DomainResource domain = info.getDomain();
      List<String> fatalValidationFailures = domain.getFatalValidationFailures();
      List<String> validationFailures = domain.getValidationFailures(new KubernetesResourceLookupImpl(packet));
      String errorMsg = getErrorMessage(fatalValidationFailures, validationFailures);
      if (validationFailures.isEmpty()) {
        return doNext(createRemoveSelectedFailuresStep(getNext(), DOMAIN_INVALID), packet)
              .withDebugComment(packet, this::domainValidated);
      } else {
        LOGGER.severe(DOMAIN_VALIDATION_FAILED, domain.getDomainUid(), errorMsg);
        return doNext(DomainStatusUpdater.createDomainInvalidFailureSteps(errorMsg), packet);
      }
    }

    @Nonnull
    private String getErrorMessage(List<String> fatalValidationFailures, List<String> validationFailures) {
      String errorMsg;
      if (fatalValidationFailures.isEmpty()) {
        errorMsg = perLine(validationFailures);
      } else {
        errorMsg = FATAL_DOMAIN_INVALID_ERROR + ": " + perLine(fatalValidationFailures);
      }
      return errorMsg;
    }

    private String perLine(List<String> validationFailures) {
      return String.join(lineSeparator(), validationFailures);
    }

    private String domainValidated(Packet packet) {
      return "Validated " + DomainPresenceInfo.fromPacket(packet).orElse(null);
    }
    
  }

  static class DomainAdditionalValidationStep extends Step {
    final V1PodSpec podSpec;

    DomainAdditionalValidationStep(V1PodSpec podSpec) {
      this.podSpec = podSpec;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      DomainResource domain = info.getDomain();
      List<String> validationFailures = domain.getAdditionalValidationFailures(podSpec);

      if (validationFailures.isEmpty()) {
        return doNext(packet);
      }

      LOGGER.severe(DOMAIN_VALIDATION_FAILED, domain.getDomainUid(), perLine(validationFailures));
      Step step = DomainStatusUpdater.createDomainInvalidFailureSteps(perLine(validationFailures));
      return doNext(step, packet);
    }

    private String perLine(List<String> validationFailures) {
      return String.join(lineSeparator(), validationFailures);
    }

  }

  static class ValidateDomainTopologyStep extends Step {

    ValidateDomainTopologyStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      final WlsConfigValidator validator = new WlsConfigValidator(packet).loggingTo(LOGGER);
      final List<String> failures = validator.getTopologyFailures();
      final List<String> replicasTooHigh = validator.getReplicaTooHighFailures();
      if (!failures.isEmpty()) {
        return doNext(createTopologyMismatchFailureSteps(failures), packet);
      } else if (!replicasTooHigh.isEmpty()) {
        return doNext(createReplicasTooHighFailureSteps(getNext(), replicasTooHigh), packet);
      } else {
        return doNext(createRemoveSelectedFailuresStep(getNext(), TOPOLOGY_MISMATCH, REPLICAS_TOO_HIGH), packet);
      }
    }

    private Step createTopologyMismatchFailureSteps(List<String> failures) {
      final String failureString = String.join(lineSeparator(), failures);
      return DomainStatusUpdater.createTopologyMismatchFailureSteps(failureString, null);
    }

    private Step createReplicasTooHighFailureSteps(Step next, List<String> failures) {
      final String failureString = String.join(lineSeparator(), failures);
      return Step.chain(
          createRemoveSelectedFailuresStep(null, TOPOLOGY_MISMATCH),
          DomainStatusUpdater.createReplicasTooHighFailureSteps(failureString, next)
      );
    }
  }

  static class KubernetesResourceLookupImpl implements KubernetesResourceLookup {

    private final Packet packet;

    KubernetesResourceLookupImpl(Packet packet) {
      this.packet = packet;
    }

    @Override
    public List<V1Secret> getSecrets() {
      return getSecrets(packet);
    }

    @SuppressWarnings("unchecked")
    private List<V1Secret> getSecrets(Packet packet) {
      return (List<V1Secret>) packet.get(SECRETS);
    }

    @Override
    public boolean isConfigMapExists(String name, String namespace) {
      return getConfigMaps(packet).stream().anyMatch(s -> isSpecifiedConfigMap(s, name, namespace));
    }

    @SuppressWarnings("unchecked")
    private List<ClusterResource> getClusters(Packet packet) {
      return Optional.ofNullable(packet.getSpi(DomainPresenceInfo.class))
          .map(DomainPresenceInfo::getReferencedClusters)
          .or(() -> Optional.ofNullable((List<ClusterResource>) packet.get(CLUSTERS))).orElse(Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private List<ClusterResource> getClustersInNamespace(Packet packet) {
      return Optional.ofNullable((List<ClusterResource>) packet.get(CLUSTERS)).orElse(Collections.emptyList());
    }

    @Override
    public ClusterResource findCluster(V1LocalObjectReference reference) {
      return Optional.ofNullable(reference.getName())
          .flatMap(name -> Optional.ofNullable(getClusters(packet))
              .orElse(Collections.emptyList())
              .stream()
              .filter(cluster -> name.equals(cluster.getMetadata().getName()))
              .findFirst())
          .orElse(null);
    }

    @Override
    public ClusterResource findClusterInNamespace(V1LocalObjectReference reference, String namespace) {
      return Optional.ofNullable(reference.getName())
          .flatMap(name -> getClustersInNamespace(packet)
              .stream().filter(cluster -> hasMatchingMetadata(cluster.getMetadata(), name, namespace))
              .findFirst())
          .orElse(null);
    }

    boolean isSpecifiedConfigMap(V1ConfigMap configmap, String name, String namespace) {
      return hasMatchingMetadata(configmap.getMetadata(), name, namespace);
    }

    @SuppressWarnings("unchecked")
    private List<V1ConfigMap> getConfigMaps(Packet packet) {
      return (List<V1ConfigMap>) packet.get(CONFIGMAPS);
    }

    private boolean hasMatchingMetadata(V1ObjectMeta metadata, String name, String namespace) {
      return metadata != null
            && Objects.equals(name, metadata.getName())
            && Objects.equals(namespace, metadata.getNamespace());
    }

    @Override
    public List<DomainResource> getDomains(String ns) {
      return DomainProcessorImpl.getDomains(ns);
    }
  }

}
