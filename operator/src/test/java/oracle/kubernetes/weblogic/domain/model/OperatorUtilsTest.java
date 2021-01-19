// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static oracle.kubernetes.utils.OperatorUtils.compareSortingStrings;
import static oracle.kubernetes.utils.OperatorUtils.createSortedMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
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

  @Test
  public void verifyThatCreateSortedMapWithNull_returnsEmptyMap() {
    assertThat(createSortedMap(null), anEmptyMap());
  }

  @Test
  public void verifyThatCreateSortedMap_returnsSortedMap() {
    Map<String, String> map = new HashMap<>();
    map.put("server2", "server2-value");
    map.put("server10", "server10-value");
    map.put("server1", "server1-value");

    assertThat(createSortedMap(map).keySet(), contains("server1", "server2", "server10"));
    assertThat(createSortedMap(map).values(), contains("server1-value", "server2-value", "server10-value"));
  }

}
