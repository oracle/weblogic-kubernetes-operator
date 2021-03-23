// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.math.BigInteger;
import java.time.OffsetDateTime;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class KubernetesUtilsTest {

  private static final OffsetDateTime startTime = OffsetDateTime.now();
  private static final OffsetDateTime time1 = startTime.plusSeconds(1);
  private static final OffsetDateTime time2 = startTime.plusSeconds(2);

  @Test
  public void whenCreationTimesDiffer_metadataWithLaterTimeIsNewer() {
    V1ObjectMeta meta1 = new V1ObjectMeta().creationTimestamp(time2).resourceVersion("2");
    V1ObjectMeta meta2 = new V1ObjectMeta().creationTimestamp(time1).resourceVersion("1");

    assertThat(KubernetesUtils.isFirstNewer(meta1, meta2), is(true));
    assertThat(KubernetesUtils.isFirstNewer(meta2, meta1), is(false));
  }

  @Test
  public void whenCreationTimesMatch_metadataWithHigherResourceVersionIsNewer() {
    V1ObjectMeta meta1 = new V1ObjectMeta().creationTimestamp(time1).resourceVersion("2");
    V1ObjectMeta meta2 = new V1ObjectMeta().creationTimestamp(time1).resourceVersion("1");

    assertThat(KubernetesUtils.isFirstNewer(meta1, meta2), is(true));
    assertThat(KubernetesUtils.isFirstNewer(meta2, meta1), is(false));
  }

  @Test
  public void whenCreationTimesAndResourceVersionsMatch_neitherIsNewer() {
    V1ObjectMeta meta1 = new V1ObjectMeta().creationTimestamp(time1).resourceVersion("2");
    V1ObjectMeta meta2 = new V1ObjectMeta().creationTimestamp(time1).resourceVersion("2");

    assertThat(KubernetesUtils.isFirstNewer(meta2, meta1), is(false));
    assertThat(KubernetesUtils.isFirstNewer(meta1, meta2), is(false));
  }

  @Test
  public void whenHaveLargeResourceVersionsAndSameTime_succeedIsFirstNewer() {
    OffsetDateTime now = OffsetDateTime.now();

    // This needs to be a value bigger than 2147483647
    String resVersion = "2733280673";
    String evenBiggerResVersion = "2733280673000";

    V1ObjectMeta first = new V1ObjectMeta().creationTimestamp(now).resourceVersion(resVersion);
    V1ObjectMeta second = new V1ObjectMeta().creationTimestamp(now).resourceVersion(evenBiggerResVersion);

    assertThat(KubernetesUtils.isFirstNewer(first, second), is(false));
  }

  @Test
  public void whenHaveNonParsableResourceVersionsAndSameTime_succeedIsFirstNewer() {
    OffsetDateTime now = OffsetDateTime.now();

    String resVersion = "ThisIsNotANumber";
    String differentResVersion = "SomeOtherValueAlsoNotANumber";

    V1ObjectMeta first = new V1ObjectMeta().creationTimestamp(now).resourceVersion(resVersion);
    V1ObjectMeta second = new V1ObjectMeta().creationTimestamp(now).resourceVersion(differentResVersion);

    assertThat(KubernetesUtils.isFirstNewer(first, second), is(false));
  }

  @Test
  public void whenHaveSmallResourceVersion_parseCorrectly() {
    String resVersion = "1";

    BigInteger bigInteger = KubernetesUtils.getResourceVersion(resVersion);
    assertThat(bigInteger, is(BigInteger.ONE));
  }

  @Test
  public void whenHaveNullResourceVersion_parseCorrectly() {
    BigInteger bigInteger = KubernetesUtils.getResourceVersion((String) null);
    assertThat(bigInteger, is(BigInteger.ZERO));
  }

  @Test
  public void whenHaveOpaqueResourceVersion_parseCorrectly() {
    String resVersion = "123NotANumber456";

    BigInteger bigInteger = KubernetesUtils.getResourceVersion(resVersion);
    assertThat(bigInteger, is(BigInteger.ZERO));
  }
}
