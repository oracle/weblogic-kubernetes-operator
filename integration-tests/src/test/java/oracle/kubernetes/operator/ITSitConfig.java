// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

import static oracle.kubernetes.operator.BaseTest.getLeaseId;
import static oracle.kubernetes.operator.BaseTest.getProjectRoot;
import static oracle.kubernetes.operator.BaseTest.initialize;
import static oracle.kubernetes.operator.BaseTest.logger;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

public class ITSitConfig extends BaseTest {

    private final static String OPERATOR1FILE = "operator1.yaml";
    private final static String OPERATOR2FILE = "operator2.yaml";
    private final static String DOMAINONPVWLSTFILE = "domainonpvwlst.yaml";
    private final static String DOMAINONPVWDTFILE = "domainonpvwdt.yaml";
    private static String TESTSRCDIR = "";
    private static String TESTSCRIPTDIR = "";

    private static String ADMINPODNAME = "";
    private static final String DOMAINUID = "customsitdomain";
    private static final String ADMINPORT = "30704";
    private static final int T3CHANNELPORT = 30051;
    private static String fqdn = "";

    private static Domain domain = null;
    private static Operator operator1, operator2;
    // property file used to configure constants for integration tests
    private static final String APPPROPSFILE = "OperatorIT.properties";

    private static boolean QUICKTEST;
    private static final boolean SMOKETEST;
    private static boolean JENKINS;
    private static boolean INGRESSPERDOMAIN = true;

    // Set QUICKTEST env var to true to run a small subset of tests.
    // Set SMOKETEST env var to true to run an even smaller subset
    // of tests, plus leave domain1 up and running when the test completes.
    // set INGRESSPERDOMAIN to false to create LB's ingress by kubectl yaml file
    static {
        QUICKTEST
                = System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true");
        SMOKETEST
                = System.getenv("SMOKETEST") != null && System.getenv("SMOKETEST").equalsIgnoreCase("true");
        if (SMOKETEST) {
            QUICKTEST = true;
        }
        if (System.getenv("JENKINS") != null) {
            JENKINS = Boolean.valueOf(System.getenv("JENKINS"));
        }
        if (System.getenv("INGRESSPERDOMAIN") != null) {
            INGRESSPERDOMAIN = Boolean.valueOf(System.getenv("INGRESSPERDOMAIN"));
        }
    }

    /**
     * This method gets called only once before any of the test methods are
     * executed. It does the initialization of the integration test properties
     * defined in OperatorIT.properties and setting the resultRoot, pvRoot and
     * projectRoot attributes.
     *
     * @throws Exception
     */
    @BeforeClass
    public static void staticPrepare() throws Exception {
        // initialize test properties and create the directories
        initialize(APPPROPSFILE);
        Assume.assumeFalse(QUICKTEST);
        if (operator1 == null) {
            operator1 = TestUtils.createOperator(OPERATOR1FILE);
        }
        TESTSRCDIR = BaseTest.getProjectRoot() + "/integration-tests/src/test/java/oracle/kubernetes/operator/";
        TESTSCRIPTDIR = BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/";
        domain = createSitConfigDomain();
        Assert.assertNotNull(domain);
        ADMINPODNAME = domain.getDomainUid() + "-" + domain.getAdminServerName();
        TestUtils.copyFileViaCat(TESTSRCDIR + "SitConfigTests.java.template", "SitConfigTests.java", ADMINPODNAME, domain.getDomainNS());
        TestUtils.copyFileViaCat(TESTSCRIPTDIR + "sitconfig/scripts/runSitConfigTests.sh", "runSitConfigTests.sh", ADMINPODNAME, domain.getDomainNS());
        fqdn = TestUtils.getHostName();
    }

