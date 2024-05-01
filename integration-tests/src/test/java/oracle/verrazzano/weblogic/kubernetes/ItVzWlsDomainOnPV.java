// Copyright (c) 2023, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
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
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.CreateIfNotExists;
import oracle.weblogic.domain.DomainCreationImage;
import oracle.weblogic.domain.DomainOnPV;
import oracle.weblogic.domain.DomainOnPVType;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.DomainUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.createApplication;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.createComponent;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainResourceOnPv;
import static oracle.weblogic.kubernetes.utils.FmwUtils.getConfiguration;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.VerrazzanoUtils.getIstioHost;
import static oracle.weblogic.kubernetes.utils.VerrazzanoUtils.getLoadbalancerAddress;
import static oracle.weblogic.kubernetes.utils.VerrazzanoUtils.setLabelToNamespace;
import static oracle.weblogic.kubernetes.utils.VerrazzanoUtils.verifyVzApplicationAccess;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to create model in image domain and verify the domain started successfully
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to a create model in image domain and start the domain in verrazzano")
@VzIntegrationTest
@Tag("v8o")
class ItVzWlsDomainOnPV {

  private static String domainNamespace;
  private final String domainUid = "domain1";
  private static final String storageClassName = "weblogic-domain-storage-class";
  private static String DOMAINHOMEPREFIX;
  private static final String clusterName = "cluster-1";
  private static final int replicaCount = 2;
  private final String wlsModelFilePrefix = "vz-model-wlsdomain-onpv-simplified";
  private Map<String, OffsetDateTime> podsWithTimeStamps;
  private final String adminServerPodName = domainUid + "-admin-server";
  private String managedServerPrefix = domainUid + "-managed-server";

  private static LoggingFacade logger;

