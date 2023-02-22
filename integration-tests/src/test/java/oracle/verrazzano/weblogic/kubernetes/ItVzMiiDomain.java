// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic.kubernetes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Yaml;
import oracle.verrazzano.weblogic.ApplicationConfiguration;
import oracle.verrazzano.weblogic.ApplicationConfigurationSpec;
import oracle.verrazzano.weblogic.Component;
import oracle.verrazzano.weblogic.ComponentSpec;
import oracle.verrazzano.weblogic.Components;
import oracle.verrazzano.weblogic.Destination;
import oracle.verrazzano.weblogic.IngressRule;
import oracle.verrazzano.weblogic.IngressTrait;
import oracle.verrazzano.weblogic.IngressTraitSpec;
import oracle.verrazzano.weblogic.IngressTraits;
import oracle.verrazzano.weblogic.Path;
import oracle.verrazzano.weblogic.Workload;
import oracle.verrazzano.weblogic.WorkloadSpec;
import oracle.verrazzano.weblogic.kubernetes.annotations.VzIntegrationTest;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.createApplication;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.createComponent;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.replaceNamespace;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Test to create model in image domain and verify the domain started successfully

@DisplayName("Test to a create model in image domain and start the domain in verrazzano")
@VzIntegrationTest
@Tag("v8o")
class ItVzMiiDomain {
  
  private static String domainNamespace = null;
  private final String domainUid = "domain1";
  private static LoggingFacade logger = null;
  

  /**
   * Label domain namespace.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(1) List<String> namespaces) throws Exception {
    logger = getLogger();
    logger.info("Getting unique namespace for Domain");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    domainNamespace = namespaces.get(0);
    setLabelToNamespace(domainNamespace);
  }

  /**
   * Create a WebLogic domain VerrazzanoWebLogicWorkload component in verrazzano.
   */
  @Test
  @DisplayName("Create model in image domain and verify services and pods are created and ready in verrazzano.")
  void testCreateVzMiiDomain() {

    // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
            "weblogicenc", "weblogicenc");

    // create cluster object
    String clusterName = "cluster-1";
    
    DomainResource domain = createDomainResource(domainUid, domainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName, new String[]{TEST_IMAGES_REPO_SECRET_NAME},
        encryptionSecretName, replicaCount, Arrays.asList(clusterName));

    Component component = new Component()
        .apiVersion("core.oam.dev/v1alpha2")
        .kind("Component")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new ComponentSpec()
            .workLoad(new Workload()
                .apiVersion("oam.verrazzano.io/v1alpha1")
                .kind("VerrazzanoWebLogicWorkload")
                .spec(new WorkloadSpec()
                    .template(domain))));
    
    Map<String, String> keyValueMap = new HashMap<>();
    keyValueMap.put("version", "v1.0.0");    
    keyValueMap.put("description", "My vz wls application");
    
    ApplicationConfiguration application = new ApplicationConfiguration()
        .apiVersion("core.oam.dev/v1alpha2")
        .kind("ApplicationConfiguration")
        .metadata(new V1ObjectMeta()
            .name("myvzdomain")
            .namespace(domainNamespace)
            .annotations(keyValueMap))
        .spec(new ApplicationConfigurationSpec()
            .components(Arrays.asList(new Components()
                    .componentName(domainUid))));
    
    logger.info(Yaml.dump(component));
    logger.info(Yaml.dump(application));
    
    logger.info("Deploying components");
    assertDoesNotThrow(() -> createComponent(component));    
    logger.info("Deploying application");
    assertDoesNotThrow(() -> createApplication(application));

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
    IngressTraits ingressTraits = new IngressTraits()
        .trait(Arrays.asList(new IngressTrait()
            .apiVersion("oam.verrazzano.io/v1alpha1")
            .kind("IngressTrait")
            .metadata(new V1ObjectMeta()
                .name("mydomain-ingress")
                .namespace(domainNamespace))
            .spec(new IngressTraitSpec()
                .ingressRules(Arrays.asList(new IngressRule()
                    .destination(new Destination()
                        .host(adminServerPodName)
                        .port(7001))
                    .paths(Arrays.asList(new Path()
                        .path("/console")
                        .pathType("Prefix"))))))));
    logger.info(Yaml.dump(ingressTraits));
  }

  private static void setLabelToNamespace(String domainNS) throws ApiException {
    //add label to domain namespace
    assertDoesNotThrow(() -> TimeUnit.MINUTES.sleep(1));
    Map<String, String> labels = new java.util.HashMap<>();
    labels.put("verrazzano-managed", "true");
    labels.put("istio-injection", "enabled");
    V1Namespace namespaceObject = assertDoesNotThrow(() -> Kubernetes.getNamespace(domainNS));
    logger.info(Yaml.dump(namespaceObject));
    assertNotNull(namespaceObject, "Can't find namespace with name " + domainNS);
    namespaceObject.getMetadata().setLabels(labels);
    assertDoesNotThrow(() -> replaceNamespace(namespaceObject));
    logger.info(Yaml.dump(Kubernetes.getNamespace(domainNS)));
  }

}
