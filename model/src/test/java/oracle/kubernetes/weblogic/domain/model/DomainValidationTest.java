// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import org.junit.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainValidationTest {
  private Domain domain = new Domain();

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

    assertThat(domain.getValidationFailures(), contains(stringContainsInOrder("server", "ms1")));
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

    assertThat(domain.getValidationFailures(), contains(stringContainsInOrder("cluster", "cluster1")));
  }

  @Test
  public void whenLogHomeDisabled_dontReportError() {
    configureDomain(domain).withLogHomeEnabled(false);

    assertThat(domain.getValidationFailures(), empty());
  }

  @Test
  public void whenVolumeMountHasLogHomeDirectory_dontReportError() {
    configureDomain(domain).withLogHomeEnabled(true).withLogHome("/shared/logs/mydomain");
    configureDomain(domain).withAdditionalVolumeMount("sharedlogs", "/shared/logs");

    assertThat(domain.getValidationFailures(), empty());
  }

  @Test
  public void whenNoVolumeMountHasLogHomeDirectory_reportError() {
    configureDomain(domain).withLogHomeEnabled(true).withLogHome("/private/log/mydomain");
    configureDomain(domain).withAdditionalVolumeMount("sharedlogs", "/shared/logs");

    assertThat(domain.getValidationFailures(), contains(containsStringIgnoringCase("log home")));
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return new DomainCommonConfigurator(domain);
  }
}
