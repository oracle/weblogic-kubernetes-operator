// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.utils.*;
import org.junit.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

public class ItManagedCoherence extends BaseTest {

    private static final String COHERENCE_CLUSTER_SCRIPT = "create-domain-coherence-cluster.py";
    private static final String COHERENCE_CLUSTER_IN_IMAGE_SCRIPT = "create-domain-in-image-coherence-cluster.py";
    private static final String DOMAINUID = "cmdomonpv";
    private static final String DOMAINUID1 = "cmdominimage";
    private static String customDomainTemplate;
    private static final String testAppName = "coherenceapp";
    private static final String appToDeploy = "CoherenceApp";
    private static final String scriptName = "buildDeployCoherenceAppInPod.sh";

    private static Operator operator1;
    Domain domain = null;

    /**
     * This method gets called only once before any of the test methods are executed. It does the
     * initialization of the integration test properties defined in OperatorIT.properties and setting
     * the resultRoot, pvRoot and projectRoot attributes.
     *
     * @throws Exception exception
     */
    @BeforeClass
    public static void staticPrepare() throws Exception {
        if (!QUICKTEST) {
            // initialize test properties and create the directories
            initialize(APP_PROPS_FILE);
            String template =
                    BaseTest.getProjectRoot() + "/kubernetes/samples/scripts/common/domain-template.yaml";
            String add =
                    "  - clusterName: dataCluster\n"
                            + "    serverStartState: \"RUNNING\"\n"
                            + "    replicas: %INITIAL_MANAGED_SERVER_REPLICAS%\n";
            customDomainTemplate = BaseTest.getResultDir() + "/customDomainTemplate.yaml";

            Files.copy(
                    Paths.get(template),
                    Paths.get(customDomainTemplate),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.write(Paths.get(customDomainTemplate), add.getBytes(), StandardOpenOption.APPEND);

        }
    }

    /**
     * Releases k8s cluster lease, archives result, pv directories.
     *
     * @throws Exception exception
     */
    @AfterClass
    public static void staticUnPrepare() throws Exception {
        logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
        logger.info("BEGIN");
        logger.info("Run once, release cluster lease");

        tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());

        logger.info("SUCCESS");
    }

    /**
     * Create operator and verify its deployed successfully. Create domain with 2 Managed coherence
     * clusters verify domain is started. Deploy an application to the cluster with no storage enabled
     * and the GAR file to the cluster with storage enabled. Verify that data can be added and stored
     * in the cache and can also be retrieved from cache.
     *
     * @throws Exception exception
     */
    @Test
    public void testCreateCoherenceDomainOnPvUsingWlst() throws Exception {
        Assume.assumeFalse(QUICKTEST);
        String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();

        logTestBegin(testMethodName);
        logger.info("Creating coeherence domain on pv using wlst and testing the cache");

        boolean testCompletedSuccessfully = false;
        domain = null;

        // create operator1
        if (operator1 == null) {
            operator1 = TestUtils.createOperator(OPERATOR1_YAML);
        }

        try {
            Map<String, Object> domainMap = TestUtils.loadYaml(DOMAINONPV_WLST_YAML);
            domainMap.put("domainUID", DOMAINUID);
            domainMap.put("clusterType", "DYNAMIC");
            domainMap.put("clusterName", "appCluster");
            domainMap.put("initialManagedServerReplicas", new Integer("2"));
            domainMap.put("customDomainTemplate", customDomainTemplate);
            domainMap.put(
                    "createDomainPyScript",
                    "integration-tests/src/test/resources/domain-home-on-pv/"
                            + COHERENCE_CLUSTER_SCRIPT);
            if ((System.getenv("LB_TYPE") != null && System.getenv("LB_TYPE").equalsIgnoreCase("VOYAGER"))
                    || (domainMap.containsKey("loadBalancer")
                    && ((String) domainMap.get("loadBalancer")).equalsIgnoreCase("VOYAGER"))) {
                domainMap.put("voyagerWebPort", new Integer("30366"));
            }
            domain = TestUtils.createDomain(domainMap);
            domain.verifyDomainCreated();
            String[] pods = {
                    DOMAINUID + "-" + domain.getAdminServerName(),
                    DOMAINUID + "-managed-server",
                    DOMAINUID + "-managed-server1",
                    DOMAINUID + "-managed-server2",
                    DOMAINUID + "-new-managed-server1",
                    DOMAINUID + "-new-managed-server2",
            };
            verifyServersStatus(domain, pods, DOMAINUID);

            // Build WAR in the admin pod and deploy it from the admin pod to a weblogic target
            TestUtils.buildDeployCoherenceAppInPod(domain,
                    testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword(),
                    appToDeploy, "dataCluster");

            // Wait some time for deployment to get into Active state
            Thread.sleep(60 * 1000);

            coherenceCacheTest();

            testCompletedSuccessfully = true;
        } finally {
            if (domain != null && !SMOKETEST && (JENKINS || testCompletedSuccessfully)) {
              domain.destroy();
            }
        }
        logger.info("SUCCESS - " + testMethodName);
    }

