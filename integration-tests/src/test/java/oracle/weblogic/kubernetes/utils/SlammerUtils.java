// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import oracle.weblogic.kubernetes.actions.impl.primitive.Slammer;
import oracle.weblogic.kubernetes.actions.impl.primitive.SlammerParams;

public class SlammerUtils {

  /* CPU stress functions */

  /**
   * execute slammer command to stress cpu.
   * A cpu stress of around provided value of cpuPercent is infused
   * to the target machine for a provided timeout value ( in seconds) .
   *
   * @param cpuPercent - desired cpu percent
   * @param timeout    -time in seconds
   */
  public static boolean stressByCpuPercentage(String cpuPercent, String timeout) {
    SlammerParams slammerParams = new SlammerParams()
        .service("stress")
        .operation("infuse")
        .cpuPercent(cpuPercent)
        .timeout(timeout);

    return Slammer.run(slammerParams);
  }

  /**
   * execute slammer command to stress by numbers of cpu.
   * A cpu stress of around provided value of cpuPercent is infused
   * to the target machine for a provided timeout value ( in seconds) .
   *
   * @param cpuNumber number of cpu to stress
   * @param timeout   time in seconds
   */
  public static boolean stressByNumberOfCpus(String cpuNumber, String timeout) {
    SlammerParams slammerParams = new SlammerParams()
        .service("stress")
        .operation("cpu")
        .cpuPercent(cpuNumber)
        .timeout(timeout);

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
   */
  public static boolean addNetworkLatencyDelay(String delayTime) {
    SlammerParams slammerParams = new SlammerParams()
        .service("network")
        .operation("add")
        .delay(delayTime);

    return Slammer.run(slammerParams);
  }

  /**
   * Change network delay to your target's network interface.
   * A network delay of delayTime im ms is added to the active interface
   * causing latency in response at the network layer,
   * this affects all applications running on the target host
   * @param delayTime
   **/
  public static boolean changeNetworkLatencyDelay(String delayTime) {
    SlammerParams slammerParams = new SlammerParams()
        .service("network")
        .operation("change")
        .delay(delayTime);

    return Slammer.run(slammerParams);
  }

  /**
   * Delete network delay to your target's network interface.
   * A network delay of delayTime im ms is deleted from the active interface
   */
  public static boolean deleteNetworkLatencyDelay() {
    SlammerParams slammerParams = new SlammerParams()
        .service("network")
        .operation("delete");
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
   */
  public static boolean changeTraffic(String trafficDirection, String portNumber, String operation, String timeout) {
    SlammerParams slammerParams = new SlammerParams()
        .service("iptables")
        .operation(operation)
        .port(portNumber)
        .traffic(trafficDirection);
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
   */
  public static boolean allowTrafficToChain(String portNumber, String chain) {
    SlammerParams slammerParams = new SlammerParams()
        .service("iptables")
        .operation("accept")
        .port(portNumber)
        .chain(chain);
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
   */
  public static boolean deleteTrafficToChain(String portNumber, String chain) {
    SlammerParams slammerParams = new SlammerParams()
        .service("iptables")
        .operation("delete")
        .port(portNumber)
        .chain(chain);
    return Slammer.run(slammerParams);
  }

  /**
   * Backup a target host's iptables rules.
   */
  public static boolean backupHostIptablesRules() {
    SlammerParams slammerParams = new SlammerParams()
        .service("iptables")
        .operation("backup");
    return Slammer.run(slammerParams);
  }

  /**
   * Restore a target host's iptables rules.
   */
  public static boolean restoreHostIptablesRules() {
    SlammerParams slammerParams = new SlammerParams()
        .service("iptables")
        .operation("restore");
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
   */
  public static boolean memoryStress(String vm, String vmSize, String timeout) {
    SlammerParams slammerParams = new SlammerParams()
        .service("stress")
        .operation("operation")
        .vm(vm)
        .vmSize(vmSize)
        .timeout(timeout);
    return Slammer.run(slammerParams);
  }
}
