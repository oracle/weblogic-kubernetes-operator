// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.DbUtils;
import oracle.kubernetes.operator.utils.JrfDomain;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestMethodOrder(Alphanumeric.class)

public class ItJrfPvWlst extends BaseTest {
  private static String rcuSchemaPrefix = "jrfdomain";
  private static Operator operator;
  private static String domainNS;
  private static String domainUid = "";
  private static String restartTmpDir = "";
  private static boolean testCompletedSuccessfully;
  private static String testClassName;
  private static StringBuffer namespaceList;
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
        rcuSchemaPrefix, dbNamespace));
        
    // create operator
    if (operator == null) {
      Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(),
          true, testClassName);
      operator = assertDoesNotThrow(() -> TestUtils.createOperator(operatorMap, 
          Operator.RestCertType.SELF_SIGNED));
      assertNotNull(operator);
      LoggerHelper.getLocal().log(Level.INFO, "Operator is created for {0}", testClassName);
      
      domainNS = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
      namespaceList.append((String)operatorMap.get("namespace"));
      namespaceList.append(" ").append(domainNS);
    }
  }
   
  /**
   * This method will run once after every test method is finished. It delete both RCU and DB pods.
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
   * Releases k8s cluster lease, archives result, pv directories.
   */
  @AfterAll
  public static void staticUnPrepare() {
    assertDoesNotThrow(() -> tearDown(new Object() {
        }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString()),
         "tearDown failed");
  }

  /**
   * Create and deploy a JRF domain. Verify the domain is created successfully.
   */
  @Test
  public void testJrfDomainOnPvUsingWlst() {
    if (QUICKTEST) {
      String testMethodName = new Object() {
      }.getClass().getEnclosingMethod().getName();
      logTestBegin(testMethodName);
      
      boolean testCompletedSuccessfully = false;

      try {
        // create JRF domain
        Map<String, Object> domainMap = createDomainMap(getNewSuffixCount(), testClassName);
        domainMap.put("namespace", domainNS);
        domainMap.put("initialManagedServerReplicas", new Integer("2"));
        domainMap.put("clusterName", "infra-cluster");
        domainMap.put("managedServerNameBase", "infraserver");
        domainMap.put("domainHomeSourceType", "PersistentVolume");
        domainMap.put("rcuSchemaPrefix", "jrfdomain");
        LoggerHelper.getLocal().log(Level.INFO, "DEBUG " + testClassName + "domain: dbUrl: " 
            + dbUrl);
        domainMap.put("rcuDatabaseURL", dbUrl);
        domainUid = (String) domainMap.get("domainUID");
        LoggerHelper.getLocal().log(Level.INFO,
            "Creating and verifying the domain creation with domainUid: " + domainUid);

        jrfdomain = assertDoesNotThrow(() -> new JrfDomain(domainMap), 
            "Failed: JRF domain creation");
        LoggerHelper.getLocal().log(Level.INFO, "JRF domain is created for {0}", 
            testClassName);
        assertDoesNotThrow(() -> jrfdomain.verifyDomainCreated(60), 
            "Failed: domain verification");
        LoggerHelper.getLocal().log(Level.INFO, "JRF domain verification succeeded for {0}", 
            testClassName);
        
        // basic test cases
        assertDoesNotThrow(() -> testBasicUseCases(jrfdomain, false));
        LoggerHelper.getLocal().log(Level.INFO, "JRF domain BasicUseCases succeeded for {0}", 
            testClassName);

        testCompletedSuccessfully = true;
      } finally {
        if (jrfdomain != null  && (JENKINS || testCompletedSuccessfully)) {
          assertDoesNotThrow(() -> jrfdomain.shutdownUsingServerStartPolicy());
        }
      }

      LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
    }
  }
}