    /**
     * Create operator and verify its deployed successfully. Create domain with 2 Managed coherence
     * clusters verify domain is started. Deploy an application to the cluster with no storage enabled
     * and the GAR file to the cluster with storage enabled. Verify that data can be added and stored
     * in the cache and can also be retrieved from cache.
     *
     * @throws Exception exception
     */
    @Test
    public void testCreateCoherenceDomainInImageUsingWlst() throws Exception {
        Assume.assumeFalse(QUICKTEST);
        String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();

        logTestBegin(testMethodName);
        logger.info("Creating coeherence domain in image using wlst and testing the cache");

        boolean testCompletedSuccessfully = false;
        domain = null;

        // create operator1
        if (operator1 == null) {
            operator1 = TestUtils.createOperator(OPERATOR1_YAML);
        }

        try {
            Map<String, Object> domainMap = TestUtils.loadYaml(DOMAININIMAGE_WLST_YAML);
            domainMap.put("domainUID", DOMAINUID1);
            domainMap.put("clusterType", "DYNAMIC");
            domainMap.put("clusterName", "appCluster");
            domainMap.put("initialManagedServerReplicas", new Integer("2"));
            domainMap.put("customDomainTemplate", customDomainTemplate);
            domainMap.put(
                    "createDomainPyScript",
                    "integration-tests/src/test/resources/"
                            + COHERENCE_CLUSTER_IN_IMAGE_SCRIPT);
            if ((System.getenv("LB_TYPE") != null && System.getenv("LB_TYPE").equalsIgnoreCase("VOYAGER"))
                    || (domainMap.containsKey("loadBalancer")
                    && ((String) domainMap.get("loadBalancer")).equalsIgnoreCase("VOYAGER"))) {
                domainMap.put("voyagerWebPort", new Integer("30366"));
            }
            domain = TestUtils.createDomain(domainMap);
            domain.verifyDomainCreated();
            String[] pods = {
                    DOMAINUID1 + "-" + domain.getAdminServerName(),
                    DOMAINUID1 + "-managed-server",
                    DOMAINUID1 + "-managed-server1",
                    DOMAINUID1 + "-managed-server2",
                    DOMAINUID1 + "-new-managed-server1",
                    DOMAINUID1 + "-new-managed-server2",
            };
            verifyServersStatus(domain, pods, DOMAINUID1);

            // Build WAR in the admin pod and deploy it from the admin pod to a weblogic target
            TestUtils.buildDeployCoherenceAppInPod(domain,
                    testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword(),
                    appToDeploy, "dataCluster");

            // Wait some time for deployment to get into Active state
            Thread.sleep(60 * 1000);

            coherenceCacheTest();

            testCompletedSuccessfully = true;
        } finally {
            if (domain != null && !SMOKETEST && (JENKINS || testCompletedSuccessfully)) {
              domain.destroy();
            }
        }
        logger.info("SUCCESS - " + testMethodName);
    }
    /**
     * Verifies all of the servers in the cluster are in Running status.
     *
     * @param domain Domain
     * @param pods   array pod names to check the status for
     */
    private static void verifyServersStatus(Domain domain, String[] pods, String domainUID) {
        K8sTestUtils testUtil = new K8sTestUtils();
        String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", domainUID);
        String namespace = domain.getDomainNs();
        for (String pod : pods) {
            assertTrue(
                    pod + " Pod not running", testUtil.isPodRunning(namespace, domain1LabelSelector, pod));
        }
    }