    /**
     * Releases k8s cluster lease, archives result, pv directories
     *
     * @throws Exception
     */
    @AfterClass
    public static void staticUnPrepare() throws Exception {
        logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
        logger.info("BEGIN");
        logger.info("Run once, release cluster lease");
        StringBuffer cmd = new StringBuffer("export RESULT_ROOT=$RESULT_ROOT && export PV_ROOT=$PV_ROOT && ");
        cmd.append(BaseTest.getProjectRoot()).append("/integration-tests/src/test/resources/statedump.sh");
        logger.log(Level.INFO, "Running {0}", cmd);
        ExecResult result = ExecCommand.exec(cmd.toString());
        if (result.exitValue() == 0) {
            logger.log(Level.INFO, "Executed statedump.sh {0}", result.stdout());
        } else {
            logger.log(Level.INFO, "Execution of statedump.sh failed, {0}\n{1}", new Object[]{result.stderr(), result.stdout()});
        }
        destroySitConfigDomain();
        if (JENKINS) {
            cleanup();
        }
        if (!"".equals(getLeaseId())) {
            logger.info("Release the k8s cluster lease");
            TestUtils.releaseLease(getProjectRoot(), getLeaseId());
        }
        logger.info("SUCCESS");
    }

    /**
     * This test covers custom situational configuration use cases for
     * config.xml and resources jdbc, jms and wldf. Create Operator and create
     * domain with listen address not set for admin server and t3 channel/NAP
     * and incorrect file for admin server log location. Introspector should
     * override these with sit-config automatically. Also, with some junk value
     * for t3 channel public address and using custom situational config
     * override replace with valid public address using secret. Verify the
     * domain is started successfully and web application can be deployed and
     * accessed. Verify that the JMS client can actually use the overridden
     * values. Use NFS storage on Jenkins
     *
     * @throws Exception
     */
    @Test
    public void testCustomSitConfigOverridesForDomain() throws Exception {
        boolean testCompletedSuccessfully = false;
        String testMethod = new Object() {
        }.getClass().getEnclosingMethod().getName();
        logTestBegin(testMethod);
        String stdout = callShellScriptByExecToPod("runSitConfigTests.sh", fqdn + " " + T3CHANNELPORT + " weblogic welcome1 " + testMethod, ADMINPODNAME, domain.getDomainNS());
        Assert.assertFalse(stdout.toLowerCase().contains("error"));
        testCompletedSuccessfully = true;
        logger.log(Level.INFO, "SUCCESS - {0}", testMethod);
    }

    @Test
    public void testCustomSitConfigOverridesForJdbc() throws Exception {
        boolean testCompletedSuccessfully = false;
        String testMethod = new Object() {
        }.getClass().getEnclosingMethod().getName();
        logTestBegin(testMethod);
        String stdout = callShellScriptByExecToPod("runSitConfigTests.sh", fqdn + " " + T3CHANNELPORT + " weblogic welcome1 " + testMethod, ADMINPODNAME, domain.getDomainNS());
        Assert.assertFalse(stdout.toLowerCase().contains("error"));
        testCompletedSuccessfully = true;
        logger.log(Level.INFO, "SUCCESS - {0}", testMethod);
    }

    @Test
    public void testCustomSitConfigOverridesForJms() throws Exception {
        boolean testCompletedSuccessfully = false;
        String testMethod = new Object() {
        }.getClass().getEnclosingMethod().getName();
        logTestBegin(testMethod);
        String stdout = callShellScriptByExecToPod("runSitConfigTests.sh", fqdn + " " + T3CHANNELPORT + " weblogic welcome1 " + testMethod, ADMINPODNAME, domain.getDomainNS());
        Assert.assertFalse(stdout.toLowerCase().contains("error"));
        testCompletedSuccessfully = true;
        logger.log(Level.INFO, "SUCCESS - {0}", testMethod);
    }

    @Test
    public void testCustomSitConfigOverridesForWldf() throws Exception {
        boolean testCompletedSuccessfully = false;
        String testMethod = new Object() {
        }.getClass().getEnclosingMethod().getName();
        logTestBegin(testMethod);
        String stdout = callShellScriptByExecToPod("runSitConfigTests.sh", fqdn + " " + T3CHANNELPORT + " weblogic welcome1 " + testMethod, ADMINPODNAME, domain.getDomainNS());
        Assert.assertFalse(stdout.toLowerCase().contains("error"));
        testCompletedSuccessfully = true;
        logger.log(Level.INFO, "SUCCESS - {0}", testMethod);
    }

