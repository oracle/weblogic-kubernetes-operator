// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;

import static oracle.kubernetes.common.logging.MessageKeys.ABORTED_ERROR_EVENT_SUGGESTION;
import static oracle.kubernetes.common.logging.MessageKeys.ABORTED_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INVALID_ERROR_EVENT_SUGGESTION;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INVALID_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.INTERNAL_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.INTROSPECTION_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.KUBERNETES_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.PERSISTENT_VOLUME_CLAIM_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.PERSISTENT_VOLUME_CLAIM_EVENT_SUGGESTION;
import static oracle.kubernetes.common.logging.MessageKeys.REPLICAS_TOO_HIGH_ERROR_EVENT_SUGGESTION;
import static oracle.kubernetes.common.logging.MessageKeys.REPLICAS_TOO_HIGH_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.SERVER_POD_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.TOPOLOGY_MISMATCH_ERROR_EVENT_SUGGESTION;
import static oracle.kubernetes.common.logging.MessageKeys.TOPOLOGY_MISMATCH_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.WILL_RETRY_EVENT_SUGGESTION;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_DOMAIN_INVALID_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR;

public enum DomainFailureReason {
  @SerializedName("DomainInvalid")
  DOMAIN_INVALID("DomainInvalid") {
    @Override
    public String getEventError() {
      return DOMAIN_INVALID_EVENT_ERROR;
    }

    @Override
    public String getEventSuggestion() {
      return DOMAIN_INVALID_ERROR_EVENT_SUGGESTION;
    }

    @Override
    boolean hasFatalError(String message) {
      return message.contains(FATAL_DOMAIN_INVALID_ERROR);
    }
  },
  @SerializedName("Introspection")
  INTROSPECTION("Introspection") {
    @Override
    public String getEventError() {
      return INTROSPECTION_EVENT_ERROR;
    }

    @Override
    public String getEventSuggestion() {
      return WILL_RETRY_EVENT_SUGGESTION;
    }

    @Override
    public String getEventSuggestionParam(DomainPresenceInfo info) {
      return getAdditionalMessageFromStatus(info);
    }

    @Override
    boolean hasFatalError(String message) {
      return message.contains(FATAL_INTROSPECTOR_ERROR);
    }
  },
  @SerializedName("Kubernetes")
  KUBERNETES("Kubernetes") {
    @Override
    public String getEventError() {
      return KUBERNETES_EVENT_ERROR;
    }
  },
  @SerializedName("KubernetesNetworkException")
  KUBERNETES_NETWORK_EXCEPTION("KubernetesNetworkException") {
    @Override
    public String getEventError() {
      return KUBERNETES_EVENT_ERROR;
    }
  },
  @SerializedName("ServerPod")
  SERVER_POD("ServerPod") {
    @Override
    public String getEventError() {
      return SERVER_POD_EVENT_ERROR;
    }
  },
  @SerializedName("PersistentVolumeClaim")
  PERSISTENT_VOLUME_CLAIM("PersistentVolumeClaim") {
    @Override
    public String getEventError() {
      return PERSISTENT_VOLUME_CLAIM_EVENT_ERROR;
    }

    @Override
    public String getEventSuggestion() {
      return PERSISTENT_VOLUME_CLAIM_EVENT_SUGGESTION;
    }

    @Override
    DomainFailureSeverity getDefaultSeverity() {
      return DomainFailureSeverity.WARNING;
    }
  },
  @SerializedName("ReplicasTooHigh")
  REPLICAS_TOO_HIGH("ReplicasTooHigh") {
    @Override
    public String getEventError() {
      return REPLICAS_TOO_HIGH_EVENT_ERROR;
    }

    @Override
    public String getEventSuggestion() {
      return REPLICAS_TOO_HIGH_ERROR_EVENT_SUGGESTION;
    }

    @Override
    DomainFailureSeverity getDefaultSeverity() {
      return DomainFailureSeverity.WARNING;
    }
  },
  @SerializedName("TopologyMismatch")
  TOPOLOGY_MISMATCH("TopologyMismatch") {
    @Override
    public String getEventError() {
      return TOPOLOGY_MISMATCH_EVENT_ERROR;
    }

    @Override
    public String getEventSuggestion() {
      return TOPOLOGY_MISMATCH_ERROR_EVENT_SUGGESTION;
    }
  },
  @SerializedName("Internal")
  INTERNAL("Internal") {
    @Override
    public String getEventError() {
      return INTERNAL_EVENT_ERROR;
    }

    @Override
    public String getEventSuggestion() {
      return WILL_RETRY_EVENT_SUGGESTION;
    }

    @Override
    public String getEventSuggestionParam(DomainPresenceInfo info) {
      return getAdditionalMessageFromStatus(info);
    }
  },
  @SerializedName("Aborted")
  ABORTED("Aborted") {
    @Override
    public String getEventError() {
      return ABORTED_EVENT_ERROR;
    }

    @Override
    public String getEventSuggestion() {
      return ABORTED_ERROR_EVENT_SUGGESTION;
    }

    @Override
    DomainFailureSeverity getDefaultSeverity() {
      return DomainFailureSeverity.FATAL;
    }
  };


  static boolean isFatalError(DomainFailureReason reason, String message) {
    return Optional.ofNullable(reason).map(r -> r.hasFatalError(message)).orElse(false);
  }

  public abstract String getEventError();

  /**
   * Return the ID for the message containing suggested actions for this event.
   * @return Message ID for the suggestion message
   */
  public String getEventSuggestion() {
    return null;
  }

  /**
   * Return a String which is used as parameter for the event suggestion message.
   * @param info DomainPresenceInfo may be used by overriding classes to obtain the message
   *             parameter String from
   */
  public String getEventSuggestionParam(DomainPresenceInfo info) {
    return null;
  }

  private static String getAdditionalMessageFromStatus(DomainPresenceInfo info) {
    return Optional.ofNullable(info)
        .map(DomainPresenceInfo::getDomain)
        .map(DomainResource::getStatus)
        .map(DomainStatus::getMessage)
        .orElse("");
  }

  private final String value;

  DomainFailureReason(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }

  DomainFailureSeverity getDefaultSeverity() {
    return DomainFailureSeverity.SEVERE;
  }

  /**
   * Returns true if, for this failure reason, the message indicates a fatal error.
   * @param message a description of what went wrong.
   */
  boolean hasFatalError(String message) {
    return false;
  }
}
