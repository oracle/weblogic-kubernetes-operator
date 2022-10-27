// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.Step;
import org.apache.commons.lang3.StringUtils;

import static java.util.Collections.emptyList;
import static oracle.kubernetes.common.logging.MessageKeys.SERVER_POD_FAILURE;
import static oracle.kubernetes.operator.helpers.LegalNames.toJobIntrospectorName;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodDomainUid;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodName;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodNamespace;

public class IntrospectionStatus {

  private IntrospectionStatus() {
    // no-op
  }

  private static final String K8S_POD_UNSCHEDULABLE = "Unschedulable";

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  public static final String ERR_IMAGE_PULL = "ErrImagePull";
  public static final String IMAGE_PULL_BACK_OFF = "ImagePullBackOff";

  static V1ContainerStatus getIntrospectorStatus(@Nonnull V1Pod pod) {
    final String introspectorName = toJobIntrospectorName(getPodDomainUid(pod));
    final Predicate<V1ContainerStatus> hasIntrospectorName = status -> introspectorName.equals(status.getName());

    return Optional.ofNullable(pod.getStatus())
          .map(V1PodStatus::getContainerStatuses).orElse(emptyList()).stream()
          .filter(hasIntrospectorName)
          .findFirst()
          .orElse(null);
  }

  @Nullable
  static Step createStatusUpdateSteps(@Nonnull V1Pod pod) {
    final String terminatedErrorMessage = getTerminatedMessage(pod);
    final String waitingMessage = getWaitingMessageFromStatus(pod);
    final String initContainerWaitingMessages = getInitContainerWaitingMessages(pod);

    if (FailedPhase.inFailedPhase(pod)) {
      return new FailedPhase(pod).createStatusUpdateSteps();
    } else if (Unschedulable.isUnschedulable(pod)) {
      return new Unschedulable(pod).createStatusUpdateSteps();
    } else if (terminatedErrorMessage != null) {
      return new SelectedMessage(pod, terminatedErrorMessage, true).createStatusUpdateSteps();
    } else if (waitingMessage != null) {
      return new SelectedMessage(pod, waitingMessage, false).createStatusUpdateSteps();
    } else if (initContainerWaitingMessages != null) {
      return new SelectedMessage(pod, initContainerWaitingMessages, false).createStatusUpdateSteps();
    } else {
      return null;
    }
  }

  private static String getTerminatedMessage(@Nonnull V1Pod pod) {
    return Optional.ofNullable(getIntrospectorStatus(pod))
          .map(V1ContainerStatus::getState)
          .map(V1ContainerState::getTerminated)
          .map(V1ContainerStateTerminated::getMessage)
          .orElse(null);
  }

  private static String getWaitingMessageFromStatus(@Nonnull V1Pod pod) {
    return getWaitingMessageFromStatus(getIntrospectorStatus(pod));
  }

  private static String getWaitingMessageFromStatus(V1ContainerStatus status) {
    return Optional.ofNullable(status)
            .map(V1ContainerStatus::getState)
            .map(V1ContainerState::getWaiting)
            .map(V1ContainerStateWaiting::getMessage)
            .orElse(null);
  }

  private static String getInitContainerWaitingMessages(@Nonnull V1Pod pod) {
    List<String> waitingMessages = new ArrayList<>();

    Optional.ofNullable(getInitContainerStatuses(pod)).orElse(emptyList()).stream()
            .filter(status -> isImagePullError(getWaitingReason(status)))
            .forEach(status -> waitingMessages.add(getWaitingMessageFromStatus(status)));

    return waitingMessages.isEmpty() ? null : String.join(System.lineSeparator(), waitingMessages);
  }

  private static List<V1ContainerStatus> getInitContainerStatuses(@Nonnull V1Pod pod) {
    return Optional.ofNullable(pod.getStatus()).map(V1PodStatus::getInitContainerStatuses).orElse(null);
  }

  public static boolean isImagePullError(String reason) {
    return ERR_IMAGE_PULL.equals(reason) || IMAGE_PULL_BACK_OFF.equals(reason);
  }

  private static String getWaitingReason(V1ContainerStatus status) {
    return Optional.ofNullable(status)
            .map(V1ContainerStatus::getState)
            .map(V1ContainerState::getWaiting)
            .map(V1ContainerStateWaiting::getReason)
            .orElse(null);
  }

  abstract static class PodStatus {
    private final V1Pod pod;

    PodStatus(V1Pod pod) {
      this.pod = pod;
    }

    Step createStatusUpdateSteps() {
      if (isIntrospectorFailure()) {
        LOGGER.info(MessageKeys.INTROSPECTOR_POD_FAILED, getPodName(pod), getPodNamespace(pod), pod.getStatus());
      }

      return Optional.ofNullable(getErrorMessage(pod))
            .map(m -> DomainStatusUpdater.createServerPodFailureSteps(createFailureMessage(pod, m)))
            .orElse(null);
    }

