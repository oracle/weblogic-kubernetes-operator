// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Probe;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static oracle.kubernetes.operator.helpers.Matchers.EnvVarMatcher.envVarWithName;
import static oracle.kubernetes.operator.helpers.Matchers.EnvVarMatcher.envVarWithNameAndValue;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;

public class Matchers {

  public static Matcher<Iterable<? super V1Container>> hasContainer(
      String name, String image, String... command) {
    return hasItem(createContainer(name, image, command));
  }

  public static Matcher<Iterable<? super V1EnvVar>> hasEnvVar(String name, String value) {
    return hasItem(new V1EnvVar().name(name).value(value));
  }

  public static Matcher<Iterable<? super V1EnvVar>> hasEnvVar(String name) {
    return hasItem(envVarWithName(name));
  }

  public static Matcher<Iterable<? super V1EnvVar>> hasEnvVarRegEx(String name, String regex) {
    return hasItem(envVarWithNameAndValue(name,regex));
  }

  static Matcher<Map<? extends String, ? extends Quantity>> hasResourceQuantity(
      String resource, String quantity) {
    return hasEntry(resource, Quantity.fromString(quantity));
  }

  static Matcher<Iterable<? super V1VolumeMount>> hasVolumeMount(String name, String path) {
    return hasItem(new V1VolumeMount().name(name).mountPath(path));
  }

  static Matcher<Iterable<? super V1Volume>> hasVolume(String name, String path) {
    return hasItem(new V1Volume().name(name).hostPath(new V1HostPathVolumeSource().path(path)));
  }

  static Matcher<Iterable<? super V1Volume>> hasPvClaimVolume(String name, String claimName) {
    return hasItem(new V1Volume().name(name).persistentVolumeClaim(
        new V1PersistentVolumeClaimVolumeSource().claimName(claimName)));
  }

  private static V1Container createContainer(String name, String image, String... command) {
    return new V1Container().name(name).image(image).command(Arrays.asList(command));
  }

  @SuppressWarnings("unused")
  public static class VolumeMountMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.openapi.models.V1VolumeMount> {
    private final String expectedName;
    private final String expectedPath;
    private final boolean readOnly;

    private VolumeMountMatcher(String expectedName, String expectedPath, boolean readOnly) {
      this.expectedName = expectedName;
      this.expectedPath = expectedPath;
      this.readOnly = readOnly;
    }

    public static VolumeMountMatcher writableVolumeMount(String expectedName, String expectedPath) {
      return new VolumeMountMatcher(expectedName, expectedPath, false);
    }

    public static VolumeMountMatcher readOnlyVolumeMount(String expectedName, String expectedPath) {
      return new VolumeMountMatcher(expectedName, expectedPath, true);
    }

    @Override
    protected boolean matchesSafely(V1VolumeMount item, Description mismatchDescription) {
      return expectedName.equals(item.getName())
          && expectedPath.equals(item.getMountPath())
          && readOnly == isReadOnly(item);
    }

    private Boolean isReadOnly(V1VolumeMount item) {
      return item.getReadOnly() != null && item.getReadOnly();
    }

    @Override
    public void describeTo(Description description) {
      description
          .appendText(getReadable())
          .appendText(" V1VolumeMount ")
          .appendValue(expectedName)
          .appendText(" at ")
          .appendValue(expectedPath);
    }

    private String getReadable() {
      return readOnly ? "read-only" : "writable";
    }
  }

  @SuppressWarnings("unused")
  public static class ProbeMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.openapi.models.V1Probe> {
    private static final Integer EXPECTED_FAILURE_THRESHOLD = 1;
    private final Integer expectedInitialDelay;
    private final Integer expectedTimeout;
    private final Integer expectedPeriod;

    private ProbeMatcher(int expectedInitialDelay, int expectedTimeout, int expectedPeriod) {
      this.expectedInitialDelay = expectedInitialDelay;
      this.expectedTimeout = expectedTimeout;
      this.expectedPeriod = expectedPeriod;
    }

    public static ProbeMatcher hasExpectedTuning(
        int expectedInitialDelay, int expectedTimeout, int expectedPeriod) {
      return new ProbeMatcher(
          expectedInitialDelay, expectedTimeout, expectedPeriod);
    }

    @Override
    protected boolean matchesSafely(V1Probe item, Description mismatchDescription) {
      if (Objects.equals(expectedInitialDelay, item.getInitialDelaySeconds())
          && Objects.equals(expectedTimeout, item.getTimeoutSeconds())
          && Objects.equals(expectedPeriod, item.getPeriodSeconds())
          && Objects.equals(EXPECTED_FAILURE_THRESHOLD, item.getFailureThreshold())) {
        return true;
      } else {
        mismatchDescription
            .appendText("probe with initial delay ")
            .appendValue(item.getInitialDelaySeconds())
            .appendText(", timeout ")
            .appendValue(item.getTimeoutSeconds())
            .appendText(", period ")
            .appendValue(item.getPeriodSeconds())
            .appendText(" and failureThreshold ")
            .appendValue(item.getFailureThreshold());

        return false;
      }
    }

    @Override
    public void describeTo(Description description) {
      description
          .appendText("probe with initial delay ")
          .appendValue(expectedInitialDelay)
          .appendText(", timeout ")
          .appendValue(expectedTimeout)
          .appendText(", period ")
          .appendValue(expectedPeriod)
          .appendText(" and failureThreshold ")
          .appendValue(EXPECTED_FAILURE_THRESHOLD);
    }
  }

  static class EnvVarMatcher extends TypeSafeDiagnosingMatcher<V1EnvVar> {
    private static final String DONTCARE = "SENTINEL_DONT_CARE";
    private final String expectedName;
    private final String expectedValueRegEx;

    private EnvVarMatcher(String expectedName) {
      this.expectedName = expectedName;
      this.expectedValueRegEx = DONTCARE;
    }

    private EnvVarMatcher(String expectedName, String expectedValueRegEx) {
      this.expectedName = expectedName;
      this.expectedValueRegEx = expectedValueRegEx;
    }

    static EnvVarMatcher envVarWithName(@Nonnull String name) {
      return new EnvVarMatcher(name);
    }

    static EnvVarMatcher envVarWithNameAndValue(@Nonnull String name, String value) {
      return new EnvVarMatcher(name, value);
    }

    @Override
    protected boolean matchesSafely(V1EnvVar item, Description mismatchDescription) {
      if (expectedValueRegEx == DONTCARE) {
        if (expectedName.equals(item.getName())) {
          return true;
        }
        mismatchDescription.appendText("EnvVar with name ").appendValue(item.getName());
        return false;
      }
      if (expectedValueRegEx == null) {
        if (expectedName.equals(item.getName()) && item.getValue() == null) {
          return true;
        }
        mismatchDescription.appendText("EnvVar with name ").appendValue(item.getName());
        return false;
      }
      if (expectedName.equals(item.getName()) 
          && item.getValue() != null 
          && item.getValue().matches(expectedValueRegEx)) {
        return true;
      }
      mismatchDescription
          .appendText("EnvVar with name=")
          .appendValue(item.getName())
          .appendText(" value=")
          .appendValue(item.getValue());
      return false;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("EnvVar with name=").appendValue(expectedName);
      if (expectedValueRegEx != DONTCARE) {
        description.appendText(" value=").appendValue(expectedValueRegEx);
      }
    }
  }
}
