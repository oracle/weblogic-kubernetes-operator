// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;
import static oracle.kubernetes.operator.DomainSourceType.FromModel;
import static oracle.kubernetes.operator.DomainSourceType.Image;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainValidationTest {

  private static final String SECRET_NAME = "mysecret";
  private static final String OVERRIDES_CM_NAME_IMAGE = "overrides-cm-image";
  private static final String OVERRIDES_CM_NAME_MODEL = "overrides-cm-model";
  private Domain domain = createTestDomain();
  private KubernetesResourceLookupStub resourceLookup = new KubernetesResourceLookupStub();

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @Before
  public void setUp() throws Exception {
    resourceLookup.defineResource(SECRET_NAME, KubernetesResourceType.Secret, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_MODEL, KubernetesResourceType.ConfigMap, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_IMAGE, KubernetesResourceType.ConfigMap, NS);
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
    resourceLookup.undefineResource(SECRET_NAME, KubernetesResourceType.Secret, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("WebLogicCredentials", SECRET_NAME, "not found", NS)));
  }

  @Test
  public void whenBadWebLogicCredentialsSecretNamespaceSpecified_reportError() {
    resourceLookup.defineResource(SECRET_NAME, KubernetesResourceType.Secret, "badNamespace");
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
    resourceLookup.defineResource("a-secret", KubernetesResourceType.Secret, NS);
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
    resourceLookup.defineResource("override-secret", KubernetesResourceType.Secret, NS);
    configureDomain(domain).withConfigOverrideSecrets("override-secret");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenConfigOverrideCmExistsTypeImage_dontReportError() {
    resourceLookup.defineResource("overrides-cm-image", KubernetesResourceType.ConfigMap, NS);
    configureDomain(domain).withConfigOverrides("overrides-cm-image").withDomainHomeSourceType(Image);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenConfigOverrideCmExistsTypeFromModel_reportError() {
    resourceLookup.defineResource("overrides-cm-model", KubernetesResourceType.ConfigMap, NS);
    resourceLookup.defineResource("wdt-cm-secret", KubernetesResourceType.Secret, NS);
    configureDomain(domain).withConfigOverrides("overrides-cm-model")
        .withRuntimeEncryptionSecret("wdt-cm-secret")
        .withDomainHomeSourceType(FromModel);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("Configuration overridesConfigMap", 
            "overrides-cm", "not supported", "FromModel")));
  }

  @Test
  public void whenWdtConfigMapExists_fromModel_dontReportError() {
    resourceLookup.defineResource("wdt-cm", KubernetesResourceType.ConfigMap, NS);
    resourceLookup.defineResource("wdt-cm-secret-model1", KubernetesResourceType.Secret, NS);
    configureDomain(domain)
        .withRuntimeEncryptionSecret("wdt-cm-secret-model1")
        .withModelConfigMap("wdt-cm")
        .withDomainHomeSourceType(FromModel);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenWdtConfigMapSpecifiedButDoesNotExist_fromModel_reportError() {
    resourceLookup.defineResource("wdt-cm-secret-model2", KubernetesResourceType.Secret, NS);
    configureDomain(domain).withRuntimeEncryptionSecret("wdt-cm-secret-model2")
        .withModelConfigMap("wdt-configmap")
        .withDomainHomeSourceType(FromModel);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("ConfigMap", "wdt-configmap", "spec.configuration.model.configMap", 
            "not found", NS)));
  }

  @Test
  public void whenWdtConfigMapSpecifiedButDoesNotExist_Image_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(Image)
        .withModelConfigMap("wdt-configmap");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenRuntimeEncryptionSecretSpecifiedButDoesNotExist_Image_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(Image)
        .withRuntimeEncryptionSecret("runtime-secret");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenRuntimeEncryptionSecretUnspecified_Image_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(Image);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenRuntimeEncryptionSecretSpecifiedButDoesNotExist_fromModel_reportError() {
    configureDomain(domain).withDomainHomeSourceType(FromModel)
        .withRuntimeEncryptionSecret("runtime-secret");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("RuntimeEncryption", "runtime-secret", "not found", NS)));
  }

  @Test
  public void whenRuntimeEncryptionSecretExists_fromModel_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(FromModel)
        .withRuntimeEncryptionSecret("runtime-good-secret");
    resourceLookup.defineResource("runtime-good-secret", KubernetesResourceType.Secret, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenRuntimeEncryptionSecretUnspecified_fromModel_reportError() {
    configureDomain(domain).withDomainHomeSourceType(FromModel);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("spec.configuration.model.runtimeEncryptionSecret", 
            "must be specified", "FromModel")));
  }

  @Test
  public void whenWalletPasswordSecretSpecifiedButDoesNotExist_fromModel_reportError() {
    configureDomain(domain).withDomainHomeSourceType(FromModel)
        .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
        .withOpssWalletPasswordSecret("wallet-password-secret-missing");

    resourceLookup.defineResource("runtime-encryption-secret-good", KubernetesResourceType.Secret, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("secret", "wallet-password-secret-missing", "not found", NS)));
  }

  @Test
  public void whenWalletFileSecretSpecifiedButDoesNotExist_Image_reportError() {
    configureDomain(domain).withDomainHomeSourceType(FromModel)
        .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
        .withOpssWalletFileSecret("wallet-file-secret-missing");

    resourceLookup.defineResource("runtime-encryption-secret-good", KubernetesResourceType.Secret, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("secret", 
            "wallet-file-secret-missing", "not found", NS)));
  }

  @Test
  public void whenWalletPasswordSecretExists_fromModel_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(FromModel)
        .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
        .withOpssWalletPasswordSecret("wallet-password-secret-good");
    resourceLookup.defineResource("runtime-encryption-secret-good", KubernetesResourceType.Secret, NS);
    resourceLookup.defineResource("wallet-password-secret-good", KubernetesResourceType.Secret, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenWalletFileSecretExists_fromModel_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(FromModel)
        .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
        .withOpssWalletFileSecret("wallet-file-secret-good");
    resourceLookup.defineResource("runtime-encryption-secret-good", KubernetesResourceType.Secret, NS);
    resourceLookup.defineResource("wallet-file-secret-good", KubernetesResourceType.Secret, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenWalletPasswordSecretUnspecified_fromModel_jrf_reportError() {
    configureDomain(domain).withDomainHomeSourceType(FromModel)
        .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
        .withDomainType("JRF");
    resourceLookup.defineResource("runtime-encryption-secret-good", KubernetesResourceType.Secret, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("spec.configuration.opss.walletPasswordSecret", 
            "must be specified", "FromModel", "JRF")));
  }

  @Test
  public void whenWalletFileSecretUnspecified_fromModel_jrf_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(FromModel)
        .withDomainType("JRF")
        .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
        .withOpssWalletPasswordSecret("wallet-password-secret-good");

    resourceLookup.defineResource("runtime-encryption-secret-good", KubernetesResourceType.Secret, NS);
    resourceLookup.defineResource("wallet-password-secret-good", KubernetesResourceType.Secret, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenWalletPasswordSecretUnspecified_Image_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(Image)
        .withOpssWalletFileSecret("wallet-file-secret");

    resourceLookup.defineResource("wallet-file-secret", KubernetesResourceType.Secret, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenWalletPasswordSecretUnspecified_fromModel_wls_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(Image)
        .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
        .withDomainType("WLS")
        .withOpssWalletFileSecret("wallet-file-secret");

    resourceLookup.defineResource("runtime-encryption-secret-good", KubernetesResourceType.Secret, NS);
    resourceLookup.defineResource("wallet-file-secret", KubernetesResourceType.Secret, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenExposingDefaultChannelIfIstio_Enabled() {
    configureDomain(domain)
        .withDomainHomeSourceType(Image)
        .withIstio()
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    assertThat(domain.getValidationFailures(resourceLookup),  contains(stringContainsInOrder(
        "Istio is enabled and the domain resource specified to expose channel",
        "default")));
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return new DomainCommonConfigurator(domain);
  }

  /**
   *  Types of Kubernetes resources which can be looked up on a domain.
   *   
   */
  public enum KubernetesResourceType {
    Secret, ConfigMap
  }

  @SuppressWarnings("SameParameterValue")
  private class KubernetesResourceLookupStub implements KubernetesResourceLookup {
    private Map<KubernetesResourceType, List<V1ObjectMeta>> definedResources = new ConcurrentHashMap<>();

    private List<V1ObjectMeta> getResourceList(KubernetesResourceType type) {
      return definedResources.computeIfAbsent(type, (key) -> new ArrayList<>());
    }

    void undefineResource(String name, KubernetesResourceType type, String namespace) {
      for (Iterator<V1ObjectMeta> each = getResourceList(type).iterator(); each.hasNext();) {
        if (hasSpecification(each.next(), name, namespace)) {
          each.remove();
        }
      }
    }

    void defineResource(String name, KubernetesResourceType type, String namespace) {
      Optional.ofNullable(getResourceList(type)).orElse(Collections.emptyList())
          .add(new V1ObjectMeta().name(name).namespace(namespace));
    }

    @Override
    public boolean isSecretExists(String name, String namespace) {
      return isResourceExists(name, KubernetesResourceType.Secret, namespace);
    }

    @Override
    public boolean isConfigMapExists(String name, String namespace) {
      return isResourceExists(name, KubernetesResourceType.ConfigMap, namespace);
    }

    private boolean isResourceExists(String name, KubernetesResourceType type, String namespace) {
      return getResourceList(type).stream().anyMatch(m -> hasSpecification(m, name, namespace));
    }

    boolean hasSpecification(V1ObjectMeta m, String name, String namespace) {
      return Objects.equals(name, m.getName()) && Objects.equals(namespace, m.getNamespace());
    }
  }
}
