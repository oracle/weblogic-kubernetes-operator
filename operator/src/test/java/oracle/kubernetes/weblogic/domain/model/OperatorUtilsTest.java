// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import org.junit.Test;

import static oracle.kubernetes.utils.OperatorUtils.compareSortingStrings;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

public class OperatorUtilsTest {

  @Test
  public void verifyThatMember2Foo_before_member12foo() {
    assertThat(compareSortingStrings("member2foo", "member12foo"), lessThan(0));
  }

  @Test
  public void verifyThatCompareSortingStrings_worksOnStringWith20Digits() {
    assertThat(compareSortingStrings("member2foo", "member12345678901234567890foo"), lessThan(0));
  }

}
