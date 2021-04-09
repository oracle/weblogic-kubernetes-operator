// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;


import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;
import static oracle.kubernetes.operator.DomainSourceType.FromModel;
import static oracle.kubernetes.operator.DomainSourceType.Image;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainValidationTest extends DomainValidationBaseTest {

  private static final String ENV_NAME1 = "MY_ENV";
  private static final String RAW_VALUE_1 = "123";
  private static final String RAW_MOUNT_PATH_1 = "$(DOMAIN_HOME)/servers/$(SERVER_NAME)";
  private static final String RAW_MOUNT_PATH_2 = "$(MY_ENV)/bin";
  private static final String BAD_MOUNT_PATH_1 = "$DOMAIN_HOME/servers/$SERVER_NAME";
  private static final String BAD_MOUNT_PATH_2 = "$(DOMAIN_HOME/servers/$(SERVER_NAME";
  private static final String BAD_MOUNT_PATH_3 = "$()DOMAIN_HOME/servers/SERVER_NAME";

  private final Domain domain = createTestDomain();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();

  private static final String ADMIN_SERVER_NAME = "admin";
  private static final String CLUSTER = "cluster";

  private final WlsDomainConfig domainConfig = createDomainConfig();

  private static WlsDomainConfig createDomainConfig() {
    return createDomainConfig(CLUSTER);
  }

  private static WlsDomainConfig createDomainConfig(String clusterName) {
    WlsClusterConfig clusterConfig = new WlsClusterConfig(clusterName);
    return new WlsDomainConfig("base_domain")
        .withAdminServer(ADMIN_SERVER_NAME, "domain1-admin-server", 7001)
        .withCluster(clusterConfig);
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    resourceLookup.defineResource(SECRET_NAME, KubernetesResourceType.Secret, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_MODEL, KubernetesResourceType.ConfigMap, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_IMAGE, KubernetesResourceType.ConfigMap, NS);
    configureDomain(domain)
        .withWebLogicCredentialsSecret(SECRET_NAME, null);
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
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
  public void whenDomainHasAdditionalVolumeMountsWithInvalidChar_1_reportError() {
    configureDomain(domain)
        .withAdditionalVolumeMount("volume1", BAD_MOUNT_PATH_1);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder(
            "The mount path", BAD_MOUNT_PATH_1, "volume1", "of domain resource", "is not valid")));
  }

  @Test
  public void whenDomainHasAdditionalVolumeMountsWithInvalidChar_2_reportError() {
    configureDomain(domain)
        .withAdditionalVolumeMount("volume2", BAD_MOUNT_PATH_2);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder(
            "The mount path", BAD_MOUNT_PATH_2, "volume2", "of domain resource", "is not valid")));
  }

  @Test
  public void whenDomainHasAdditionalVolumeMountsWithInvalidChar_3_reportError() {
    configureDomain(domain)
        .withAdditionalVolumeMount("volume3", BAD_MOUNT_PATH_3);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder(
            "The mount path", BAD_MOUNT_PATH_3, "volume3", "of domain resource", "is not valid")));
  }

  @Test
  public void whenDomainHasAdditionalVolumeMountsWithReservedVariables_dontReportError() {
    configureDomain(domain)
        .withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_1);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenDomainHasAdditionalVolumeMountsWithCustomVariables_dontReportError() {
    configureDomain(domain)
        .withEnvironmentVariable(ENV_NAME1, RAW_VALUE_1)
        .withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenDomainHasAdditionalVolumeMountsWithNonExistingVariables_reportError() {
    configureDomain(domain)
        .withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder(
            "The mount path", RAW_MOUNT_PATH_2, "volume1", "of domain resource", "is not valid")));
  }

  @Test
  public void whenDomainAdminServerHasAdditionalVolumeMountsWithInvalidChar_reportError() {
    configureDomain(domain)
        .configureAdminServer()
        .getAdminServer()
        .addAdditionalVolumeMount("volume1", BAD_MOUNT_PATH_1);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("The mount path", BAD_MOUNT_PATH_1, 
            "volume1", "of domain resource", "is not valid")));
  }

  @Test
  public void whenDomainAdminServerHasAdditionalVolumeMountsWithReservedVariables_dontReportError() {
    configureDomain(domain)
        .configureAdminServer()
        .getAdminServer()
        .addAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_1);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenDomainAdminServerHasAdditionalVolumeMountsWithCustomVariables_dontReportError() {
    configureDomain(domain)
        .withEnvironmentVariable(ENV_NAME1, RAW_VALUE_1)
        .configureAdminServer()
        .getAdminServer()
        .addAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenDomainAdminServerHasAdditionalVolumeMountsWithNonExistingVariables_reportError() {
    configureDomain(domain)
        .configureAdminServer()
        .getAdminServer()
        .addAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("The mount path", "volume1", "of domain resource", "is not valid")));
  }
  
  @Test
  public void whenClusterServerPodHasAdditionalVolumeMountsWithInvalidChar_reportError() {
    configureDomain(domain)
        .configureCluster("Cluster-1").withAdditionalVolumeMount("volume1", BAD_MOUNT_PATH_1);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("The mount path", "of domain resource", "is not valid")));
  }

  @Test
  public void whenClusterServerPodHasAdditionalVolumeMountsWithReservedVariables_dontReportError() {
    configureDomain(domain)
        .configureCluster("Cluster-1").withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_1);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenClusterServerPodHasAdditionalVolumeMountsWithCustomVariables_dontReportError() {
    configureDomain(domain)
        .withEnvironmentVariable(ENV_NAME1, RAW_VALUE_1)
        .configureCluster("Cluster-1").withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  public void whenClusterServerPodHasAdditionalVolumeMountsWithNonExistingVariables_reportError() {
    configureDomain(domain)
        .configureCluster("Cluster-1").withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("The mount path", "volume1", "of domain resource", "is not valid")));
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

  @Test
  public void whenDomainUidExceedMaxAllowed_reportError() {
    String domainUID = "mydomainthatislongerthan46charactersandshouldfail";
    Domain myDomain = createTestDomain(domainUID);
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    assertThat(myDomain.getValidationFailures(resourceLookup),  contains(stringContainsInOrder(
        "DomainUID ", domainUID, "exceeds maximum allowed length")));
  }

  @Test
  public void whenDomainUidExceedMaxAllowedWithCustomSuffix_reportError() {
    String domainUID = "mydomainthatislongerthan42charactersandshould";
    Domain myDomain = createTestDomain(domainUID);
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    TuningParameters.getInstance().put(LegalNames.INTROSPECTOR_JOB_NAME_SUFFIX_PARAM, "introspect-domain-job");
    assertThat(myDomain.getValidationFailures(resourceLookup),  contains(stringContainsInOrder(
        "DomainUID ", domainUID, "exceeds maximum allowed length")));
  }

  @Test
  public void whenDomainUidNotExceedMaxAllowedWithCustomSuffix_dontReportError() {
    String domainUID = "mydomainthatislongerthan42charactersandshould";
    Domain myDomain = createTestDomain(domainUID);
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    TuningParameters.getInstance().put(LegalNames.INTROSPECTOR_JOB_NAME_SUFFIX_PARAM, "-job");
    assertThat(myDomain.getValidationFailures(resourceLookup),  empty());
  }

  @Test
  public void whenDomainUidNotExceedMaxAllowedWithEmptyCustomSuffix_dontReportError() {
    String domainUID = "mydomainthatislongerthan42charactersandshould";
    Domain myDomain = createTestDomain(domainUID);
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    TuningParameters.getInstance().put(LegalNames.INTROSPECTOR_JOB_NAME_SUFFIX_PARAM, "");
    assertThat(myDomain.getValidationFailures(resourceLookup),  empty());
  }

  @Test
  public void whenDomainUidPlusASNameNotExceedMaxAllowed_externalServiceDisabled_dontReportError() {
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String asName = "servernamecontains30character";
    domainConfig.setAdminServerName(asName);
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer();

    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainUidPlusASNameNotExceedMaxAllowed_externalServiceEnabled_dontReportError() {
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String asName = "servernamecontains26chars";
    domainConfig.setAdminServerName(asName);
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainUidPlusASNameExceedMaxAllowed_externalServiceEnabled_reportTwoErrors() {
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String asName = "servernamecontains32characterss";
    domainConfig.setAdminServerName(asName);
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  contains(
        stringContainsInOrder(
            "DomainUID ", domainUID, "server name", asName, "exceeds maximum allowed length"),
        stringContainsInOrder(
            "DomainUID ", domainUID, "admin server name", asName, "exceeds maximum allowed length")));
  }

  @Test
  public void whenDomainUidPlusASNameExceedMaxAllowed_externalServiceDisabled_reportOneError() {
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String asName = "servernamecontains32characterss";
    domainConfig.setAdminServerName(asName);
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer();
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  contains(
        stringContainsInOrder(
            "DomainUID ", domainUID, "server name", asName, "exceeds maximum allowed length")));
  }

  @Test
  public void whenDomainUidPlusASNameOnlyExternalServiceExceedMaxAllowed_reportOneError() {
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String asName = "servernamecontains30characters";
    domainConfig.setAdminServerName(asName);
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  contains(stringContainsInOrder(
        "DomainUID ", domainUID, "admin server name", asName, "exceeds maximum allowed length")));
  }

  @Test
  public void whenDomainUidPlusASNameNotExceedMaxAllowedWithCustomSuffix_dontReportError() {
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String asName = "servernamecontains21c";
    domainConfig.setAdminServerName(asName);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    TuningParameters.getInstance().put(LegalNames.EXTERNAL_SERVICE_NAME_SUFFIX_PARAM, "-external");
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainUidPlusASNameNotExceedMaxAllowedWithEmptyCustomSuffix_dontReportError() {
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String asName = "servernamecontains30characters";
    domainConfig.setAdminServerName(asName);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    TuningParameters.getInstance().put(LegalNames.EXTERNAL_SERVICE_NAME_SUFFIX_PARAM, "");
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainUidPlusASNameExceedMaxAllowedWithCustomSuffix_reportTwoErrors() {
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String asName = "servernamecontains31characterss";
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    domainConfig.setAdminServerName(asName);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    TuningParameters.getInstance().put(LegalNames.EXTERNAL_SERVICE_NAME_SUFFIX_PARAM, "-external");
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  contains(
        stringContainsInOrder(
            "DomainUID ", domainUID, "server name", asName, "exceeds maximum allowed length"),
        stringContainsInOrder(
            "DomainUID ", domainUID, "admin server name", asName, "exceeds maximum allowed length")));
  }

  @Test
  public void whenDomainUidPlusMSNameNotExceedMaxAllowed_dontReportError() {
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String msName = "servernamecontains29characte";
    domainConfig.getClusterConfig(CLUSTER)
        .addServerConfig(new WlsServerConfig(msName, "domain1-" + msName, 8001));
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainUidPlusMSNameNotExceedMaxAllowedWithClusterSize9_dontReportError() {
    WlsDomainConfig domainConfigWithCluster = createDomainConfig("CLUSTER-9");
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String msName = "servernamecontains27charact";
    for (int i = 1; i < 10; i++) {
      domainConfigWithCluster.getClusterConfig("CLUSTER-9")
          .addServerConfig(new WlsServerConfig(msName + i, "domain1-" + msName + "-" + i, 8001));
    }
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainUidPlusMSNameNotExceedMaxAllowedWithClusterSize99_dontReportError() {
    WlsDomainConfig domainConfigWithCluster = createDomainConfig("CLUSTER-99-good");
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String msName = "servernamecontains27charact";

    for (int i = 1; i < 100; i++) {
      domainConfigWithCluster.getClusterConfig("CLUSTER-99-good")
          .addServerConfig(new WlsServerConfig(msName + i, "domain1-" + msName + "-" + i, 8001));
    }
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfigWithCluster);
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize9_reportError() {
    WlsDomainConfig domainConfigWithCluster = createDomainConfig("CLUSTER-9-bad");
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String msNameBase = "servernamecontains28characte";

    ArrayList<String> errors = new ArrayList<>();
    for (int i = 1; i < 10; i++) {
      String msName = msNameBase + i;
      domainConfigWithCluster.getClusterConfig("CLUSTER-9-bad")
          .addServerConfig(new WlsServerConfig(msName, "domain1-" + msName, 8001));
      errors.add(String.format(
          "DomainUID '%s' and server name '%s' combination '%s' exceeds maximum allowed length '61'.",
          domainUID, msName, LegalNames.toServerServiceName(domainUID, msName)));
    }

    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfigWithCluster);
    List<String> reported = myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket());
    assertThat(reported, hasSize(9));
    for (int i = 0; i < reported.size(); i++) {
      assertThat(reported.get(i), equalTo(errors.get(i)));
    }
  }

  @Test
  public void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize99_reportError() {
    WlsDomainConfig domainConfigWithCluster = createDomainConfig("CLUSTER-99-bad");
    String domainUID = "mydomainnamecontains32characterS";
    Domain myDomain = createTestDomain(domainUID);
    String msNameBase = "servernamecontains28charactE";
    ArrayList<String> errors = new ArrayList<>();
    for (int i = 1; i < 100; i++) {
      String msName = msNameBase + i;
      domainConfigWithCluster.getClusterConfig("CLUSTER-99-bad")
          .addServerConfig(new WlsServerConfig(msName, "domain1-" + msName, 8001));
      errors.add(String.format(
          "DomainUID '%s' and server name '%s' combination '%s' exceeds maximum allowed length '62'.",
          domainUID, msName, LegalNames.toServerServiceName(domainUID, msName)));
    }
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfigWithCluster);
    List<String> reported = myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket());
    // the first 9 servers are fine so we only get 90 errors
    assertThat(reported, hasSize(90));
    for (int i = 0; i < reported.size(); i++) {
      assertThat(reported.get(i), equalTo(errors.get(i + 9)));
    }
  }

  @Test
  public void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize100_noExtrSpaceShouldBeReserved_dontReportError() {
    WlsDomainConfig domainConfigWithCluster = createDomainConfig("CLUSTER-100-good");
    String domainUID = "mydomainnamecontains32charactess";
    Domain myDomain2 = createTestDomain(domainUID);
    String msNameBase = "servernamecontains27characs";
    for (int i = 1; i <= 100; i++) {
      String msName = msNameBase + i;
      domainConfigWithCluster.getClusterConfig("CLUSTER-100-good")
          .addServerConfig(new WlsServerConfig(msName, "domain1-" + msName, 8001));
    }
    configureDomain(myDomain2)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfigWithCluster);
    assertThat(myDomain2.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize9ButClusterPaddingDisabled_dontReportError() {
    WlsDomainConfig domainConfigWithCluster = createDomainConfig("CLUSTER-9-bad-ok");
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String msNameBase = "servernamecontains28characte";

    ArrayList<String> errors = new ArrayList<>();
    for (int i = 1; i < 10; i++) {
      String msName = msNameBase + i;
      domainConfigWithCluster.getClusterConfig("CLUSTER-9-bad-ok")
          .addServerConfig(new WlsServerConfig(msName, "domain1-" + msName, 8001));
      errors.add(String.format(
          "DomainUID '%s' and server name '%s' combination '%s' exceeds maximum allowed length '61'.",
          domainUID, msName, LegalNames.toServerServiceName(domainUID, msName)));
    }

    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfigWithCluster);
    TuningParametersStub.setParameter(Domain.CLUSTER_SIZE_PADDING_VALIDATION_ENABLED_PARAM, "false");
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize99ButClusterPaddingDisabled_reportError() {
    WlsDomainConfig domainConfigWithCluster = createDomainConfig("CLUSTER-99-bad-ok");
    String domainUID = "mydomainnamecontains32characterS";
    Domain myDomain = createTestDomain(domainUID);
    String msNameBase = "servernamecontains28charactE";
    ArrayList<String> errors = new ArrayList<>();
    for (int i = 1; i < 100; i++) {
      String msName = msNameBase + i;
      domainConfigWithCluster.getClusterConfig("CLUSTER-99-bad-ok")
          .addServerConfig(new WlsServerConfig(msName, "domain1-" + msName, 8001));
      errors.add(String.format(
          "DomainUID '%s' and server name '%s' combination '%s' exceeds maximum allowed length '62'.",
          domainUID, msName, LegalNames.toServerServiceName(domainUID, msName)));
    }
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfigWithCluster);
    TuningParametersStub.setParameter(Domain.CLUSTER_SIZE_PADDING_VALIDATION_ENABLED_PARAM, "false");
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainUidPlusClusterNameNotExceedMaxAllowed_dontReportError() {
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String clusterName = "clusternamecontain21c";
    domainConfig.withCluster(new WlsClusterConfig(clusterName));
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainUidPlusClusterNameExceedMaxAllowed_reportError() {
    String domainUID = "mydomainnamecontains32characters";
    Domain myDomain = createTestDomain(domainUID);
    String clusterName = "servernamecontains31characters";
    domainConfig.withCluster(new WlsClusterConfig(clusterName));
    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  contains(stringContainsInOrder(
        "DomainUID ", domainUID, "cluster name", clusterName, "exceeds maximum allowed length")));
  }

  @Test
  public void whenDomainServerHasListenPort_dontReportError() {
    String domainUID = "TestDomainForRest";
    WlsDomainConfig domainConfigWithCluster = createDomainConfig("TestClusterForRest");
    Domain myDomain = createTestDomain(domainUID);
    String msNameBase = "TestServerForRest";
    String msName = msNameBase + 1;
    WlsServerConfig server = new WlsServerConfig(msName, domainUID + "-" + msName, 8001);
    server.setSslListenPort(null);
    server.setAdminPort(null);
    server.addNetworkAccessPoint(new NetworkAccessPoint("test-nap", "t3", 9001, 9001));
    domainConfigWithCluster.getClusterConfig("TestClusterForRest").addServerConfig(server);

    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfigWithCluster);

    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainServerHasSSLListenPort_dontReportError() {
    String domainUID = "TestDomainForRest";
    WlsDomainConfig domainConfigWithCluster = createDomainConfig("TestClusterForRest");
    Domain myDomain = createTestDomain(domainUID);
    String msNameBase = "TestServerForRest";
    String msName = msNameBase + 1;
    WlsServerConfig server = new WlsServerConfig(msName, domainUID + "-" + msName, 0);
    server.setListenPort(null);
    server.setSslListenPort(9001);
    server.setAdminPort(null);
    domainConfigWithCluster.getClusterConfig("TestClusterForRest").addServerConfig(server);

    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfigWithCluster);

    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainServerHasAdminPort_dontReportError() {
    String domainUID = "TestDomainForRest";
    WlsDomainConfig domainConfigWithCluster = createDomainConfig("TestClusterForRest");
    Domain myDomain = createTestDomain(domainUID);
    String msNameBase = "TestServerForRest";
    String msName = msNameBase + 1;
    WlsServerConfig server = new WlsServerConfig(msName, domainUID + "-" + msName, 0);
    server.setListenPort(null);
    server.setSslListenPort(null);
    server.setAdminPort(8800);
    domainConfigWithCluster.getClusterConfig("TestClusterForRest").addServerConfig(server);

    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfigWithCluster);

    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainServerHasAdminNAP_dontReportError() {
    String domainUID = "TestDomainForRest";
    WlsDomainConfig domainConfigWithCluster = createDomainConfig("TestClusterForRest");
    Domain myDomain = createTestDomain(domainUID);
    String msNameBase = "TestServerForRest";
    String msName = msNameBase + 1;
    WlsServerConfig server = new WlsServerConfig(msName, domainUID + "-" + msName, 8001);
    server.setListenPort(null);
    server.setSslListenPort(null);
    server.setAdminPort(null);
    server.addNetworkAccessPoint(new NetworkAccessPoint("test-nap", "admin", 9001, 9001));
    domainConfigWithCluster.getClusterConfig("TestClusterForRest").addServerConfig(server);

    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS")
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfigWithCluster);

    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  empty());
  }

  @Test
  public void whenDomainServerNoAvailablePortForREST_reportError() {
    String domainUID = "TestDomainForRest";
    WlsDomainConfig domainConfigWithCluster = createDomainConfig("TestClusterForRest");
    Domain myDomain = createTestDomain(domainUID);
    String msNameBase = "TestServerForRest";
    String msName = msNameBase + 1;
    WlsServerConfig server = new WlsServerConfig(msName, domainUID + "-" + msName, 8001);
    server.setListenPort(null);
    server.setSslListenPort(null);
    server.setAdminPort(null);
    server.addNetworkAccessPoint(new NetworkAccessPoint("test-nap", "t3", 9001, 9001));
    domainConfigWithCluster.getClusterConfig("TestClusterForRest").addServerConfig(server);

    configureDomain(myDomain)
        .withDomainHomeSourceType(Image)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withDomainType("WLS");

    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfigWithCluster);

    assertThat(myDomain.getAfterIntrospectValidationFailures(testSupport.getPacket()),  contains(stringContainsInOrder(
        "DomainUID", domainUID, "server", msName,
        "does not have a port available for the operator to send REST calls.")));
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return new DomainCommonConfigurator(domain);
  }
}
