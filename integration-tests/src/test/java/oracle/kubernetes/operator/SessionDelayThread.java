// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.utils.Domain;

class SessionDelayThread implements Runnable {
  long delayTime = 0;
  Domain domain = null;

  public SessionDelayThread(long delayTime, Domain domain) {
    this.delayTime = delayTime;
    this.domain = domain;
  }

  /**
   * send request to web app deployed on wls.
   *
   * @param delayTime - sleep time in mills to keep session alive
   * @throws Exception exception
   */
  private static void keepSessionAlive(long delayTime, Domain domain) throws Exception {
    String testAppPath = "httpsessionreptestapp/CounterServlet?delayTime=" + delayTime;
    ItPodsShutdown.callWebApp(testAppPath, domain, false);
  }

  @Override
  public void run() {
    try {
      keepSessionAlive(delayTime, domain);
    } catch (Exception e) {
      Thread.currentThread().interrupt();
      e.printStackTrace();
    }
  }
}
