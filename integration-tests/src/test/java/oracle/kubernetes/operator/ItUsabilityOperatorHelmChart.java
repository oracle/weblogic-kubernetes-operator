// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.Operator.RestCertType;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for testing Helm install for Operator(s)
 */
@TestMethodOrder(Alphanumeric.class)
public class ItUsabilityOperatorHelmChart extends BaseTest {

  private int waitTime = 5;
  private int maxIterations = 60;
  private static String testClassName;
  private static StringBuffer namespaceList;

  /**
   * This method gets called only once before any of the test methods are executed
   * It does the initialization of the integration test properties defined in 
   * OperatorIT.properties and define resultRoot, pvRoot, and projectRoot attributes.
   *
   * @throws Exception if the test initialization fails
   */
  @BeforeAll
  public static void staticPrepare() throws Exception {
    namespaceList = new StringBuffer("");
    testClassName = new Object() {
    }.getClass().getEnclosingClass().getSimpleName();
    initialize(APP_PROPS_FILE, testClassName);
  }

  /**
   * This method gets called before every test. 
   * It creates the result/pv root directories for the test. 
   * Creates the operator and domain if its not running.
   *
   * @throws Exception if result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {
    if (QUICKTEST) {
      createResultAndPvDirs(testClassName);
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception when tearDown method fails
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    tearDown(new Object() {}.getClass()
        .getEnclosingClass().getSimpleName(), namespaceList.toString());
  }

  /**
   * Install two operators op1 and op2. 
   * Delete and (re)install op2 with same attributes while op1 is running.
   * Make sure op2 is (re)installed successfully.
   *
   * @throws Exception if operator installation fails 
   */
  @Test
  public void testOperatorCreateDeleteCreate() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator firstoperator = null;
    Operator secondoperator = null;
    boolean gotException = false;
    try {
      LoggerHelper.getLocal().log(Level.INFO, "Checking if first operator is running, if not creating");
      Map<String, Object> operatorMap1 = createOperatorMap(getNewSuffixCount(), true, "usab");
      firstoperator =
          new Operator(operatorMap1, RestCertType.SELF_SIGNED);
      firstoperator.callHelmInstall();
      namespaceList.append(" ").append(operatorMap1.get("namespace"));
      int randNumber = getNewSuffixCount();
      Map<String, Object> operatorMap2 = createOperatorMap(randNumber, true, "usab");
      secondoperator =
          new Operator(operatorMap2, RestCertType.SELF_SIGNED);
      secondoperator.callHelmInstall();
      namespaceList.append(" ").append(operatorMap1.get("namespace"));
      LoggerHelper.getLocal().log(Level.INFO, "Delete second operator and verify the first operator pod still running");
      secondoperator.destroy();
      Thread.sleep(BaseTest.getWaitTimePod() * 1000);

      LoggerHelper.getLocal().log(Level.INFO, "Verify first perator pod is running");
      firstoperator.verifyOperatorReady();
      LoggerHelper.getLocal().log(Level.INFO,
          "Create again second operator pod and verify it is started again after create - delete -create steps");
      secondoperator =
          new Operator(
              (createOperatorMap(randNumber, true, "usab")),
              false,
              false,
              false,
              RestCertType.SELF_SIGNED);
      secondoperator.callHelmInstall();

    } catch (Exception ex) {
      gotException = true;
      LoggerHelper.getLocal().log(Level.INFO, "Got exception - " + testMethodName);
    } finally {
      if (firstoperator != null) {
        firstoperator.destroy();
      }
      if (secondoperator != null) {
        secondoperator.destroy();
      }
    }