    private String createFailureMessage(V1Pod pod, String message) {
      return LOGGER.formatMessage(SERVER_POD_FAILURE, getPodName(pod), getPodNamespace(pod), message);
    }

    protected abstract boolean isIntrospectorFailure();

    abstract String getErrorMessage(@Nonnull V1Pod pod);
  }

  /**
   * Processing when an introspector pod is in the failed phase.
   */
  static class FailedPhase extends PodStatus {

    static boolean inFailedPhase(@Nonnull V1Pod pod) {
      return "Failed".equals(getPodPhase(pod));
    }

    private static String getPodPhase(@Nonnull V1Pod pod) {
      return Optional.ofNullable(pod.getStatus()).map(V1PodStatus::getPhase).orElse(null);
    }

    FailedPhase(V1Pod pod) {
      super(pod);
    }

    @Override
    protected boolean isIntrospectorFailure() {
      return true;
    }

    @Override
    String getErrorMessage(@Nonnull V1Pod pod) {
      return isNotTerminatedByOperator(pod) ? getPodStatusMessage(pod) : null;
    }

    // a pod terminated by the operator will have null values for reason and message
    private boolean isNotTerminatedByOperator(V1Pod pod) {
      return hasStatusReason(pod) || hasStatusMessage(pod) || !isJobPodTerminated(pod);
    }

    private boolean hasStatusReason(V1Pod pod) {
      return hasStatusString(pod, V1PodStatus::getReason);
    }

    private boolean hasStatusMessage(V1Pod pod) {
      return hasStatusString(pod, V1PodStatus::getMessage);
    }

    private boolean hasStatusString(V1Pod pod, Function<V1PodStatus, String> getter) {
      return Optional.ofNullable(pod)
            .map(V1Pod::getStatus)
            .map(getter)
            .map(StringUtils::isNotEmpty)
            .orElse(false);
    }

    private boolean isJobPodTerminated(V1Pod pod) {
      return getContainerStateTerminatedReason(getIntrospectorStatus(pod)).contains("Error");
    }

    private String getContainerStateTerminatedReason(V1ContainerStatus containerStatus) {
      return Optional.of(containerStatus)
          .map(V1ContainerStatus::getState)
          .map(V1ContainerState::getTerminated)
          .map(V1ContainerStateTerminated::getReason).orElse("");
    }

    private String getPodStatusMessage(V1Pod pod) {
      return Optional.ofNullable(pod).map(V1Pod::getStatus).map(V1PodStatus::getMessage).orElse(null);
    }
  }

  /**
   * Processing when a pod is reported as unschedulable by Kubernetes.
   */
  static class Unschedulable extends PodStatus {

    static boolean isUnschedulable(@Nonnull V1Pod pod) {
      return getPodConditions(pod).stream().anyMatch(Unschedulable::isPodUnschedulable);
    }

    private static List<V1PodCondition> getPodConditions(@Nonnull V1Pod pod) {
      return Optional.ofNullable(pod.getStatus()).map(V1PodStatus::getConditions).orElse(emptyList());
    }

    private static boolean isPodUnschedulable(V1PodCondition podCondition) {
      return getReason(podCondition).contains(K8S_POD_UNSCHEDULABLE);
    }

    private static String getReason(V1PodCondition podCondition) {
      return Optional.ofNullable(podCondition).map(V1PodCondition::getReason).orElse("");
    }

    Unschedulable(V1Pod pod) {
      super(pod);
    }

    @Override
    protected boolean isIntrospectorFailure() {
      return true;
    }

    @Override
    String getErrorMessage(@Nonnull V1Pod pod) {
      return Optional.ofNullable(getMatchingPodCondition(pod)).map(V1PodCondition::getMessage).orElse(null);
    }

    private V1PodCondition getMatchingPodCondition(V1Pod pod) {
      return Optional.ofNullable(pod.getStatus())
              .map(V1PodStatus::getConditions)
              .flatMap(this::getPodCondition)
              .orElse(null);
    }

    private Optional<V1PodCondition> getPodCondition(Collection<V1PodCondition> conditions) {
      return conditions.stream().findFirst();
    }
  }

  /**
   * Processing when a pod has a message to be copied to the domain status.
   */
  static class SelectedMessage extends PodStatus {
    private final String errorMessage;
    private final boolean failure;

    SelectedMessage(V1Pod pod, String errorMessage, boolean failure) {
      super(pod);
      this.errorMessage = errorMessage;
      this.failure = failure;
    }

    @Override
    protected boolean isIntrospectorFailure() {
      return failure;
    }

    @Override
    String getErrorMessage(@Nonnull V1Pod pod) {
      return errorMessage;
    }
  }
}
