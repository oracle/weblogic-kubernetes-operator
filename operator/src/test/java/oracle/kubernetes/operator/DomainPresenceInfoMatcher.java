// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import javax.annotation.Nonnull;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

class DomainPresenceInfoMatcher extends TypeSafeDiagnosingMatcher<DomainPresenceInfo> {
  private String expectedUid;
  private String expectedNamespace;

  private DomainPresenceInfoMatcher(String expectedUid) {
    this.expectedUid = expectedUid;
  }

  static DomainPresenceInfoMatcher domain(@Nonnull String expectedUid) {
    return new DomainPresenceInfoMatcher(expectedUid);
  }

  @SuppressWarnings("SameParameterValue")
  DomainPresenceInfoMatcher withNamespace(@Nonnull String expectedNamespace) {
    this.expectedNamespace = expectedNamespace;
    return this;
  }

  @Override
  protected boolean matchesSafely(DomainPresenceInfo item, Description mismatchDescription) {
    if (!expectedUid.equals(getDomainUid(item))) {
      return mismatchedUid(mismatchDescription, getDomainUid(item));
    } else if (expectedNamespace != null && !expectedNamespace.equals(getNamespace(item))) {
      return mismatchedNamespace(mismatchDescription, getNamespace(item));
    }
    return true;
  }

  private String getDomainUid(DomainPresenceInfo item) {
    return item.getDomainUid();
  }

  private boolean mismatchedUid(Description description, String actualDomainUid) {
    description.appendText("domain with UID ").appendValue(actualDomainUid);
    return false;
  }

  private String getNamespace(DomainPresenceInfo item) {
    return item.getNamespace();
  }

  private boolean mismatchedNamespace(Description description, String actualNamespace) {
    description.appendText("DomainPresenceInfo with namespace ").appendValue(actualNamespace);
    return false;
  }

  @Override
  public void describeTo(Description description) {
    description
        .appendText("DomainPresenceInfo with UID ")
        .appendValue(expectedUid)
        .appendText(" and namespace ")
        .appendValue(expectedNamespace);
  }
}
