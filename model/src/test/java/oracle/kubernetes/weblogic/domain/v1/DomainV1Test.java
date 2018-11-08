// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import static oracle.kubernetes.operator.StartupControlConstants.ADMIN_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.ALL_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.AUTO_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.NONE_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.SPECIFIED_STARTUPCONTROL;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainTestBase;
import oracle.kubernetes.weblogic.domain.v2.ProbeTuning;
import org.junit.Before;
import org.junit.Test;

public class DomainV1Test extends DomainTestBase {

  @Before
  public void setUp() {
    configureDomain(domain).withStartupControl(NONE_STARTUPCONTROL);
  }

  @Override
  protected DomainConfigurator configureDomain(Domain domain) {
    return new DomainV1Configurator(domain);
  }

  @Test
  public void whenStartupControlSpecified_returnIt() {
    assertThat(domain.getEffectiveStartupControl(), equalTo(NONE_STARTUPCONTROL));
  }

  @Test
  public void whenStartupControlNotSpecified_defaultToAuto() {
    configureDomain(domain).withStartupControl(null);

    assertThat(domain.getEffectiveStartupControl(), equalTo(AUTO_STARTUPCONTROL));
  }

  @Test
  public void whenStartupControlIsMixedCase_capitalizeIt() {
    configureDomain(domain).withStartupControl("auto");

    assertThat(domain.getEffectiveStartupControl(), equalTo("AUTO"));
  }

  @Test
  public void whenStartupControlNone_shouldStartReturnsFalse() {
    configureDomain(domain).withStartupControl(NONE_STARTUPCONTROL);

    assertThat(domain.getServer(CLUSTER_NAME, SERVER1).shouldStart(0), is(false));
  }

  @Test
  public void whenStartupControlAdmin_shouldStartReturnsFalse() {
    configureDomain(domain).withStartupControl(ADMIN_STARTUPCONTROL);

    assertThat(domain.getServer(CLUSTER_NAME, SERVER1).shouldStart(0), is(false));
  }

  @Test
  public void whenStartupControlUnrecognized_shouldStartReturnsFalse() {
    configureDomain(domain).withStartupControl("xyzzy");

    assertThat(domain.getServer(CLUSTER_NAME, SERVER1).shouldStart(0), is(false));
  }

  @Test
  public void whenStartAll_shouldStartReturnsTrue() {
    configureDomain(domain).withStartupControl(ALL_STARTUPCONTROL);

    assertThat(domain.getServer(CLUSTER_NAME, SERVER1).shouldStart(0), is(true));
  }

