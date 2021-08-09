// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

class SemanticVersionTest {

  @Test
  void whenVersionsSame_compareReportsZero() {
    SemanticVersion first = new SemanticVersion("5.6.7");
    SemanticVersion second = new SemanticVersion("5.6.7");

    assertThat(first.compareTo(second), equalTo(0));
  }

  @Test
  void whenVersionsSameWithDefaultRevisionZero_compareReportsZero() {
    SemanticVersion first = new SemanticVersion("5.6");
    SemanticVersion second = new SemanticVersion("5.6.0");

    assertThat(first.compareTo(second), equalTo(0));
  }

  @Test
  void whenMajorVersionIsLower_compareReportsLower() {
    SemanticVersion first = new SemanticVersion("5.6.7");
    SemanticVersion second = new SemanticVersion("10.3.4");

    assertThat(first.compareTo(second), lessThan(0));
  }

  @Test
  void whenMajorVersionIsGreater_compareReportsGreater() {
    SemanticVersion first = new SemanticVersion("5.6.7");
    SemanticVersion second = new SemanticVersion("2.8.9");

    assertThat(first.compareTo(second), greaterThan(0));
  }

  @Test
  void whenMinorVersionIsLower_compareReportsLower() {
    SemanticVersion first = new SemanticVersion("5.6.7");
    SemanticVersion second = new SemanticVersion("5.7.2");

    assertThat(first.compareTo(second), lessThan(0));
  }

  @Test
  void whenMinorVersionIsGreater_compareReportsGreater() {
    SemanticVersion first = new SemanticVersion("5.6.7");
    SemanticVersion second = new SemanticVersion("5.5.9");

    assertThat(first.compareTo(second), greaterThan(0));
  }

  @Test
  void whenRevisionVersionIsLower_compareReportsLower() {
    SemanticVersion first = new SemanticVersion("5.6.7");
    SemanticVersion second = new SemanticVersion("5.6.8");

    assertThat(first.compareTo(second), lessThan(0));
  }

  @Test
  void whenRevisionVersionIsGreater_compareReportsGreater() {
    SemanticVersion first = new SemanticVersion("5.6.7");
    SemanticVersion second = new SemanticVersion("5.6.6");

    assertThat(first.compareTo(second), greaterThan(0));
  }
}
