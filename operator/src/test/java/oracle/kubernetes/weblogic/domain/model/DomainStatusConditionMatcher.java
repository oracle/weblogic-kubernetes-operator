// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import oracle.kubernetes.utils.OperatorUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

@SuppressWarnings("unused")
public class DomainStatusConditionMatcher extends TypeSafeDiagnosingMatcher<DomainStatus> {
  private @Nonnull final DomainConditionType expectedType;
  private String expectedStatus;
  private DomainFailureReason expectedReason;
  private String expectedMessage;
  private OffsetDateTime expectedTransitionTime;

  private DomainStatusConditionMatcher(@Nonnull DomainConditionType expectedType) {
    this.expectedType = expectedType;
  }

  public static DomainStatusConditionMatcher hasCondition(DomainConditionType type) {
    return new DomainStatusConditionMatcher(type);
  }

  DomainStatusConditionMatcher withStatus(String status) {
    expectedStatus = status;
    return this;
  }

  DomainStatusConditionMatcher withReason(@Nonnull DomainFailureReason reason) {
    expectedReason = reason;
    return this;
  }

  DomainStatusConditionMatcher withMessageContaining(String message) {
    expectedMessage = message;
    return this;
  }

  public DomainStatusConditionMatcher atTime(OffsetDateTime transitionTime) {
    expectedTransitionTime = transitionTime;
    return this;
  }

  @Override
  protected boolean matchesSafely(DomainStatus item, Description mismatchDescription) {
    for (DomainCondition condition : item.getConditions()) {
      if (matches(condition)) {
        return true;
      }
    }

    mismatchDescription.appendValueList(
        "found domain with conditions ", ", ", ".", item.getConditions());
    return false;
  }

  private boolean matches(DomainCondition condition) {
    if (expectedType != condition.getType()) {
      return false;
    }
    if (expectedStatus != null && !expectedStatus.equals(condition.getStatus())) {
      return false;
    }
    if (expectedMessage != null && !messageContainsExpectedString(condition)) {
      return false;
    }
    if (expectedTransitionTime != null && !expectedTransitionTime.equals(condition.getLastTransitionTime())) {
      return false;
    }
    return expectedReason == null || expectedReason == condition.getReason();
  }

  private boolean messageContainsExpectedString(DomainCondition condition) {
    return condition.getMessage() != null && condition.getMessage().contains(expectedMessage);
  }

  private DomainStatus getStatus(DomainResource domain) {
    return Optional.ofNullable(domain.getStatus()).orElse(new DomainStatus());
  }

  @Override
  public void describeTo(Description description) {
    describeTo(description, "domain status containing condition: ");
  }

  void describeTo(Description description, String commentPrefix) {
    List<String> expectations = new ArrayList<>();
    expectations.add(expectation("type", expectedType.toString()));
    if (expectedStatus != null) {
      expectations.add(expectation("status", expectedStatus));
    }
    if (expectedReason != null) {
      expectations.add(expectation("reason", expectedReason.toString()));
    }
    if (expectedMessage != null) {
      expectations.add(expectation("reason", expectedMessage));
    }
    if (expectedTransitionTime != null) {
      expectations.add(expectation("lastTransitionTime", expectedTransitionTime));
    }
    description
        .appendText(commentPrefix)
        .appendText(OperatorUtils.joinListGrammatically(expectations));
  }

  private String expectation(String description, String value) {
    return description + " = '" + value + "'";
  }

  @SuppressWarnings("SameParameterValue")
  private String expectation(String description, OffsetDateTime value) {
    return description + " = " + value;
  }
}
