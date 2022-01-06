// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Optional;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;

import static oracle.kubernetes.operator.EventConstants.WILL_RETRY;

public enum DomainFailureReason {
  DomainInvalid {
    @Override
    public String getEventError() {
      return EventConstants.DOMAIN_INVALID_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return EventConstants.DOMAIN_INVALID_ERROR_SUGGESTION;
    }
  },
  Introspection {
    @Override
    public String getEventError() {
      return EventConstants.INTROSPECTION_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return getFailureRetryAdditionalMessage(info);
    }
  },
  Kubernetes {
    @Override
    public String getEventError() {
      return EventConstants.KUBERNETES_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return EventConstants.KUBERNETES_ERROR_SUGGESTION;
    }
  },
  ServerPod {
    @Override
    public String getEventError() {
      return EventConstants.SERVER_POD_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return EventConstants.SERVER_POD_ERROR_SUGGESTION;
    }
  },
  ReplicasTooHigh {
    @Override
    public String getEventError() {
      return EventConstants.REPLICAS_TOO_HIGH_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return EventConstants.REPLICAS_TOO_HIGH_ERROR_SUGGESTION;
    }
  },
  TopologyMismatch {
    @Override
    public String getEventError() {
      return EventConstants.TOPOLOGY_MISMATCH_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return EventConstants.TOPOLOGY_MISMATCH_ERROR_SUGGESTION;
    }
  },
  Internal {
    @Override
    public String getEventError() {
      return EventConstants.INTERNAL_ERROR;
    }

    @Override
    public String getEventSuggestion(DomainPresenceInfo info) {
      return getFailureRetryAdditionalMessage(info);
    }
  },
  Aborted {
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
}
