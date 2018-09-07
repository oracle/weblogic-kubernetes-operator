// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import static oracle.kubernetes.operator.StartupControlConstants.ALL_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.AUTO_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.NONE_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.SPECIFIED_STARTUPCONTROL;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import io.kubernetes.client.models.V1Probe;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainTestBase;
import org.junit.Before;
import org.junit.Test;

public class DomainV1Test extends DomainTestBase {

  @Before
  public void setUp() {
    domain.getSpec().setStartupControl(NONE_STARTUPCONTROL);
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
    domain.getSpec().setStartupControl(null);

    assertThat(domain.getEffectiveStartupControl(), equalTo(AUTO_STARTUPCONTROL));
  }

  @Test
  public void whenStartupControlIsMixedCase_capitalizeIt() {
    domain.getSpec().setStartupControl("auto");

    assertThat(domain.getEffectiveStartupControl(), equalTo("AUTO"));
  }

  @Test
  public void whenStartAll_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(ALL_STARTUPCONTROL);

    assertThat(domain.getServer(CLUSTER_NAME, SERVER1).shouldStart(0), is(true));
  }

  @Test
  public void whenStartAutoAndNamedClusterHasRoomByDefault_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(AUTO_STARTUPCONTROL);
    domain.getSpec().setReplicas(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(true));
  }

  @Test
  public void whenStartAutoAndNamedClusterHasRoomExplicitly_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(AUTO_STARTUPCONTROL);
    domain.getSpec().setReplicas(1);
    configureCluster(CLUSTER_NAME).withReplicas(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(true));
  }

  @Test
  public void whenStartAutoAndNamedClusterHasNoRoomByDefault_shouldStartReturnsFalse() {
    domain.getSpec().setStartupControl(AUTO_STARTUPCONTROL);
    domain.getSpec().setReplicas(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(3), is(false));
  }

  @Test
  public void whenStartAutoAndNamedClusterHasNoRoomExplicitly_shouldStartReturnsFalse() {
    domain.getSpec().setStartupControl(AUTO_STARTUPCONTROL);
    domain.getSpec().setReplicas(5);
    configureCluster(CLUSTER_NAME).withReplicas(1);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(false));
  }

  @Test
  public void whenStartAutoAndServerSpecifiedWithoutCluster_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(AUTO_STARTUPCONTROL);
    domain.getSpec().setReplicas(5);
    configureServer(SERVER1);

    assertThat(domain.getServer(SERVER1, null).shouldStart(0), is(true));
  }

  @Test
  public void whenStartSpecifiedAndSpecifiedClusterHasRoom_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(SPECIFIED_STARTUPCONTROL);
    configureCluster(CLUSTER_NAME).withReplicas(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(true));
  }

  @Test
  public void whenStartSpecifiedAndServerSpecifiedWithoutCluster_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(SPECIFIED_STARTUPCONTROL);
    domain.getSpec().setReplicas(5);
    configureServer(SERVER1);

    assertThat(domain.getServer(SERVER1, null).shouldStart(0), is(true));
  }

  @Test
  public void whenStartSpecifiedAndNamedClusterHasRoom_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(SPECIFIED_STARTUPCONTROL);
    domain.getSpec().setReplicas(3);
    configureCluster(CLUSTER_NAME).withReplicas(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(true));
  }

  @Test
  public void whenStartSpecifiedAndNeitherServerNorClusterSpecified_shouldStartReturnsFalse() {
    domain.getSpec().setStartupControl(SPECIFIED_STARTUPCONTROL);
    domain.getSpec().setReplicas(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(false));
  }

  @Test
  public void whenClusterNotConfigured_useDefaultReplicaCount() {
    configureDomain(domain).setDefaultReplicas(5);

    assertThat(domain.getReplicaCount("nosuchcluster"), equalTo(5));
  }

  @Test
  public void afterReplicaCountSetForCluster_defaultIsUnchanged() {
    configureCluster("cluster1").withReplicas(5);

    assertThat(domain.getReplicaCount("cluster2"), equalTo(Domain.DEFAULT_REPLICA_LIMIT));
  }

  @Test
  public void whenClusterDefinedWithReplicas_useClusterReplicaCount() {
    configureDomain(domain).setDefaultReplicas(5);
    configureCluster("cluster1").withReplicas(3);

    assertThat(domain.getReplicaCount("cluster1"), equalTo(3));
  }

  @Test
  public void whenClusterDefinedWithoutReplicas_useDomainReplicaCount() {
    configureDomain(domain).setDefaultReplicas(5);
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
    V1Probe probe = spec.getLivenessProbe();

    assertThat(probe.getInitialDelaySeconds(), nullValue());
    assertThat(probe.getFailureThreshold(), nullValue());
    assertThat(probe.getPeriodSeconds(), nullValue());
  }

  @Test
  public void readinessProbeSettings_returnsNullValues() {
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);
    V1Probe probe = spec.getReadinessProbe();

    assertThat(probe.getInitialDelaySeconds(), nullValue());
    assertThat(probe.getFailureThreshold(), nullValue());
    assertThat(probe.getPeriodSeconds(), nullValue());
  }
}
