// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import org.junit.Test;

public class DomainStorageTest {

  private Domain domain1 = new Domain();
  private Domain domain2 = new Domain();
  private Domain domain3 = new Domain();

  @Test
  public void domainsWithDifferentPredefinedClaims_areNotEqual() {
    configureDomain(domain1).withPredefinedClaim("claim1");
    configureDomain(domain2).withPredefinedClaim("claim2");

    assertThat(domain1, not(equalTo(domain2)));
  }

  @Test
  public void domainsWithSamePredefinedClaims_areEqual() {
    configureDomain(domain1).withPredefinedClaim("claim1");
    configureDomain(domain2).withPredefinedClaim("claim1");

    assertThat(domain1, equalTo(domain2));
  }

  @Test
  public void domainsWithDifferentHostPathStorage_areNotEqual() {
    configureDomain(domain1).withHostPathStorage("/my/path").withStorageSize("100Gi");
    configureDomain(domain2).withHostPathStorage("/my/path");

    assertThat(domain1, not(equalTo(domain2)));
  }

  @Test
  public void domainsWithSameHostPathStorage_areEqual() {
    configureDomain(domain1).withHostPathStorage("/my/path").withStorageSize("100Gi");
    configureDomain(domain2).withHostPathStorage("/my/path").withStorageSize("100Gi");

    assertThat(domain1, equalTo(domain2));
  }

  @Test
  public void domainsWithDifferentNfsStorage_areNotEqual() {
    configureDomain(domain1).withNfsStorage("oneHost", "/my/path");
    configureDomain(domain2).withNfsStorage("anotherHost", "/my/path");

    assertThat(domain1, not(equalTo(domain2)));
  }

  @Test
  public void domainsWithSameNfsStorage_areEqual() {
    configureDomain(domain1)
        .withNfsStorage("oneHost", "/my/path")
        .withStorageReclaimPolicy("Delete");
    configureDomain(domain2)
        .withNfsStorage("oneHost", "/my/path")
        .withStorageReclaimPolicy("Delete");

    assertThat(domain1, equalTo(domain2));
  }

  @Test
  public void domainsWithPredefinedClaimsAreNotEqualToDomainsWithOperatorCreatedStorage() {
    configureDomain(domain1).withPredefinedClaim("claim1");
    configureDomain(domain2).withHostPathStorage("/my/path");
    configureDomain(domain3).withNfsStorage("oneHost", "/my/path");

    assertThat(domain1, not(equalTo(domain2)));
    assertThat(domain1, not(equalTo(domain3)));
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return DomainConfiguratorFactory.forDomain(domain);
  }
}
