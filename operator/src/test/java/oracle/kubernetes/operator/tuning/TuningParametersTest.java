// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.tuning;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import oracle.kubernetes.operator.work.FiberTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.tuning.TuningParameters.DEFAULT_NAMESPACE_RECHECK_SECONDS;
import static oracle.kubernetes.operator.tuning.TuningParameters.FEATURE_GATES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class TuningParametersTest {
  private static final File mountPointDir = new File("/kubernetes/tuning_params/");

  private final List<Memento> mementos = new ArrayList<>();
  private final InMemoryFileSystem inMemoryFileSystem = InMemoryFileSystem.createInstance();
  private final Function<String, Path> getInMemoryPath = inMemoryFileSystem::getPath;
  private final FiberTestSupport testSupport = new FiberTestSupport();

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(StaticStubSupport.install(TuningParameters.class, "getPath", getInMemoryPath));
    mementos.add(StaticStubSupport.install(TuningParameters.class, "instance", null));
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenNoTuningParametersConfigured_useDefaultValues() {
    assertThat(getTuningParameters().getParameter("longExample", 3L), equalTo(3L));
    assertThat(getTuningParameters().getParameter("booleanExample", true), is(true));
    assertThat(getTuningParameters().getParameter("stringExample", "red"), equalTo("red"));
  }

  private TuningParameters getTuningParameters() {
    if (TuningParameters.getInstance() == null) {
      TuningParameters.initializeInstance(testSupport.getEngine().getExecutor(), mountPointDir);
    }
    return TuningParameters.getInstance();
  }

  @Test
  void whenNoTuningParametersConfigured_facadesReturnDefaultValues() {
    assertThat(getTuningParameters().getNamespaceRecheckIntervalSeconds(), equalTo(DEFAULT_NAMESPACE_RECHECK_SECONDS));
    assertThat(getTuningParameters().isRestartEvictedPods(), is(true));
  }

  @Test
  void whenTuningParametersConfigured_facadesReturnConfiguredValues() {
    configureParameter("domainNamespaceRecheckIntervalSeconds", "12");
    configureParameter("restartEvictedPods", "false");

    assertThat(getTuningParameters().getNamespaceRecheckIntervalSeconds(), equalTo(12));
    assertThat(getTuningParameters().isRestartEvictedPods(), is(false));
  }

  private void configureParameter(String name, String value) {
    inMemoryFileSystem.defineFile(new File(mountPointDir, name), value);
  }

  @Test
  void whenTuningParametersConfiguredAfterStart_facadesReturnConfiguredValues() {
    configureParameter("domainNamespaceRecheckIntervalSeconds", "12");
    readInitialParameters();

    configureParameter("domainNamespaceRecheckIntervalSeconds", "9");
    testSupport.setTime(1, TimeUnit.MINUTES);

    assertThat(getTuningParameters().getNamespaceRecheckIntervalSeconds(), equalTo(9));
  }

  // Force initialization of tuning parameters instance, thus reading the initial values.
  private void readInitialParameters() {
    getTuningParameters();
  }

  @Test
  void whenFeatureGatesParameterSpecified_FeatureGatesContainsListOfEnabledFetures() {
    configureParameter(FEATURE_GATES, "red=true,,blue=false,green=true,junk");

    FeatureGates featureGates = getTuningParameters().getFeatureGates();

    assertThat(featureGates.getEnabledFeatures(), containsInAnyOrder("red", "green"));
  }

  @Test
  void whenFeatureGatesParameterSpecified_reportsEnabledFeatures() {
    configureParameter(FEATURE_GATES, "red=true,blue=false,green=true");

    FeatureGates featureGates = getTuningParameters().getFeatureGates();

    assertThat(featureGates.isFeatureEnabled("red"), is(true));
    assertThat(featureGates.isFeatureEnabled("green"), is(true));
    assertThat(featureGates.isFeatureEnabled("blue"), is(false));
    assertThat(featureGates.isFeatureEnabled("yellow"), is(false));
  }
}
