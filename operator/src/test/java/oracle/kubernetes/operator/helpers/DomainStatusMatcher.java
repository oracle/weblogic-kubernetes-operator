// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Objects;
import java.util.Optional;

import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

class DomainStatusMatcher extends TypeSafeDiagnosingMatcher<Domain> {
  private final String expectedReason;
  private final String expectMessage;

  private DomainStatusMatcher(String expectedReason, String expectMessage) {
    this.expectedReason = expectedReason;
    this.expectMessage = expectMessage;
  }

  static DomainStatusMatcher hasStatus(String expectedReason, String expectMessage) {
    return new DomainStatusMatcher(expectedReason, expectMessage);
  }

  @Override
  protected boolean matchesSafely(Domain item, Description mismatchDescription) {
    if (hasExpectedReason(item) && hasExpectedMessage(item)) {
      return true;
    }

    mismatchDescription.appendText("domain status with reason ").appendValue(getStatusReason(item))
                       .appendText(" and message ").appendValue(getStatusMessage(item));
    return false;
  }

  private boolean hasExpectedReason(Domain item) {
    return Objects.equals(expectedReason, getStatusReason(item));
  }

  private boolean hasExpectedMessage(Domain item) {
    return Objects.equals(expectMessage, getStatusMessage(item));
  }

  private String getStatusReason(Domain item) {
    return Optional.ofNullable(item.getStatus()).map(DomainStatus::getReason).orElse(null);
  }

  private String getStatusMessage(Domain item) {
    return Optional.ofNullable(item.getStatus()).map(DomainStatus::getMessage).orElse(null);
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("domain status with reason ").appendValue(expectedReason)
               .appendText(" and message ").appendValue(expectMessage);
  }
}
