// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.List;

import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.verifyIntrospectorPodLogContainsExpectedErrorMsg;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Test introspector will fail with error msg if the consensus leasing is configured in the cluster or the database
// leasing is configured but there is no datasource.
@DisplayName("Test introspector will fail with error msg if the consensus leasing is configured in the cluster")
@IntegrationTest
@Tag("kind-parallel")
@Tag("oke-parallel")
@Tag("olcne")
class ItConsensusLeasingValidation {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private String domainUid = "domain1";
  private int replicaCount = 2;

  private static LoggingFacade logger = null;
  private static String adminSecretName = "weblogic-credentials";
  private static String encryptionSecretName = "encryptionsecret";

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // this secret is used only for non-kind cluster
    logger.info("Create the repo secret {0} to pull the image", TEST_IMAGES_REPO_SECRET_NAME);
    assertDoesNotThrow(() -> createTestRepoSecret(domainNamespace),
        String.format("createSecret failed for %s", TEST_IMAGES_REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));
  }

  @AfterEach
  public void deleteDomain() {
    deleteDomainResource(domainNamespace, domainUid);
  }

  /**
   * WebLogic Operator does not support consensus leasing.
   * Make sure the Introspector checks against this validation rule and provide the correct error message and fails
   */
  @Test
  @DisplayName("Introspector will fail if consensus leasing is configured in the cluster")
  void testConsensusLeasingValidation() {
    // create image with consensus leasing model file
    String modelfile = "model-consensus-leasing-clusterdomain-sampleapp.yaml";
    String consensusLeasingImageName =
        createMiiImageAndVerify("consensus-leasing-image", modelfile, MII_BASIC_APP_NAME);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(consensusLeasingImageName);

    // create the domain custom resource
    logger.info("Create domain resource {0} object in namespace {1} and verify that it is created",
        domainUid, domainNamespace);
    DomainResource domain = createDomainResource(domainUid,
        domainNamespace,
        consensusLeasingImageName,
        adminSecretName,
        new String[]{TEST_IMAGES_REPO_SECRET_NAME},
        encryptionSecretName
    );

    createDomainAndVerify(domain, domainNamespace);

    String expectedErrorMsg = "Automatic service migration is enabled for FileStore \\\"ClusterFileStore\\\" with "
        + "cluster target \\\"cluster-1\\\", but cluster is configured to use consensus leasing which is not supported "
        + "by the WebLogic Kubernetes Operator. Please configure cluster to use database leasing or set "
        + "SKIP_LEASING_VALIDATIONS environment variable to 'True' to skip this validation.";
    verifyIntrospectorPodLogContainsExpectedErrorMsg(domainUid, domainNamespace, expectedErrorMsg, Boolean.TRUE);
  }

  /**
   * The cluster is configured to use database leasing but no datasource configured.
   * Make sure the Introspector checks against this validation rule and provide the correct error message and fails
   */
  @Test
  @DisplayName("Introspector will fail if database leasing is configured in the cluster but no datasource")
  void testDatabaseLeasingNoDatasourceValidation() {
    // create image with database leasing model file
    String modelfile = "model-database-leasing-clusterdomain-sampleapp.yaml";
    String databaseLeasingImageName = createMiiImageAndVerify("database-leasing-image", modelfile, MII_BASIC_APP_NAME);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(databaseLeasingImageName);

    // create the domain custom resource
    logger.info("Create domain resource {0} object in namespace {1} and verify that it is created",
        domainUid, domainNamespace);
    DomainResource domain = createDomainResource(domainUid,
        domainNamespace,
        databaseLeasingImageName,
        adminSecretName,
        new String[]{TEST_IMAGES_REPO_SECRET_NAME},
        encryptionSecretName
    );

    createDomainAndVerify(domain, domainNamespace);

    String expectedErrorMsg = "At least one configured service requires leasing, but datasource for database leasing "
        + "is not configued in cluster \\\"cluster-1\\\". Set SKIP_LEASING_VALIDATIONS environment variable to 'True' "
        + "to skip this validation.";
    verifyIntrospectorPodLogContainsExpectedErrorMsg(domainUid, domainNamespace, expectedErrorMsg, Boolean.TRUE);
  }

}
