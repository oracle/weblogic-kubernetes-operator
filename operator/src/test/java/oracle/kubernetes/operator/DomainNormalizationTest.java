// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static java.util.Arrays.asList;
import static oracle.kubernetes.operator.StartupControlConstants.AUTO_STARTUPCONTROL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.models.V1EnvVar;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DomainNormalizationTest {

  private static final String LATEST_IMAGE = "store/oracle/weblogic:latest";
  private static final String IMAGE_PULL_POLICY = "Never";
  private static final String[] T3_CHANNELS = {"channel1", "channel2"};
  private static final int REPLICAS = 5;
  private static final String SERVER1 = "s1";
  private static final String SERVER2 = "s2";
  private static final String SERVER3 = "s3";
  private static final String CLUSTER_1 = "cluster1";
  private static final String CLUSTER_2 = "cluster2";

  private final Domain domain = new Domain().withSpec(new DomainSpec());
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private final DomainSpec domainSpec = domain.getSpec();
  private List<Memento> mementos = new ArrayList<>();

  @Before
  public void setUp() {
    mementos.add(TestUtils.silenceOperatorLogger());
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  @Test
  public void whenDomainSpecHasNulls_normalizationSetsDefaultValues() {
    domainSpec.setExportT3Channels(null);

    DomainPresenceControl.normalizeDomainSpec(domainSpec);

    assertThat(domainSpec.getImage(), equalTo(KubernetesConstants.DEFAULT_IMAGE));
    assertThat(
        domainSpec.getImagePullPolicy(), equalTo(KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY));
    assertThat(domainSpec.getExportT3Channels(), empty());
    assertThat(
        domainSpec.getStartupControl(), equalTo(StartupControlConstants.AUTO_STARTUPCONTROL));
    assertThat(domain.getReplicaCount("nocluster"), equalTo(Domain.DEFAULT_REPLICA_LIMIT));
    assertThat(
        domain.getIncludeServerOutInPodLog(),
        equalTo(KubernetesConstants.DEFAULT_INCLUDE_SERVER_OUT_IN_POD_LOG));
  }

  @Test
  public void whenDomainSpecHasDefinedValues_normalizationDoesNotChangeThem() {
    domainSpec.setImage(LATEST_IMAGE);
    domainSpec.setImagePullPolicy(IMAGE_PULL_POLICY);
    domainSpec.setExportT3Channels(asList(T3_CHANNELS));

    DomainPresenceControl.normalizeDomainSpec(domainSpec);

    assertThat(domainSpec.getImage(), equalTo(LATEST_IMAGE));
    assertThat(domainSpec.getImagePullPolicy(), equalTo(IMAGE_PULL_POLICY));
    assertThat(domainSpec.getExportT3Channels(), contains(T3_CHANNELS));
  }

  @Test
  public void whenDomainSpecHasNoServersOrClustersConfigured_normalizationLeavesDefaultBehavior() {
    DomainConfiguratorFactory.forDomain(domain)
        .setStartupControl(AUTO_STARTUPCONTROL)
        .withDefaultReplicaCount(REPLICAS);

    DomainPresenceControl.normalizeDomainSpec(domainSpec);

    assertThat(domain.getServer(SERVER1, null).shouldStart(1), is(false));
    assertThat(domain.getServer(SERVER2, CLUSTER_1).shouldStart(REPLICAS - 1), is(true));
  }

  @Test
  public void whenDomainSpecHasServersAndClustersConfigured_normalizationLeavesDesiredBehavior() {
    domainSpec.setStartupControl(AUTO_STARTUPCONTROL);
    domainSpec.setReplicas(REPLICAS);
    configurator
        .configureServer(SERVER1)
        .withDesiredState("STANDBY")
        .withEnvironmentVariable("name1", "value1");
    configurator
        .configureServer(SERVER2)
        .withDesiredState("RUNNING")
        .withEnvironmentVariable("name2", "value2");
    configurator
        .configureCluster(CLUSTER_1)
        .withEnvironmentVariable("name3", "value3")
        .withReplicas(3);
    configurator
        .configureCluster(CLUSTER_2)
        .withDesiredState("UNKNOWN")
        .withEnvironmentVariable("name4", "value4");

    DomainPresenceControl.normalizeDomainSpec(domainSpec);

    assertThat(domain.getServer(SERVER1, null).shouldStart(REPLICAS), is(false));
    assertThat(domain.getServer(SERVER1, null).getDesiredState(), equalTo("STANDBY"));
    assertThat(
        domain.getServer(SERVER1, null).getEnvironmentVariables(),
        contains(envVar("name1", "value1")));

    assertThat(domain.getServer(SERVER2, CLUSTER_1).shouldStart(2), is(true));
    assertThat(domain.getServer(SERVER2, CLUSTER_1).getDesiredState(), equalTo("RUNNING"));
    assertThat(
        domain.getServer(SERVER2, CLUSTER_1).getEnvironmentVariables(),
        contains(envVar("name2", "value2")));

    assertThat(domain.getServer(SERVER3, CLUSTER_2).shouldStart(REPLICAS - 1), is(true));
    assertThat(domain.getServer(SERVER3, CLUSTER_2).getDesiredState(), equalTo("UNKNOWN"));
    assertThat(
        domain.getServer(SERVER3, CLUSTER_2).getEnvironmentVariables(),
        contains(envVar("name4", "value4")));
  }

  private V1EnvVar envVar(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  @Test
  public void whenDomainSpecHasLatestImageAndNoPullPolicy_normalizationSetsAlwaysPull() {
    domainSpec.setImage(LATEST_IMAGE);

    DomainPresenceControl.normalizeDomainSpec(domainSpec);

    assertThat(
        domainSpec.getImagePullPolicy(), equalTo(KubernetesConstants.ALWAYS_IMAGEPULLPOLICY));
  }

  @Test
  public void whenDomainSpecHasConfiguredServerWithoutDesiredState_normalizationSetsRunningState() {
    configurator.configureServer(SERVER1);

    DomainPresenceControl.normalizeDomainSpec(domainSpec);

    assertThat(
        domain.getServer(SERVER1, null).getDesiredState(),
        equalTo(WebLogicConstants.RUNNING_STATE));
  }

  @Test
  public void whenDomainSpecHasConfiguredServerWithoutEnv_normalizationSetsEmptyList() {
    configurator.configureServer(SERVER1);

    DomainPresenceControl.normalizeDomainSpec(domainSpec);

    assertThat(domain.getServer(SERVER1, null).getEnvironmentVariables(), empty());
  }

  @Test
  public void whenDomainSpecHasClusterStartupsWithoutDesiredState_normalizationSetsRunningState() {
    configurator.configureCluster(CLUSTER_1);

    DomainPresenceControl.normalizeDomainSpec(domainSpec);

    assertThat(
        domain.getServer(SERVER1, CLUSTER_1).getDesiredState(),
        equalTo(WebLogicConstants.RUNNING_STATE));
  }

  @Test
  public void whenDomainSpecHasClusterStartupsWithoutEnv_normalizationSetsEmptyList() {
    configurator.configureCluster(CLUSTER_1);

    DomainPresenceControl.normalizeDomainSpec(domainSpec);

    assertThat(domain.getServer(SERVER1, CLUSTER_1).getEnvironmentVariables(), empty());
  }
}