    Assertions.assertFalse(gotException, "Helm installation should success");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Install operator usab-1 with namespace op-usab-ns.
   * Install operator usab-2 with same namesapce op-usab-ns.
   * Second operator should fail to install with following exception  
   * Error: rendered manifests contain a resource that already exists. 
   * Unable to continue with install: existing resource conflict: kind: Secret, 
   * namespace: usab-opns-1, name: weblogic-operator-secrets
   *
   * @throws Exception when second operator installation does not fail
   */
  @Test
  public void testCreateSecondOperatorUsingSameOperatorNsNegativeInstall() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO, "Creating first operator");
    Operator firstoperator =
        new Operator(createOperatorMap(getNewSuffixCount(), true, "usab"), RestCertType.SELF_SIGNED);
    firstoperator.callHelmInstall();

    Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, "usab");
    operatorMap.replace("namespace", firstoperator.getOperatorMap().get("namespace"));
    Operator secondoperator = new Operator(operatorMap, false, true, true, RestCertType.SELF_SIGNED);
    String oprelease = (String)(secondoperator.getOperatorMap()).get("releaseName");
    String opnamespace = (String)(secondoperator.getOperatorMap()).get("namespace");
    boolean installFailed = true;
    boolean gotExpectedInfo = true;
    try {
      secondoperator.callHelmInstall();
      installFailed = false;
    } catch (Exception ex) {
      if (!ex.getMessage()
          .contains("weblogic-operator-secrets")) {
        gotExpectedInfo = false;
      }
      LoggerHelper.getLocal().log(Level.INFO, "Exception Messsage " + ex.getMessage());
    } finally {
      if (firstoperator != null) {
        firstoperator.destroy();
      }
    }
    Assertions.assertTrue(installFailed, "Second operator helm installation should fail");
    Assertions.assertTrue(gotExpectedInfo, "Helm installation should fail with correct Exception String");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Install the operator with non existing operator namespace.
   * The helm install command should fail.
   * 
   * @throws Exception when helm install does not fail
   */
  @Test
  public void testNotPreCreatedOpNsCreateOperatorNegativeInstall() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    boolean gotException = true;

    operator =
        new Operator(
            (createOperatorMap(getNewSuffixCount(), false, "usab")),
            true,
            false,
            true,
            RestCertType.SELF_SIGNED);
    String command = " kubectl delete namespace " + operator.getOperatorNamespace();
    TestUtils.exec(command);
    try {
      operator.callHelmInstall();
      gotException = false;
    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "Helm install operator with non existing namespace failed as expected");
    }
    Assertions.assertTrue(gotException, "Helm install operator with non existing namespace should fail");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Install the operator with non existing operator service account.
   * Operator installation should fail.
   * Create the service account.
   * Make sure operator pod is in ready state.
   *
   * @throws Exception when operator pod is not ready after the service account is created 
   */
  @Test
  public void testNotPreexistedOpServiceAccountCreateOperatorNegativeInstall() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    operator =
        new Operator(
            (createOperatorMap(getNewSuffixCount(),false, "usab")),
            true,
            false,
            true,
            RestCertType.SELF_SIGNED);
    String oprelease = (String)(operator.getOperatorMap()).get("releaseName");
    String opnamespace = (String)(operator.getOperatorMap()).get("namespace");
    try {
      operator.callHelmInstall();
      throw new Exception(
          "FAILURE: Helm installs operator with not preexisted service account ");

    } catch (Exception ex) {
      String cmdLb = "";
      cmdLb = "helm list --failed --namespace " + opnamespace + " | grep " + oprelease;
      LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new Exception("FAILURE: failed helm is not showed in the failed list ");
      }
      // create operator service account
      String serviceAccount = (String) operator.getOperatorMap().get("serviceAccount");
      String operatorNS = (String) operator.getOperatorMap().get("namespace");
      if (serviceAccount != null && !serviceAccount.equals("default")) {
        result =
            ExecCommand.exec(
                "kubectl create serviceaccount " + serviceAccount + " -n " + operatorNS);
        if (result.exitValue() != 0) {
          throw new Exception(
              "FAILURE: Couldn't create serviceaccount "
                  + serviceAccount
                  + ". Cmd returned "
                  + result.stdout()
                  + "\n"
                  + result.stderr());
        }
      }
      // after service account created the operator should be started
      Thread.sleep(BaseTest.getWaitTimePod() * 2000);
      operator.verifyOperatorReady();
    } finally {
      if (operator != null) {
        operator.destroy();
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Install operator usab-1 with target DomainNameSpace [usab-domainns-1].
   * Install operator usab-2 with same target DomainNamesapce [usab-domainns-1].
   * Second operator should fail to install with following exception 
   * Error: rendered manifests contain a resource that already exists. 
   * Unable to continue with install: existing resource conflict: 
   * kind: RoleBinding, namespace: usab-domainns-1, 
   * name: weblogic-operator-rolebinding-namespace
   *
   * @throws Exception when second operator installation does not fail
   */
  @Test
  public void testSecondOpSharingSameTargetDomainsNsNegativeInstall() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO, "Creating first operator");
    Operator firstoperator =
        new Operator(createOperatorMap(getNewSuffixCount(), true, "usab"), RestCertType.SELF_SIGNED);
    firstoperator.callHelmInstall();

    Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), false, "usab");
    ArrayList<String> targetDomainsNS =
        (ArrayList<String>) firstoperator.getOperatorMap().get("domainNamespaces");
    operatorMap.put("domainNamespaces", targetDomainsNS);
    Operator secondoperator = new Operator(operatorMap, true, true, false, RestCertType.SELF_SIGNED);
    String oprelease = (String)(secondoperator.getOperatorMap()).get("releaseName");
    String opnamespace = (String)(secondoperator.getOperatorMap()).get("namespace");
    boolean installFailed = true;
    boolean gotExpectedInfo = true;
    try {
      secondoperator.callHelmInstall();
      installFailed = false;
    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "Caught exception " + ex.getMessage() + ex.getStackTrace());
      if (!ex.getMessage()
          .contains("weblogic-operator-rolebinding-namespace")) {
        gotExpectedInfo = false;
      }
    } finally {
      if (firstoperator != null) {
        firstoperator.destroy();
      }
    }
    Assertions.assertTrue(installFailed, "Second operator helm installation should fail");
    Assertions.assertTrue(gotExpectedInfo, "Helm installation should fail with correct exception string");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Install an operator with a non-existing target domain namespace.
   * The installation should fail with following exception 
   * Error: namespaces "usab-domainns-1" not found
   *
   * @throws Exception when operator installation does not fail
   */
  @Test
  public void testTargetNsIsNotPreexistedNegativeInstall() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), false, "usab");
    operator =
        new Operator(operatorMap,
            true,
            true,
            false,
            RestCertType.SELF_SIGNED);
    String oprelease = (String)(operator.getOperatorMap()).get("releaseName");
    String opnamespace = (String)(operator.getOperatorMap()).get("namespace");

    boolean installFailed = true;
    boolean gotExpectedInfo = true;
    try {
      operator.callHelmInstall();
      installFailed = false;
    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "Caught exception " + ex.getMessage() + ex.getStackTrace());
      if (!ex.getMessage()
          .contains(" namespaces \""  
                  + ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0) + "\" not found")) {
        gotExpectedInfo = false;
      }
    } finally {
      if (operator != null) {
        operator.destroy();
      }
    }
    Assertions.assertTrue(installFailed, "Operator helm installation should fail");
    Assertions.assertTrue(gotExpectedInfo, "Helm installation should fail with correct exception string");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Intitialize two operators op1 and op2 with same ExternalRestHttpPort.
   * Install operator op1.
   * Install operator op2.
   * Installation of second operator should fail.
   *
   * @throws Exception when second operator installation does not fail
   */
  @Test
  public void testSecondOpSharingSameExternalRestPortNegativeInstall() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    int httpsRestPort = 0;

    Operator operator1 = new Operator(createOperatorMap(getNewSuffixCount(),
        true, "usab"),
        RestCertType.SELF_SIGNED);
    operator1.callHelmInstall();

    httpsRestPort = new Integer(operator1.getOperatorMap().get("externalRestHttpsPort").toString()).intValue();
    LoggerHelper.getLocal().log(Level.INFO, "Creating second operator with externalRestHttpPort " + httpsRestPort);
    Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, "usab");
    operatorMap.replace("externalRestHttpsPort", httpsRestPort);
    String oprelease = (String)operatorMap.get("releaseName");
    String opnamespace = (String)operatorMap.get("namespace");
    Operator operator2 = new Operator(operatorMap, RestCertType.SELF_SIGNED);
    try {
      operator2.callHelmInstall();
      throw new Exception(
          "FAILURE: Helm install operator with duplicate rest port number ");
    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "Error message " + ex.getMessage());
      if (!ex.getMessage()
          .contains(
              "Service \"external-weblogic-operator-svc\" is invalid: spec.ports[0].nodePort: Invalid value:")) {
        throw new Exception(
            "FAILURE: Helm install operator with duplicate rest port number does not report the expected message "
                + ex.getMessage());
      }
      String cmdLb = "";
      cmdLb = "helm list --failed --namespace " + opnamespace + " | grep " + oprelease;
      LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new Exception(
            "FAILURE: Helm install operator with duplicate Rest Port number ");
      }
    } finally {
      if (operator1 != null) {
        operator1.destroy();
      }
      if (operator2 != null) {
        operator2.destroy();
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Initialize the operator with elkIntegrationEnabled attribute set to "true".
   * Install the operator.
   * Installation should fail as elkIntegrationEnabled is supposed to be boolean.
   * Initialize the operator with javaLoggingLevel attribute set to "VERBOSE".
   * Install the operator.
   * Installation should fail as VERBOSE is not a valid value for javaLoggingLevel 
   * @throws Exception when operator installation does not fail
   */
  @Test
  public void testCreateChartWithInvalidAttributesNegativeInstall() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, "usab");

    try {
      operatorMap.put("elkIntegrationEnabled", "true");
      operator = new Operator(operatorMap, RestCertType.SELF_SIGNED);
      operator.callHelmInstall();
      throw new Exception(
          "FAILURE: Helm installs the operator with an invalid value for attribute elkIntegrationEnabled ");

    } catch (Exception ex) {
      if (!ex.getMessage().contains("elkIntegrationEnabled must be a bool : string")) {
        throw new Exception(
            "FAILURE: Helm installs the operator with an invalid value for an attribute "
                + "elkIntegrationEnabled does not report expected message "
                + ex.getMessage());
      }
    }
    namespaceList.append(" ").append(operatorMap.get("namespace"));
    try {
      operatorMap = createOperatorMap(getNewSuffixCount(), true, "usab");
      operatorMap.put("javaLoggingLevel", "VERBOSE");
      operator = new Operator(operatorMap, true, true, false, RestCertType.SELF_SIGNED);
      operator.callHelmInstall();
      throw new Exception(
          "FAILURE: Helm installs the operator with an invalid value for an attribute javaLoggingLevel ");

    } catch (Exception ex) {
      if (!ex.getMessage()
          .contains(
              "javaLoggingLevel must be one of the following values [SEVERE WARNING "
                  + "INFO CONFIG FINE FINER FINEST] : VERBOSE")) {
        throw new Exception(
            "FAILURE: Helm installs the operator with an invalid value for an attribute "
                + "externalRestEnabled does not report expected message "
                + ex.getMessage());
      }
    } 
    namespaceList.append(" ").append(operatorMap.get("namespace"));
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Install the operator with no override for domain namespaces
   * Remove domainNamespaces override completely 
   * Make sure the chart picks the default value as specified chart values.yaml.
   *
   * @throws Exception when operator pod is not ready
   */
  @Test
  public void testCreateWithMissingTargetDomainInstall() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    try {
      Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, "usab");
      operatorMap.remove("domainNamespaces");
      operator = new Operator(operatorMap, RestCertType.SELF_SIGNED);
      operator.callHelmInstall();
      operator.verifyOperatorReady();
      namespaceList.append(" ").append(operatorMap.get("namespace"));
    } finally {
      if (operator != null) {
        operator.destroy();
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Install the operator with empty string as target domains namespaces
   * This is equivalent of QuickStart guide does when it installs the operator 
   * with ' --set "domainNamespaces={}" '.
   *
   * @throws Exception when operator pod is not ready
   */
  @Test
  public void testCreateWithEmptyTargetDomainInstall() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    try {
      Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, "usab");
      ArrayList<String> targetDomainsNS = new ArrayList<String>();
      targetDomainsNS.add("");
      operatorMap.replace("domainNamespaces", targetDomainsNS);
      operator = new Operator(operatorMap, RestCertType.SELF_SIGNED);
      operator.callHelmInstall();
      operator.verifyOperatorReady();

    } finally {
      if (operator != null) {
        operator.destroy();
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create operator and verify it is deployed successfully 
   * Create domain1 and verify the domain is started 
   * Upgrade the operator target domainNamespaces to include namespace for domain2 
   * Verify both domains are managed by the operator by making a REST API call
   * Call helm upgrade to remove the first domain from operator target domainNamespaces
   * Verify it can't be managed by operator anymore.
   * @throws Exception when an operator fails to manage the domain as expected
   */
  @Test
  public void testAddRemoveDomainUpdateOperatorHC() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, "Creating Operator & waiting for the script to complete execution");
    // create operator
    int testNumber1 = getNewSuffixCount();
    int testNumber2 = getNewSuffixCount();
    Map<String, Object> operatorMap = createOperatorMap(testNumber1, true, "usab");
    Operator operator = new Operator(operatorMap, RestCertType.SELF_SIGNED);
    operator.callHelmInstall();
    namespaceList.append((String)operatorMap.get("namespace"));

    Domain domain = null;
    Domain domainnew = null;
    boolean testCompletedSuccessfully = false;
    try {
      LoggerHelper.getLocal().log(Level.INFO, "kubectl create namespace usab-domainns-" + testNumber2);
      ExecCommand.exec("kubectl create namespace usab-domainns-" + testNumber2);
      domain = createVerifyDomain(testNumber1, operator);
      ArrayList<String> targetDomainsNS =
          (ArrayList<String>) (operator.getOperatorMap().get("domainNamespaces"));
      targetDomainsNS.add("usab-domainns-" + testNumber2);
      upgradeOperatorDomainNamespaces(operator, targetDomainsNS);
      domainnew = createVerifyDomain(testNumber2,operator);
      LoggerHelper.getLocal().log(Level.INFO, "verify that old domain is managed by operator after upgrade");
      verifyOperatorDomainManagement(operator, domain, true);
      LoggerHelper.getLocal().log(Level.INFO, "Upgrade to remove first domain");
      String domainNS1 = domain.getDomainNs();
      targetDomainsNS.remove(domainNS1);
      upgradeOperatorDomainNamespaces(operator, targetDomainsNS);
      LoggerHelper.getLocal().log(Level.INFO, "verify that old domain is not managed by operator");
      verifyOperatorDomainManagement(operator, domain, false);
      verifyOperatorDomainManagement(operator, domainnew, true);
      LoggerHelper.getLocal().log(Level.INFO, "Upgrade to add first domain namespace in target domains");
      targetDomainsNS.add(domainNS1);
      upgradeOperatorDomainNamespaces(operator, targetDomainsNS);
      verifyOperatorDomainManagement(operator, domain, true);
      namespaceList.append(" ").append(String.join(" ", targetDomainsNS));

      testCompletedSuccessfully = true;
    } finally {
      if (domain != null) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
      if (domainnew != null) {
        domainnew.destroy();
      }
      if (operator != null) {
        operator.destroy();
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create operator and verify it is deployed successfully 
   * Create domain1 and verify domain is started 
   * Delete operator.
   * Make sure domain1 is still accessible by checking liveness probe for server(s)
   * @throws Exception when domain1 is not accessible in the absence of operator
   */
  @Test
  public void testDeleteOperatorButNotDomain() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, "Creating Operator & waiting for the script to complete execution");
    // create operator
    Operator operator = null;
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      int testNumber = getNewSuffixCount();
      Map<String, Object> operatorMap = createOperatorMap(testNumber, true, "usab");

      operator = new Operator(operatorMap, RestCertType.SELF_SIGNED);
      operator.callHelmInstall();
      domain = createVerifyDomain(testNumber, operator);
      LoggerHelper.getLocal().log(Level.INFO, "Deleting operator to check that domain functionality is not effected");
      operator.destroy();
      operator = null;
      domain.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
      if (operator != null) {
        operator.destroy();
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  private void verifyOperatorDomainManagement(
      Operator operator, Domain domain, boolean isAccessible) throws Exception {
    for (int i = 0; i < maxIterations; i++) {
      try {
        operator.verifyDomainExists(domain.getDomainUid());
        if (!isAccessible) {
          throw new Exception("FAILURE: Operator still able to manage old namespace ");
        } else {
          break;
        }
      } catch (Exception ex) {
        if (!isAccessible) {
          if (!ex.getMessage()
              .contains(
                  "Response {\"status\":404,\"detail\":\"/operator/latest/domains/" + domain.getDomainUid())) {
            // no-op
          } else {
            LoggerHelper.getLocal().log(Level.INFO,
                "Got 404, Operator can not access the domain " + domain.getDomainUid());
            break;
          }
        }
      }
      if (i == maxIterations - 1) {
        String errorMsg = "FAILURE: Operator can't access the domain " + domain.getDomainUid();
        if (!isAccessible) {
          errorMsg = "FAILURE: Operator still can access the domain " + domain.getDomainUid();
        }
        throw new Exception(errorMsg);
      }
      LoggerHelper.getLocal().log(Level.INFO, "iteration " + i + " of " + maxIterations);
      Thread.sleep(waitTime * 1000);
    }
  }

  private Domain createVerifyDomain(int number, Operator operator) throws Exception {

    Map<String, Object> wlsDomainMap = createDomainMap(number,"usab");
    Domain domain = TestUtils.createDomain(wlsDomainMap);
    domain.verifyDomainCreated();
    testAdminT3Channel(domain, false);
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
    LoggerHelper.getLocal().log(Level.INFO, "verify that domain is managed by operator");
    operator.verifyDomainExists(domain.getDomainUid());
    return domain;
  }

  private void upgradeOperatorDomainNamespaces(
      Operator operator, ArrayList<String> targetNamespaces) throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "update operator with new target domain");
    String upgradeSet =
        "domainNamespaces="
            + targetNamespaces
            .toString()
            .replaceAll("\\[", "{")
            .replaceAll("\\]", "}")
            .replaceAll(" ", "");
    LoggerHelper.getLocal().log(Level.INFO, "update operator with new target domain " + upgradeSet);
    operator.callHelmUpgrade(upgradeSet);
    operator.getOperatorMap().replace("domainNamespaces", targetNamespaces);
  }
}
