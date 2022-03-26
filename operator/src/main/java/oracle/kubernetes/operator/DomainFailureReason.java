// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;

import static oracle.kubernetes.operator.EventConstants.WILL_RETRY;

public enum DomainFailureReason implements Labeled {
  @SerializedName("DomainInvalid")
  DOMAIN_INVALID("DomainInvalid") {
    @Override
    public String getEventError() {
      return EventConstants.DOMAIN_INVALID_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return EventConstants.DOMAIN_INVALID_ERROR_SUGGESTION;
    }
  },
  @SerializedName("Introspection")
  INTROSPECTION("Introspection") {
    @Override
    public String getEventError() {
      return EventConstants.INTROSPECTION_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return getFailureRetryAdditionalMessage(info);
    }
  },
  @SerializedName("Kubernetes")
  KUBERNETES("Kubernetes") {
    @Override
    public String getEventError() {
      return EventConstants.KUBERNETES_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return EventConstants.KUBERNETES_ERROR_SUGGESTION;
    }
  },
  @SerializedName("ServerPod")
  SERVER_POD("ServerPod") {
    @Override
    public String getEventError() {
      return EventConstants.SERVER_POD_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return EventConstants.SERVER_POD_ERROR_SUGGESTION;
    }
  },
  @SerializedName("ReplicasTooHigh")
  REPLICAS_TOO_HIGH("ReplicasTooHigh") {
    @Override
    public String getEventError() {
      return EventConstants.REPLICAS_TOO_HIGH_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return EventConstants.REPLICAS_TOO_HIGH_ERROR_SUGGESTION;
    }
  },
  @SerializedName("TopologyMismatch")
  TOPOLOGY_MISMATCH("TopologyMismatch") {
    @Override
    public String getEventError() {
      return EventConstants.TOPOLOGY_MISMATCH_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return EventConstants.TOPOLOGY_MISMATCH_ERROR_SUGGESTION;
    }
  },
  @SerializedName("Internal")
  INTERNAL("Internal") {
    @Override
    public String getEventError() {
      return EventConstants.INTERNAL_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return getFailureRetryAdditionalMessage(info);
    }
  },
  @SerializedName("Aborted")
  ABORTED("Aborted") {
    @Override
    public String getEventError() {
      return EventConstants.ABORTED_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return EventConstants.ABORTED_ERROR_SUGGESTION;
    }
  };

  public abstract String getEventError();

  public abstract String getEventSuggestion(DomainPresenceInfo info);

  private static String getRetryMessage() {
    return WILL_RETRY;
  }

  private static String getAdditionalMessageFromStatus(DomainPresenceInfo info) {
    return Optional.ofNullable(info)
        .map(DomainPresenceInfo::getDomain)
        .map(Domain::getStatus)
        .map(DomainStatus::getMessage)
        .orElse("");
  }

  String getFailureRetryAdditionalMessage(DomainPresenceInfo info) {
    return DomainFailureReason.getAdditionalMessageFromStatus(info) + getRetryMessage();
  }

  private final String label;

  DomainFailureReason(String label) {
    this.label = label;
  }

  @Override
  public String label() {
    return label;
  }

  @Override
  public String toString() {
    return label();
  }
}
