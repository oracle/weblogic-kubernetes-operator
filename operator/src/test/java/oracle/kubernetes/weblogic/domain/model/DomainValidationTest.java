// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainValidationTest {

  private static final String SECRET_NAME = "mysecret";
  private Domain domain = createTestDomain();
  private KubernetesResourceLookupStub resourceLookup = new KubernetesResourceLookupStub();

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @Before
  public void setUp() throws Exception {
    resourceLookup.defineSecret(SECRET_NAME, NS);
    configureDomain(domain)
        .withWebLogicCredentialsSecret(SECRET_NAME, null);
  }

  @Test
  public void whenManagerServerSpecsHaveUniqueNames_dontReportError() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms2"));

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenManagerServerSpecsHaveDuplicateNames_reportError() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));

    assertThat(domain.getValidationFailures(resourceLookup),
               contains(stringContainsInOrder("managedServers", "ms1")));
  }

  @Test
  public void whenManagerServerSpecsHaveDns1123DuplicateNames_reportError() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("Server-1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("server_1"));

    assertThat(domain.getValidationFailures(resourceLookup),
               contains(stringContainsInOrder("managedServers", "server-1")));
  }

  @Test
  public void whenClusterSpecsHaveUniqueNames_dontReportError() {
    domain.getSpec().getClusters().add(new Cluster().withClusterName("cluster1"));
    domain.getSpec().getClusters().add(new Cluster().withClusterName("cluster2"));

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenClusterSpecsHaveDuplicateNames_reportError() {
    domain.getSpec().getClusters().add(new Cluster().withClusterName("cluster1"));
    domain.getSpec().getClusters().add(new Cluster().withClusterName("cluster1"));

    assertThat(domain.getValidationFailures(resourceLookup),
               contains(stringContainsInOrder("clusters", "cluster1")));
  }

  @Test
  public void whenClusterSpecsHaveDns1123DuplicateNames_reportError() {
    domain.getSpec().getClusters().add(new Cluster().withClusterName("Cluster-1"));
    domain.getSpec().getClusters().add(new Cluster().withClusterName("cluster_1"));

    assertThat(domain.getValidationFailures(resourceLookup),
               contains(stringContainsInOrder("clusters", "cluster-1")));
  }

  @Test
  public void whenLogHomeDisabled_dontReportError() {
    configureDomain(domain).withLogHomeEnabled(false);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenVolumeMountHasNonValidPath_reportError() {
    configureDomain(domain).withAdditionalVolumeMount("sharedlogs", "shared/logs");

    assertThat(domain.getValidationFailures(resourceLookup),
               contains(stringContainsInOrder("shared/logs", "sharedlogs")));
  }

  @Test
  public void whenVolumeMountHasLogHomeDirectory_dontReportError() {
    configureDomain(domain).withLogHomeEnabled(true).withLogHome("/shared/logs/mydomain");
    configureDomain(domain).withAdditionalVolumeMount("sharedlogs", "/shared/logs");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenNoVolumeMountHasSpecifiedLogHomeDirectory_reportError() {
    configureDomain(domain).withLogHomeEnabled(true).withLogHome("/private/log/mydomain");
    configureDomain(domain).withAdditionalVolumeMount("sharedlogs", "/shared/logs");

    assertThat(domain.getValidationFailures(resourceLookup),
               contains(stringContainsInOrder("log home", "/private/log/mydomain")));
  }

  @Test
  public void whenNoVolumeMountHasImplicitLogHomeDirectory_reportError() {
    configureDomain(domain).withLogHomeEnabled(true);

    assertThat(domain.getValidationFailures(resourceLookup),
               contains(stringContainsInOrder("log home", "/shared/logs/" + UID)));
  }

  @Test
  public void whenNonReservedEnvironmentVariableSpecifiedAtDomainLevel_dontReportError() {
    configureDomain(domain).withEnvironmentVariable("testname", "testValue");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenReservedEnvironmentVariablesSpecifiedAtDomainLevel_reportError() {
    configureDomain(domain)
        .withEnvironmentVariable("ADMIN_NAME", "testValue")
        .withEnvironmentVariable("INTROSPECT_HOME", "/shared/home/introspection");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("variables", "ADMIN_NAME", "INTROSPECT_HOME", "spec.serverPod.env", "are")));
  }

  @Test
  public void whenReservedEnvironmentVariablesSpecifiedForAdminServer_reportError() {
    configureDomain(domain)
        .configureAdminServer()
        .withEnvironmentVariable("LOG_HOME", "testValue")
        .withEnvironmentVariable("NAMESPACE", "badValue");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("variables", "LOG_HOME", "NAMESPACE", "spec.adminServer.serverPod.env", "are")));
  }

  @Test
  public void whenReservedEnvironmentVariablesSpecifiedAtServerLevel_reportError() {
    configureDomain(domain)
        .configureServer("ms1")
        .withEnvironmentVariable("SERVER_NAME", "testValue");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("variable", "SERVER_NAME", "spec.managedServers[ms1].serverPod.env", "is")));
  }

  @Test
  public void whenReservedEnvironmentVariablesSpecifiedAtClusterLevel_reportError() {
    configureDomain(domain)
        .configureCluster("cluster1")
        .withEnvironmentVariable("DOMAIN_HOME", "testValue");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("variable", "DOMAIN_HOME", "spec.clusters[cluster1].serverPod.env", "is")));
  }

  @Test
  public void whenWebLogicCredentialsSecretNameFound_dontReportError() {
    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenWebLogicCredentialsSecretNameFoundWithExplicitNamespace_dontReportError() {
    configureDomain(domain)
        .withWebLogicCredentialsSecret(SECRET_NAME, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenWebLogicCredentialsSecretNamespaceUndefined_useDomainNamespace() {
    configureDomain(domain)
        .withWebLogicCredentialsSecret(SECRET_NAME, null);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenWebLogicCredentialsSecretNameNotFound_reportError() {
    resourceLookup.undefineSecret(SECRET_NAME, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("WebLogicCredentials", SECRET_NAME, "not found", NS)));
  }

  @Test
  public void whenBadWebLogicCredentialsSecretNamespaceSpecified_reportError() {
    resourceLookup.defineSecret(SECRET_NAME, "badNamespace");
    configureDomain(domain)
        .withWebLogicCredentialsSecret(SECRET_NAME, "badNamespace");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("Bad namespace", "badNamespace")));
  }

  @Test
  public void whenImagePullSecretSpecifiedButDoesNotExist_reportError() {
    configureDomain(domain).withDefaultImagePullSecret(new V1LocalObjectReference().name("no-such-secret"));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("ImagePull", "no-such-secret", "not found", NS)));

  }

  @Test
  public void whenImagePullSecretExists_dontReportError() {
    resourceLookup.defineSecret("a-secret", NS);
    configureDomain(domain).withDefaultImagePullSecret(new V1LocalObjectReference().name("a-secret"));

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenConfigOverrideSecretSpecifiedButDoesNotExist_reportError() {
    configureDomain(domain).withConfigOverrideSecrets("override-secret");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("ConfigOverride", "override-secret", "not found", NS)));

  }

  @Test
  public void whenConfigOverrideSecretExists_dontReportError() {
    resourceLookup.defineSecret("override-secret", NS);
    configureDomain(domain).withConfigOverrideSecrets("override-secret");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return new DomainCommonConfigurator(domain);
  }

  @SuppressWarnings("SameParameterValue")
  private class KubernetesResourceLookupStub implements KubernetesResourceLookup {
    private List<V1ObjectMeta> definedSecrets = new ArrayList<>();

    void undefineSecret(String name, String namespace) {
      for (Iterator<V1ObjectMeta> each = definedSecrets.iterator(); each.hasNext();) {
        if (hasSpecification(each.next(), name, namespace)) {
          each.remove();
        }
      }
    }

    void defineSecret(String name, String namespace) {
      definedSecrets.add(new V1ObjectMeta().name(name).namespace(namespace));
    }

    @Override
    public boolean isSecretExists(String name, String namespace) {
      return definedSecrets.stream().anyMatch(m -> hasSpecification(m, name, namespace));
    }

    boolean hasSpecification(V1ObjectMeta m, String name, String namespace) {
      return Objects.equals(name, m.getName()) && Objects.equals(namespace, m.getNamespace());
    }
  }
}
