// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.logging.Level;

public class Secret {

  protected String secretName;
  protected String namespace;
  protected String username;
  protected String password;

  public Secret() throws Exception {
    secretName = "";
  }

  /**
   * Construct secret.
   * @param namespace namespace
   * @param secretName name
   * @param username username
   * @param password password
   * @throws Exception on failure
   */
  public Secret(String namespace, String secretName, String username, String password)
      throws Exception {
    this.namespace = namespace;
    this.secretName = secretName;
    this.username = username;
    this.password = password;

    String command = "kubectl -n " + namespace + " delete secret " + secretName;
    LoggerHelper.getLocal().log(Level.INFO, "Running " + command);
    ExecCommand.exec(command);
    command =
        "kubectl -n "
            + this.namespace
            + ""
            + " create secret generic "
            + this.secretName
            + " --from-literal=username="
            + this.username
            + " --from-literal=password="
            + this.password;
    LoggerHelper.getLocal().log(Level.INFO, "Running " + command);
    ExecResult result = ExecCommand.exec(command);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command to create secret "
              + command
              + " failed, returned "
              + result.stdout()
              + "\n"
              + result.stderr());
    }
    LoggerHelper.getLocal().log(Level.INFO, "command result " + result.stdout().trim());
  }

  public String getSecretName() {
    return secretName;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

}
