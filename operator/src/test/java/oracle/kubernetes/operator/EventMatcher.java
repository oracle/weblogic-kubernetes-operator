// Copyright (c) 2022, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.EventsV1Event;
import io.kubernetes.client.openapi.models.EventsV1EventSeries;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.EVENT;
import static oracle.kubernetes.utils.OperatorUtils.joinListGrammatically;

public class EventMatcher extends TypeSafeDiagnosingMatcher<KubernetesTestSupport> {

  @Nonnull
  private final String expectedReason;
  private List<String> expectedNoteStrings = Collections.emptyList();
  private String expectedNamespace;
  private Integer expectedCount;

  private EventMatcher(@Nonnull String expectedReason) {
    this.expectedReason = expectedReason;
  }

  public static EventMatcher hasEvent(@Nonnull String expectedReason) {
    return new EventMatcher(expectedReason);
  }

  public EventMatcher withNoteContaining(String... noteStrings) {
    expectedNoteStrings = Arrays.asList(noteStrings);
    return this;
  }

  public EventMatcher inNamespace(String namespace) {
    expectedNamespace = namespace;
    return this;
  }

  public EventMatcher withCount(int expectedCount) {
    this.expectedCount = expectedCount;
    return this;
  }

  @Override
  protected boolean matchesSafely(KubernetesTestSupport testSupport, Description description) {
    if (getEvents(testSupport).stream().anyMatch(this::matches)) {
      return true;
    } else {
      description.appendText(
            getEvents(testSupport).stream()
                  .map(this::toEventString)
                  .collect(Collectors.joining("], [", "found events: [", "].")));
      return false;
    }
  }

  private List<EventsV1Event> getEvents(KubernetesTestSupport testSupport) {
    return testSupport.getResources(EVENT);
  }

  private String toEventString(EventsV1Event event) {
    List<String> descriptions = new ArrayList<>();
    descriptions.add("reason \"" + event.getReason() + '"');
    if (expectedNamespace != null) {
      descriptions.add("namespace \"" + event.getMetadata().getNamespace() + '"');
    }
    if (!expectedNoteStrings.isEmpty()) {
      descriptions.add("note '" + event.getNote() + "'");
    }
    if (expectedCount != null) {
      descriptions.add("count <" + getCount(event) + '>');
    }
    return "with " + joinListGrammatically(descriptions);
  }

  private int getCount(EventsV1Event event) {
    return Optional.ofNullable(event).map(EventsV1Event::getSeries).map(EventsV1EventSeries::getCount).orElse(1);
  }

  private boolean matches(EventsV1Event event) {
    if (!expectedReason.equals(event.getReason())) {
      return false;
    } else if (expectedNamespace != null && !expectedNamespace.equals(event.getMetadata().getNamespace())) {
      return false;
    } else if (expectedCount != null && !expectedCount.equals(getCount(event))) {
      return false;
    } else {
      return getMissingNoteStrings(event.getNote()).isEmpty();
    }
  }

  private List<String> getMissingNoteStrings(String note) {
    return expectedNoteStrings.stream().filter(s -> !note.contains(s))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("Event with reason ").appendValue(expectedReason);
    if (expectedNamespace != null) {
      description.appendText(" in namespace ").appendValue(expectedNamespace);
    }
    if (expectedCount != null) {
      description.appendText(" with count ").appendValue(expectedCount);
    }
    if (!expectedNoteStrings.isEmpty()) {
      description.appendValueList(" and a note containing ", ", ", ".", expectedNoteStrings);
    }
  }
}
