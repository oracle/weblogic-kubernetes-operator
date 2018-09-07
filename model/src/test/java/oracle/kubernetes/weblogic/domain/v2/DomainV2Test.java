// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import io.kubernetes.client.models.V1EnvVar;
import java.io.IOException;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainTestBase;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.ServerSpec;
import org.junit.Test;

public class DomainV2Test extends DomainTestBase {

  private static final String DOMAIN_V2_SAMPLE_YAML = "v2/domain-sample.yaml";

  @Override
  protected DomainConfigurator configureDomain(Domain domain) {
    return new DomainV2Configurator(domain);
  }

  @Test
  public void whenImageConfiguredOnDomainAndAdminServer_userServerSetting() {
    configureDomain(domain).setDefaultImage("domain-image");
    configureAdminServer().withImage("server-image");

    assertThat(domain.getAdminServerSpec().getImage(), equalTo("server-image"));
  }

  @Test
  public void whenImageConfiguredOnDomainAndServer_userServerSetting() {
    configureDomain(domain).setDefaultImage("domain-image");
    configureServer("server1").withImage("server-image");

    assertThat(domain.getServer("server1", "cluster1").getImage(), equalTo("server-image"));
  }

  @Test
  public void whenImageConfiguredOnDomainAndCluster_useClusterSetting() {
    configureDomain(domain).setDefaultImage("domain-image");
    configureCluster("cluster1").withImage("cluster-image");

    assertThat(domain.getServer("ms1", "cluster1").getImage(), equalTo("cluster-image"));
  }

  @Test
  public void whenClusterNotConfigured_useDefaultReplicaCount() {
    assertThat(domain.getReplicaCount("nosuchcluster"), equalTo(Domain.DEFAULT_REPLICA_LIMIT));
  }

  @Test
  public void whenImagePullPolicyConfiguredOnClusterAndServer_useServerSetting() {
    configureCluster("cluster1").withImagePullPolicy("always");
    configureServer("server1").withImagePullPolicy("never");

    assertThat(domain.getServer("server1", "cluster1").getImagePullPolicy(), equalTo("never"));
  }

  @Test
  public void whenImagePullSecretConfiguredOnClusterAndServer_useServerSetting() {
    configureCluster("cluster1").withImagePullSecret("cluster");
    configureServer("server1").withImagePullSecret("server");

    assertThat(
        domain.getServer("server1", "cluster1").getImagePullSecret().getName(), equalTo("server"));
  }

  @Test
  public void whenServerStartStateConfiguredOnClusterAndServer_useServerSetting() {
    configureCluster("cluster1").withServerStartState("cluster");
    configureServer("server1").withServerStartState("server");

    assertThat(domain.getServer("server1", "cluster1").getDesiredState(), equalTo("server"));
  }

  @Test
  public void whenEnvironmentConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withEnvironmentVariable("name1", "domain")
        .withEnvironmentVariable("name2", "domain");
    configureCluster("cluster1")
        .withEnvironmentVariable("name2", "cluster")
        .withEnvironmentVariable("name3", "cluster")
        .withEnvironmentVariable("name4", "cluster");
    configureServer("server1").withEnvironmentVariable("name4", "server");

    assertThat(
        domain.getServer("server1", "cluster1").getEnvironmentVariables(),
        containsInAnyOrder(
            envVar("name1", "domain"),
            envVar("name2", "cluster"),
            envVar("name3", "cluster"),
            envVar("name4", "server")));
  }

  private V1EnvVar envVar(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  @Test
  public void whenDomain2ReadFromYaml_unconfiguredServerHasDomainDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server0", null);

    assertThat(serverSpec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(serverSpec.getImagePullPolicy(), equalTo(IFNOTPRESENT_IMAGEPULLPOLICY));
    assertThat(serverSpec.getImagePullSecret().getName(), equalTo("pull-secret"));
    assertThat(serverSpec.getEnvironmentVariables(), contains(envVar("var1", "value0")));
    assertThat(serverSpec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  public void whenDomain2ReadFromYaml_adminServerOverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getAdminServerSpec();

    assertThat(serverSpec.getImage(), equalTo("store/oracle/weblogic:latest"));
    assertThat(serverSpec.getImagePullSecret().getName(), equalTo("pull-secret"));
    assertThat(serverSpec.getEnvironmentVariables(), contains(envVar("var1", "value1")));
  }

  @Test
  public void whenDomain2ReadFromYaml_server1OverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server1", "cluster1");

    assertThat(serverSpec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(serverSpec.getNodePort(), equalTo(7001));
    assertThat(
        serverSpec.getEnvironmentVariables(),
        containsInAnyOrder(
            envVar("JAVA_OPTIONS", "-server"),
            envVar("USER_MEM_ARGS", "-Xms64m -Xmx256m "),
            envVar("var1", "value0")));
  }

  @Test
  public void whenDomain2ReadFromYaml_cluster2OverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server2", "cluster2");

    assertThat(serverSpec.getDesiredState(), equalTo("ADMIN"));
    assertThat(
        serverSpec.getEnvironmentVariables(),
        containsInAnyOrder(
            envVar("JAVA_OPTIONS", "-Dweblogic.management.startupMode=ADMIN -verbose"),
            envVar("USER_MEM_ARGS", "-Xms64m -Xmx256m "),
            envVar("var1", "value0")));
  }
}
