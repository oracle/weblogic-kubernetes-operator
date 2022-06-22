// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

class CombinationsTest {

  @Test
  void whenSourceListHasOneElement_collectionIsListOfSame() {
    List<String> source = List.of("one");

    List<List<String>> combinations = Combinations.of(source).collect(Collectors.toList());
    assertThat(combinations, contains(source));
  }

  @Test
  void whenSourceListHasTwoElement_collectionContainsCombinations() {
    String first = "one";
    String second = "two";
    List<String> source = List.of(first, second);

    List<List<String>> combinations = Combinations.of(source).collect(Collectors.toList());
    assertThat(combinations, containsInAnyOrder(List.of(first), List.of(second), source));
  }

  @Test
  void whenSourceListHasThreeElement_collectionContainsCombinations() {
    String first = "one";
    String second = "two";
    String third = "three";
    List<String> source = List.of(first, second, third);

    List<List<String>> combinations = Combinations.of(source).collect(Collectors.toList());
    assertThat(combinations, containsInAnyOrder(
        List.of(first), List.of(second), List.of(third),
        List.of(first, second), List.of(first, third), List.of(second, third),
        source));
  }
}
