// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

//import java.util.Arrays;
import java.util.List;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import oracle.weblogic.kubernetes.utils.DbUtils;
//import oracle.weblogic.kubernetes.utils.LoggingUtil;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.actions.ActionConstants.FMW_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.FMW_BASE_IMAGE_TAG;
//import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;


/**
 * Tests to create domain in persistent volume using WLST and WDT.
 */
@DisplayName("Verify the WebLogic server pods can run with domain created in persistent volume")
@IntegrationTest
public class ItJrfDomainInPV implements LoggedTest {

  private static String dbNamespace = null;
  private static String opNamespace = null;
  private static String jrfDomainNamespace = null;

  private static final String RCUSCHEMAPREFIX = "jrfdomainpv";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static final String ORACLEDBSUFFIX = ".svc.cluster.local:1521/devpdb.k8s";


  private static String dbUrl = null;
  private static int dbPort = getNextFreePort(30000, 32767);

  private static String image = FMW_BASE_IMAGE_NAME + ":" + FMW_BASE_IMAGE_TAG;
  private static boolean isUseSecret = true;

  private final String wlSecretName = "weblogic-credentials";

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(5, MINUTES).await();

  /**
   * Start DB service and create RCU schema
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(1) List<String> namespaces) {

    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ORACLEDBSUFFIX;

    /*
    TODO temporarily being commented out. Will be needed when JRF domain is added
    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for JRF domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    jrfDomainNamespace = namespaces.get(2);

    //TODO in the final version when JRF domain is added setupDBandRCUschema should be here
    //start DB and create RCU schema
    logger.info("Start DB and create RCU schema for namespace: {0}, RCU prefix: {1}, dbPort: {2}, "
        + "dbUrl: {3}", dbNamespace, RCUSCHEMAPREFIX, dbPort, dbUrl);
    assertDoesNotThrow(() -> DbUtils.setupDBandRCUschema(RCUSCHEMAPREFIX, dbNamespace,
        dbPort, dbUrl), String.format("Failed to create RCU schema for prefix %s in the namespace %s with "
        + "dbPort %s and dbUrl %s", RCUSCHEMAPREFIX, dbNamespace, dbPort, dbUrl));


    // install operator and verify its running in ready state
     installAndVerifyOperator(opNamespace, jrftDomainNamespace);

     */

    //determine if the tests are running in Kind cluster. if true use images from Kind registry
    if (KIND_REPO != null) {
      String kindRepoImage = KIND_REPO + image.substring(TestConstants.OCR_REGISTRY.length() + 1);
      logger.info("Using image {0}", kindRepoImage);
      image = kindRepoImage;
      isUseSecret = false;
    }


  }

  /**
   * Create a JRF domain using WLST in a persistent volume.
   * Create a domain custom resource with domainHomeSourceType as PersistentVolume.
   * Verify domain pods runs in ready state and services are created.
   * Verify login to WebLogic console is successful.
   */
  @Test
  @DisplayName("Create JRF domain in PV using WLST script")
  public void testDomainInPvUsingWlst() {

    //TODO temporarily being here.  Will be moved to BeforeAll when JRF domain is added
    logger.info("Start DB and create RCU schema for namespace: {0}, RCU prefix: {1}, dbPort: {2}, "
        + "dbUrl: {3}", dbNamespace, RCUSCHEMAPREFIX, dbPort, dbUrl);
    assertDoesNotThrow(() -> DbUtils.setupDBandRCUschema(RCUSCHEMAPREFIX, dbNamespace,
        dbPort, dbUrl), String.format("Failed to create RCU schema for prefix %s in the namespace %s with "
        + "dbPort %s and dbUrl %s", RCUSCHEMAPREFIX, dbNamespace, dbPort, dbUrl));
    //LoggingUtil.generateLog(this, Arrays.asList(dbNamespace));
  }


}

