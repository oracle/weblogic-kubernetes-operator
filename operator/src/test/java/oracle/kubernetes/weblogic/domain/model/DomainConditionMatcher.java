// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.time.OffsetDateTime;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

@SuppressWarnings("unused")
public class DomainConditionMatcher extends TypeSafeDiagnosingMatcher<DomainResource> {
  private final DomainStatusConditionMatcher statusMatcher;

  private DomainConditionMatcher(DomainStatusConditionMatcher statusMatcher) {
    this.statusMatcher = statusMatcher;
  }

  public static DomainConditionMatcher hasCondition(DomainConditionType type) {
    return new DomainConditionMatcher(DomainStatusConditionMatcher.hasCondition(type));
  }

  public DomainConditionMatcher withStatus(String status) {
    statusMatcher.withStatus(status);
    return this;
  }

  public DomainConditionMatcher withReason(DomainFailureReason reason) {
    statusMatcher.withReason(reason);
    return this;
  }

  public DomainConditionMatcher withMessageContaining(String message) {
    statusMatcher.withMessageContaining(message);
    return this;
  }

  public DomainConditionMatcher atTime(OffsetDateTime transitionTime) {
    statusMatcher.atTime(transitionTime);
    return this;
  }

  @Override
  protected boolean matchesSafely(DomainResource item, Description mismatchDescription) {
    return statusMatcher.matchesSafely(item.getStatus(), mismatchDescription);
  }

  @Override
  public void describeTo(Description description) {
    statusMatcher.describeTo(description, "domain containing condition: ");
  }

}
