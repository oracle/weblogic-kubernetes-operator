// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_BASE;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Slammer {

  private static String slammerPropertyFile = Optional.ofNullable(System.getenv("SLAMMER_PROPERTY_FILE"))
      .orElse(null);

  private static String remotehost = Optional.ofNullable(System.getenv("SLAMMER_REMOTEHOST"))
      .orElse(K8S_NODEPORT_HOST);

  private static String remotepass = Optional.ofNullable(System.getenv("SLAMMER_REMOTEHOST_PASS"))
      .orElse(null);
  private static String remoteuser = Optional.ofNullable(System.getenv("SLAMMER_REMOTEHOST_USER"))
      .orElse(null);

  private static String slammerInstallDir = Optional.ofNullable(System.getenv("SLAMMER_INSTALL_DIR"))
      .orElse(RESULTS_BASE + "/slammerinstall");

  public static String getSlammerDir() {
    return slammerInstallDir + "/slammer";
  }

  /**
   * install slammer src using specific location.
   */
  public static void installSlammer() {
    installSlammer(slammerInstallDir);
  }

  /**
   * install slammer src using specific location.
   * @param installDir location for installation
   */
  public static void installSlammer(String installDir) {
    LoggingFacade logger = getLogger();
    logger.info("create a staging location for slammer project");
    Path slammerTemp = Paths.get(installDir);
    assertDoesNotThrow(() -> deleteDirectory(slammerTemp.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(slammerTemp));

    String slammerSrcLocation = Optional.ofNullable(System.getenv("SLAMMER_DOWNLOAD_URL"))
        .orElse(null);

    CommandParams params = Command.defaultCommandParams()
        .command("cd " + installDir + " && wget  --no-proxy "
            + slammerSrcLocation
            + " && tar xf slammer.tar")
        .saveResults(true)
        .redirect(false);
    assertTrue(() -> Command.withParams(params)
        .execute());
  }

  /**
   * Run a Slammer.
   * @param slammerParams the parameters to run slammer
   * @return true on success, false otherwise
   */
  public static boolean run(SlammerParams slammerParams) {
    String slammerDir = getSlammerDir();
    String operation = slammerParams.getOperation();
    String service = slammerParams.getService();
    // assertions for required parameters
    assertThat(slammerDir)
        .as("make sure slammerDir is not empty or null")
        .isNotNull()
        .isNotEmpty();
    assertTrue(slammerPropertyFile != null || (remoteuser != null && remotepass != null),
        "make sure slammer property or remote user/pass is provided ");

    assertThat(operation)
        .as("make sure operation is not empty or null")
        .isNotNull()
        .isNotEmpty();

    assertThat(service)
        .as("make sure service is not empty or null")
        .isNotNull()
        .isNotEmpty();


    // build Slammer run command
    String runCmd = String.format("cd %1s && %2s/slammer.pl  --service %3s --operation %4s",
        slammerDir, slammerDir, service, operation);

    // if we have timeout
    String timeout = slammerParams.getTimeout();
    if (timeout != null) {
      runCmd = runCmd + " --timeout " + timeout;
    }

    // if we have property file
    if (slammerPropertyFile != null) {
      runCmd = runCmd + " --property " + slammerPropertyFile;
    } else {
      // if we have remotehost and remotepass
      runCmd = runCmd + " --remotehost " + remotehost
        + " --remotepass " + remotepass
        + " --remoteuser " + remoteuser;
    }

    // if we have delay
    String delay = slammerParams.getDelay();
    if (delay != null) {
      runCmd = runCmd + " --delay " + delay;
    }

    // if we have port
    String port = slammerParams.getPort();
    if (port != null) {
      runCmd = runCmd + " --port " + port;
    }

    // if we have traffic direction
    String trafficDirection = slammerParams.getTraffic();
    if (trafficDirection != null) {
      runCmd = runCmd + " --traffic " + trafficDirection;
    }

    // if we have cpu number
    String cpu = slammerParams.getCpu();
    if (cpu != null) {
      runCmd = runCmd + " --cpu " + cpu;
    }

    // if we have cpu percent
    String cpuPercent = slammerParams.getCpuPercent();
    if (cpuPercent != null) {
      runCmd = runCmd + " --cpu-percent " + cpuPercent;
    }

    // if we have vm
    String vm = slammerParams.getVm();
    if (vm != null) {
      runCmd = runCmd + " --vm " + vm;
    }

    // if we have vmSize
    String vmSize = slammerParams.getVmSize();
    if (vmSize != null) {
      runCmd = runCmd + " --vmsize " + vmSize;
    }

    // if we have fill
    String fill = slammerParams.getFill();
    if (fill != null) {
      runCmd = runCmd + " --fill " + fill;
    }

    // if we have chain
    String chain = slammerParams.getChain();
    if (chain != null) {
      runCmd = runCmd + " --chain " + chain;
    }

    // if we have ociimage
    String ociimage = slammerParams.getOciImage();
    if (ociimage != null) {
      runCmd = runCmd  + "--ocitype kubectl " + " --ociimage " + ociimage;
    }

    // run the command
    return exec(runCmd);
  }

  /**
   * List operation.
   * @param service slammer service name to list
   * @return true on success
   */
  public static boolean list(String service) {
    SlammerParams slammerParams = new SlammerParams().service(service).operation("list");
    return Slammer.run(slammerParams);
  }


  /**
   * Executes the given command.
   * @param command the command to execute
   * @return true on success, false otherwise
   */
  private static boolean exec(String command) {
    getLogger().info("Running command - \n" + command);
    try {
      ExecResult result = ExecCommand.exec(command, true);
      getLogger().info("The command returned exit value: "
          + result.exitValue() + " command output: "
          + result.stderr() + "\n" + result.stdout());
      if (result.exitValue() != 0) {
        getLogger().info("Command failed with errors " + result.stderr() + "\n" + result.stdout());
        return false;
      }
    } catch (Exception e) {
      getLogger().info("Got exception, command failed with errors " + e.getMessage());
      return false;
    }
    return true;
  }
}
