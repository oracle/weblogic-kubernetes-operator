// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import static java.util.Arrays.asList;
import static oracle.kubernetes.operator.StartupControlConstants.AUTO_STARTUPCONTROL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import io.kubernetes.client.models.V1EnvVar;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import org.junit.Before;
import org.junit.Test;

public class DomainV1NormalizationTest {

  private static final String LATEST_IMAGE = "store/oracle/weblogic:latest";
  private static final String IMAGE_PULL_POLICY = "Never";
  private static final String[] T3_CHANNELS = {"channel1", "channel2"};
  private static final int REPLICAS = 5;
  private static final String SERVER1 = "s1";
  private static final String SERVER2 = "s2";
  private static final String SERVER3 = "s3";
  private static final String CLUSTER_1 = "cluster1";
  private static final String CLUSTER_2 = "cluster2";
  private static final String RUNNING_STATE = "RUNNING";

  private final Domain domain = new Domain().withSpec(new DomainSpec());

  @Before
  public void setUp() {
    DomainConfiguratorFactory.selectV1DomainModel();
  }

  @Test
  public void whenDomainSpecHasNulls_normalizationSetsDefaultValues() {
    domain.getSpec().setImage(null);
    domain.getSpec().setImagePullPolicy(null);
    domain.getSpec().setExportT3Channels(null);

    ServerSpec serverSpec = domain.getServer("", null);
    assertThat(serverSpec.getImage(), equalTo(KubernetesConstants.DEFAULT_IMAGE));
    assertThat(
        serverSpec.getImagePullPolicy(), equalTo(KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY));
    assertThat(serverSpec.shouldStart(0), is(false));
    assertThat(domain.getReplicaCount("nocluster"), equalTo(Domain.DEFAULT_REPLICA_LIMIT));

    assertThat(domain.getExportedNetworkAccessPointNames(), empty());
  }

  @Test
  public void whenDomainSpecHasDefinedValues_normalizationDoesNotChangeThem() {
    domain.getSpec().setImage(LATEST_IMAGE);
    domain.getSpec().setImagePullPolicy(IMAGE_PULL_POLICY);
    domain.getSpec().setExportT3Channels(asList(T3_CHANNELS));

    assertThat(domain.getServer("ms1", null).getImage(), equalTo(LATEST_IMAGE));
    assertThat(domain.getServer("ms1", null).getImagePullPolicy(), equalTo(IMAGE_PULL_POLICY));
    assertThat(domain.getExportedNetworkAccessPointNames(), contains(T3_CHANNELS));
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return DomainConfiguratorFactory.forDomain(domain);
  }

  @Test
  public void whenDomainSpecHasNoServersOrClustersConfigured_normalizationLeavesDefaultBehavior() {
    configureDomain(domain)
        .withStartupControl(AUTO_STARTUPCONTROL)
        .withDefaultReplicaCount(REPLICAS);

    assertThat(domain.getServer(SERVER1, null).shouldStart(1), is(false));
    assertThat(domain.getServer(SERVER2, CLUSTER_1).shouldStart(REPLICAS - 1), is(true));
  }

  @Test
  public void whenDomainSpecHasServersAndClustersConfigured_normalizationLeavesDesiredBehavior() {
    configureDomain(domain)
        .withStartupControl(AUTO_STARTUPCONTROL)
        .withDefaultReplicaCount(REPLICAS);
    configureServer(SERVER1).withDesiredState("STANDBY").withEnvironmentVariable("name1", "value1");
    configureServer(SERVER2).withDesiredState("RUNNING").withEnvironmentVariable("name2", "value2");
    configureCluster(CLUSTER_1).withEnvironmentVariable("name3", "value3").withReplicas(3);
    configureCluster(CLUSTER_2)
        .withDesiredState("UNKNOWN")
        .withEnvironmentVariable("name4", "value4");

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

  private ClusterConfigurator configureCluster(String cluster) {
    return DomainConfiguratorFactory.forDomain(domain).configureCluster(cluster);
  }

  private ServerConfigurator configureServer(String server) {
    return DomainConfiguratorFactory.forDomain(domain).configureServer(server);
  }

  private V1EnvVar envVar(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  @Test
  public void whenDomainSpecHasLatestImageAndNoPullPolicy_normalizationSetsAlwaysPull() {
    configureDomain(domain).withDefaultImage(LATEST_IMAGE);

    assertThat(
        domain.getServer("ms1", null).getImagePullPolicy(),
        equalTo(KubernetesConstants.ALWAYS_IMAGEPULLPOLICY));
  }

  @Test
  public void whenDomainSpecHasConfiguredServerWithoutDesiredState_defaultIsRunningState() {
    configureServer(SERVER1);

    assertThat(domain.getServer(SERVER1, null).getDesiredState(), equalTo(RUNNING_STATE));
  }

  @Test
  public void whenDomainSpecHasConfiguredServerWithoutEnv_environmentIsEmpty() {
    configureServer(SERVER1);

    assertThat(domain.getServer(SERVER1, null).getEnvironmentVariables(), empty());
  }

  @Test
  public void whenDomainSpecHasClusterStartupsWithoutDesiredState_defaultToRunningState() {
    configureCluster(CLUSTER_1);

    assertThat(domain.getServer(SERVER1, CLUSTER_1).getDesiredState(), equalTo(RUNNING_STATE));
  }

  @Test
  public void whenDomainSpecHasClusterStartupsWithoutEnv_effectiveEnvironmentIsEmpty() {
    configureCluster(CLUSTER_1);

    assertThat(domain.getServer(SERVER1, CLUSTER_1).getEnvironmentVariables(), empty());
  }
}
