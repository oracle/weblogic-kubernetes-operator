// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Optional;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;

import static oracle.kubernetes.operator.EventConstants.WILL_NOT_RETRY;
import static oracle.kubernetes.operator.EventConstants.WILL_RETRY_SECONDS;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR_MSG;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_MAX_ERRORS_EXCEEDED;

public enum DomainFailureReason {
  DomainInvalid {
    @Override
    public String getError() {
      return EventConstants.DOMAIN_INVALID_ERROR;
    }

    @Override
    public String getSuggestion(DomainPresenceInfo info) {
      return EventConstants.DOMAIN_INVALID_ERROR_SUGGESTION;
    }
  },
  Introspection {
    @Override
    public String getError() {
      return EventConstants.INTROSPECTION_ERROR;
    }

    @Override
    public String getSuggestion(DomainPresenceInfo info) {
      return getIntrospectionFailureAdditionalMessage(info);
    }
  },
  Kubernetes {
    @Override
    public String getError() {
      return EventConstants.KUBERNETES_ERROR;
    }

    @Override
    public String getSuggestion(DomainPresenceInfo info) {
      return EventConstants.KUBERNETES_ERROR_SUGGESTION;
    }
  },
  ServerPod {
    @Override
    public String getError() {
      return EventConstants.SERVER_POD_ERROR;
    }

    @Override
    public String getSuggestion(DomainPresenceInfo info) {
      return EventConstants.SERVER_POD_ERROR_SUGGESTION;
    }
  },
  ReplicasTooHigh {
    @Override
    public String getError() {
      return EventConstants.REPLICAS_TOO_HIGH_ERROR;
    }

    @Override
    public String getSuggestion(DomainPresenceInfo info) {
      return EventConstants.REPLICAS_TOO_HIGH_ERROR_SUGGESTION;
    }
  },
  Internal {
    @Override
    public String getError() {
      return EventConstants.INTERNAL_ERROR;
    }

    @Override
    public String getSuggestion(DomainPresenceInfo info) {
      return getIntrospectionFailureAdditionalMessage(info);
    }
  };

  public abstract String getError();

  public abstract String getSuggestion(DomainPresenceInfo info);

  private static String getRetryMessage() {
    return String.format(WILL_RETRY_SECONDS, DomainPresence.getDomainPresenceFailureRetrySeconds());
  }

  private static String getAdditionalMessageFromStatus(DomainPresenceInfo info) {
    return Optional.ofNullable(info)
        .map(DomainPresenceInfo::getDomain)
        .map(Domain::getStatus)
        .map(DomainStatus::getMessage)
        .orElse("");
  }

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  String getIntrospectionFailureAdditionalMessage(DomainPresenceInfo info) {
    String msg = DomainFailureReason.getAdditionalMessageFromStatus(info);
    String logMessage = LOGGER.formatMessage(INTROSPECTOR_MAX_ERRORS_EXCEEDED, 5).substring(30);
    if (msg.contains(logMessage) || msg.contains(FATAL_INTROSPECTOR_ERROR_MSG)) {
      msg += WILL_NOT_RETRY;
    } else {
      msg += getRetryMessage();
    }
    return msg;
  }
}
