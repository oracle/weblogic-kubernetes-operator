// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import javax.annotation.Nonnull;
import oracle.kubernetes.weblogic.domain.model.Channel;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class ChannelMatcher extends TypeSafeDiagnosingMatcher<Channel> {
  private String expectedName;
  private Integer expectedNodePort;

  private ChannelMatcher(String expectedName, Integer expectedNodePort) {
    this.expectedName = expectedName;
    this.expectedNodePort = expectedNodePort;
  }

  public static ChannelMatcher channelWith(
      @Nonnull String expectedName, @Nonnull Integer expectedNodePort) {
    return new ChannelMatcher(expectedName, expectedNodePort);
  }

  @Override
  protected boolean matchesSafely(Channel item, Description mismatchDescription) {
    if (item.getChannelName().equals(expectedName) && item.getNodePort().equals(expectedNodePort))
      return true;

    describe(mismatchDescription, item.getChannelName(), item.getNodePort());
    return false;
  }

  private void describe(Description description, String channelName, Integer nodePort) {
    description
        .appendText("channel with name ")
        .appendValue(channelName)
        .appendText(" and port ")
        .appendValue(nodePort);
  }

  @Override
  public void describeTo(Description description) {
    describe(description, expectedName, expectedNodePort);
  }
}
