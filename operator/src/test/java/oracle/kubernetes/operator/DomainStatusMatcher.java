// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import oracle.kubernetes.weblogic.domain.model.DomainFailureReason;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static oracle.kubernetes.utils.OperatorUtils.joinListGrammatically;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@SuppressWarnings("unused")
public class DomainStatusMatcher extends TypeSafeDiagnosingMatcher<DomainResource> {
  private String expectedReason;
  private List<String> expectedMessageStrings = Collections.emptyList();
  private boolean expectEmptyReasonAndMessage;

  private DomainStatusMatcher() {
  }

  public static DomainStatusMatcher hasStatus() {
    return new DomainStatusMatcher();
  }

  public DomainStatusMatcher withReason(@Nonnull String reason) {
    expectedReason = reason;
    return this;
  }

  public DomainStatusMatcher withReason(@Nonnull DomainFailureReason reason) {
    return withReason(reason.toString());
  }

  public final DomainStatusMatcher withMessageContaining(@Nonnull String... messageStrings) {
    expectedMessageStrings = Arrays.asList(messageStrings);
    return this;
  }

  public final DomainStatusMatcher withEmptyReasonAndMessage() {
    expectEmptyReasonAndMessage = true;
    return this;
  }

  @Override
  protected boolean matchesSafely(DomainResource domain, Description mismatchDescription) {
    if (domain.getStatus() == null) {
      mismatchDescription.appendText("found domain with no status");
      return false;
    } else if (expectEmptyReasonAndMessage) {
      return foundEmptyReasonAndMessage(domain, mismatchDescription);
    } else if (expectedReason != null && !(expectedReason.equals(domain.getStatus().getReason()))) {
      mismatchDescription.appendText("found domain status with reason ").appendValue(domain.getStatus().getReason());
      return false;
    } else {
      List<String> missingMessageStrings = getMissingMessageStrings(domain.getStatus().getMessage());
      if (missingMessageStrings.isEmpty()) {
        return true;
      } else {
        mismatchDescription.appendText("domain status message ");
        mismatchDescription.appendValue(domain.getStatus().getMessage());
        mismatchDescription.appendText(" is missing ");
        mismatchDescription.appendText(joinListGrammaticallyWithQuotes(missingMessageStrings));
        return false;
      }
    }
  }

  private boolean foundEmptyReasonAndMessage(DomainResource domain, Description description) {
    final String reason = domain.getStatus().getReason();
    final String message = domain.getStatus().getMessage();

    if (isEmpty(reason) && isEmpty(message)) {
      return true;
    } else {
      if (reason != null) {
        description.appendText("found domain status with reason ").appendValue(reason);
      }
      if (message != null) {
        description.appendText(reason == null ? " with" : " and");
        description.appendText(" message ").appendValue(message);
      }
      return false;
    }
  }

  private List<String> getMissingMessageStrings(String message) {
    return expectedMessageStrings.stream().filter(s -> !message.contains(s)).collect(Collectors.toList());
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("domain with status");
    if (expectEmptyReasonAndMessage) {
      description.appendValue(" with no reason or message");
    } else {
      if (expectedReason != null) {
        description.appendText(" with reason ");
        description.appendValue(expectedReason);
      }
      if (!expectedMessageStrings.isEmpty()) {
        description.appendText(expectedReason == null ? " with" : " and");
        description.appendText(" message containing ");
        description.appendText(joinListGrammaticallyWithQuotes(expectedMessageStrings));
      }
    }
  }

  private String joinListGrammaticallyWithQuotes(List<String> strings) {
    return joinListGrammatically(strings.stream().map(this::quote).collect(Collectors.toList()));
  }

  private String quote(String s) {
    return String.format("'%s'", s);
  }
}
