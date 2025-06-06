// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetSpec;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import oracle.kubernetes.operator.CoreDelegate;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_PDB_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_PDB_EXISTS;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_PDB_PATCHED;
import static oracle.kubernetes.operator.DomainStatusUpdater.createKubernetesFailureSteps;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.helpers.LegalNames.toDns1123LegalName;

/**
 * Operations for dealing with namespaces.
 */
public class PodDisruptionBudgetHelper {

  private PodDisruptionBudgetHelper() {
    // no-op
  }
  
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  public static final String PDB_API_VERSION = "policy/v1";

  /**
   * Factory for {@link Step} that verifies and creates pod disruption budget if needed.
   *
   * @param next the next step
   * @return Step for creating pod disruption budget
   */
  public static Step createPodDisruptionBudgetForClusterStep(Step next) {
    return new CreatePodDisruptionBudgetStep(next);
  }

  static class CreatePodDisruptionBudgetStep extends Step {
    CreatePodDisruptionBudgetStep(Step next) {
      super(next);
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      CoreDelegate delegate = (CoreDelegate) packet.get(ProcessingConstants.DELEGATE_COMPONENT_NAME);
      return doNext(createContext(packet).verifyPodDisruptionBudget(delegate, getNext()), packet);
    }

    protected PodDisruptionBudgetHelper.PodDisruptionBudgetContext createContext(Packet packet) {
      return new PodDisruptionBudgetHelper.PodDisruptionBudgetContext(this, packet);
    }
  }

  static class PodDisruptionBudgetContext extends StepContextBase {
    private final Step conflictStep;
    private final String clusterName;

    PodDisruptionBudgetContext(Step conflictStep, Packet packet) {
      super((DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO));
      this.conflictStep = conflictStep;
      this.clusterName = (String) packet.get(ProcessingConstants.CLUSTER_NAME);
    }

    Step getConflictStep() {
      return new PodDisruptionBudgetHelper.PodDisruptionBudgetContext.ConflictStep();
    }

    private class CreateResponseStep extends ResponseStep<V1PodDisruptionBudget> {
      private final String messageKey;

      CreateResponseStep(String messageKey, Step next) {
        super(next);
        this.messageKey = messageKey;
      }

      @Override
      public Result onFailure(Packet packet, KubernetesApiResponse<V1PodDisruptionBudget> callResponse) {
        if (isUnrecoverable(callResponse)) {
          return updateDomainStatus(packet, callResponse);
        } else {
          return onFailure(getConflictStep(), packet, callResponse);
        }
      }

      private Result updateDomainStatus(Packet packet, KubernetesApiResponse<V1PodDisruptionBudget> callResponse) {
        return doNext(createKubernetesFailureSteps(callResponse, createFailureMessage(callResponse)), packet);
      }

      @Override
      public Result onSuccess(Packet packet, KubernetesApiResponse<V1PodDisruptionBudget> callResponse) {
        logPodDisruptionBudgetCreated(messageKey);
        return doNext(packet);
      }
    }

    private class ReadResponseStep extends DefaultResponseStep<V1PodDisruptionBudget> {
      ReadResponseStep(Step next) {
        super(next);
      }

      @Override
      public Result onFailure(Packet packet, KubernetesApiResponse<V1PodDisruptionBudget> callResponse) {
        return callResponse.getHttpStatusCode() == HTTP_NOT_FOUND
                ? onSuccess(packet, callResponse)
                : onFailure(getConflictStep(), packet, callResponse);
      }
    }

    private class PatchResponseStep extends ResponseStep<V1PodDisruptionBudget> {
      PatchResponseStep(Step next) {
        super(next);
      }

      @Override
      public Result onFailure(Packet packet, KubernetesApiResponse<V1PodDisruptionBudget> callResponse) {
        return callResponse.getHttpStatusCode() == HTTP_NOT_FOUND
                ? onSuccess(packet, callResponse)
                : onFailure(getConflictStep(), packet, callResponse);
      }

      @Override
      public Result onSuccess(Packet packet, KubernetesApiResponse<V1PodDisruptionBudget> callResponse) {
        logPodDisruptionBudgetPatched();
        return doNext(packet);
      }
    }

    private class ConflictStep extends Step {
      @Override
      public @Nonnull Result apply(Packet packet) {
        CoreDelegate delegate = (CoreDelegate) packet.get(ProcessingConstants.DELEGATE_COMPONENT_NAME);
        return doNext(
            delegate.getPodDisruptionBudgetBuilder().get(info.getNamespace(), getPDBName(),
                new PodDisruptionBudgetContext.ReadResponseStep(conflictStep)),
            packet);
      }

      @Override
      public boolean equals(Object other) {
        if (other == this) {
          return true;
        }
        if (!(other instanceof ConflictStep rhs)) {
          return false;
        }
        return new EqualsBuilder().append(conflictStep, rhs.getConflictStep()).isEquals();
      }

      @Override
      public int hashCode() {
        HashCodeBuilder builder =
            new HashCodeBuilder()
                .appendSuper(super.hashCode())
                .append(conflictStep);

        return builder.toHashCode();
      }

      private Step getConflictStep() {
        return conflictStep;
      }
    }

