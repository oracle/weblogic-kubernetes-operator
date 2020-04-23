// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;


public class Docker {

  /**
   * Log in to a Docker registry.
   * @param registryName registry name
   * @param username user
   * @param password password
   * @return true if successfull
   */
  public static boolean login(String registryName, String username, String password) {
    String cmdToExecute = String.format("docker login %s -u %s -p \"%s\"",
        registryName, username, password);
    return new Command()
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute();
  }

  /**
   * Push an image to a registry.
   * @param image image
   * @return true if successfull
   */
  public static boolean push(String image) {
    String cmdToExecute = String.format("docker push %s", image);
    return new Command()
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute();
  }

  /**
   * Delete docker image.
   * @param image image name:image tag
   * @return true if delete image is successful
   */
  public static boolean deleteImage(String image) {
    String cmdToExecute = String.format("docker rmi -f %s", image);
    return new Command()
        .withParams(new CommandParams()
                  .command(cmdToExecute))
        .execute();
  }

}