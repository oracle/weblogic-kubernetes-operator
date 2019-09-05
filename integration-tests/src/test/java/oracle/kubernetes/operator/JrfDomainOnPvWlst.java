package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.DbUtils;
import oracle.kubernetes.operator.utils.DomainCrd;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.JrfDomain;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)

public class JrfDomainOnPvWlst extends BaseTest {
//property file used to customize operator properties for operator inputs yaml
 private static final String JRF_OPERATOR_FILE_1 = "jrfoperator1.yaml";
 // file used to customize domain properties for domain, PV and LB inputs yaml
 private static final String JRF_DOMAIN_ON_PV_WLST_FILE = "jrf_domainonpvwlst.yaml";
 private static String rcuSchemaPrefix = "jrfdomain";
 private static Operator operator1;

 /**
  * This method gets called only once before any of the test methods are executed. It does the
  * initialization of the integration test properties defined in OperatorIT.properties and setting
  * the resultRoot, pvRoot and projectRoot attributes. It also creates Oracle DB pod which used for
  * RCU.
  *
  * @throws Exception - if an error occurs when load property file or create DB pod
  */
 @BeforeClass
 public static void staticPrepare() throws Exception {
   // initialize test properties and create the directories
   initialize(APP_PROPS_FILE);
   TestUtils.exec(
       "cp -rf " + BaseTest.getProjectRoot() + "/kubernetes/samples/scripts/create-rcu-schema " + BaseTest.getResultDir(),
       true);
   DbUtils.startOracleDB();
   DbUtils.createRcuSchema(rcuSchemaPrefix);

   
 }

 /**
  * This method will run once after all test methods are finished. It Releases k8s cluster lease,
  * archives result, pv directories.
  *
  * @throws Exception - if any error occurs
  */
 @AfterClass
 public static void staticUnPrepare() throws Exception {
   logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
   logger.info("BEGIN");
   logger.info("Run once, release cluster lease");

   tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());

   logger.info("SUCCESS");
 }
 
 @Test
 public void testJrfDomainOnPvUsingWlst() throws Exception {
   Assume.assumeTrue(QUICKTEST);
   String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
   logTestBegin(testMethodName);
   logger.info("Creating Operator & waiting for the script to complete execution");
   // create operator1
   if (operator1 == null) {
     operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
   }

   JrfDomain jrfdomain = null;
   boolean testCompletedSuccessfully = false;

   try {
     // run RCU script to load db schema
     //DbUtils.runRcu(rcuPodName, JRF_DOMAIN_ON_PV_WLST_FILE);

     // create JRF domain
     jrfdomain = new JrfDomain(JRF_DOMAIN_ON_PV_WLST_FILE);

     // verify JRF domain created, servers up and running
     jrfdomain.verifyDomainCreated();

     // basic test cases
     testBasicUseCases(jrfdomain);

     

     testCompletedSuccessfully = true;
   } finally {
     /*if (jrfdomain != null && !SMOKETEST && (JENKINS || testCompletedSuccessfully)) {
       jrfdomain.shutdownUsingServerStartPolicy();
     }*/
   }

   logger.info("SUCCESS - " + testMethodName);
 }


 
 


}
