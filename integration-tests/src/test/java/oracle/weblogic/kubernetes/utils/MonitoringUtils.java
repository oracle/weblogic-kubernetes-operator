// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.Callable;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MONITORING_EXPORTER_WEBAPP_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkFile;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A utility class for Monitoring Weblogic Domain via Weblogic MonitoringExporter and Prometheus.
 */
public class MonitoringUtils {
  /**
   * Download monitoring exporter webapp wls-exporter.war based on provided version
   * and insert provided configuration.
   * @param configFile configuration to monitor Weblogic Domain
   * @param applicationDir location where application war file will be created
   */
  public static void downloadMonitoringExporterApp(String configFile, String applicationDir) {
    LoggingFacade logger = getLogger();
    //version of wls-exporter.war published in https://github.com/oracle/weblogic-monitoring-exporter/releases/
    String monitoringExporterWebAppVersion = Optional.ofNullable(System.getenv("MONITORING_EXPORTER_WEBAPP_VERSION"))
        .orElse(MONITORING_EXPORTER_WEBAPP_VERSION);

    String monitoringExporterBuildFile = String.format(
        "%s/get%s.sh", applicationDir, monitoringExporterWebAppVersion);
    checkDirectory(applicationDir);
    logger.info("Download a monitoring exporter build file {0} ", monitoringExporterBuildFile);
    String monitoringExporterRelease =
        monitoringExporterWebAppVersion.equals("2.0") ? "2.0.0" : monitoringExporterWebAppVersion;
    String curlDownloadCmd = String.format("cd %s && "
            + "curl -O -L -k https://github.com/oracle/weblogic-monitoring-exporter/releases/download/v%s/get%s.sh",
        applicationDir,
        monitoringExporterRelease,
        monitoringExporterWebAppVersion);
    logger.info("execute command  a monitoring exporter curl command {0} ", curlDownloadCmd);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(curlDownloadCmd))
        .execute(), "Failed to download monitoring exporter webapp");
    String command = String.format("chmod 777 %s ", monitoringExporterBuildFile);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(command))
        .execute(), "Failed to download monitoring exporter webapp");

    command = String.format("cd %s && %s  %s",
        applicationDir,
        monitoringExporterBuildFile,
        configFile);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(command))
        .execute(), "Failed to build monitoring exporter webapp");
    assertDoesNotThrow(() -> checkFile(applicationDir + "/wls-exporter.war"),
        "Monitoring Exporter web application file was not found");
  }

  /**
   * Build monitoring exporter web applicaiont wls-exporter.war with provided configuration
   * @param monitoringExporterSrcDir directory containing github monitoring exporter
   * @param configFile configuration file for weblogic domain monitoring
   * @param appDir directory where war file will be created
   */
  public static void buildMonitoringExporterApp(String monitoringExporterSrcDir, String configFile, String appDir) {

    String command = String.format("cd %s && mvn clean install -Dmaven.test.skip=true -Dconfiguration=%s",
        monitoringExporterSrcDir,
        RESOURCE_DIR,
        configFile);
    LoggingFacade logger = getLogger();
    logger.info("Executing command " + command);
    Path srcFile = Paths.get(monitoringExporterSrcDir,
        "target", "wls-exporter.war");

    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(command))
        .execute(), "Failed to build monitoring exporter webapp");

    Path targetFile = Paths.get(appDir, "wls-exporter.war");
    assertDoesNotThrow(() ->
        Files.copy(srcFile, targetFile, StandardCopyOption.REPLACE_EXISTING));
  }

  /**
   * Check metrics using Prometheus.
   *
   * @param searchKey   - metric query expression
   * @param expectedVal - expected metrics to search
   * @param prometheusPort prometheusPort
   * @throws Exception if command to check metrics fails
   */
  public static void checkMetricsViaPrometheus(String searchKey, String expectedVal, int prometheusPort)
      throws Exception {

    LoggingFacade logger = getLogger();
    // url
    String curlCmd =
        String.format("curl --silent --show-error --noproxy '*'  http://%s:%s/api/v1/query?query=%s",
            K8S_NODEPORT_HOST, prometheusPort, searchKey);

    logger.info("Executing Curl cmd {0}", curlCmd);
    logger.info("Checking searchKey: {0}", searchKey);
    logger.info(" expected Value {0} ", expectedVal);
    ConditionFactory withStandardRetryPolicy = null;
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Check prometheus metric {0} against expected {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                searchKey,
                expectedVal,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(searchForKey(curlCmd, expectedVal));
  }

  /**
   * Check output of the command against expected output.
   *
   * @param cmd command
   * @param searchKey expected response from the command
   * @return true if the command succeeds
   */
  public static boolean execCommandCheckResponse(String cmd, String searchKey) {
    CommandParams params = Command
        .defaultCommandParams()
        .command(cmd)
        .saveResults(true)
        .redirect(false)
        .verbose(true);
    return Command.withParams(params).executeAndVerify(searchKey);
  }

  /**
   * Check if executed command contains expected output.
   *
   * @param cmd   command to execute
   * @param searchKey expected output
   * @return true if the output matches searchKey otherwise false
   */
  public static Callable<Boolean> searchForKey(String cmd, String searchKey) {
    return () -> execCommandCheckResponse(cmd, searchKey);
  }
}
