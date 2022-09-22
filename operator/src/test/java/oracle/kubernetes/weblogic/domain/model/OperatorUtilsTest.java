// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static oracle.kubernetes.utils.OperatorUtils.compareSortingStrings;
import static oracle.kubernetes.utils.OperatorUtils.createSortedMap;
import static oracle.kubernetes.utils.OperatorUtils.joinListGrammatically;
import static oracle.kubernetes.utils.OperatorUtils.joinListGrammaticallyWithLimit;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

class OperatorUtilsTest {

  @Test
  void verifyThatMember2Foo_before_member12foo() {
    assertThat(compareSortingStrings("member2foo", "member12foo"), lessThan(0));
  }

  @Test
  void verifyThatCompareSortingStrings_worksOnStringWith20Digits() {
    assertThat(compareSortingStrings("member2foo", "member12345678901234567890foo"), lessThan(0));
  }

  @Test
  void verifyThatCreateSortedMapWithNull_returnsEmptyMap() {
    assertThat(createSortedMap(null), anEmptyMap());
  }

  @Test
  void verifyThatCreateSortedMap_returnsSortedMap() {
    Map<String, String> map = new HashMap<>();
    map.put("server2", "server2-value");
    map.put("server10", "server10-value");
    map.put("server1", "server1-value");

    assertThat(createSortedMap(map).keySet(), contains("server1", "server2", "server10"));
    assertThat(createSortedMap(map).values(), contains("server1-value", "server2-value", "server10-value"));
  }

  @Test
  void grammaticallySortedListWithOneElement_containsOnlyThatElement() {
    assertThat(joinListGrammatically(List.of("one")), equalTo("one"));
  }

  @Test
  void grammaticallySortedListWithTwoElements_separatesThemWithAnd() {
    assertThat(joinListGrammatically(List.of("one", "two")), equalTo("one and two"));
  }

  @Test
  void grammaticallySortedListWithThreeElements_useOxfordComma() {
    assertThat(joinListGrammatically(List.of("one", "two", "three")), equalTo("one, two, and three"));
  }

  @Test
  void limitedGrammaticallySortedListWithOneElement_containsOnlyThatElement() {
    assertThat(joinListGrammaticallyWithLimit(5, List.of("one")), equalTo("one"));
  }

  @Test
  void limitedGrammaticallySortedListWithTwoElements_separatesThemWithAnd() {
    assertThat(joinListGrammaticallyWithLimit(5, List.of("one", "two")), equalTo("one and two"));
  }

  @Test
  void limitedGrammaticallySortedListWithThreeElements_useOxfordComma() {
    assertThat(joinListGrammaticallyWithLimit(5, List.of("one", "two", "three")), equalTo("one, two, and three"));
  }

  @Test
  void limitedGrammaticallySortedListMoreElementsThanLimit_truncatesTheResult() {
    assertThat(joinListGrammaticallyWithLimit(5, List.of("one", "two", "three", "four", "five", "six", "seven")),
        equalTo("one, two, three, four, five, and 2 more"));
  }
}
