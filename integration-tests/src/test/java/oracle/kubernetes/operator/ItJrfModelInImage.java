// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

//import java.nio.charset.Charset;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
import java.util.ArrayList;
//import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.DbUtils;
//import oracle.kubernetes.operator.utils.Domain;
//import oracle.kubernetes.operator.utils.DomainCrd;
//import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.JrfDomain;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.Operator.RestCertType;
import oracle.kubernetes.operator.utils.RcuSecret;
import oracle.kubernetes.operator.utils.Secret;
import oracle.kubernetes.operator.utils.TestUtils;
import oracle.kubernetes.operator.utils.WalletSecret;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Simple JUnit test file used for testing Model in Image.
 *
 * <p>This test is used for creating domain using model in image.
 */

public class ItJrfModelInImage extends MiiBaseTest {
  private static Operator operator;
  private static String domainNS;
  private static String testClassName;
  private static StringBuffer namespaceList;
  private static String rcuSchemaPrefix = "jrfmii";
  private static String rcuDatabaseURL = "oracle-db.default.svc.cluster.local:1521/devpdb.k8s";
  private static String rcuSchemaPass = "Oradoc_db1";
  private static String walletPassword = "welcome1";
  
  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception
   */
  @BeforeAll
  public static void staticPrepare() throws Exception {
    namespaceList = new StringBuffer();
    testClassName = new Object() {
    }.getClass().getEnclosingClass().getSimpleName();
    // initialize test properties and create the directories
    initialize(APP_PROPS_FILE, testClassName);
  }

  /**
   * This method gets called before every test. It creates the result/pv root directories
   * for the test. Creates the operator and domain if its not running.
   *
   * @throws Exception exception if result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {

    createResultAndPvDirs(testClassName);
    
    TestUtils.exec(
            "cp -rf " 
            + BaseTest.getProjectRoot() 
            + "/kubernetes/samples/scripts " 
            + getResultDir(),
            true);
    //delete leftover pods caused by test being aborted
    DbUtils.deleteRcuPod(getResultDir());
    DbUtils.stopOracleDB(getResultDir());
         
    DbUtils.startOracleDB(getResultDir());
    DbUtils.createRcuSchema(getResultDir(),rcuSchemaPrefix);

    // create operator1
    if (operator == null) {
      Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(),
          true, testClassName);
      operator = TestUtils.createOperator(operatorMap, RestCertType.SELF_SIGNED);
      Assertions.assertNotNull(operator);
      domainNS = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
      namespaceList.append((String)operatorMap.get("namespace"));
      namespaceList.append(" ").append(domainNS);
    }
  }
  
  @AfterEach
  public void unPrepare() throws Exception {
    DbUtils.deleteRcuPod(getResultDir());
    DbUtils.stopOracleDB(getResultDir());
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    tearDown(new Object() {
    }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());

    LoggerHelper.getLocal().info("SUCCESS");
  }

  /**
   * Create a domain using model in image with model yaml and model properties
   * file in the image. Deploy the domain, verify the running domain has
   * the correct configuration as given in the image.
   *
   * @throws Exception exception
   */
  @Test
  public void testMiiWithNoConfigMap() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain & waiting for the script to complete execution");
    JrfDomain jrfdomain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap =
          createModelInImageMap(getNewSuffixCount(), testClassName);
      domainMap.put("namespace", domainNS);
      domainMap.put("wdtModelFile", "./model.jrf.yaml");
      domainMap.put("wdtModelPropertiesFile", "./model.properties");
      domainMap.put("domainHomeImageBase", BaseTest.getfmwImageName() + ":" + BaseTest.getfmwImageTag());
      domainMap.put("rcuSchemaPrefix", rcuSchemaPrefix);
      domainMap.put("rcuDatabaseURL", rcuDatabaseURL);
      domainMap.put("wdtDomainType", "JRF");
      domainMap.put("introspectorJobActiveDeadlineSeconds", "300");
      
      String domainUid = (String)domainMap.get("domainUID");
      String namespace = (String)domainMap.get("namespace");
      Secret rcuAccess = new RcuSecret(namespace, domainUid + "-rcu-access", 
          rcuSchemaPrefix, rcuSchemaPass, rcuDatabaseURL);
      Secret walletPass = new WalletSecret(namespace, domainUid 
          + "-opss-wallet-password-secret", walletPassword);
      
      domainMap.put("secrets", rcuAccess);
      domainMap.put("walletPasswordSecret", walletPass);
      
      jrfdomain = new JrfDomain(domainMap);
      jrfdomain.verifyDomainCreated(80);
      testCompletedSuccessfully = true;
    } finally {
      if (jrfdomain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(jrfdomain.getDomainUid());
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }
  
}