    private void coherenceCacheTest() throws Exception {
        //Assume.assumeFalse(QUICKTEST);
        //String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
        //logTestBegin(testMethodName);

        String[] firstNameList = {"Frodo", "Samwise", "Bilbo", "peregrin", "Meriadoc", "Gandalf"};
        String[] secondNameList = {"Baggins", "Gamgee", "Baggins", "Took", "Brandybuck", "TheGrey"};
        ExecResult result;

        for (int i = 0; i < firstNameList.length; i++) {
           result = addDataToCache(firstNameList[i], secondNameList[i]);
           logger.info("addDataToCache returned" + result.stdout());
        }
        //check if cache size is 6
        result = getCacheSize();
        logger.info("number of records in cache = " + result.stdout());
        if (!(result.stdout().equals("6"))) {
            logger.info("number of records in cache = " + result.stdout());
            assertFalse("Expected 6 records", "6".equals(result.stdout()));
        }
        //get the data from cache
        result = getCacheContents();
        logger.info("Cache contains the following entries \n" + result.stdout());
  
        //Now clear the cache
        result = clearCache();
        logger.info("Cache is cleared and should be empty" + result.stdout());
  
        //logger.info("SUCCESS - " + testMethodName);
    }

    private ExecResult addDataToCache(String firstName, String secondName) throws Exception {
        logger.info("Add initial data to cache");

        StringBuffer curlCmd = new StringBuffer("curl --silent ");
        curlCmd.append("-d 'action=add&first=")
                .append(firstName)
                .append("&second=")
                .append(secondName)
                .append("' ")
                .append("-X POST -H 'host: ")
                .append(domain.getDomainUid())
                .append(".org' ")
                .append("http://")
                .append(domain.getHostNameForCurl())
                .append(":")
                .append(domain.getLoadBalancerWebPort())
                .append("/")
                .append(appToDeploy)
                .append("/")
                .append(appToDeploy);
        logger.info("curlCmd is " + curlCmd.toString());
        return TestUtils.exec(curlCmd.toString());
    }

    private ExecResult getCacheSize() throws Exception {
        logger.info("get the number of records in cache");

        StringBuffer curlCmd = new StringBuffer("curl --silent ");
        curlCmd.append("-d 'action=size' ")
                .append("-H 'host: ")
                .append(domain.getDomainUid())
                .append(".org' ")
                .append("http://")
                .append(domain.getHostNameForCurl())
                .append(":")
                .append(domain.getLoadBalancerWebPort())
                .append("/")
                .append(appToDeploy)
                .append("/")
                .append(appToDeploy);
        return TestUtils.exec(curlCmd.toString());
    }

    private ExecResult getCacheContents() throws Exception {
        logger.info("get the records from cache");

        StringBuffer curlCmd = new StringBuffer("curl --silent ");
        curlCmd.append("-d 'action=get' ")
                .append("-H 'host: ")
                .append(domain.getDomainUid())
                .append(".org' ")
                .append("http://")
                .append(domain.getHostNameForCurl())
                .append(":")
                .append(domain.getLoadBalancerWebPort())
                .append("/")
                .append(appToDeploy)
                .append("/")
                .append(appToDeploy);
        return TestUtils.exec(curlCmd.toString());
    }

    private ExecResult clearCache() throws Exception {
        logger.info("clear the cache");

        StringBuffer curlCmd = new StringBuffer("curl --silent ");
        curlCmd.append("-d 'action=clear' ")
                .append("-H 'host: ")
                .append(domain.getDomainUid())
                .append(".org' ")
                .append("http://")
                .append(domain.getHostNameForCurl())
                .append(":")
                .append(domain.getLoadBalancerWebPort())
                .append("/")
                .append(appToDeploy)
                .append("/")
                .append(appToDeploy);
        return TestUtils.exec(curlCmd.toString());
    }

}