    Step verifyPodDisruptionBudget(CoreDelegate delegate, Step next) {
      V1PodDisruptionBudget podDisruptionBudget = getPodDisruptionBudgetFromRecord();
      if (podDisruptionBudget == null) {
        return createNewPodDisruptionBudget(delegate, next);
      } else if (mustPatch(podDisruptionBudget)) {
        return patchPodDisruptionBudgetStep(delegate, next);
      } else {
        logPodDisruptionBudgetExists();
        return next;
      }
    }

    private Step patchPodDisruptionBudgetStep(CoreDelegate delegate, Step next) {
      return delegate.getPodDisruptionBudgetBuilder().patch(
          info.getNamespace(), getPDBName(),
          V1Patch.PATCH_FORMAT_JSON_PATCH,
          createPodDisruptionBudgetPatch(clusterName, info), new PatchResponseStep(next));
    }

    private String getPDBName() {
      return toDns1123LegalName(getDomainUid() + "-" + clusterName);
    }

    private V1Patch createPodDisruptionBudgetPatch(String clusterName, DomainPresenceInfo info) {
      JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
      patchBuilder.replace("/spec/minAvailable", Math.max(0, info.getReplicaCount(clusterName)
              - info.getMaxUnavailable(clusterName)));
      return new V1Patch(patchBuilder.build().toString());
    }

    private boolean mustPatch(V1PodDisruptionBudget existingPdb) {
      int minAvailable = Optional.ofNullable(existingPdb.getSpec())
              .map(V1PodDisruptionBudgetSpec::getMinAvailable).map(IntOrString::getIntValue).orElse(0);
      return minAvailable != expectedMinAvailableValue(info, clusterName);
    }

    private static int expectedMinAvailableValue(DomainPresenceInfo info, String clusterName) {
      return Math.max(0, info.getReplicaCount(clusterName)
              - info.getMaxUnavailable(clusterName));
    }

    private Step createNewPodDisruptionBudget(CoreDelegate delegate, Step next) {
      return createPodDisruptionBudget(delegate, getPDBCreatedMessageKey(), next);
    }

    protected String getPDBCreatedMessageKey() {
      return CLUSTER_PDB_CREATED;
    }

    private Step createPodDisruptionBudget(CoreDelegate delegate, String messageKey, Step next) {
      return delegate.getPodDisruptionBudgetBuilder().create(
          createModel(), new PodDisruptionBudgetHelper.PodDisruptionBudgetContext.CreateResponseStep(messageKey, next));
    }

    public V1PodDisruptionBudget createModel() {
      return withNonHashedElements(AnnotationHelper.withSha256Hash(createRecipe()));
    }

    V1PodDisruptionBudget withNonHashedElements(V1PodDisruptionBudget service) {
      V1ObjectMeta metadata = service.getMetadata();
      updateForOwnerReference(metadata);
      return service;
    }

    V1PodDisruptionBudget createRecipe() {
      int minAvailable = Math.max(0, info.getReplicaCount(clusterName)
              - info.getMaxUnavailable(clusterName));
      Map<String, String> labels = new HashMap<>();
      labels.put(CREATEDBYOPERATOR_LABEL, "true");
      labels.put(DOMAINUID_LABEL, info.getDomainUid());
      labels.put(CLUSTERNAME_LABEL, clusterName);
      return new V1PodDisruptionBudget()
              .metadata(new V1ObjectMeta().name(getPDBName()).namespace(info.getNamespace()).labels(labels))
              .apiVersion(PDB_API_VERSION)
              .spec(new V1PodDisruptionBudgetSpec().minAvailable(new IntOrString(minAvailable))
                      .selector(new V1LabelSelector().matchLabels(labels)));
    }

    protected V1PodDisruptionBudget getPodDisruptionBudgetFromRecord() {
      return info.getPodDisruptionBudget(clusterName);
    }

    protected void logPodDisruptionBudgetCreated(String messageKey) {
      LOGGER.info(messageKey, getDomainUid(), clusterName);
    }

    protected void logPodDisruptionBudgetExists() {
      LOGGER.fine(CLUSTER_PDB_EXISTS, getDomainUid(), clusterName);
    }

    protected void logPodDisruptionBudgetPatched() {
      LOGGER.fine(CLUSTER_PDB_PATCHED, getDomainUid(), clusterName);
    }

    DomainResource getDomain() {
      return info.getDomain();
    }

    String getDomainUid() {
      return getDomain().getDomainUid();
    }
  }

  /**
   * get PodDisruptionBudget's domain uid.
   *
   * @param pdb PodDisruptionBudget
   * @return Domain uid
   */
  public static String getDomainUid(V1PodDisruptionBudget pdb) {
    return Optional.ofNullable(pdb.getMetadata()).map(V1ObjectMeta::getLabels)
            .map(s -> s.get(DOMAINUID_LABEL)).orElse(null);
  }

  /**
   * Get PodDisruptionBudget's cluster name.
   *
   * @param pdb PodDisruptionBudget
   * @return cluster name
   */
  public static String getClusterName(V1PodDisruptionBudget pdb) {
    return getLabelValue(pdb);
  }

  private static String getLabelValue(V1PodDisruptionBudget podDisruptionBudget) {
    return Optional.ofNullable(podDisruptionBudget).map(V1PodDisruptionBudget::getMetadata)
            .map(V1ObjectMeta::getLabels)
            .map(m -> m.get(LabelConstants.CLUSTERNAME_LABEL)).orElse(null);
  }
}
