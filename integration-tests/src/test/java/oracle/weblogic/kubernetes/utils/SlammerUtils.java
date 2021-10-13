// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

import oracle.weblogic.kubernetes.actions.impl.primitive.Slammer;
import oracle.weblogic.kubernetes.actions.impl.primitive.SlammerParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.apache.commons.io.FileUtils;

public class SlammerUtils {

  /* CPU stress functions */

  /**
   * execute slammer command to stress cpu.
   * A cpu stress of around provided value of cpuPercent is infused
   * to the target machine for a provided timeout value ( in seconds) .
   *
   * @param cpuPercent - desired cpu percent
   * @param timeout    -time in seconds
   * @param propFile - slammer properties file or null
   */
  public static boolean stressByCpuPercentage(String cpuPercent, String timeout, String propFile) {
    SlammerParams slammerParams = new SlammerParams()
        .service("stress")
        .operation("infuse")
        .cpuPercent(cpuPercent)
        .timeout(timeout)
        .propertyFile(propFile);

    return Slammer.run(slammerParams);
  }

  /**
   * execute slammer command to stress by numbers of cpu.
   * A cpu stress of around provided value of cpuPercent is infused
   * to the target machine for a provided timeout value ( in seconds) .
   *
   * @param cpuNumber number of cpu to stress
   * @param timeout   time in seconds
   * @param propFile - slammer properties file or null
   */
  public static boolean stressByNumberOfCpus(String cpuNumber, String timeout, String propFile) {
    SlammerParams slammerParams = new SlammerParams()
        .service("stress")
        .operation("cpu")
        .cpuPercent(cpuNumber)
        .timeout(timeout)
        .propertyFile(propFile);

    return Slammer.run(slammerParams);
  }

  /* Network Latency Delay functions */

  /**
   * Add network delay to your target's network interface.
   * A network delay of delayTime im ms is added to the active interface
   * causing latency in response at the network layer,
   * this affects all applications running on the target host
   *
   * @param delayTime time to delay in milliseconds
   * @param propFile - slammer properties file or null
   */
  public static boolean addNetworkLatencyDelay(String delayTime, String propFile) {
    SlammerParams slammerParams = new SlammerParams()
        .service("network")
        .operation("add")
        .delay(delayTime)
        .propertyFile(propFile);
    return Slammer.run(slammerParams);
  }

  /**
   * Change network delay to your target's network interface.
   * A network delay of delayTime im ms is added to the active interface
   * causing latency in response at the network layer,
   * this affects all applications running on the target host
   * @param delayTime
   * @param propFile - slammer properties file or null
   **/
  public static boolean changeNetworkLatencyDelay(String delayTime, String propFile) {
    SlammerParams slammerParams = new SlammerParams()
        .service("network")
        .operation("change")
        .delay(delayTime)
        .propertyFile(propFile);
    return Slammer.run(slammerParams);
  }

  /**
   * Delete network delay to your target's network interface.
   * A network delay of delayTime im ms is deleted from the active interface
   * @param propFile - slammer properties file or null
   */
  public static boolean deleteNetworkLatencyDelay(String propFile) {
    SlammerParams slammerParams = new SlammerParams()
        .service("network")
        .operation("delete")
        .propertyFile(propFile);
    return Slammer.run(slammerParams);
  }

  /* Block traffic to target host */

  /**
   * Restrict traffic to specified port.
   *
   * @param operation        - block or delete
   * @param portNumber       - port number to restrict the traffic
   * @param trafficDirection incoming or outgoing
   * @param timeout          , optional timeout time in seconds
   * @param propertyFile    - slammer property file or null
   */
  public static boolean changeTraffic(String trafficDirection, String portNumber, String operation, String timeout, String propertyFile) {
    SlammerParams slammerParams = new SlammerParams()
        .service("iptables")
        .operation(operation)
        .port(portNumber)
        .traffic(trafficDirection)
        .propertyFile(propertyFile);
    if (timeout != null) {
      slammerParams.timeout(timeout);
    }
    return Slammer.run(slammerParams);
  }

  /**
   * Allow traffic coming to and from specified port.
   * of a target host of a custom iptable chain
   * Allows traffic on port on any chain of a target host,
   * this use case is common in OCI environments
   * where most ports are blocked by default and you want to open one
   *
   * @param portNumber - port number to restrict the traffic
   * @param chain      - custom iptable chain
   * @param propFile - slammer properties file or null
   */
  public static boolean allowTrafficToChain(String portNumber, String chain, String propFile) {
    SlammerParams slammerParams = new SlammerParams()
        .service("iptables")
        .operation("accept")
        .port(portNumber)
        .chain(chain)
        .propertyFile(propFile);
    return Slammer.run(slammerParams);
  }

