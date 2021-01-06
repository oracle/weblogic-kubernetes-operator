// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static oracle.kubernetes.operator.helpers.PodCompatibility.getMissingElements;

class CompatibleMaps<K, V> implements CompatibilityCheck {
  private final String description;
  private final Map<K, V> expected;
  private final Map<K, V> actual;
  private final List<String> ignoredKeys = new ArrayList<>();

  CompatibleMaps(String description, Map<K, V> expected, Map<K, V> actual) {
    this.description = description;
    this.expected = expected;
    this.actual = actual;
  }

  @Override
  public boolean isCompatible() {
    for (K key : expected.keySet()) {
      if (isKeyToCheck(key) && isIncompatible(key)) {
        return false;
      }
    }
    return true;
  }

  private boolean isKeyToCheck(K key) {
    return !ignoredKeys.contains(key.toString());
  }

  private boolean isIncompatible(K key) {
    return !actual.containsKey(key) || valuesDiffer(key);
  }

  private boolean valuesDiffer(K key) {
    return !Objects.equals(expected.get(key), actual.get(key));
  }

  @Override
  public String getIncompatibility() {
    StringBuilder sb = new StringBuilder();

    Set<K> missingKeys = getMissingElements(expected.keySet(), actual.keySet());
    if (!missingKeys.isEmpty()) {
      sb.append(String.format("actual %s has no entry for '%s'%n", description, missingKeys));
    }

    for (K key : expected.keySet()) {
      if (isKeyToCheck(key) && actual.containsKey(key) && valuesDiffer(key)) {
        sb.append(
            String.format(
                "actual %s has entry '%s' with value '%s' rather than '%s'%n",
                description, key, actual.get(key), expected.get(key)));
      }
    }

    return sb.toString();
  }

  @Override
  public CompatibilityCheck ignoring(String... keys) {
    ignoredKeys.addAll(Arrays.asList(keys));
    return this;
  }
}
