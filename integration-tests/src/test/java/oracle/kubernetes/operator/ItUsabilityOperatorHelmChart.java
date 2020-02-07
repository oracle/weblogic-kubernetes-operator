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
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception
   */
  @BeforeAll
  public static void staticPrepare() throws Exception {
    namespaceList = new StringBuffer("");
    testClassName = new Object() {
    }.getClass().getEnclosingClass().getSimpleName();
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
    if (QUICKTEST) {
      createResultAndPvDirs(testClassName);
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    tearDown(new Object() {}.getClass()
        .getEnclosingClass().getSimpleName(), namespaceList.toString());
  }

  /**
   * Helm will install 2 operators, delete, install again second operator with same attributes.
   *
   * @throws Exception exception
   */
  @Test
  public void testOperatorCreateDeleteCreate() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator firstoperator = null;
    Operator secondoperator = null;
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

    } finally {
      if (firstoperator != null) {
        firstoperator.destroy();
      }
      if (secondoperator != null) {
        secondoperator.destroy();
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Negative test: Helm will install 2 operators, with same namespace, second operator should fail.
   *
   * @throws Exception exception
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
    try {
      secondoperator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm installs second operator with same namespace as the first one");

    } catch (Exception ex) {
      if (!ex.getMessage()
          .contains(
              "Error: release "
                  + oprelease
                  + " failed: secrets \"weblogic-operator-secrets\" already exists")) {
        throw new RuntimeException(
            "FAILURE: Helm installs second operator with same namespace as the first one "
                + "does not report expected message "
                + ex.getMessage());
      }
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Helm installs second operator with same namespace as the first one ");
      }
    } finally {
      if (firstoperator != null) {
        firstoperator.destroy();
      }
      if (secondoperator != null) {
        secondoperator.destroy();
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Negative test : Helm will install the operator with not preexisted operator namespace.
   *
   * @throws Exception exception
   */
  @Test
  public void testNotPreCreatedOpNsCreateOperatorNegativeInstall() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;

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
      throw new RuntimeException("FAILURE: Helm install operator with not preexisted namespace ");

    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "Helm install operator with not preexisted ns failed as expected");
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Negative test : Helm will install the operator with not preexisted operator service account,
   * deployment will not start until service account will be created.
   *
   * @throws Exception exception
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
    try {
      operator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm installs operator with not preexisted service account ");

    } catch (Exception ex) {
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException("FAILURE: failed helm is not showed in the failed list ");
      }
      // create operator service account
      String serviceAccount = (String) operator.getOperatorMap().get("serviceAccount");
      String operatorNS = (String) operator.getOperatorMap().get("namespace");
      if (serviceAccount != null && !serviceAccount.equals("default")) {
        result =
            ExecCommand.exec(
                "kubectl create serviceaccount " + serviceAccount + " -n " + operatorNS);
        if (result.exitValue() != 0) {
          throw new RuntimeException(
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
   * Negative test : Helm will install the second operator with same target domain namespace as the
   * first operator.
   *
   * @throws Exception exception
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
    try {
      secondoperator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm installs second operator with same as first operator's target domains namespaces ");

    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "Caught exception " + ex.getMessage() + ex.getStackTrace());
      if (!ex.getMessage()
          .contains(
              "Error: release "
                  + oprelease
                  + " failed: rolebindings.rbac.authorization.k8s.io "
                  + "\"weblogic-operator-rolebinding-namespace\" already exists")) {
        throw new RuntimeException(
            "FAILURE: Helm installs second operator with same as first operator's "
                + "target domains namespaces does not report expected message "
                + ex.getMessage());
      }
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Helm installs second operator with same as first operator's target domains namespaces ");
      }

    } finally {
      if (firstoperator != null) {
        firstoperator.destroy();
      }
      if (secondoperator != null) {
        secondoperator.destroy();
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Negative test : Create operator with not preexisted target domain namespace.
   *
   * @throws Exception exception
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
    try {
      operator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm install operator with not preexisted target domains namespaces ");
    } catch (Exception ex) {
      if (!ex.getMessage()
          .contains(
              "Error: release "
                  + operatorMap.get("releaseName")
                  + " failed: namespaces \""
                  + ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0) + "\" not found")) {
        throw new RuntimeException(
            "FAILURE: Helm install operator with not preexisted target domains "
                + "namespaces does not report expected message "
                + ex.getMessage());
      }
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Helm install operator with not preexisted target domains namespaces ");
      }

    } finally {
      if (operator != null) {
        operator.destroy();
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Negative test : Helm installs the second operator with same ExternalRestPort as the first
   * operator.
   *
   * @throws Exception exception
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
    Operator operator2 = new Operator(operatorMap, RestCertType.SELF_SIGNED);
    try {
      operator2.callHelmInstall();

      throw new RuntimeException(
          "FAILURE: Helm install operator with dublicated Rest Port number ");

    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "Error message " + ex.getMessage());
      if (!ex.getMessage()
          .contains(
              "Service \"external-weblogic-operator-svc\" is invalid: spec.ports[0].nodePort: Invalid value:")) {
        throw new RuntimeException(
            "FAILURE: Helm install operator with dublicated rest port number does not report expected message "
                + ex.getMessage());
      }
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Helm install operator with dublicated Rest Port number ");
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
   * Negative test : Helm installs the operator with invalid target domains namespaces (UpperCase).
   *
   * @throws Exception exception
   */
  @Test
  public void testCreateWithUpperCaseTargetDomainNegativeInstall() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;

    Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, "usab");
    ArrayList<String> targetDomainsNS = new ArrayList<String>();
    targetDomainsNS.add("Test9");
    operatorMap.replace("domainNamespaces", targetDomainsNS);
    operator = new Operator(operatorMap, RestCertType.SELF_SIGNED);
    String oprelease = (String)operatorMap.get("releaseName");
    try {
      operator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm install operator with UpperCase for target domains ");

    } catch (Exception ex) {
      if (!ex.getMessage()
          .contains("Error: release " + oprelease + " failed: namespaces \"Test9\" not found")) {
        throw new RuntimeException(
            "FAILURE: Helm installs the operator with UpperCase for target "
                + "domains namespace does not report expected message "
                + ex.getMessage());
      }
      String cmdLb = "helm list --failed " + "  | grep " + oprelease;
      LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
      ExecResult result = ExecCommand.exec(cmdLb);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Helm installs the operator with UpperCase Target Domain NS ");
      }

    } finally {
      if (operator != null) {
        operator.destroy();
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Negative test : Helm installs the operator with invalid attributes values.
   *
   * @throws Exception exception
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
      throw new RuntimeException(
          "FAILURE: Helm installs the operator with invalid value for attribute elkIntegrationEnabled ");

    } catch (Exception ex) {
      if (!ex.getMessage().contains("elkIntegrationEnabled must be a bool : string")) {
        throw new RuntimeException(
            "FAILURE: Helm installs the operator with invalid value for attribute "
                + "elkIntegrationEnabled does not report expected message "
                + ex.getMessage());
      }
    }
    namespaceList.append(" ").append(operatorMap.get("namespace"));
    try {

      operatorMap = createOperatorMap(getNewSuffixCount(), true, "usab");
      operatorMap.put("javaLoggingLevel", "INVALIDOPTION");
      operator = new Operator(operatorMap, true, true, false, RestCertType.SELF_SIGNED);
      operator.callHelmInstall();
      throw new RuntimeException(
          "FAILURE: Helm installs the operator with invalid value for attribute javaLoggingLevel ");

    } catch (Exception ex) {
      if (!ex.getMessage()
          .contains(
              "javaLoggingLevel must be one of the following values [SEVERE WARNING "
                  + "INFO CONFIG FINE FINER FINEST] : INVALIDOPTION")) {
        throw new RuntimeException(
            "FAILURE: Helm installs the operator with invalid value for attribute "
                + "externalRestEnabled does not report expected message "
                + ex.getMessage());
      }
    } finally {
      //number++;
    }
    namespaceList.append(" ").append(operatorMap.get("namespace"));
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Helm will install the operator with no override for domainNamespaces, resulting in the use of
   * "default" as the target namespace. NOTE: This test must not override domainNamespaces with an
   * empty set or the operator will fail when it performs security checks because the RoleBinding
   * for the weblogic-operator-rolebinding-namespace will be missing. Rather, just remove the
   * domainNamespaces override completely so that we pick up the Operator defaults specified in the
   * Operator helm chart values.yaml.
   *
   * @throws Exception exception
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
   * Helm will install the operator with empty string as target domains namespaces. This is
   * equivalent to what the QuickStart guide does when it installs the operator with ' --set
   * "domainNamespaces={}" '
   *
   * @throws Exception exception
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
   * Helm installs the operator with default target domains namespaces.
   *
   * @throws Exception exception
   */
  //@Test -commenting out, it fails for runs in parallel due sharing same targetDomainNS.
  // uncomment if want to run in single run
  public void testCreateWithDefaultTargetDomainInstall() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Operator operator = null;
    try {
      Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, "usab");
      ArrayList<String> targetDomainsNS = new ArrayList<String>();
      targetDomainsNS.add("default");
      operatorMap.replace("domainNamespaces", targetDomainsNS);
      operator = new Operator(operatorMap, true, true, false, RestCertType.SELF_SIGNED);
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
   * Create operator and verify its deployed successfully. Create domain1 and verify domain is
   * started. Call helm upgrade to add domainnew to manage, verify both domains are managed by
   * operator Call helm upgrade to remove first domain from operator target domains, verify it can't
   * not be managed by operator anymore.
   *
   * @throws Exception exception
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
   * Create operator and verify its deployed successfully. Create domain1 and verify domain is
   * started. Delete operator and make sure domain1 is still functional.
   *
   * @throws Exception exception
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
          throw new RuntimeException("FAILURE: Operator still able to manage old namespace ");
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
        throw new RuntimeException(errorMsg);
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
