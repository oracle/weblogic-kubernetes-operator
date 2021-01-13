// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/** Kubernetes event associated with a domain. */
class EventKubernetesObjects {
  private final Map<EventIdentifier, V1Event> events = new HashMap<>();

  EventKubernetesObjects() {
  }

  void remove(V1Event event) {
    events.remove(new EventIdentifier(event));
  }

  public void update(V1Event event) {
    events.put(new EventIdentifier(event), event);
  }

  V1Event getExistingEvent(V1Event event) {
    return events.get(new EventIdentifier(event));
  }

  private static class EventIdentifier {
    String reason;
    V1ObjectReference involvedObject;
    String message;

    EventIdentifier(V1Event event) {
      this.involvedObject = event.getInvolvedObject();
      this.reason = event.getReason();
      this.message = event.getMessage();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      EventIdentifier that = (EventIdentifier) o;

      return new EqualsBuilder()
          .append(involvedObject, that.involvedObject)
          .append(reason, that.reason)
          .append(message, that.message)
          .isEquals();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder(17, 37)
          .append(involvedObject)
          .append(reason)
          .append(message)
          .toHashCode();
    }
  }
}
