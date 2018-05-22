// Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import javax.annotation.Nonnull;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

class DomainPresenceInfoMatcher extends TypeSafeDiagnosingMatcher<DomainPresenceInfo> {
  private String expectedUID;
  private String expectedNamespace;
  private String ingressClusterName;

  private DomainPresenceInfoMatcher(String expectedUID) {
    this.expectedUID = expectedUID;
  }

  static DomainPresenceInfoMatcher domain(@Nonnull String expectedUID) {
    return new DomainPresenceInfoMatcher(expectedUID);
  }

  @SuppressWarnings("SameParameterValue")
  DomainPresenceInfoMatcher withNamespace(@Nonnull String expectedNamespace) {
    this.expectedNamespace = expectedNamespace;
    return this;
  }

  @SuppressWarnings("SameParameterValue")
  DomainPresenceInfoMatcher withIngressForCluster(@Nonnull String ingressClusterName) {
    this.ingressClusterName = ingressClusterName;
    return this;
  }

  @Override
  protected boolean matchesSafely(DomainPresenceInfo item, Description mismatchDescription) {
    if (!expectedUID.equals(getDomainUID(item))) {
      return mismatchedUID(mismatchDescription, getDomainUID(item));
    } else if (expectedNamespace != null && !expectedNamespace.equals(getNamespace(item))) {
      return mismatchedNamespace(mismatchDescription, getNamespace(item));
    } else if (ingressClusterName != null && !hasIngressForCluster(item, ingressClusterName)) {
      return missingIngress(mismatchDescription, ingressClusterName);
    }

    return true;
  }

  private String getDomainUID(DomainPresenceInfo item) {
    return item.getDomain().getSpec().getDomainUID();
  }

  private boolean mismatchedUID(Description description, String actualDomainUID) {
    description.appendText("domain with UID ").appendValue(actualDomainUID);
    return false;
  }

  private String getNamespace(DomainPresenceInfo item) {
    return item.getDomain().getMetadata().getNamespace();
  }

  private boolean mismatchedNamespace(Description description, String actualNamespace) {
    description.appendText("DomainPresenceInfo with namespace ").appendValue(actualNamespace);
    return false;
  }

  private boolean hasIngressForCluster(DomainPresenceInfo item, String ingressClusterName) {
    return item.getIngresses().containsKey(ingressClusterName);
  }

  private boolean missingIngress(Description description, String ingressClusterName) {
    description
        .appendText("DomainPresenceInfo with ingress for cluster ")
        .appendValue(ingressClusterName);
    return false;
  }

  @Override
  public void describeTo(Description description) {
    description
        .appendText("DomainPresenceInfo with UID ")
        .appendValue(expectedUID)
        .appendText(" and namespace ")
        .appendValue(expectedNamespace);
  }
}
