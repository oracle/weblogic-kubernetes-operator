// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainValidationTest {
  private Domain domain = createTestDomain();

  @Test
  public void whenManagerServerSpecsHaveUniqueNames_dontReportError() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms2"));

    assertThat(domain.getValidationFailures(), empty());
  }

  @Test
  public void whenManagerServerSpecsHaveDuplicateNames_reportError() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));

    assertThat(domain.getValidationFailures(), contains(stringContainsInOrder("managedServers", "ms1")));
  }

  @Test
  public void whenManagerServerSpecsHaveDns1123DuplicateNames_reportError() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("Server-1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("server_1"));

    assertThat(domain.getValidationFailures(), contains(stringContainsInOrder("managedServers", "server-1")));
  }

  @Test
  public void whenClusterSpecsHaveUniqueNames_dontReportError() {
    domain.getSpec().getClusters().add(new Cluster().withClusterName("cluster1"));
    domain.getSpec().getClusters().add(new Cluster().withClusterName("cluster2"));

    assertThat(domain.getValidationFailures(), empty());
  }

  @Test
  public void whenClusterSpecsHaveDuplicateNames_reportError() {
    domain.getSpec().getClusters().add(new Cluster().withClusterName("cluster1"));
    domain.getSpec().getClusters().add(new Cluster().withClusterName("cluster1"));

    assertThat(domain.getValidationFailures(), contains(stringContainsInOrder("clusters", "cluster1")));
  }

  @Test
  public void whenClusterSpecsHaveDns1123DuplicateNames_reportError() {
    domain.getSpec().getClusters().add(new Cluster().withClusterName("Cluster-1"));
    domain.getSpec().getClusters().add(new Cluster().withClusterName("cluster_1"));

    assertThat(domain.getValidationFailures(), contains(stringContainsInOrder("clusters", "cluster-1")));
  }

  @Test
  public void whenLogHomeDisabled_dontReportError() {
    configureDomain(domain).withLogHomeEnabled(false);

    assertThat(domain.getValidationFailures(), empty());
  }

  @Test
  public void whenVolumeMountHasNonValidPath_reportError() {
    configureDomain(domain).withAdditionalVolumeMount("sharedlogs", "shared/logs");

    assertThat(domain.getValidationFailures(), contains(stringContainsInOrder("shared/logs", "sharedlogs")));
  }

  @Test
  public void whenVolumeMountHasLogHomeDirectory_dontReportError() {
    configureDomain(domain).withLogHomeEnabled(true).withLogHome("/shared/logs/mydomain");
    configureDomain(domain).withAdditionalVolumeMount("sharedlogs", "/shared/logs");

    assertThat(domain.getValidationFailures(), empty());
  }

  @Test
  public void whenNoVolumeMountHasSpecifiedLogHomeDirectory_reportError() {
    configureDomain(domain).withLogHomeEnabled(true).withLogHome("/private/log/mydomain");
    configureDomain(domain).withAdditionalVolumeMount("sharedlogs", "/shared/logs");

    assertThat(domain.getValidationFailures(), contains(stringContainsInOrder("log home", "/private/log/mydomain")));
  }

  @Test
  public void whenNoVolumeMountHasImplicitLogHomeDirectory_reportError() {
    configureDomain(domain).withLogHomeEnabled(true);

    assertThat(domain.getValidationFailures(), contains(stringContainsInOrder("log home", "/shared/logs/" + UID)));
  }

  @Test
  public void whenNonReservedEnvironmentVariableSpecifiedAtDomainLevel_dontReportError() {
    configureDomain(domain).withEnvironmentVariable("testname", "testValue");

    assertThat(domain.getValidationFailures(), empty());
  }

  @Test
  public void whenReservedEnvironmentVariablesSpecifiedAtDomainLevel_reportError() {
    configureDomain(domain)
        .withEnvironmentVariable("ADMIN_NAME", "testValue")
        .withEnvironmentVariable("INTROSPECT_HOME", "/shared/home/introspection");

    assertThat(domain.getValidationFailures(),
        contains(stringContainsInOrder("variables", "ADMIN_NAME", "INTROSPECT_HOME", "spec.serverPod.env", "are")));
  }

  @Test
  public void whenReservedEnvironmentVariablesSpecifiedForAdminServer_reportError() {
    configureDomain(domain)
        .configureAdminServer()
        .withEnvironmentVariable("LOG_HOME", "testValue")
        .withEnvironmentVariable("NAMESPACE", "badValue");

    assertThat(domain.getValidationFailures(),
        contains(stringContainsInOrder("variables", "LOG_HOME", "NAMESPACE", "spec.adminServer.serverPod.env", "are")));
  }

  @Test
  public void whenReservedEnvironmentVariablesSpecifiedAtServerLevel_reportError() {
    configureDomain(domain)
        .configureServer("ms1")
        .withEnvironmentVariable("SERVER_NAME", "testValue");

    assertThat(domain.getValidationFailures(),
        contains(stringContainsInOrder("variable", "SERVER_NAME", "spec.managedServers[ms1].serverPod.env", "is")));
  }

  @Test
  public void whenReservedEnvironmentVariablesSpecifiedAtClusterLevel_reportError() {
    configureDomain(domain)
        .configureCluster("cluster1")
        .withEnvironmentVariable("DOMAIN_HOME", "testValue");

    assertThat(domain.getValidationFailures(),
        contains(stringContainsInOrder("variable", "DOMAIN_HOME", "spec.clusters[cluster1].serverPod.env", "is")));
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return new DomainCommonConfigurator(domain);
  }
}