  /**
   * Delete traffic coming to and from specified port.
   * of a target host of a custom iptable chain
   * Delete traffic on port on any chain of a target host, this use case
   * is used in conjunction
   * with the above accept operation to create a flakey connection
   *
   * @param portNumber - port number to restrict the traffic
   * @param chain      - custom iptable chain
   * @param propertyFile    - slammer property file or null
   */
  public static boolean deleteTrafficToChain(String portNumber, String chain, String propertyFile) {
    SlammerParams slammerParams = new SlammerParams()
        .service("iptables")
        .operation("delete")
        .port(portNumber)
        .chain(chain)
        .propertyFile(propertyFile);
    return Slammer.run(slammerParams);
  }

  /**
   * Backup a target host's iptables rules.
   * @param propFile - slammer properties file or null
   */
  public static boolean backupHostIptablesRules(String propFile) {
    SlammerParams slammerParams = new SlammerParams()
        .service("iptables")
        .operation("backup");
    return Slammer.run(slammerParams);
  }

  /**
   * Restore a target host's iptables rules.
   * @param propFile - slammer property file or null
   */
  public static boolean restoreHostIptablesRules(String propFile) {
    SlammerParams slammerParams = new SlammerParams()
        .service("iptables")
        .operation("restore")
        .propertyFile(propFile);
    return Slammer.run(slammerParams);
  }

  /* Memory Stress */

  /**
   * Memory Stress by Size and Threads.
   * A memory stress of vmsize (for example 2g) multiplied by number threads (vm) , for example 2,
   * to induce a total of 2 x 2g = 4g of memory hog to the target machine for a timeout of 10 seconds
   *
   * @param vm      - number of threads
   * @param vmSize  memory size in gigabytes
   * @param timeout - time in seconds
   * @param propertyFile    - slammer property file or null
   */
  public static boolean memoryStress(String vm, String vmSize, String timeout, String propertyFile) {
    SlammerParams slammerParams = new SlammerParams()
        .service("stress")
        .operation("operation")
        .vm(vm)
        .vmSize(vmSize)
        .timeout(timeout)
        .propertyFile(propertyFile);
    return Slammer.run(slammerParams);
  }

  /**
   * Setup slammer inside the pod.
   *
   * @param propFile      - slammer property file for pod
   */
  public static boolean setupSlammerInPod(String propFile) {
    SlammerParams slammerParams = new SlammerParams()
        .service("docker")
        .operation("setup")
        .debug(true)
        .propertyFile(propFile);
    return Slammer.run(slammerParams);
  }

  /**
   * Generate slammer property file to run slammer inside the pod.
   *
   * @param host  - localhost or ip address for remote host
   * @param email  - email for user
   * @param containerID  - docker container id for pod
   * @param fileName  - file name
   */
  public static String generateSlammerInPodPropertiesFile(String host,
                                                          String email,
                                                          String containerID,
                                                          String fileName) throws IOException
  {
    LoggingFacade logger = getLogger();
    logger.info("create a staging location for slammer property file");
    Path slammerDir = Paths.get(Slammer.getSlammerDir());

    Path srcPropFile = Paths.get(RESOURCE_DIR, "slammer", "slammer.props");
    Path targetPropFile = Paths.get(slammerDir.toString(), fileName);
    logger.info("copy the slammer.props to staging location" + targetPropFile.toString());
    Files.copy(srcPropFile, targetPropFile, StandardCopyOption.REPLACE_EXISTING);
    String oldValue = "@host@";
    replaceStringInFile(targetPropFile.toString(),
        "@host@",
        host);
    replaceStringInFile(targetPropFile.toString(),
        "@user@",
        Slammer.remoteuser);
    replaceStringInFile(targetPropFile.toString(),
        "@pass@",
        Slammer.remotepass);
    replaceStringInFile(targetPropFile.toString(),
        "@email@",
        email);
    replaceStringInFile(targetPropFile.toString(),
        "@containerID@",
        containerID);
    replaceStringInFile(targetPropFile.toString(),
        "@slammerSrcLocation@",
        Slammer.slammerSrcLocation);
    return targetPropFile.toString();
  }
}
