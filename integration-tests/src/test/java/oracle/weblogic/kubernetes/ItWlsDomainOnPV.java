// Copyright (c) 2023, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.custom.Quantity;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.CreateIfNotExists;
import oracle.weblogic.domain.DomainCreationImage;
import oracle.weblogic.domain.DomainOnPV;
import oracle.weblogic.domain.DomainOnPVType;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.deleteClusterCustomResourceAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainResourceOnPv;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.FmwUtils.getConfiguration;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyDomainReady;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test to create a WLS domain in persistent volume with new simplified feature.
 */
@DisplayName("Test to create a WLS domain in persistent volume with new simplified feature")
@IntegrationTest
@Tag("kind-parallel")
@Tag("olcne-mrg")
@Tag("oke-parallel")
class ItWlsDomainOnPV {

  private static String domainNamespace = null;
  private static final String storageClassName = "weblogic-domain-storage-class";

  private static LoggingFacade logger = null;
  private static String DOMAINHOMEPREFIX = null;
  private static final String clusterName = "cluster-1";
  private static final int replicaCount = 2;
  private final String wlsModelFilePrefix = "model-wlsdomain-onpv-simplified";

  /**
   * Assigns unique namespaces for operator and domain.
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    String opNamespace = namespaces.get(0);

    // get a new unique domainNamespace
    logger.info("Assign a unique namespace for WLS domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);

    DOMAINHOMEPREFIX = "/shared/" + domainNamespace + "/domains/";

    // install operator and verify its running in ready state
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    installAndVerifyOperator(opNamespace, opNamespace + "-sa", false,
        0, opHelmParams, ELASTICSEARCH_HOST, false, true, null,
        null, false, "INFO", "DomainOnPvSimplification=true", false, domainNamespace);

    // create pull secrets for domainNamespace when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);
  }

  /**
   * Create a basic WLS domain on PV.
   * Operator will create PV/PVC and WLS Domain.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @DisplayName("Create a WLS domain on PV using simplified feature, Operator creates PV/PVC and WLS Domain")
  void testOperatorCreatesPvPvcWlsDomain() {
    String domainUid = "wlsonpv-simplified";
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();
    final String wlSecretName = domainUid + "-weblogic-credentials";
    final String wlsModelFile = wlsModelFilePrefix + ".yaml";
    try {
      // create WLS domain credential secret
      createSecretWithUsernamePassword(wlSecretName, domainNamespace,
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

      // create a model property file
      File wlsModelPropFile = createWdtPropertyFile("wlsonpv-simplified1");

      // create domainCreationImage
      String domainCreationImageName = DOMAIN_IMAGES_PREFIX + "wls-domain-on-pv-image";
      // create image with model and wdt installation files
      WitParams witParams =
          new WitParams()
              .modelImageName(domainCreationImageName)
              .modelImageTag(MII_BASIC_IMAGE_TAG)
              .modelFiles(Collections.singletonList(MODEL_DIR + "/" + wlsModelFile))
              .modelVariableFiles(Collections.singletonList(wlsModelPropFile.getAbsolutePath()));
      createAndPushAuxiliaryImage(domainCreationImageName, MII_BASIC_IMAGE_TAG, witParams);

      DomainCreationImage domainCreationImage =
          new DomainCreationImage().image(domainCreationImageName + ":" + MII_BASIC_IMAGE_TAG);

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
          new String[]{BASE_IMAGES_REPO_SECRET_NAME},
          DOMAINHOMEPREFIX,
          replicaCount,
          t3ChannelPort,
          configuration);

      // Set the inter-pod anti-affinity for the domain custom resource
      setPodAntiAffinity(domain);

      // create a domain custom resource and verify domain is created
      createDomainAndVerify(domain, domainNamespace);

      // verify that all servers are ready
      verifyDomainReady(domainNamespace, domainUid, replicaCount, "nosuffix");
    } finally {
      // delete the domain
      deleteDomainResource(domainNamespace, domainUid);
      // delete the cluster
      deleteClusterCustomResourceAndVerify(domainUid + "-" + clusterName, domainNamespace);
      deletePersistentVolumeClaim(pvcName, domainNamespace);
      deletePersistentVolume(pvName);
    }
  }

  private File createWdtPropertyFile(String domainName) {

    // create property file used with domain model file
    Properties p = new Properties();
    p.setProperty("adminUsername", ADMIN_USERNAME_DEFAULT);
    p.setProperty("adminPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("domainName", domainName);

    // create a model property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
        File.createTempFile(wlsModelFilePrefix, ".properties", new File(RESULTS_TEMPFILE)),
        "Failed to create WLS model properties file");

    // create the property file
    assertDoesNotThrow(() ->
        p.store(new FileOutputStream(domainPropertiesFile), "WLS properties file"),
        "Failed to write WLS properties file");

    return domainPropertiesFile;
  }
}