  /**
   * Label domain namespace.
   *
   * @param namespaces list of namespaces created by test infra.
   */
  @BeforeAll
  public static void initAll(@Namespaces(1) List<String> namespaces) throws Exception {
    logger = getLogger();
    logger.info("Getting unique namespace for Domain");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    domainNamespace = namespaces.get(0);
    DOMAINHOMEPREFIX = "/shared/" + domainNamespace + "/domains/";
    setLabelToNamespace(Arrays.asList(domainNamespace));
    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);
  }

  /**
   * Create a persistent volume WebLogic domain VerrazzanoWebLogicWorkload component in verrazzano.
   */
  @Order(1)
  @Test
  @DisplayName("Create domain on persistent volume using new simplified domain on pv feature and"
      + " verify services and pods are created and ready in verrazzano.")
  void testCreateVzDPVDomain() {

    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();
    final String wlSecretName = domainUid + "-weblogic-credentials";

    // create WLS domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    DomainCreationImage domainCreationImage = createDomainCreationImage();

    // create a domain resource
    logger.info("Creating domain custom resource");
    Map<String, Quantity> pvCapacity = new HashMap<>();
    pvCapacity.put("storage", new Quantity("2Gi"));

    Map<String, Quantity> pvcRequest = new HashMap<>();
    pvcRequest.put("storage", new Quantity("2Gi"));

    Configuration configuration = null;
    if (OKE_CLUSTER) {
      configuration = getConfiguration(pvcName, pvcRequest, "oci-fss");
    } else {
      configuration = getConfiguration(pvName, pvcName, pvCapacity, pvcRequest, storageClassName,
          this.getClass().getSimpleName());
    }
    configuration.getInitializeDomainOnPV().domain(new DomainOnPV()
        .createMode(CreateIfNotExists.DOMAIN)
        .domainCreationImages(Collections.singletonList(domainCreationImage))
        .domainType(DomainOnPVType.WLS));

    DomainResource domain = createDomainResourceOnPv(
        domainUid,
        domainNamespace,
        wlSecretName,
        clusterName,
        pvName,
        pvcName,
        new String[]{TEST_IMAGES_REPO_SECRET_NAME},
        DOMAINHOMEPREFIX,
        replicaCount,
        t3ChannelPort,
        configuration);

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
    keyValueMap.put("description", "My vz wls domain on pv application");

    ApplicationConfiguration application = new ApplicationConfiguration()
        .apiVersion("core.oam.dev/v1alpha2")
        .kind("ApplicationConfiguration")
        .metadata(new V1ObjectMeta()
            .name("myvzdomainonpv")
            .namespace(domainNamespace)
            .annotations(keyValueMap))
        .spec(new ApplicationConfigurationSpec()
            .components(Arrays.asList(new Components()
                .componentName(domainUid)
                .traits(Arrays.asList(new IngressTraits()
                    .trait(new IngressTrait()
                        .apiVersion("oam.verrazzano.io/v1alpha1")
                        .kind("IngressTrait")
                        .metadata(new V1ObjectMeta()
                            .name("mydomain-ingress")
                            .namespace(domainNamespace))
                        .spec(new IngressTraitSpec()
                            .ingressRules(Arrays.asList(
                                new IngressRule()
                                    .paths(Arrays.asList(new Path()
                                        .path("/console")
                                        .pathType("Prefix")))
                                    .destination(new Destination()
                                        .host(adminServerPodName)
                                        .port(7001)),
                                new IngressRule()
                                    .paths(Arrays.asList(new Path()
                                        .path("/sample-war")
                                        .pathType("Prefix")))
                                    .destination(new Destination()
                                        .host(domainUid + "-cluster-" + clusterName)
                                        .port(8001)))))))))));

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

    // get istio gateway host and loadbalancer address
    String host = getIstioHost(domainNamespace);
    String address = getLoadbalancerAddress();

    // verify WebLogic console page is accessible through istio/loadbalancer
    String message = "Oracle WebLogic Server Administration Console";
    String consoleUrl = "https://" + host + "/console/login/LoginForm.jsp --resolve " + host + ":443:" + address;
    assertTrue(verifyVzApplicationAccess(consoleUrl, message), "Failed to get WebLogic administration console");
    
    // verify sample running in cluster is accessible through istio/loadbalancer
    message = "Hello World, you have reached server managed-server";
    String appUrl = "https://" + host + "/sample-war/index.jsp --resolve " + host + ":443:" + address;
    assertTrue(verifyVzApplicationAccess(appUrl, message), "Failed to get access to sample application");    
  }

  /**
   * Modify the domain scope restartVersion on the domain resource.
   * Verify all pods are restarted and back to ready state.
   * Verifies that the domain roll starting/pod cycle starting events are logged.
   */
  @Order(2)
  @Test
  @DisplayName("Restart pods using restartVersion flag")
  void testRestartVersion() {
    // get the original domain resource before update
    DomainUtils.getAndValidateInitialDomain(domainNamespace, domainUid);

    // get the map with server pods and their original creation timestamps
    podsWithTimeStamps = getPodsWithTimeStamps();

    String oldVersion = assertDoesNotThrow(()
        -> getDomainCustomResource(domainUid, domainNamespace).getSpec().getRestartVersion());
    int newVersion = oldVersion == null ? 1 : Integer.valueOf(oldVersion) + 1;

    OffsetDateTime timestamp = now();

    logger.info("patch the domain resource with new WebLogic secret, restartVersion and introspectVersion");
    String patchStr
        = "["
        + "{\"op\": \"add\", \"path\": \"/spec/restartVersion\", "
        + "\"value\": \"" + newVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));
  }

  private DomainCreationImage createDomainCreationImage() {
    final String wlsModelFile = wlsModelFilePrefix + ".yaml";
    // create a model property file
    File wlsModelPropFile = createWdtPropertyFile("wlsonpv-simplified1");
    // create domainCreationImage
    String domainCreationImageName = DOMAIN_IMAGES_PREFIX + "wls-domain-on-pv-image";
    String appName = "sample-app";
    // create image with model and wdt installation files
    // build an application archive using what is in resources/apps/APP_NAME
    logger.info("Build an application archive using resources/apps/{0}", appName);
    AppParams appParams = defaultAppParams().appArchiveDir(ARCHIVE_DIR + this.getClass().getSimpleName());
    assertTrue(buildAppArchive(appParams
        .srcDirList(Collections.singletonList(appName))),
        String.format("Failed to create app archive for %s", appName));

    // build the archive list
    String zipFile = String.format("%s/%s.zip", appParams.appArchiveDir(), appName);
    final List<String> archiveList = Collections.singletonList(zipFile);   
    WitParams witParams
        = new WitParams()
            .modelImageName(domainCreationImageName)
            .modelImageTag(MII_BASIC_IMAGE_TAG)
            .modelFiles(Collections.singletonList(MODEL_DIR + "/" + wlsModelFile))
            .modelVariableFiles(Collections.singletonList(wlsModelPropFile.getAbsolutePath()))
            .modelArchiveFiles(archiveList);
    createAndPushAuxiliaryImage(domainCreationImageName, MII_BASIC_IMAGE_TAG, witParams);

    DomainCreationImage domainCreationImage
        = new DomainCreationImage().image(domainCreationImageName + ":" + MII_BASIC_IMAGE_TAG);
    return domainCreationImage;
  }

  private File createWdtPropertyFile(String domainName) {
    // create property file used with domain model file
    Properties p = new Properties();
    p.setProperty("adminUsername", ADMIN_USERNAME_DEFAULT);
    p.setProperty("adminPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("domainName", domainName);

    // create a model property file
    File domainPropertiesFile = assertDoesNotThrow(()
        -> File.createTempFile(wlsModelFilePrefix, ".properties", new File(RESULTS_TEMPFILE)),
        "Failed to create WLS model properties file");

    // create the property file
    assertDoesNotThrow(()
        -> p.store(new FileOutputStream(domainPropertiesFile), "WLS properties file"),
        "Failed to write WLS properties file");

    return domainPropertiesFile;
  }

  private Map<String, OffsetDateTime> getPodsWithTimeStamps() {
    // create the map with server pods and their original creation timestamps
    podsWithTimeStamps = new LinkedHashMap<>();
    podsWithTimeStamps.put(adminServerPodName,
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
            String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                adminServerPodName, domainNamespace)));

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;
      podsWithTimeStamps.put(managedServerPodName,
          assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                  managedServerPodName, domainNamespace)));
    }
    return podsWithTimeStamps;
  }

}
