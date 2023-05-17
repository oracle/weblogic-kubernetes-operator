// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import oracle.kubernetes.utils.OperatorUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

@SuppressWarnings("unused")
public class DomainStatusNoConditionMatcher extends TypeSafeDiagnosingMatcher<DomainStatus> {
  private @Nonnull final DomainConditionType unexpectedType;
  private String unexpectedStatus;
  private DomainFailureReason unexpectedReason;

  private DomainStatusNoConditionMatcher(@Nonnull DomainConditionType unexpectedType) {
    this.unexpectedType = unexpectedType;
  }

  public static DomainStatusNoConditionMatcher hasNoCondition(DomainConditionType type) {
    return new DomainStatusNoConditionMatcher(type);
  }

  public DomainStatusNoConditionMatcher withStatus(String status) {
    unexpectedStatus = status;
    return this;
  }

  public DomainStatusNoConditionMatcher withReason(@Nonnull DomainFailureReason reason) {
    unexpectedReason = reason;
    return this;
  }

  @Override
  protected boolean matchesSafely(DomainStatus item, Description mismatchDescription) {
    for (DomainCondition condition : item.getConditions()) {
      if (matches(condition)) {
        mismatchDescription.appendValueList(
            "found domain with conditions ", ", ", ".", item.getConditions());
        return false;
      }
    }
    return true;
  }

  private boolean matches(DomainCondition condition) {
    if (unexpectedType != condition.getType()) {
      return false;
    }
    if (unexpectedStatus != null && !unexpectedStatus.equals(condition.getStatus())) {
      return false;
    }
    return unexpectedReason == null || unexpectedReason == condition.getReason();
  }

  private DomainStatus getStatus(DomainResource domain) {
    return Optional.ofNullable(domain.getStatus()).orElse(new DomainStatus());
  }

  @Override
  public void describeTo(Description description) {
    describeTo(description, "domain status not containing condition: ");
  }

  void describeTo(Description description, String commentPrefix) {
    List<String> expectations = new ArrayList<>();
    expectations.add(expectation("type", unexpectedType.toString()));
    if (unexpectedStatus != null) {
      expectations.add(expectation("status", unexpectedStatus));
    }
    if (unexpectedReason != null) {
      expectations.add(expectation("reason", unexpectedReason.toString()));
    }
    description
        .appendText(commentPrefix)
        .appendText(OperatorUtils.joinListGrammatically(expectations));
  }

  private String expectation(String description, String value) {
    return description + " = '" + value + "'";
  }
}