    private static Domain createSitConfigDomain() throws Exception {
        String createDomainScript = TESTSCRIPTDIR + "/domain-home-on-pv/create-domain.py";
        // load input yaml to map and add configOverrides
        Map<String, Object> domainMap = TestUtils.loadYaml(DOMAINONPVWLSTFILE);
        domainMap.put("configOverrides", "sitconfigcm");
        domainMap.put(
                "configOverridesFile",
                "/integration-tests/src/test/resources/sitconfig/configoverrides");
        domainMap.put("domainUID", DOMAINUID);
        domainMap.put("adminNodePort", new Integer(ADMINPORT));
        domainMap.put("t3ChannelPort", new Integer(T3CHANNELPORT));

        // use NFS for this domain on Jenkins, defaultis HOST_PATH
        if (System.getenv("JENKINS") != null && System.getenv("JENKINS").equalsIgnoreCase("true")) {
            domainMap.put("weblogicDomainStorageType", "NFS");
        }
        // copy the custom domain create script file
        backuprestoreDomainCreateFile("backup");
        Files.copy(
                new File(TESTSCRIPTDIR + "/sitconfig/scripts/create-domain-auto-custom-sit-config20.py").toPath(),
                new File(createDomainScript).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        copyOverrideFiles();
        domain = TestUtils.createDomain(domainMap);
        domain.verifyDomainCreated();
        return domain;
    }

    private static void destroySitConfigDomain() throws Exception {
        backuprestoreDomainCreateFile("restore");
        if (domain != null) {
            domain.destroy();
        }
    }

    private static void backuprestoreDomainCreateFile(String op) throws IOException {
        String createDomainScriptDir = TESTSCRIPTDIR + "/domain-home-on-pv";
        String srcFileName;
        String dstFileName;
        switch (op) {
            case "backup":
                srcFileName = createDomainScriptDir + "/create-domain.py";
                dstFileName = createDomainScriptDir + "/create-domain.py.bak";
                break;
            case "restore":
                srcFileName = createDomainScriptDir + "/create-domain.py.bak";
                dstFileName = createDomainScriptDir + "/create-domain.py";
                break;
            default:
                return;
        }
        Files.copy(
                new File(srcFileName).toPath(),
                new File(dstFileName).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copyOverrideFiles() throws IOException {
        String files[] = {"config.xml", "version.txt", "diagnostics-WLDF-MODULE-0.xml", "jdbc-JdbcTestDataSource-0.xml", "jms-ClusterJmsSystemResource.xml"};
        String overrideDstDir = TESTSCRIPTDIR + "/domain-home-on-pv/customsitconfig/";
        for (String file : files) {
            Files.copy(
                    new File(TESTSCRIPTDIR + "/sitconfig/configoverrides/" + file).toPath(),
                    new File(overrideDstDir + file).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String callShellScriptByExecToPod(
            String scriptPath, String arguments, String podName, String namespace) throws Exception {

        StringBuffer cmdKubectlSh = new StringBuffer("kubectl -n ");
        cmdKubectlSh
                .append(namespace)
                .append(" exec -it ")
                .append(podName)
                .append(" -- bash -c 'sh ")
                .append(scriptPath)
                .append(" ")
                .append(arguments)
                .append("'");
        logger.info("Command to call kubectl sh file " + cmdKubectlSh);
        ExecResult result = ExecCommand.exec(cmdKubectlSh.toString());
        logger.log(Level.INFO, result.stdout().trim());
        if (result.exitValue() != 0) {
            throw new RuntimeException(
                    "FAILURE: command " + cmdKubectlSh + " failed, returned " + result.stderr());
        }
        logger.log(Level.INFO, result.stdout().trim());
        return result.stdout().trim();
    }
}
