// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.DomainConfigMapKeys;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainConfigTestUtils;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;
import static oracle.kubernetes.operator.ProcessingConstants.OVERRIDES_MODIFIED;
import static oracle.kubernetes.operator.helpers.ConfigMapHelper.createOverrideDetectionStep;
import static oracle.kubernetes.operator.helpers.ConfigMapHelper.createOverridesState;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;


public class ConfigurationOverrideDistributionTest {

  private final TerminalStep terminalStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final Domain domain1 = createTestDomain();
  private final Domain domain2 = createTestDomain();

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());

    configureDomain1().withConfigOverrides("aMap").withConfigOverrideSecrets("secret1", "secret2");
  }

  private void defineDomainConfigMap(Map<String, String> data) {
    testSupport.defineResources(new V1ConfigMap()
          .metadata(new V1ObjectMeta().namespace(NS).name(ConfigMapHelper.getDomainConfigMapName(UID)))
          .data(new HashMap<>(data)));
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenOverridesAdded_stateStringChanged() {
    assertThat(createOverridesState(domain1), not(equalTo(createOverridesState(domain2))));
  }

  @Test
  public void whenOverridesChanged_stateStringChanged() {
    configureDomain2().withConfigOverrides("aDifferentMap").withConfigOverrideSecrets("secret3");

    assertThat(createOverridesState(domain1), not(equalTo(createOverridesState(domain2))));
  }

  @Test
  public void whenOverridesUnchanged_stateStringUnchanged() {
    configureDomain2().withConfigOverrides("aMap").withConfigOverrideSecrets("secret1", "secret2");

    assertThat(createOverridesState(domain1), equalTo(createOverridesState(domain2)));
  }

  private DomainConfigurator configureDomain1() {
    return DomainConfiguratorFactory.forDomain(domain1);
  }

  private DomainConfigurator configureDomain2() {
    return DomainConfiguratorFactory.forDomain(domain2);
  }

  @Test
  public void whenDomainMapHasCurrentOverrides_dontAddToPacket() {
    defineDomainConfigMap(Map.of(DomainConfigMapKeys.CONFIGURATION_OVERRIDES, createOverridesState(domain1)));

    Packet packet = testSupport.runSteps(createOverrideDetectionStep(domain1, terminalStep));

    assertThat(packet.get(OVERRIDES_MODIFIED), nullValue());
  }

  @Test
  public void whenDomainMapDoesNotHaveCurrentOverrides_addToPacket() {
    defineDomainConfigMap(Map.of(DomainConfigMapKeys.CONFIGURATION_OVERRIDES, createOverridesState(domain2)));

    Packet packet = testSupport.runSteps(createOverrideDetectionStep(domain1, terminalStep));

    assertThat(packet.get(OVERRIDES_MODIFIED), notNullValue());
  }

  @Test
  public void whenDomainMapDoesNotHaveCurrentOverrides_updateDomainMap() {
    defineDomainConfigMap(Map.of("other", "data"));

    testSupport.addDomainPresenceInfo(new DomainPresenceInfo(domain1));
    testSupport.runSteps(createOverrideDetectionStep(domain1, terminalStep));

    assertThat(DomainConfigTestUtils.getDomainConfigMapData(testSupport),
          hasEntry(DomainConfigMapKeys.CONFIGURATION_OVERRIDES, createOverridesState(domain1)));
  }
}