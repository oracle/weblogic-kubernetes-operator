// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for testing operator working with Elastic Stack
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ItElasticLogging extends BaseTest {
  private static final String logstashIndexKey = "logstash";
  private static final String kibanaIndexKey = "kibana";
  private static final String wlsIndexKey = "wls";
  private static final String elasticStackYamlLoc =
      "kubernetes/samples/scripts/elasticsearch-and-kibana/elasticsearch_and_kibana.yaml";
  private final String loggingJarRepos = 
      "https://github.com/oracle/weblogic-logging-exporter/releases/download/v0.1.1";
  private final String wlsLoggingExpJar = "weblogic-logging-exporter-0.1.1.jar";
  private final String snakeyamlJarRepos = 
      "https://repo1.maven.org/maven2/org/yaml/snakeyaml/1.23";
  private final String snakeyamlJar = "snakeyaml-1.23.jar";
  private static Operator operator;
  private static Domain domain;
  private static String k8sExecCmdPrefix;
  private static String elasticSearchURL;
  private static Map<String, Object> testVarMap;
  private static String loggingExpArchiveLoc;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. It installs Elastic Stack, verifies Elastic
   * Stack is ready to use, creates an operator and a Weblogic domain
   *
   * @throws Exception exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (!QUICKTEST) {
      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE);

      // Install Elastic Stack
      StringBuffer cmd =
          new StringBuffer("kubectl apply -f ")
              .append(getProjectRoot())
              .append("/")
              .append(elasticStackYamlLoc);
      logger.info("Command to Install Elastic Stack: " + cmd.toString());
      TestUtils.exec(cmd.toString());

      // Create operator-elk
      if (operator == null) {
        logger.info("Creating Operator & waiting for the script to complete execution");
        operator = TestUtils.createOperator(OPERATOR1_ELK_YAML, "2/2");
      }

      // create domain
      if (domain == null) {
        logger.info("Creating WLS Domain & waiting for the script to complete execution");
        domain = TestUtils.createDomain(DOMAINONPV_LOGGINGEXPORTER_YAML);
        domain.verifyDomainCreated();
      }

      // Get Elasticsearch host and port from yaml file and build Elasticsearch URL
      testVarMap = TestUtils.loadYaml(OPERATOR1_ELK_YAML);
      String operatorPodName = operator.getOperatorPodName();
      StringBuffer elasticSearchUrlBuff =
          new StringBuffer("http://")
              .append(testVarMap.get("elasticSearchHost"))
              .append(":")
              .append(testVarMap.get("elasticSearchPort"));
      elasticSearchURL = elasticSearchUrlBuff.toString();
      Assume.assumeFalse(
          "Got null when building Elasticsearch URL", elasticSearchURL.contains("null"));

      // Create the prefix of k8s exec command
      StringBuffer k8sExecCmdPrefixBuff =
          new StringBuffer("kubectl exec -it ")
              .append(operatorPodName)
              .append(" -n ")
              .append(operator.getOperatorNamespace())
              .append(" -- /bin/bash -c ")
              .append("'curl ")
              .append(elasticSearchURL);
      k8sExecCmdPrefix = k8sExecCmdPrefixBuff.toString();

      // Verify that Elastic Stack is ready to use
      verifyLoggingExpReady(logstashIndexKey);
      verifyLoggingExpReady(kibanaIndexKey);
      
      // Create a dir to hold required Weblogic logging exporter archive files
      loggingExpArchiveLoc = BaseTest.getResultDir() + "/loggingExpArchDir";
      Files.createDirectories(Paths.get(loggingExpArchiveLoc));
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories and uninstall Elastic Stack.
   *
   * @throws Exception exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (!QUICKTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");

      // Uninstall Elastic Stack
      StringBuffer cmd =
          new StringBuffer("kubectl delete -f ")
              .append(getProjectRoot())
              .append("/")
              .append(elasticStackYamlLoc);
      logger.info("Command to uninstall Elastic Stack: " + cmd.toString());
      TestUtils.exec(cmd.toString());

      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());

      logger.info("SUCCESS");
    }
  }

  /**
   * Use Elasticsearch Count API to query logs of level=INFO. Verify that total number of logs for
   * level=INFO is not zero and failed count is zero
   *
   * @throws Exception exception
   */
  @Test
  public void testLogLevelSearch() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // Verify that number of logs is not zero and failed count is zero
    String regex = ".*count\":(\\d+),.*failed\":(\\d+)";
    String queryCriteria = "/_count?q=level:INFO";
    verifySearchResults(queryCriteria, regex, logstashIndexKey,true);
    
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Use Elasticsearch Search APIs to query Operator log info. Verify that log hits for
   * type=weblogic-operator are not empty
   *
   * @throws Exception exception
   */
  @Test
  public void testOperatorLogSearch() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // Verify that log hits for Operator are not empty
    String regex = ".*took\":(\\d+),.*hits\":\\{(.+)\\}";
    String queryCriteria = "/_search?q=type:weblogic-operator";
    verifySearchResults(queryCriteria, regex, logstashIndexKey, false);
    
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Use Elasticsearch Search APIs to query Weblogic log info. Verify that log hits for Weblogic
   * servers are not empty
   *
   * @throws Exception exception
   */
  @Test
  public void testWeblogicLogSearch() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    final Map<String, Object> domainMap = domain.getDomainMap();
    final String domainUid = domain.getDomainUid();
    final String adminServerName = (String) domainMap.get("adminServerName");
    final String adminServerPodName = domainUid + "-" + adminServerName;
    final String managedServerNameBase = domainMap.get("managedServerNameBase").toString();
    final String managedServerPodName = domainUid + "-" + managedServerNameBase + "1";

    // Verify that log hits for admin server are not empty
    String regex = ".*took\":(\\d+),.*hits\":\\{(.+)\\}";
    String queryCriteria = "/_search?q=log:" + adminServerPodName + " | grep RUNNING";
    verifySearchResults(queryCriteria, regex, logstashIndexKey, false);
    
    // Verify that log hits for managed server are not empty
    queryCriteria = "/_search?q=log:" + managedServerPodName + " | grep RUNNING";
    verifySearchResults(queryCriteria, regex, logstashIndexKey, false);

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Install Weblogic logging exporter in all Weblogic server pods to collect Weblogic logs. 
   * Use Elasticsearch Search APIs to query Weblogic log info pushed to Elasticsearch repository 
   * by Weblogic logging exporter . Verify that log hits for Weblogic servers are not empty
   *
   * @throws Exception exception
   */
  @Test
  public void testWlsLoggingExporter() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // Download Weblogic logging exporter 
    downloadWlsLoggingExporterJars();
    // Copy required resources to all wls server pods
    copyResourceFilesToAllPods();

    // Rrestart Weblogic domain
    domain.shutdownUsingServerStartPolicy();
    domain.restartUsingServerStartPolicy();
    
    // Verify that Weblogic logging exporter installed successfully
    verifyLoggingExpReady(wlsIndexKey);
    
    // Verify that hits of log level = Notice are not empty
    String regex = ".*took\":(\\d+),.*hits\":\\{(.+)\\}";
    String queryCriteria = "/_search?q=level:Notice";
    verifySearchResults(queryCriteria, regex, wlsIndexKey, false);
    // Verify that hits of loggerName = WebLogicServer are not empty
    queryCriteria = "/_search?q=loggerName:WebLogicServer";
    verifySearchResults(queryCriteria, regex, wlsIndexKey, false);
    // Verify that hits of _type:doc are not empty
    queryCriteria = "/_search?q=_type:doc";
    verifySearchResults(queryCriteria, regex, wlsIndexKey, false);
 
    logger.info("SUCCESS - " + testMethodName);
  }
  
  private static void verifyLoggingExpReady(String index) throws Exception {
    // Get index status info
    String healthStatus = execLoggingExpStatusCheck("*" + index + "*", "$1");
    String indexStatus = execLoggingExpStatusCheck("*" + index + "*", "$2");
    String indexName = execLoggingExpStatusCheck("*" + index + "*", "$3");
    
    Assume.assumeNotNull(healthStatus);
    Assume.assumeNotNull(indexStatus);
    Assume.assumeNotNull(indexName);
    
    if (!index.equalsIgnoreCase(kibanaIndexKey)) {
      // Add the logstash and wls index name to a Map
      testVarMap.put(index, indexName);
    }

    //There are multiple indexes from Kibana 6.8.0
    String[] healthStatusArr = 
      healthStatus.split(System.getProperty("line.separator"));
    String[] indexStatusArr = 
      indexStatus.split(System.getProperty("line.separator"));
    String[] indexNameArr = 
      indexName.split(System.getProperty("line.separator"));
    
    for (int i = 0; i < indexStatusArr.length; i++) {
      logger.info("Health status of " + indexNameArr[i] + " is: " + healthStatusArr[i]);
      logger.info("Index status of " + indexNameArr[i] + " is: " + indexStatusArr[i]);
      // Verify that the health status of index
      Assume.assumeTrue(
          index + " is not ready!",
          healthStatusArr[i].trim().equalsIgnoreCase("yellow")
              || healthStatusArr[i].trim().equalsIgnoreCase("green"));
      // Verify that the index is open for use
      Assume.assumeTrue(index + " index is not open!", 
                        indexStatusArr[i].trim().equalsIgnoreCase("open"));
    }
    
    logger.info("ELK Stack is up and running and ready to use!");
  }

  private static String execLoggingExpStatusCheck(String indexName, String varLoc)
      throws Exception {
    ExecResult result = null;
    StringBuffer k8sExecCmdPrefixBuff = new StringBuffer(k8sExecCmdPrefix);
    String cmd =
        k8sExecCmdPrefixBuff
            .append("/_cat/indices/")
            .append(indexName)
            .append(" | awk '\\''{ print ")
            .append(varLoc)
            .append(" }'\\'")
            .toString();
    logger.info("Command to exec Elastic Stack status check: " + cmd);
    
    int i = 0;
    while (i < BaseTest.getMaxIterationsPod()) {
      result = TestUtils.exec(cmd);
      logger.info("Result: " + result.stdout());
      if (null != result.stdout()) {
        break;
      }
      
      logger.info(
          "ELK Stack is not ready Ite ["
              + i
              + "/"
              + BaseTest.getMaxIterationsPod()
              + "], sleeping "
              + BaseTest.getWaitTimePod()
              + " seconds more");
      Thread.sleep(BaseTest.getWaitTimePod() * 1000);
      i++;
    }
        
    return result.stdout();
  }
  
  private void verifySearchResults(String queryCriteria, String regex, 
                                   String index, boolean checkCount) throws Exception {
    int count = -1;
    int failedCount = -1;
    String hits = "";
    String results = null;
    int i = 0;
    while (i < BaseTest.getMaxIterationsPod()) {
      results = execSearchQuery(queryCriteria, index);
      Pattern pattern = Pattern.compile(regex);
      Matcher matcher = pattern.matcher(results);
      if (matcher.find()) {
        count = Integer.parseInt(matcher.group(1));
        if (checkCount) {
          failedCount = Integer.parseInt(matcher.group(2));
        } else {
          hits = matcher.group(2);
        }
        
        break;
      }
      
      logger.info(
          "Logs are not pushed to ELK Stack Ite ["
              + i
              + "/"
              + BaseTest.getMaxIterationsPod()
              + "], sleeping "
              + BaseTest.getWaitTimePod()
              + " seconds more");
      Thread.sleep(BaseTest.getWaitTimePod() * 1000);
      i++;
    }

    Assume.assumeTrue("Total count of logs should be more than 0!", count > 0);
    logger.info("Total count of logs: " + count);
    if (checkCount) {
      Assume.assumeTrue("Total failed count should be 0!", failedCount == 0);
      logger.info("Total failed count: " + failedCount);
    } else {
      Assume.assumeFalse("Total hits of search is empty!", hits.isEmpty());
    }
  }

  private String execSearchQuery(String queryCriteria, String index) throws Exception {
    StringBuffer k8sExecCmdPrefixBuff = new StringBuffer(k8sExecCmdPrefix);
    int offset = k8sExecCmdPrefixBuff.indexOf("http");
    k8sExecCmdPrefixBuff.insert(offset, " -X GET ");
    String indexName = (String) testVarMap.get(index);
    String cmd =
        k8sExecCmdPrefixBuff
            .append("/")
            .append(indexName)
            .append(queryCriteria)
            .append("'")
            .toString();
    logger.info("Command to search: " + cmd);
    ExecResult result = TestUtils.exec(cmd);

    return result.stdout();
  }
  
  private void downloadWlsLoggingExporterJars() throws Exception {
    File loggingJatReposDir = new File(loggingExpArchiveLoc);

    if (loggingJatReposDir.list().length == 0) {
      StringBuffer getJars = new StringBuffer();
      getJars
          .append(" wget -P ")
          .append(loggingExpArchiveLoc)
          .append(" ")
          .append(loggingJarRepos)
          .append("/")
          .append(wlsLoggingExpJar)
          .append(" ; ")
          .append("wget -P ")
          .append(loggingExpArchiveLoc)
          .append(" ")
          .append(snakeyamlJarRepos)
          .append("/")
          .append(snakeyamlJar);
      logger.info("Executing cmd " + getJars.toString());
      ExecResult result = TestUtils.exec(getJars.toString());
      logger.info("Result: " + result.stdout());
    }
    
    int i = 0;
    File wlsLoggingExpFile = new File(loggingExpArchiveLoc + "/" + wlsLoggingExpJar);
    File snakeyamlFile = new File(loggingExpArchiveLoc + "/" + snakeyamlJar);
    
    // Make sure downloading completed
    while (i < BaseTest.getMaxIterationsPod()) {
      if(wlsLoggingExpFile.exists() && snakeyamlFile.exists()) {
        break;
      }
      
      logger.info(
          "Downloading wls logging exporter jar files not done ["
              + i
              + "/"
              + BaseTest.getMaxIterationsPod()
              + "], sleeping "
              + BaseTest.getWaitTimePod()
              + " seconds more");
      Thread.sleep(BaseTest.getWaitTimePod() * 1000);
      i++;
    }
    
    Assume.assumeTrue("Failed to download <" + wlsLoggingExpFile + ">", wlsLoggingExpFile.exists());
    Assume.assumeTrue("Failed to download <" + snakeyamlFile + ">", snakeyamlFile.exists());
    File[] jarFiles = loggingJatReposDir.listFiles();
    for (File jarFile : jarFiles) {
      logger.info("Downloaded jar file : " + jarFile.getName());
    }
  }
  
  private void copyResourceFilesToAllPods() throws Exception  {
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainUid = domain.getDomainUid();
    String domainNS = domainMap.get("namespace").toString();
    String adminServerName = (String) domainMap.get("adminServerName");
    String adminServerPodName = domainUid + "-" + adminServerName;
    String managedServerNameBase = domainMap.get("managedServerNameBase").toString();
    String managedServerPodNameBase = domainUid + "-" + managedServerNameBase;
    int initialManagedServerReplicas =
        ((Integer) domainMap.get("initialManagedServerReplicas")).intValue();

    //Copy test files to admin pod
    logger.info(
        "Copying the resources to admin pod("
            + domainUid
            + "-"
            + adminServerPodName
            + ")");
    copyResourceFilesToOnePod(adminServerPodName, domainNS);

    //Copy test files to all managed server pods
    for (int i = 1; i <= initialManagedServerReplicas; i++) {
      logger.info(
          "Copying the resources to managed pod("
              + domainUid
              + "-"
              + managedServerNameBase
              + i
              + ")");
      copyResourceFilesToOnePod(managedServerPodNameBase + i, domainNS);
    }
  }
  
  private void copyResourceFilesToOnePod(String serverName, String domainNS) 
      throws Exception {
    String resourceDir = BaseTest.getProjectRoot() + "/integration-tests/src/test/resources";
    String testResourceDir = resourceDir + "/loggingexporter";
    final String loggingYamlFile = "WebLogicLoggingExporter.yaml";
    
    //Copy test files to Weblogic server pod
    TestUtils.kubectlcp(
        loggingExpArchiveLoc + "/" + wlsLoggingExpJar,
        "/shared/domains/domainonpvwlst/lib/" + wlsLoggingExpJar,
        serverName,
        domainNS);

    TestUtils.kubectlcp(
        loggingExpArchiveLoc + "/" + snakeyamlJar,
        "/shared/domains/domainonpvwlst/lib/" + snakeyamlJar,
        serverName,
        domainNS);

    TestUtils.kubectlcp(
        testResourceDir + "/" + loggingYamlFile,
        "/shared/domains/domainonpvwlst/config/" + loggingYamlFile,
        serverName,
        domainNS);
  }
}
