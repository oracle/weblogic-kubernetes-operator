// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.CoreV1Event;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.EVENT;
import static oracle.kubernetes.utils.OperatorUtils.joinListGrammatically;

public class EventMatcher extends TypeSafeDiagnosingMatcher<KubernetesTestSupport> {

  @Nonnull
  private final String expectedReason;
  private List<String> expectedMessageStrings = Collections.emptyList();
  private String expectedNamespace;
  private Integer expectedCount;

  private EventMatcher(@Nonnull String expectedReason) {
    this.expectedReason = expectedReason;
  }

  public static EventMatcher hasEvent(@Nonnull String expectedReason) {
    return new EventMatcher(expectedReason);
  }

  public EventMatcher withMessageContaining(String... messageStrings) {
    expectedMessageStrings = Arrays.asList(messageStrings);
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

  private List<CoreV1Event> getEvents(KubernetesTestSupport testSupport) {
    return testSupport.getResources(EVENT);
  }

  private String toEventString(CoreV1Event event) {
    List<String> descriptions = new ArrayList<>();
    descriptions.add("reason \"" + event.getReason() + '"');
    if (expectedNamespace != null) {
      descriptions.add("namespace \"" + event.getMetadata().getNamespace() + '"');
    }
    if (!expectedMessageStrings.isEmpty()) {
      descriptions.add("message '" + event.getMessage() + "'");
    }
    if (expectedCount != null) {
      descriptions.add("count <" + event.getCount() + '>');
    }
    return "with " + joinListGrammatically(descriptions);
  }

  private boolean matches(CoreV1Event event) {
    if (!expectedReason.equals(event.getReason())) {
      return false;
    } else if (expectedNamespace != null && !expectedNamespace.equals(event.getMetadata().getNamespace())) {
      return false;
    } else if (expectedCount != null && !expectedCount.equals(event.getCount())) {
      return false;
    } else {
      return getMissingMessageStrings(event.getMessage()).isEmpty();
    }
  }

  private List<String> getMissingMessageStrings(String message) {
    return expectedMessageStrings.stream().filter(s -> !message.contains(s)).collect(Collectors.toList());
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
    if (!expectedMessageStrings.isEmpty()) {
      description.appendValueList(" and a message containing ", ", ", ".", expectedMessageStrings);
    }
  }
}
