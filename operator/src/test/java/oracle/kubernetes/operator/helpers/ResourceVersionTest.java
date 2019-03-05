// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

public class ResourceVersionTest {

  @Test
  public void whenWellFormed_createIt() {
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
  }

  @Test
  public void whenNotWellFormedButLegal_createIt() {
    new ResourceVersion("token20");
    new ResourceVersion("r100");
    new ResourceVersion("prefix3");
  }

  @Test
  public void whenParsed_verifyVersions() {
    ResourceVersion rv;
    rv = new ResourceVersion("v1");
    assertThat(rv.getVersion(), equalTo(1));

    rv = new ResourceVersion("v27");
    assertThat(rv.getVersion(), equalTo(27));

    rv = new ResourceVersion("v100alpha42");
    assertThat(rv.getVersion(), equalTo(100));
  }

  @Test
  public void whenNotWellFormedButLegalParsed_verifyVersions() {
    ResourceVersion rv;
    rv = new ResourceVersion("token20");
    assertThat(rv.getVersion(), equalTo(20));

    rv = new ResourceVersion("r100");
    assertThat(rv.getVersion(), equalTo(100));

    rv = new ResourceVersion("prefix3");
    assertThat(rv.getVersion(), equalTo(3));
  }

  @Test
  public void whenParsed_verifyWellFormed() {
    ResourceVersion rv;
    rv = new ResourceVersion("v1");
    assert (rv.isWellFormed());

    rv = new ResourceVersion("v27");
    assert (rv.isWellFormed());

    rv = new ResourceVersion("v100alpha42");
    assert (rv.isWellFormed());

    rv = new ResourceVersion("token20");
    assert (!rv.isWellFormed());

    rv = new ResourceVersion("r100");
    assert (!rv.isWellFormed());

    rv = new ResourceVersion("prefix3");
    assert (!rv.isWellFormed());
  }

  @Test
  public void whenParsed_verifyPrefix() {
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
  public void whenParsed_verifyPrerelease() {
    ResourceVersion rv;
    rv = new ResourceVersion("v1");
    assertNull(rv.getPrerelease());

    rv = new ResourceVersion("v100alpha42");
    assertThat(rv.getPrerelease(), equalTo("alpha"));
  }

  @Test
  public void whenParsed_verifyPrereleaseVersion() {
    ResourceVersion rv;
    rv = new ResourceVersion("v1");
    assertNull(rv.getPrereleaseVersion());

    rv = new ResourceVersion("v100alpha42");
    assertThat(rv.getPrereleaseVersion(), equalTo(42));
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenNoDigits_throwIllegalArgument() {
    new ResourceVersion("nonumber");
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenOnlyNumber_throwIllegalArgument() {
    new ResourceVersion("3");
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenNumberFirst_throwIllegalArgument() {
    new ResourceVersion("555v");
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenIllegalPrerelease_throwIllegalArgument() {
    new ResourceVersion("v10gamma12");
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenExtraTokenAlpha_throwIllegalArgument() {
    new ResourceVersion("v5alpha7t");
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenExtraTokenBeta_throwIllegalArgument() {
    new ResourceVersion("v3beta1alpha");
  }

  @Test
  public void whenMultipleValues_sort() {
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
        values.stream().map(e -> new ResourceVersion(e)).collect(Collectors.toList());

    Collections.sort(rvs);

    List<String> actual = rvs.stream().map(e -> e.toString()).collect(Collectors.toList());

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
  public void whenUsedInMap_findCorrectValue() {
    Map<ResourceVersion, String> map = new HashMap<>();
    map.put(new ResourceVersion("v1"), "value");
    map.put(new ResourceVersion("v2"), "value2");

    assertThat(map.get(new ResourceVersion("v1")), equalTo("value"));
  }
}
