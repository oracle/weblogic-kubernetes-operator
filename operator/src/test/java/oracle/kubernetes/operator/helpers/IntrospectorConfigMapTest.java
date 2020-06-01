// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.IntrospectorConfigMapKeys;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.rest.ScanCacheStub;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
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
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.CONFIGURATION_OVERRIDES;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.DOMAINZIP_HASH;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.DOMAIN_INPUTS_HASH;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.DOMAIN_RESTART_VERSION;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.SECRETS_MD_5;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.TOPOLOGY_YAML;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class IntrospectorConfigMapTest {

  private static final String TOPOLOGY_VALUE = "domainValid: true\ndomain:\n  name: sample";
  private static final String DOMAIN_HASH_VALUE = "MII_domain_hash";
  private static final String INPUTS_HASH_VALUE = "MII_inputs_hash";
  private static final String MD5_SECRETS = "md5-secrets";
  private static final String RESTART_VERSION = "123";
  private static final String OVERRIDES_VALUE = "a[]";
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final TerminalStep terminalStep = new TerminalStep();
  private final IntrospectResult introspectResult = new IntrospectResult();
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(ScanCacheStub.install());

    testSupport.addDomainPresenceInfo(new DomainPresenceInfo(domain));
    testSupport.addToPacket(JobHelper.START_TIME, System.currentTimeMillis() - 10);
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  class IntrospectResult {
    private final StringBuilder builder = new StringBuilder();

    IntrospectResult defineFile(String fileName, String... contents) {
      addLine(">>> /" + fileName);
      Arrays.stream(contents).forEach(this::addLine);
      addLine(">>> EOF");
      return this;
    }

    private void addLine(String line) {
      builder.append(line).append(System.lineSeparator());
    }

    void addToPacket() {
      testSupport.addToPacket(ProcessingConstants.DOMAIN_INTROSPECTOR_LOG_RESULT, builder.toString());
    }

  }

  @Test
  public void whenNoTopologySpecified_abortProcessing() {
    introspectResult.defineFile(SECRETS_MD_5, "not telling").addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenTopologyNotValid_abortProcessing() {
    introspectResult.defineFile(TOPOLOGY_YAML, "domainValid: false", "validationErrors: []").addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenTopologyPresent_continueProcessing() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"").addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenTopologyPresent_addToPacket() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"").addToPacket();

    Packet packet = testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(packet.get(DOMAIN_TOPOLOGY), instanceOf(WlsDomainConfig.class));
  }

  @Test
  public void whenTopologyAndDomainZipHashPresent_addToPacket() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .defineFile(DOMAINZIP_HASH, DOMAIN_HASH_VALUE)
          .addToPacket();

    Packet packet = testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(packet.get(DOMAINZIP_HASH), equalTo(DOMAIN_HASH_VALUE));
  }

  @Test
  public void whenTopologyAndDomainZipHashPresent_addToConfigMap() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .defineFile(DOMAINZIP_HASH, DOMAIN_HASH_VALUE)
          .addToPacket();

    testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(getIntrospectorConfigMapData(), hasEntry(DOMAINZIP_HASH, DOMAIN_HASH_VALUE));
  }

  public Map<String, String> getIntrospectorConfigMapData() {
    final KubernetesTestSupport testSupport = this.testSupport;
    return IntrospectorCMTestUtils.getIntrospectorConfigMapData(testSupport);
  }

  @Test
  public void whenTopologyAndMIISecretsHashPresent_addToPacket() {
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .defineFile(SECRETS_MD_5, MD5_SECRETS)
          .addToPacket();

    Packet packet = testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(packet.get(SECRETS_MD_5), equalTo(MD5_SECRETS));
  }

  @Test
  public void whenDomainHasRestartVersion_addToPacket() {
    configureDomain().withRestartVersion(RESTART_VERSION);
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .addToPacket();

    Packet packet = testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(packet.get(IntrospectorConfigMapKeys.DOMAIN_RESTART_VERSION), equalTo(RESTART_VERSION));
  }

  private DomainConfigurator configureDomain() {
    return DomainConfiguratorFactory.forDomain(domain);
  }

  @Test
  public void whenDomainIsModelInImage_addImageSpecHashToPacket() {
    configureDomain().withDomainHomeSourceType(DomainSourceType.FromModel);
    introspectResult
          .defineFile(TOPOLOGY_YAML, "domainValid: true", "domain:", "  name: \"sample\"")
          .addToPacket();

    Packet packet = testSupport.runSteps(ConfigMapHelper.createIntrospectorConfigMapStep(terminalStep));

    assertThat(packet.get(DOMAIN_INPUTS_HASH), notNullValue());
  }

  @Test
  public void whenIntrospectorConfigMapExists_addEntries() {
    testSupport.defineResources(createIntrospectorConfigMap(Map.of(SECRETS_MD_5, MD5_SECRETS)));

    Step chain = ConfigMapHelper.addIntrospectorConfigMapEntriesStep(domain, Map.of("a", "b", "c", "d"), terminalStep);
    testSupport.runSteps(chain);

    assertThat(getIntrospectorConfigMapData(),
          allOf(hasEntry(SECRETS_MD_5, MD5_SECRETS), hasEntry("a", "b"), hasEntry("c", "d")));
  }

  private V1ConfigMap createIntrospectorConfigMap(Map<String, String> entries) {
    return new V1ConfigMap()
          .metadata(new V1ObjectMeta().name(IntrospectorCMTestUtils.getIntrospectorConfigMapName()).namespace(NS))
          .data(new HashMap<>(entries));
  }

  @Test
  public void whenIntrospectorConfigMapDoesNotExist_addEntries() {
    Step chain = ConfigMapHelper.addIntrospectorConfigMapEntriesStep(domain, Map.of("a", "b", "c", "d"), terminalStep);
    testSupport.runSteps(chain);

    assertThat(getIntrospectorConfigMapData(), allOf(hasEntry("a", "b"), hasEntry("c", "d")));
  }

  @Test
  public void loadExistingEntriesFromIntrospectorConfigMap() {
    testSupport.defineResources(createIntrospectorConfigMap(Map.of(
          TOPOLOGY_YAML, TOPOLOGY_VALUE,
          SECRETS_MD_5, MD5_SECRETS,
          DOMAINZIP_HASH, DOMAIN_HASH_VALUE,
          DOMAIN_RESTART_VERSION, RESTART_VERSION,
          DOMAIN_INPUTS_HASH, INPUTS_HASH_VALUE,
          CONFIGURATION_OVERRIDES, OVERRIDES_VALUE)));

    Packet packet = testSupport.runSteps(ConfigMapHelper.readExistingIntrospectorConfigMap(NS, UID));

    assertThat(packet.get(SECRETS_MD_5), equalTo(MD5_SECRETS));
    assertThat(packet.get(DOMAINZIP_HASH), equalTo(DOMAIN_HASH_VALUE));
    assertThat(packet.get(DOMAIN_RESTART_VERSION), equalTo(RESTART_VERSION));
    assertThat(packet.get(DOMAIN_INPUTS_HASH), equalTo(INPUTS_HASH_VALUE));
    assertThat(packet.get(CONFIGURATION_OVERRIDES), equalTo(OVERRIDES_VALUE));
    assertThat(packet.get(DOMAIN_TOPOLOGY), equalTo(getParsedDomain(TOPOLOGY_VALUE)));
  }

  @SuppressWarnings("SameParameterValue")
  private WlsDomainConfig getParsedDomain(String topologyYaml) {
    return Optional.ofNullable(topologyYaml)
          .map(DomainTopology::parseDomainTopologyYaml)
          .map(DomainTopology::getDomain)
          .orElse(null);
  }

}
