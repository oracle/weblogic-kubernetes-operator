// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Description;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.helpers.ConfigMapSplitterTest.TargetMatcher.isTarget;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ConfigMapSplitterTest {

  private static final int TEST_DATA_LIMIT = 1000;
  private static final String UNIT_DATA = "123456789";
  private static final String LARGE_DATA_VALUE = createLargeData(2.5); // require three maps, including other data

  private final ConfigMapSplitter<TestTarget> splitter = new ConfigMapSplitter<>(TestTarget::new);
  private final Map<String, String> data = new HashMap<>();
  private final List<Memento> mementos = new ArrayList<>();

  private static String createLargeData(double fraction) {
    final int numRepeats = (int) Math.round(fraction * TEST_DATA_LIMIT / UNIT_DATA.length());
    return UNIT_DATA.repeat(numRepeats);
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(StaticStubSupport.install(ConfigMapSplitter.class, "DATA_LIMIT", TEST_DATA_LIMIT));
  }

  @After
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenDataWithinLimits_createOneTargetObject() {
    data.put("a", "aValue");
    data.put("b", "bValue");

    final List<TestTarget> result = splitter.split(data);

    assertThat(result, Matchers.hasSize(1));
    assertThat(result.get(0), isTarget(0, "a", "b"));
  }

  @Test
  public void whenDataTooLarge_createMultipleTargetObjects() {
    data.put("a", "aValue");
    data.put("b", LARGE_DATA_VALUE);

    final List<TestTarget> result = splitter.split(data);

    assertThat(result, Matchers.hasSize(3));
    assertThat(result.get(0), isTarget(0, "a", "b"));
    assertThat(result.get(1), isTarget(1, "b"));
    assertThat(result.get(2), isTarget(2, "b"));
  }

  @Test
  public void whenDataTooLarge_canReconstituteSplitValue() {
    data.put("a", "aValue");
    data.put("b", "123456789".repeat(250));

    final List<TestTarget> result = splitter.split(data);

    final String reassembled = result.stream().map(TestTarget::getB).collect(Collectors.joining());
    assertThat(reassembled, equalTo(data.get("b")));
  }

  @Test
  public void whenDataSplit_recordNumTargetsInFirstResult() {
    data.put("a", "aValue");
    data.put("b", "123456789".repeat(250));

    final List<TestTarget> result = splitter.split(data);

    assertThat(result.get(0).numTargets, equalTo(3));
    assertThat(result.get(1).numTargets, equalTo(0));
    assertThat(result.get(2).numTargets, equalTo(0));
  }

  @Test
  public void whenDataSplit_recordLocationOfSplitEntry() {
    data.put("a", "aValue");
    data.put("b", "123456789".repeat(250));

    final List<TestTarget> result = splitter.split(data);

    assertThat(result.get(0).splitEntries.get("b"), equalTo(new ImmutablePair<>(0, 2)));
  }

  @Test
  public void recordLocationsOfNonSplitItemsPastFirstMap() {
    data.put("a", createLargeData(0.5));
    data.put("b", createLargeData(0.6));
    data.put("c", createLargeData(0.7));

    final List<TestTarget> result = splitter.split(data);

    assertThat(result.get(0).splitEntries.get("c"), equalTo(new ImmutablePair<>(1, 1)));
  }
  
  private static class TestTarget implements SplitterTarget {
    private final Map<String, String> data;
    private final Map<String, Pair<Integer,Integer>> splitEntries = new HashMap<>();
    private final int index;
    private int numTargets;

    TestTarget(Map<String, String> data, int index) {
      this.data = data;
      this.index = index;
    }

    @Override
    public void recordNumTargets(int numTargets) {
      this.numTargets = numTargets;
    }

    @Override
    public void recordEntryLocation(String key, int firstTarget, int lastTarget) {
      splitEntries.put(key, new ImmutablePair<>(firstTarget, lastTarget));
    }

    private String getB() {
      return data.get("b");
    }
  }

  @SuppressWarnings("unused")
  static class TargetMatcher extends TypeSafeDiagnosingMatcher<TestTarget> {

    private final int expectedIndex;
    private final String[] expectedKeys;

    private TargetMatcher(int expectedIndex, String... expectedKeys) {
      this.expectedIndex = expectedIndex;
      this.expectedKeys = expectedKeys;
    }

    static TargetMatcher isTarget(int expectedIndex, String... expectedKeys) {
      return new TargetMatcher(expectedIndex, expectedKeys);
    }

    @Override
    protected boolean matchesSafely(TestTarget item, Description mismatchDescription) {
      if (isExpectedTarget(item)) {
        return true;
      } else {
        mismatchDescription.appendText("TestTarget with index ").appendValue(item.index)
              .appendValueList("and data keys [", ",", "]", item.data.keySet());
        return false;
      }
    }

    boolean isExpectedTarget(TestTarget testTarget) {
      return testTarget.index == expectedIndex && testTarget.data.keySet().equals(expectedKeySet());
    }

    private Set<String> expectedKeySet() {
      return new HashSet<>(Arrays.asList(expectedKeys));
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("TestTarget with index ").appendValue(expectedIndex)
            .appendValueList("and data keys [", ",", "]", expectedKeys);
    }
  }
}
