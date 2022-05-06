// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.operator.EventConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;

import static oracle.kubernetes.common.logging.MessageKeys.ABORTED_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INVALID_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.INTERNAL_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.INTROSPECTION_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.KUBERNETES_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.REPLICAS_TOO_HIGH_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.SERVER_POD_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.TOPOLOGY_MISMATCH_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.WILL_RETRY_EVENT_SUGGESTION;
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
      return EventConstants.DOMAIN_INVALID_ERROR_SUGGESTION;
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
    public String getEventSuggestionParams(DomainPresenceInfo info) {
      return getFailureRetryAdditionalMessage(info);
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

    @Override
    public String getEventSuggestion() {
      return EventConstants.KUBERNETES_ERROR_SUGGESTION;
    }
  },
  @SerializedName("ServerPod")
  SERVER_POD("ServerPod") {
    @Override
    public String getEventError() {
      return SERVER_POD_EVENT_ERROR;
    }

    @Override
    public String getEventSuggestion() {
      return EventConstants.SERVER_POD_ERROR_SUGGESTION;
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
      return EventConstants.REPLICAS_TOO_HIGH_ERROR_SUGGESTION;
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
      return EventConstants.TOPOLOGY_MISMATCH_ERROR_SUGGESTION;
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
    public String getEventSuggestionParams(DomainPresenceInfo info) {
      return getFailureRetryAdditionalMessage(info);
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
      return EventConstants.ABORTED_ERROR_SUGGESTION;
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

  public abstract String getEventSuggestion();

  public String getEventSuggestionParams(DomainPresenceInfo info) {
    return null;
  }

  private static String getAdditionalMessageFromStatus(DomainPresenceInfo info) {
    return Optional.ofNullable(info)
        .map(DomainPresenceInfo::getDomain)
        .map(Domain::getStatus)
        .map(DomainStatus::getMessage)
        .orElse("");
  }

  String getFailureRetryAdditionalMessage(DomainPresenceInfo info) {
    return DomainFailureReason.getAdditionalMessageFromStatus(info);
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
