// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.logging.Level;

public class RcuSecret extends Secret {

  private String sysUsername;
  private String sysPassword;

  public RcuSecret(
      String namespace,
      String secretName,
      String username,
      String password,
      String sysUsername,
      String sysPassword)
      throws Exception {
    this.namespace = namespace;
    this.secretName = secretName;
    this.username = username;
    this.password = password;
    this.sysUsername = sysUsername;
    this.sysPassword = sysPassword;

    // delete the secret first if exists
    deleteSecret();

    // create the secret
    String command =
        "kubectl -n "
            + this.namespace
            + " create secret generic "
            + this.secretName
            + " --from-literal=username="
            + this.username
            + " --from-literal=password="
            + this.password
            + " --from-literal=sys_username="
            + this.sysUsername
            + " --from-literal=sys_password="
            + this.sysPassword;
    log(Level.INFO, "Running " + command);
    ExecResult result = TestUtils.exec(command);
    log(Level.INFO, "command result " + result.stdout().trim());
  }

  public String getSysUsername() {
    return sysUsername;
  }

  public String getSysPassword() {
    return sysPassword;
  }

  private void deleteSecret() throws Exception {
    String command = "kubectl -n " + namespace + " delete secret " + secretName;
    log(Level.INFO, "Running " + command);
    ExecCommand.exec(command);
  }
  private void log(Level logLevel, String message) {
    LoggerHelper.getLocal().log(logLevel, message);
    LoggerHelper.getGlobal().log(logLevel, message);
  }
}
