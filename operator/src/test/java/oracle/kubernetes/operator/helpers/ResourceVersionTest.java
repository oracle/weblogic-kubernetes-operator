// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceVersionTest {

  @Test
  void whenWellFormed_createIt() {
    assertDoesNotThrow(() -> {
      new ResourceVersion("v1");
      new ResourceVersion("v3");
      new ResourceVersion("v27");

      new ResourceVersion("v3alpha1");
      new ResourceVersion("v3alpha12");
      new ResourceVersion("v100alpha42");
      new ResourceVersion("v100alpha");

      new ResourceVersion("v3beta1");
      new ResourceVersion("v3beta12");
      new ResourceVersion("v100beta42");
      new ResourceVersion("v100beta");
    });
  }

  @Test
  void whenNotWellFormedButLegal_createIt() {
    assertDoesNotThrow(() -> {
      new ResourceVersion("token20");
      new ResourceVersion("r100");
      new ResourceVersion("prefix3");
    });
  }

  @Test
  void whenParsed_verifyVersions() {
    ResourceVersion rv;
    rv = new ResourceVersion("v1");
    assertThat(rv.getVersion(), equalTo(1));

    rv = new ResourceVersion("v27");
    assertThat(rv.getVersion(), equalTo(27));

    rv = new ResourceVersion("v100alpha42");
    assertThat(rv.getVersion(), equalTo(100));
  }

  @Test
  void whenNotWellFormedButLegalParsed_verifyVersions() {
    ResourceVersion rv;
    rv = new ResourceVersion("token20");
    assertThat(rv.getVersion(), equalTo(20));

    rv = new ResourceVersion("r100");
    assertThat(rv.getVersion(), equalTo(100));

    rv = new ResourceVersion("prefix3");
    assertThat(rv.getVersion(), equalTo(3));
  }

  @Test
  void whenParsed_verifyWellFormed() {
    ResourceVersion rv;
    rv = new ResourceVersion("v1");
    assertTrue(rv.isWellFormed());

    rv = new ResourceVersion("v27");
    assertTrue(rv.isWellFormed());

    rv = new ResourceVersion("v100alpha42");
    assertTrue(rv.isWellFormed());

    rv = new ResourceVersion("token20");
    assertFalse(rv.isWellFormed());

    rv = new ResourceVersion("r100");
    assertFalse(rv.isWellFormed());

    rv = new ResourceVersion("prefix3");
    assertFalse(rv.isWellFormed());
  }

  @Test
  void whenParsed_verifyPrefix() {
    ResourceVersion rv;
    rv = new ResourceVersion("v1");
    assertThat(rv.getPrefix(), equalTo("v"));

    rv = new ResourceVersion("token20");
    assertThat(rv.getPrefix(), equalTo("token"));

    rv = new ResourceVersion("r100");
    assertThat(rv.getPrefix(), equalTo("r"));

    rv = new ResourceVersion("prefix3");
    assertThat(rv.getPrefix(), equalTo("prefix"));
  }

  @Test
  void whenParsed_verifyPrerelease() {
    ResourceVersion rv;
    rv = new ResourceVersion("v1");
    assertNull(rv.getPrerelease());

    rv = new ResourceVersion("v100alpha42");
    assertThat(rv.getPrerelease(), equalTo("alpha"));
  }

  @Test
  void whenParsed_verifyPrereleaseVersion() {
    ResourceVersion rv;
    rv = new ResourceVersion("v1");
    assertNull(rv.getPrereleaseVersion());

    rv = new ResourceVersion("v100alpha42");
    assertThat(rv.getPrereleaseVersion(), equalTo(42));
  }

  @Test
  void whenNoDigits_throwIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> new ResourceVersion("nonumber"));
  }

  @Test
  void whenOnlyNumber_throwIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> new ResourceVersion("3"));
  }

  @Test
  void whenNumberFirst_throwIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> new ResourceVersion("555v"));
  }

  @Test
  void whenIllegalPrerelease_throwIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> new ResourceVersion("v10gamma12"));
  }

  @Test
  void whenExtraTokenAlpha_throwIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> new ResourceVersion("v5alpha7t"));
  }

  @Test
  void whenExtraTokenBeta_throwIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> new ResourceVersion("v3beta1alpha"));
  }

  @Test
  void whenMultipleValues_sort() {
    List<String> values =
        Arrays.asList(
            "v11beta2",
            "foo1",
            "v3beta2",
            "v2",
            "v1",
            "v3beta1",
            "v11alpha2",
            "v10beta3",
            "v10",
            "foo10",
            "v12alpha1");
    List<ResourceVersion> rvs =
        values.stream().map(ResourceVersion::new).sorted().collect(Collectors.toList());

    List<String> actual = rvs.stream().map(ResourceVersion::toString).collect(Collectors.toList());

    List<String> expected =
        Arrays.asList(
            "foo10",
            "foo1",
            "v11alpha2",
            "v12alpha1",
            "v3beta1",
            "v3beta2",
            "v10beta3",
            "v11beta2",
            "v1",
            "v2",
            "v10");
    assertEquals(expected, actual);
  }

  @Test
  void whenUsedInMap_findCorrectValue() {
    Map<ResourceVersion, String> map = new HashMap<>();
    map.put(new ResourceVersion("v1"), "value");
    map.put(new ResourceVersion("v2"), "value2");

    assertThat(map.get(new ResourceVersion("v1")), equalTo("value"));
  }
}