  @Test // reverse order of overrides, capture server, need tests for cascading settings
  public void whenServerConfiguredWithNodePort_returnNodePort() {
    configureServer(SERVER1).withNodePort(31);
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getNodePort(), equalTo(31));
  }

  @Test
  public void whenStartAutoAndNamedClusterHasRoomByDefault_shouldStartReturnsTrue() {
    configureDomain(domain).withStartupControl(AUTO_STARTUPCONTROL).withDefaultReplicaCount(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(true));
  }

  @Test
  public void whenStartAutoAndNamedClusterHasRoomExplicitly_shouldStartReturnsTrue() {
    configureDomain(domain).withStartupControl(AUTO_STARTUPCONTROL).withDefaultReplicaCount(1);
    configureCluster(CLUSTER_NAME).withReplicas(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(true));
  }

  @Test
  public void whenStartAutoAndNamedClusterHasNoRoomByDefault_shouldStartReturnsFalse() {
    configureDomain(domain).withStartupControl(AUTO_STARTUPCONTROL).withDefaultReplicaCount(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(3), is(false));
  }

  @Test
  public void whenStartAutoAndNamedClusterHasNoRoomExplicitly_shouldStartReturnsFalse() {
    configureDomain(domain).withStartupControl(AUTO_STARTUPCONTROL).withDefaultReplicaCount(5);
    configureCluster(CLUSTER_NAME).withReplicas(1);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(false));
  }

  @Test
  public void whenStartAutoAndServerSpecifiedWithoutCluster_shouldStartReturnsTrue() {
    configureDomain(domain).withStartupControl(AUTO_STARTUPCONTROL).withDefaultReplicaCount(5);
    configureServer(SERVER1);

    assertThat(domain.getServer(SERVER1, null).shouldStart(0), is(true));
  }

  @Test
  public void whenStartSpecifiedAndSpecifiedClusterHasRoom_shouldStartReturnsTrue() {
    configureDomain(domain).withStartupControl(SPECIFIED_STARTUPCONTROL);
    configureCluster(CLUSTER_NAME).withReplicas(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(true));
  }

  @Test
  public void whenStartSpecifiedAndServerSpecifiedWithoutCluster_shouldStartReturnsTrue() {
    configureDomain(domain).withStartupControl(SPECIFIED_STARTUPCONTROL).withDefaultReplicaCount(5);
    configureServer(SERVER1);

    assertThat(domain.getServer(SERVER1, null).shouldStart(0), is(true));
  }

  @Test
  public void whenStartSpecifiedAndNamedClusterHasRoom_shouldStartReturnsTrue() {
    configureDomain(domain).withStartupControl(SPECIFIED_STARTUPCONTROL).withDefaultReplicaCount(3);
    configureCluster(CLUSTER_NAME).withReplicas(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(true));
  }

  @Test
  public void whenStartSpecifiedAndNeitherServerNorClusterSpecified_shouldStartReturnsFalse() {
    configureDomain(domain).withStartupControl(SPECIFIED_STARTUPCONTROL).withDefaultReplicaCount(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(false));
  }

  @Test
  public void whenExportT3ChannelsNotDefined_exportedNamesIsEmpty() {
    assertThat(domain.getExportedNetworkAccessPointNames(), empty());
  }

  @Test
  public void whenExportT3ChannelsDefined_returnChannelNames() {
    AdminServerConfigurator configurator = configureDomain(domain).configureAdminServer("");
    configurator.withExportedNetworkAccessPoints("channel1", "channel2");

    assertThat(
        domain.getExportedNetworkAccessPointNames(), containsInAnyOrder("channel1", "channel2"));
  }

  @Test
  public void whenClusterNotConfigured_useDefaultReplicaCount() {
    configureDomain(domain).withDefaultReplicaCount(5);

    assertThat(domain.getReplicaCount("nosuchcluster"), equalTo(5));
  }

  @Test
  public void afterReplicaCountSetForCluster_defaultIsUnchanged() {
    configureCluster("cluster1").withReplicas(5);

    assertThat(domain.getReplicaCount("cluster2"), equalTo(Domain.DEFAULT_REPLICA_LIMIT));
  }

  @Test
  public void whenClusterDefinedWithReplicas_useClusterReplicaCount() {
    configureDomain(domain).withDefaultReplicaCount(5);
    configureCluster("cluster1").withReplicas(3);

    assertThat(domain.getReplicaCount("cluster1"), equalTo(3));
  }

  @Test
  public void whenClusterDefinedWithoutReplicas_useDomainReplicaCount() {
    configureDomain(domain).withDefaultReplicaCount(5);
    configureCluster("cluster1");

    assertThat(domain.getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  public void domainV2Lists_areEmpty() {
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getAdditionalVolumeMounts(), empty());
    assertThat(spec.getAdditionalVolumes(), empty());
  }

  @Test
  public void domainV2Maps_areEmpty() {
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getPodLabels(), anEmptyMap());
    assertThat(spec.getPodAnnotations(), anEmptyMap());
    assertThat(spec.getListenAddressServiceLabels(), anEmptyMap());
    assertThat(spec.getListenAddressServiceAnnotations(), anEmptyMap());
    assertThat(spec.getNodeSelectors(), anEmptyMap());
  }

  @Test
  public void domainV2Objects_areNull() {
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getResources(), nullValue());
    assertThat(spec.getPodSecurityContext(), nullValue());
    assertThat(spec.getContainerSecurityContext(), nullValue());
  }

  @Test
  public void livenessProbeSettings_returnsNullValues() {
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);
    ProbeTuning probe = spec.getLivenessProbe();

    assertThat(probe.getInitialDelaySeconds(), nullValue());
    assertThat(probe.getTimeoutSeconds(), nullValue());
    assertThat(probe.getPeriodSeconds(), nullValue());
  }

  @Test
  public void readinessProbeSettings_returnsNullValues() {
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);
    ProbeTuning probe = spec.getReadinessProbe();

    assertThat(probe.getInitialDelaySeconds(), nullValue());
    assertThat(probe.getTimeoutSeconds(), nullValue());
    assertThat(probe.getPeriodSeconds(), nullValue());
  }

  @Test
  public void whenNoReplicaCountSpecified_useDefaultValue() {
    assertThat(domain.getReplicaCount("cluster1"), equalTo(Domain.DEFAULT_REPLICA_LIMIT));
  }
}
