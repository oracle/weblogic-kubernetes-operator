// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import io.kubernetes.client.models.V1ObjectMeta;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import org.junit.Test;

public class JobHelperTest {

  private static final String NS = "ns1";

  @Test
  public void creatingServers_true_whenClusterReplicas_gt_0() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureCluster(domainPresenceInfo, "cluster1").withReplicas(1);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_false_whenClusterReplicas_is_0() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureCluster(domainPresenceInfo, "cluster1").withReplicas(0);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_true_whenDomainReplicas_gt_0_and_cluster_has_no_replicas() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo).withDefaultReplicaCount(1);

    configureCluster(domainPresenceInfo, "cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_false_whenDomainReplicas_is_0_and_cluster_has_no_replicas() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo).withDefaultReplicaCount(0);

    configureCluster(domainPresenceInfo, "cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_false_when_no_domain_nor_cluster_replicas() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureCluster(domainPresenceInfo, "cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_false_when_noCluster_and_START_NEVER_startPolicy() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_true_when_noCluster_and_START_IF_NEEDED_startPolicy() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo)
        .withDefaultServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_true_when_noCluster_and_START_ALWAYS_startPolicy() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo)
        .withDefaultServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_false_when_server_with_START_NEVER_startPolicy() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureServer(domainPresenceInfo, "managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_NEVER);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_true_when_server_with_START_IF_NEEDED_startPolicy() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureServer(domainPresenceInfo, "managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_true_when_server_with_START_AWLAYS_startPolicy() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureServer(domainPresenceInfo, "managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  private DomainPresenceInfo createDomainPresenceInfo() {
    DomainPresenceInfo domainPresenceInfo =
        new DomainPresenceInfo(new Domain().withMetadata(new V1ObjectMeta().namespace(NS)));
    configureDomain(domainPresenceInfo)
        .withDefaultServerStartPolicy(ConfigurationConstants.START_NEVER);
    return domainPresenceInfo;
  }

  private DomainConfigurator configureDomain(DomainPresenceInfo domainPresenceInfo) {
    return DomainConfiguratorFactory.forDomain(domainPresenceInfo.getDomain());
  }

  private ClusterConfigurator configureCluster(
      DomainPresenceInfo domainPresenceInfo, String clusterName) {
    return configureDomain(domainPresenceInfo).configureCluster(clusterName);
  }

  private ServerConfigurator configureServer(
      DomainPresenceInfo domainPresenceInfo, String serverName) {
    return configureDomain(domainPresenceInfo).configureServer(serverName);
  }
}
