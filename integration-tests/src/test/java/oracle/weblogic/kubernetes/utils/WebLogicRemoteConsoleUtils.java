// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.


package oracle.weblogic.kubernetes.utils;

import oracle.weblogic.kubernetes.actions.TestActions;

import static oracle.weblogic.kubernetes.actions.TestActions.installWlsRemoteConsole;
import static org.assertj.core.api.Assertions.assertThat;

public class WebLogicRemoteConsoleUtils {

  /**
   * Install WebLogic Remote Console.
   * @param domainNamespace namespace in which the domain will be created
   * @param adminServerPodName the name of the admin server pod
   *
   * @return true if WebLogic Remote Console is successfully installed, false otherwise.
   */
  public static boolean installAndVerifyWlsRemoteConsole(String domainNamespace, String adminServerPodName) {

    assertThat(installWlsRemoteConsole(domainNamespace, adminServerPodName))
        .as("WebLogic Remote Console installation succeeds")
        .withFailMessage("WebLogic Remote Console installation failed")
        .isTrue();

    return true;
  }

  /**
   * Shutdown WebLogic Remote Console.
   *
   * @return true if WebLogic Remote Console is successfully shutdown, false otherwise.
   */
  public static boolean shutdownWlsRemoteConsole() {

    assertThat(TestActions.shutdownWlsRemoteConsole())
        .as("WebLogic Remote Console shutdown succeeds")
        .withFailMessage("WebLogic Remote Console shutdown failed")
        .isTrue();

    return true;
  }

}
