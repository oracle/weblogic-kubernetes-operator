// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;
import java.util.Optional;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;

import static oracle.kubernetes.operator.EventConstants.WILL_RETRY;

@JsonAdapter(DomainFailureReason.Adapter.class)
public enum DomainFailureReason {
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

  private final String value;

  DomainFailureReason(String value) {
    this.value = value;
  }

  public String getValue() {
    return this.value;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }

  /**
   * Locate enum type from value.
   * @param value Value
   * @return Domain failure reason type
   */
  public static DomainFailureReason fromValue(String value) {
    for (DomainFailureReason testValue : values()) {
      if (testValue.value.equals(value)) {
        return testValue;
      }
    }

    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

  public static class Adapter extends TypeAdapter<DomainFailureReason> {
    public void write(JsonWriter jsonWriter, DomainFailureReason enumeration) throws IOException {
      jsonWriter.value(enumeration.getValue());
    }

    public DomainFailureReason read(JsonReader jsonReader) throws IOException {
      String value = jsonReader.nextString();
      return DomainFailureReason.fromValue(value);
    }
  }
}
