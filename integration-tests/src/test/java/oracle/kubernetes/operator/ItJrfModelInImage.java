// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.DbUtils;
import oracle.kubernetes.operator.utils.DomainCrd;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.JrfDomain;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.Operator.RestCertType;
import oracle.kubernetes.operator.utils.RcuSecret;
import oracle.kubernetes.operator.utils.Secret;
import oracle.kubernetes.operator.utils.TestUtils;
import oracle.kubernetes.operator.utils.WalletPasswordSecret;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ItJrfModelInImage extends MiiBaseTest {
  private static Operator operator;
  private static String domainNS;
  private static String testClassName;
  private static StringBuffer namespaceList;
  private static String rcuSchemaPrefix = "jrfrcu";
  private static String rcuSchemaPass = "Oradoc_db1";
  private static String walletPassword = "welcome1";
  private static int dbPort;
  private static String dbNamespace;
  private static String dbUrl;
  private static JrfDomain jrfdomain; 
  
  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and sets
   * the resultRoot, pvRoot and projectRoot attributes.
   */
  @BeforeAll
  public static void staticPrepare() {
    namespaceList = new StringBuffer();
    testClassName = new Object() {
    }.getClass().getEnclosingClass().getSimpleName();
    
    LoggerHelper.getLocal().log(Level.INFO, "setting up properties, directories for: {0}", 
        testClassName);
    assertDoesNotThrow(() -> initialize(APP_PROPS_FILE, testClassName),
        "Failed: initial setup");   
  }

  /**
   * This method gets called before every test. It creates the result/pv root directories
   * for the test. It also creates RCU schema, operator.
   */
  @BeforeEach
  public void prepare() {

    LoggerHelper.getLocal().log(Level.INFO, "Creating result/pv root directories");
    assertDoesNotThrow(() -> createResultAndPvDirs(testClassName),
        "Failed: createResultAndPvDirs");
    
    LoggerHelper.getLocal().log(Level.INFO, "Copying sample dir to the result dir");
    assertDoesNotThrow(() -> TestUtils.exec(
        "cp -rf " 
         + BaseTest.getProjectRoot() 
         + "/kubernetes/samples/scripts " 
         + getResultDir(),
         true),
         "Failed: Copy sample dir to the result dir");
    
    //start DB and create RCU
    dbNamespace = "db" + String.valueOf(getNewSuffixCount());
    dbPort = 30011 + getNewSuffixCount();
    dbUrl = "oracle-db." + dbNamespace + ".svc.cluster.local:1521/devpdb.k8s";
    assertDoesNotThrow(() -> DbUtils.setupRCUdatabase(getResultDir(), dbPort, dbUrl, 
        rcuSchemaPrefix, dbNamespace),
        "Failed: setupRCUdatabase");
    
    // create operator
    if (operator == null) {
      Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(),
          true, testClassName);
      operator = assertDoesNotThrow(() -> TestUtils.createOperator(operatorMap, RestCertType.SELF_SIGNED));
      assertNotNull(operator);
      LoggerHelper.getLocal().log(Level.INFO, "Operator is created for {0}", testClassName);
      
      domainNS = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
      namespaceList.append((String)operatorMap.get("namespace"));
      namespaceList.append(" ").append(domainNS);
    }
  }
  
  /**
   * This method will run once after each test method is finished. It delete both RCU and DB pods
   */
  @AfterEach
  public void unPrepare() {
    LoggerHelper.getLocal().log(Level.INFO, "Is going to drop RCU schema and stop DB for {0}", testClassName);
    assertDoesNotThrow(() -> DbUtils.deleteRcuPod(getResultDir()),
        "Failed: drop RCU schema");
    assertDoesNotThrow(() -> DbUtils.deleteDbPod(getResultDir()),
        "Failed: stop DB");
  }

  /**
   * Archives result, pv directories.
   */
  @AfterAll
  public static void staticUnPrepare() {
    assertDoesNotThrow(() -> tearDown(new Object() {
        }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString()),
         "tearDown failed");
  }

  /**
   * Create and deploy a JRF domain using model in image. Save and restore walletFileSecret. After shutting down 
   * the domain, enable walletFileSecret in the domain yaml file. Restart the domain with the modified domain yaml 
   * file that reuses the same RCU schema. Verify in the restarted domain the required pods, services are created 
   * and the servers are ready.
   */
  @Test
  public void testReuseRCU2Deployments() {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap =
          createModelInImageMap(getNewSuffixCount(), testClassName);
      domainMap.put("namespace", domainNS);
      domainMap.put("wdtModelFile", "./model.jrf.yaml");
      domainMap.put("wdtModelPropertiesFile", "./model.properties");
      domainMap.put("domainHomeImageBase", BaseTest.getfmwImageName() + ":" + BaseTest.getfmwImageTag());
      domainMap.put("rcuSchemaPrefix", rcuSchemaPrefix);
      LoggerHelper.getLocal().log(Level.INFO, "DEBUG " + testClassName + "domain: dbUrl: " + dbUrl);
      domainMap.put("rcuDatabaseURL", dbUrl);
      domainMap.put("wdtDomainType", "JRF");
      domainMap.put("introspectorJobActiveDeadlineSeconds", "300");
      
      String domainUid = (String)domainMap.get("domainUID");
      String namespace = (String)domainMap.get("namespace");
      
      //create rcuAccess secret and walletPassword secret
      Secret rcuAccess = assertDoesNotThrow(() -> new RcuSecret(namespace, domainUid + "-rcu-access", 
          rcuSchemaPrefix, rcuSchemaPass, dbUrl));
      LoggerHelper.getLocal().log(Level.INFO, "RCU access secret is created for {0}", domainUid);
      
      Secret walletPass = assertDoesNotThrow(() -> new WalletPasswordSecret(namespace, 
          domainUid + "-opss-wallet-password-secret", walletPassword));
      LoggerHelper.getLocal().log(Level.INFO, "Wallet password secret is created for {0}", domainUid);
      
      jrfdomain = assertDoesNotThrow(() -> new JrfDomain(domainMap), 
          "Failed: JRF domain creation");
      LoggerHelper.getLocal().log(Level.INFO, "JRF domain is created for {0}", testClassName);
      assertDoesNotThrow(() -> jrfdomain.verifyDomainCreated(40), 
          "Failed: domain verification");
      LoggerHelper.getLocal().log(Level.INFO, "JRF domain verification succeeded for {0}", testClassName);
      
      //save and restore walletFile secret
      String scriptsDir = BaseTest.getProjectRoot()
          + "/integration-tests/src/test/resources/model-in-image/scripts/jrfscripts/";
      assertDoesNotThrow(() -> TestUtils.saveWalletFileSecret(scriptsDir, domainUid, namespace),
          "Failed to save walletFile secret");
      LoggerHelper.getLocal().log(Level.INFO, "Saved walletFile secret for {0}", domainUid);
      String walletFileSecretName = domainUid + "-opss-walletfile-secret";
      assertDoesNotThrow(() -> TestUtils.restoreWalletFileSecret(scriptsDir, domainUid, namespace, 
          walletFileSecretName), 
          "Failed to store walletFile secret");
      LoggerHelper.getLocal().log(Level.INFO, "Restored walletFile secret for {0}", domainUid);
      
      //shutdown the domain
      assertDoesNotThrow(() -> jrfdomain.shutdownUsingServerStartPolicy(),
          "Failed to shutdown the domain");
      LoggerHelper.getLocal().log(Level.INFO, "Shutdown the domain {0}", domainUid);
      
      //modify the original domain to enable walletFileSecret
      String originalYaml = getUserProjectsDir() + "/weblogic-domains/" + jrfdomain.getDomainUid()
          + "/domain.yaml"; 
      DomainCrd crd = assertDoesNotThrow(() -> new DomainCrd(originalYaml));
      Map<String, String> opssNode = new HashMap();
      opssNode.put("walletFileSecret", walletFileSecretName);
      crd.addObjectNodeToOpss(opssNode);
      String modYaml = assertDoesNotThrow(() -> crd.getYamlTree());
      LoggerHelper.getLocal().log(Level.INFO, modYaml);
      // Write the modified yaml to a new file
      Path path = Paths.get(getUserProjectsDir() + "/weblogic-domains/" + jrfdomain.getDomainUid(),
          "modified.domain.yaml");
      LoggerHelper.getLocal().log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      assertDoesNotThrow(() -> Files.write(path, modYaml.getBytes(charset)));
      
      //Use the new yaml to startup the domain
      LoggerHelper.getLocal().log(Level.INFO, "kubectl apply -f {0}", path.toString());
      ExecResult exec = assertDoesNotThrow(() -> TestUtils.exec("kubectl apply -f " + path.toString()));
      LoggerHelper.getLocal().log(Level.INFO, exec.stdout());
      
      assertDoesNotThrow(() -> jrfdomain.verifyDomainCreated(40));
      testCompletedSuccessfully = true;
    } catch (Exception ex) {
      ex.printStackTrace();
      Assertions.fail("FAILED - " + testMethodName);
    } finally {
      if (jrfdomain != null && (JENKINS || testCompletedSuccessfully)) {
        LoggerHelper.getLocal().log(Level.INFO, "DONE!!!");
        assertDoesNotThrow(() -> 
            TestUtils.deleteWeblogicDomainResources(jrfdomain.getDomainUid()));
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }
  
}
